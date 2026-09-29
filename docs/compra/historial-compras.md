# Consulta del historial de compras del cliente (SCRUM-16)

Diseño de la consulta que usa el Agente de Atención al Cliente para ver, en orden cronológico, las compras de un cliente (RF-08, EPIC-02).

## Fuentes de datos

| Servicio | Tabla / dato | Qué aporta |
|---|---|---|
| `servicio-comportamiento` | `comportamiento.compra` + `comportamiento.compra_item` | Las compras confirmadas (fecha, monto, canal, ítems por categoría), ya registradas por la ingesta de SCRUM-20. Índice `idx_compra_cliente (origen, id_cliente_origen, fecha DESC)`: ya está pensado para listarse en orden cronológico por identificador de origen |
| `servicio-perfil` | `GET /api/perfil/clientes/{clienteId}` → `identificadoresOrigen` | Los pares `(origen, idCliente)` de Marketplace y Ventas vinculados al perfil único del cliente (RF-62, ver `docs/perfil/identificadores-origen.md`) |

## El problema: `compra` no conoce el perfil unificado

`comportamiento.compra` identifica al cliente por `(origen, id_cliente_origen)` — el identificador crudo del canal — porque `servicio-comportamiento` ingiere sus eventos de forma independiente de `servicio-perfil` (cada uno consume su propia cola de RabbitMQ; **no hay llamadas sincrónicas entre microservicios en este proyecto**). Un mismo cliente puede tener compras bajo dos identificadores distintos (uno de Marketplace, otro de Ventas) que hay que combinar.

## Estrategia de consulta propuesta

Componer la consulta en dos pasos, sin acoplar los servicios entre sí (mantiene la arquitectura actual, donde la composición entre servicios ya ocurre en el front, por ejemplo en la ficha del cliente):

```
1. GET /api/perfil/clientes/{clienteId}
   → identificadoresOrigen: [(MARKETPLACE, mp-user-3307), (VENTAS, CLI-5521)]

2. GET /api/comportamiento/clientes/{clienteId}/compras
     ?identificador=MARKETPLACE:mp-user-3307
     &identificador=VENTAS:CLI-5521
     &pagina=0&tamanio=20
   → compras de ambos identificadores, ordenadas por fecha desc, paginadas
```

El paso 1 ya existe. El paso 2 es el endpoint que desarrolla SCRUM-194. `{clienteId}` va en la ruta porque `docs/seguridad/convencion-rutas-clientes.md` lo exige para toda ruta de datos de un cliente (así lo audita el Gateway), pero `servicio-comportamiento` no lo usa para consultar: no conoce el perfil unificado (ver el problema de arriba), así que la búsqueda real se hace con los `identificador=ORIGEN:idCliente` de la query.

Se pasa `identificador=ORIGEN:idCliente` como un único parámetro repetible (en vez de dos listas paralelas `origen=`/`idCliente=`) para no arriesgar un desalineamiento entre listas al combinarlas en el backend.

## Consulta en `servicio-comportamiento` (para SCRUM-194)

`CompraRepository` hoy solo tiene `findByOrigenAndIdCompraOrigen` (para la idempotencia de la ingesta). Para el historial hace falta una consulta por **múltiples pares** `(origen, idClienteOrigen)`, no por el producto cruzado de dos `IN` (un `origen IN (...) AND idClienteOrigen IN (...)` mezclaría pares que no corresponden al mismo cliente si dos clientes distintos comparten un identificador de canal).

Se recomienda seguir el mismo patrón que `FiltroBitacora.comoEspecificacion()` (`Specification` con una condición OR por cada par):

```java
Specification<Compra> deIdentificadores(List<Identificador> identificadores) {
    return (compra, consulta, cb) -> {
        List<Predicate> pares = identificadores.stream()
                .map(id -> cb.and(cb.equal(compra.get("origen"), id.origen()),
                                   cb.equal(compra.get("idClienteOrigen"), id.idCliente())))
                .toList();
        return cb.or(pares.toArray(Predicate[]::new));
    };
}
```

Paginación y orden con el mismo patrón que `ConsultaBitacora`: `PageRequest.of(pagina, tamanio, Sort.by(Sort.Order.desc("fecha")))`, con el mismo tope `TAMANIO_MAXIMO_PAGINA` (100) — cubre el criterio de aceptación de paginación.

## Qué devuelve (para SCRUM-197)

Con los datos que ya tiene el modelo, cada compra puede responder con: `fecha`, `referencia` (el `idCompraOrigen`), `origen` (canal), `montoTotal`, `estado` y sus `items` (`categoria`, `cantidad`, `monto`) — cubre el criterio de aceptación 1 (fecha, monto, canal y productos) y los campos que pide SCRUM-197 (fecha, productos, importe, estado y referencia de compra). El contrato final de la respuesta queda en `docs/compra/formato-historial-compras.md`.

## Cliente sin compras

Si `identificadoresOrigen` viene vacío o ninguno tiene compras, el endpoint responde una página vacía (`content: []`, `total: 0`), no un error — cubre el criterio de aceptación 2. No hace falta lógica especial: es el comportamiento natural de una consulta sin resultados.

## Control de acceso (para SCRUM-196)

No hace falta agregar nada nuevo: el permiso `INDICADORES_CONSULTAR` ("Consultar historial de compras e indicadores de comportamiento") ya existe, ya lo tiene el Agente de Atención (además de Administrador de CRM y Gerente Comercial), y la regla del Gateway `/api/comportamiento/** GET → INDICADORES_CONSULTAR` ya aplica (`docs/seguridad/matriz-permisos.md`). Alcanza con que el nuevo endpoint viva bajo `/api/comportamiento/**` y no bajo `/api/comportamiento/eventos/**` (que exige `EVENTOS_REPROCESAR`, un permiso distinto). El acceso denegado y su auditoría (criterio de aceptación 3) ya los cubre el Gateway para toda la ruta.

## Resumen de impacto en las demás subtareas de SCRUM-16

| Subtarea | Estado tras este análisis |
|---|---|
| SCRUM-192 Backend: orden cronológico | Ya resuelto por el índice `idx_compra_cliente`; se concreta al implementar la `Specification` + `Sort` de arriba |
| SCRUM-193 Backend: habilitar la consulta para el cliente | No existe ningún actor "Cliente" (portal de autoservicio) en el proyecto: la matriz de permisos solo define Administrador, Agente y Gerente, todos con JWT del Gateway. El mismo endpoint de SCRUM-194 ya es agnóstico del canal (no distingue "agente" de "cliente", solo exige `INDICADORES_CONSULTAR`); cuando exista un portal de autoservicio con su propio actor, reutiliza este endpoint con un permiso propio para ese rol. Sin cambios de código |
| SCRUM-194 Backend: endpoint | Implementado: `HistorialComprasController` + `ConsultaHistorialCompras` + `Identificador` en `servicio-comportamiento` |
| SCRUM-195 Frontend: vista | Implementada: `historial-compras.component.ts` en `frontend/crm-web`, ruta `/clientes/:id/compras` |
| SCRUM-196 Backend: restricción por rol | Ya cubierto por la configuración existente del Gateway, sin cambios |
| SCRUM-197 Análisis: formato | Cerrado en `docs/compra/formato-historial-compras.md` |
