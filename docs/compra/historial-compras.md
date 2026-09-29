# Consulta del historial de compras del cliente (SCRUM-16)

Diseño de la consulta que usa el Agente de Atención al Cliente para ver, en orden cronológico, las compras de un cliente (RF-08, EPIC-02).

## Fuentes de datos

| Servicio | Tabla / dato | Qué aporta |
|---|---|---|
| `servicio-comportamiento` | `comportamiento.compra` + `comportamiento.compra_item` | Las compras confirmadas (fecha, monto, ítems por categoría), ya registradas por la ingesta de SCRUM-20. Índice `idx_compra_cliente (id_cliente_origen, fecha DESC)`: pensado para listarlas en orden cronológico por identificador del cliente |
| `servicio-perfil` | `GET /api/perfil/clientes/{clienteId}` → `identificadoresOrigen` | Los identificadores del cliente en Marketplace y Ventas vinculados a su perfil único (RF-62, ver `docs/perfil/identificadores-origen.md`) |

## El problema: `compra` no conoce el perfil unificado

`comportamiento.compra` identifica al cliente por `id_cliente_origen` —el identificador que usa Marketplace y Ventas— porque `servicio-comportamiento` ingiere sus eventos de forma independiente de `servicio-perfil` (cada uno consume su propia cola de RabbitMQ). Un mismo cliente puede tener compras bajo más de un identificador (por ejemplo, si se unificaron dos perfiles duplicados), y hay que combinarlas.

## Estrategia de consulta

La consulta se compone en dos pasos, sin acoplar los servicios entre sí:

```
1. GET /api/perfil/clientes/{clienteId}
   → identificadoresOrigen: [CLI-5521, mp-user-9001]

2. GET /api/comportamiento/clientes/{clienteId}/compras
     ?identificador=CLI-5521
     &identificador=mp-user-9001
     &pagina=0&tamanio=20
   → compras de todos los identificadores, ordenadas por fecha desc, paginadas
```

`{clienteId}` va en la ruta porque `docs/seguridad/convencion-rutas-clientes.md` lo exige para toda ruta de datos de un cliente (así lo audita el Gateway), pero `servicio-comportamiento` no lo usa para consultar: la búsqueda real se hace con los `identificador` de la query.

## Consulta en `servicio-comportamiento` (SCRUM-194)

`ComprasDelCliente.deIdentificadores` arma una `Specification` con `idClienteOrigen IN (...)`, compartida por el historial y los indicadores. Paginación y orden con el mismo patrón que `ConsultaBitacora`: `PageRequest.of(pagina, tamanio, Sort.by(Sort.Order.desc("fecha")))`, con el tope `TAMANIO_MAXIMO_PAGINA` (100).

## Qué devuelve (SCRUM-197)

Cada compra responde con `fecha`, `referencia` (el `idCompraOrigen`), `montoTotal`, `estado` y sus `items` (`categoria`, `cantidad`, `monto`). El contrato final de la respuesta está en `docs/compra/formato-historial-compras.md`.

## Cliente sin compras

Si `identificadoresOrigen` viene vacío o ninguno tiene compras, el endpoint responde una página vacía (`content: []`, `total: 0`), no un error — cubre el criterio de aceptación 2. No hace falta lógica especial: es el comportamiento natural de una consulta sin resultados.

## Control de acceso (para SCRUM-196)

No hace falta agregar nada nuevo: el permiso `INDICADORES_CONSULTAR` ("Consultar historial de compras e indicadores de comportamiento") ya existe, ya lo tiene el Agente de Atención (además de Administrador de CRM y Gerente Comercial), y la regla del Gateway `/api/comportamiento/** GET → INDICADORES_CONSULTAR` ya aplica (`docs/seguridad/matriz-permisos.md`). Alcanza con que el nuevo endpoint viva bajo `/api/comportamiento/**` y no bajo `/api/comportamiento/eventos/**` (que exige `EVENTOS_REPROCESAR`, un permiso distinto). El acceso denegado y su auditoría (criterio de aceptación 3) ya los cubre el Gateway para toda la ruta.

## Resumen de impacto en las demás subtareas de SCRUM-16

| Subtarea | Estado tras este análisis |
|---|---|
| SCRUM-192 Backend: orden cronológico | Ya resuelto por el índice `idx_compra_cliente`; se concreta al implementar la `Specification` + `Sort` de arriba |
| SCRUM-193 Backend: habilitar la consulta para el cliente | No existe ningún actor "Cliente" (portal de autoservicio) en el proyecto: la matriz de permisos solo define Administrador, Agente y Gerente, todos con JWT del Gateway. El mismo endpoint de SCRUM-194 ya es agnóstico de quién consulta (no distingue "agente" de "cliente", solo exige `INDICADORES_CONSULTAR`); cuando exista un portal de autoservicio con su propio actor, reutiliza este endpoint con un permiso propio para ese rol. Sin cambios de código |
| SCRUM-194 Backend: endpoint | Implementado: `HistorialComprasController` + `ConsultaHistorialCompras` + `Identificador` en `servicio-comportamiento` |
| SCRUM-195 Frontend: vista | Implementada: `historial-compras.component.ts` en `frontend/crm-web`, ruta `/clientes/:id/compras` |
| SCRUM-196 Backend: restricción por rol | Ya cubierto por la configuración existente del Gateway, sin cambios |
| SCRUM-197 Análisis: formato | Cerrado en `docs/compra/formato-historial-compras.md` |
