# Consulta de preferencias — SCRUM-43 / RF-35

La consulta se abre desde **Clientes → Preferencias** o desde las pestañas de un cliente.
Ruta Angular: `/clientes/:id/preferencias`. Usa el ID interno del CRM obtenido de la búsqueda
existente por documento o identificador de Marketplace y Ventas.

## Alcance y reglas — SCRUM-376

Se muestran categorías, conforme a la historia y SCRUM-376; las menciones de productos en
SCRUM-372/373 no corresponden al contrato de eventos disponible. Las sugerencias son SCRUM-44.

La tabla conserva el orden persistido por SCRUM-41: monto vigente descendente, cantidad de
compras descendente y nombre ascendente como desempate. Muestra posición, categoría, compras,
unidades y monto en bolivianos. La primera categoría se destaca como preferencia principal.
La fecha indica cuándo se actualizó el perfil, no cuándo se abrió la pantalla.

La consulta **no recalcula ni escribe preferencias**. El botón Volver a consultar repite el GET.
Las compras y anulaciones actualizan el perfil a través de SCRUM-42. La auditoría del Gateway
puede registrar el acceso sin modificar el perfil ni su historial de actualizaciones.

## Integración — SCRUM-373 y SCRUM-375

Se reutilizan `PreferenciasController`, `PerfilesPreferencias.consultar` y
`PerfilPreferenciasRepository.obtener`. No hay endpoint paralelo, migración ni servicio nuevo.

`PreferenciasService` de Angular consume, con el interceptor JWT existente:

```http
GET /api/comportamiento/clientes/{clienteId}/preferencias
```

Respuesta existente: `clienteId`, `construido`, `sinPreferencias`, `construidoEn`, `version`,
`criterio`, `categorias`, `origen`, `idEventoOrigen`. Cada categoría contiene `posicion`,
`categoria`, `compras`, `unidades`, `monto`. Los metadatos técnicos no se presentan al usuario.
El backend valida la identidad con Servicio Perfil antes de leer la instantánea PostgreSQL.

| Caso | Comportamiento |
|---|---|
| Perfil con categorías | Tabla por relevancia y categoría principal |
| `construido=false` | Preferencias aún no disponibles |
| Perfil construido vacío | Sin preferencias registradas, sin error |
| ID inválido | Mensaje de validación; Angular evita la solicitud inválida |
| 401 | Interceptor cierra sesión y lleva al login |
| 403 | Mensaje de acceso denegado y eliminación del resultado anterior |
| 404 | Cliente no encontrado |
| 409 | Mensaje del servidor sobre cliente consolidado |
| 503/red | Error con reintento, nunca perfil vacío |
| Cambio de cliente | `switchMap` cancela la consulta anterior y limpia los datos mostrados |

## Acceso — SCRUM-374

Requiere rol `ADMINISTRADOR_CRM` o `AGENTE_ATENCION` **y** permiso
`FICHA_INTEGRAL_CONSULTAR`. `AuthService.puedeConsultarPreferencias` centraliza la condición de
la ruta, el enlace y la pestaña. El control definitivo sigue en `SecurityConfig` del Gateway,
antes de la regla genérica de comportamiento. No se requiere `SEGMENTOS_CONSULTAR`.

## Pantalla — SCRUM-372

Componente standalone con estados explícitos de carga, resultado y error. Reutiliza estilos,
pestañas y búsqueda de clientes. La navegación se ajusta al ancho móvil; la tabla dispone de
desplazamiento horizontal cuando sea necesario. Las peticiones se cancelan al destruir el
componente o cambiar el cliente. Un reintento no mantiene categorías antiguas durante la carga.

## Pruebas — SCRUM-377

Desde la raíz del repositorio, con Java 17+, Maven, Node y Docker disponibles:

```powershell
mvn -f backend/servicio-comportamiento/pom.xml "-Dtest=ConstructorPreferenciasTest,IdentificadoresPreferenciasTest,PreferenciasIntegracionTest,ActualizacionPreferenciasIntegracionTest" test
mvn -f backend/api-gateway/pom.xml "-Dtest=AccesoPorRolTest,AuditoriaAccesoFilterTest" test
./herramientas/pruebas-scrum43/ejecutar.ps1
```

El ejecutor instala dependencias, compila los tres servicios y Angular y ejecuta únicamente
`e2e/scrum43.spec.ts`. `-ReutilizarDependencias` omite `npm ci` si ya están instaladas.
Los tests Maven anteriores se ejecutan por separado; el empaquetado del ejecutor los omite.

Usa PostgreSQL temporal con datos sintéticos, credenciales aleatorias y puertos de prueba
18080/18081/18082/14200. Se detiene si están ocupados. Arranca Gateway, Perfil y Comportamiento
reales, desactiva consumidores RabbitMQ para este escenario de lectura y usa la API real en los
flujos normales, sin conectarse a la base de desarrollo. Al finalizar elimina su contenedor y
detiene solo los procesos que inició. Logs en `logs/scrum43`; reporte y capturas en los directorios
de Playwright ignorados por Git.

Las pruebas incluyen orden persistido, categoría única, perfil vacío/pendiente, búsqueda y
navegación, roles permitidos/denegados, identidad inválida/inexistente/consolidada, recarga sin
escrituras y cambio de cliente. Los casos de fallo 503, respuesta 403 posterior y variaciones de
sesión local se simulan en el navegador; los permisos reales se comprueban además en Gateway.
La persistencia y actualización por eventos se prueban con Testcontainers.

## Diagramas — SCRUM-590 y cierre

Resultados ejecutados: [verificación de SCRUM-43](verificacion-consulta.md).

Ver [diagramas de consulta](consulta-diagramas.md). El cierre de Jira requiere adjuntarlos,
revisión de un par y merge además de las pruebas. Esta implementación se basa en `main`
`6d366f0` y no incorpora los siete commits descartados de la segunda implementación de SCRUM-28.
No se modifican automáticamente estados ni asignaciones de Jira.
