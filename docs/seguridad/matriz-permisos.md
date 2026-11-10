# Matriz de permisos por rol

Los permisos son la unidad de control de acceso. El API Gateway valida el permiso requerido por cada ruta a partir del token JWT del usuario.

## Permisos

| Permiso | Qué habilita |
|---|---|
| `CLIENTE_CONSULTAR` | Consultar perfil, ficha integral e historial de cambios del cliente |
| `CLIENTE_EDITAR` | Modificar perfiles, consentimiento y unificar duplicados |
| `INDICADORES_CONSULTAR` | Consultar historial de compras e indicadores de comportamiento |
| `EVOLUCION_CONSUMO_CONSULTAR` | Consultar la evolución del consumo del cliente por categoría (SCRUM-32) |
| `SUGERENCIAS_CONSULTAR` | Consultar las categorías afines sugeridas para un cliente (SCRUM-44) |
| `EVENTOS_REPROCESAR` | Ver la bitácora de ingesta y reprocesar eventos fallidos |
| `SEGMENTOS_CONSULTAR` | Consultar segmentos, clientes por segmento e historial de segmentos del cliente |
| `SEGMENTACION_CONFIGURAR` | Configurar criterios y reglas de segmentación |
| `REPORTES_CONSULTAR` | Consultar los reportes de clientes: distribución (SCRUM-57), comportamiento de compra (SCRUM-58), panel de indicadores (SCRUM-59) y comparativo entre periodos (SCRUM-62) |
| `REPORTES_EXPORTAR` | Exportar los reportes de clientes a CSV y PDF (SCRUM-60, SCRUM-482). Se exige junto con `REPORTES_CONSULTAR` |
| `PUNTOS_CONSULTAR` | Consultar saldo y nivel de puntos |
| `FIDELIZACION_CONFIGURAR` | Configurar reglas de puntos, niveles y ajustes manuales |
| `INTERACCIONES_CONSULTAR` | Consultar el historial de interacciones |
| `INTERACCIONES_REGISTRAR` | Registrar, clasificar y asignar interacciones |
| `USUARIOS_ADMINISTRAR` | Crear usuarios internos, asignar roles y administrar permisos |
| `AUDITORIA_CONSULTAR` | Consultar los registros de auditoría |

## Matriz

| Permiso | Administrador de CRM | Agente de Atención | Gerente Comercial |
|---|:---:|:---:|:---:|
| `CLIENTE_CONSULTAR` | ✅ | ✅ | ✅ |
| `CLIENTE_EDITAR` | ✅ | | |
| `INDICADORES_CONSULTAR` | ✅ | ✅ | ✅ |
| `EVOLUCION_CONSUMO_CONSULTAR` | ✅ | | ✅ |
| `SUGERENCIAS_CONSULTAR` | ✅ | | ✅ |
| `EVENTOS_REPROCESAR` | ✅ | | |
| `SEGMENTOS_CONSULTAR` | ✅ | | ✅ |
| `SEGMENTACION_CONFIGURAR` | ✅ | | |
| `REPORTES_CONSULTAR` | ✅ | | ✅ |
| `REPORTES_EXPORTAR` | ✅ | | ✅ |
| `PUNTOS_CONSULTAR` | ✅ | ✅ | ✅ |
| `FIDELIZACION_CONFIGURAR` | ✅ | | |
| `INTERACCIONES_CONSULTAR` | ✅ | ✅ | ✅ |
| `INTERACCIONES_REGISTRAR` | ✅ | ✅ | |
| `USUARIOS_ADMINISTRAR` | ✅ | | |
| `AUDITORIA_CONSULTAR` | ✅ | | |

## Permiso requerido por ruta en el API Gateway

Las reglas se evalúan en este orden; la primera que coincide decide.

| Ruta | Método | Permiso |
|---|---|---|
| `/api/auth/login` y `/api/auth/servicio/token` | POST | Público (el segundo emite el token de servicio, SCRUM-545) |
| `/api/auth/**` | Todos | Usuario autenticado |
| `/api/admin/auditoria/**` | Todos | `AUDITORIA_CONSULTAR` |
| `/api/admin/**` | Todos | `USUARIOS_ADMINISTRAR` |
| `/api/comportamiento/eventos/**` | Todos | `EVENTOS_REPROCESAR` |
| `/api/perfil/**` | GET | `CLIENTE_CONSULTAR` |
| `/api/perfil/**` | POST, PUT, PATCH, DELETE | `CLIENTE_EDITAR` |
| `/api/comportamiento/clientes/*/evolucion-consumo` | GET | `EVOLUCION_CONSUMO_CONSULTAR` |
| `/api/comportamiento/clientes/*/sugerencias-categorias` | GET | `SUGERENCIAS_CONSULTAR` |
| `/api/comportamiento/clientes/*/preferencias/construir` | POST | Rol `ADMINISTRADOR_CRM` y `CLIENTE_EDITAR` |
| `/api/comportamiento/clientes/*/preferencias` | GET | Rol `ADMINISTRADOR_CRM` o `AGENTE_ATENCION`, y `FICHA_INTEGRAL_CONSULTAR` |
| `/api/comportamiento/comparacion-periodos` | GET | `INDICADORES_CONSULTAR` **y** `SEGMENTOS_CONSULTAR` (regla anterior a la genérica) |
| `/api/comportamiento/reportes/clientes-activos` | Todos | Denegado: consulta interna de los clientes activos del panel (SCRUM-475), no para usuarios |
| `/api/comportamiento/reportes/*/exportar` y `/api/comportamiento/reportes/comparativo/csv` | GET | `REPORTES_CONSULTAR` **y** `REPORTES_EXPORTAR`: exportación de los reportes a CSV y PDF (SCRUM-60, SCRUM-482; regla anterior a la de los reportes). servicio-comportamiento lo vuelve a comprobar |
| `/api/comportamiento/reportes/**` | GET | `REPORTES_CONSULTAR`: reporte de frecuencia, valor y categorías de compra (SCRUM-58) y comparativo entre periodos (SCRUM-62); regla anterior a la genérica. Los métodos de escritura se deniegan |
| `/api/comportamiento/**` | GET | `INDICADORES_CONSULTAR` |
| `/api/segmentacion/criterios` y `/api/segmentacion/criterios/**` | Todos | Rol `ADMINISTRADOR_CRM` **y** `SEGMENTACION_CONFIGURAR` (regla anterior a la genérica) |
| `/api/segmentacion/clientes/*/historial-segmentos` | GET | `SEGMENTOS_CONSULTAR`; los métodos de escritura y las subrutas se deniegan antes de la regla genérica |
| `/api/segmentacion/reportes/**` | GET | `REPORTES_CONSULTAR`: reportes de clientes y panel de indicadores (SCRUM-57, SCRUM-59, SCRUM-462, regla anterior a la genérica). Los métodos de escritura se deniegan |
| `/api/segmentacion/**` | GET | `SEGMENTOS_CONSULTAR` |
| `/api/segmentacion/**` | POST, PUT, PATCH, DELETE | `SEGMENTACION_CONFIGURAR` |
| `/api/fidelizacion/clientes/*/saldo` | GET | Rol `ADMINISTRADOR_CRM` o `AGENTE_ATENCION`, y `PUNTOS_CONSULTAR` (SCRUM-397, regla anterior a la genérica) |
| `/api/fidelizacion/ventas/clientes/*/puntos` | GET | Rol `SISTEMA` **y** `PUNTOS_CONSULTAR_VENTAS`: solo el token de servicio de Marketplace y Ventas (RIO-CRM-04, SCRUM-545) |
| `/api/fidelizacion/clientes/*/ajustes` | POST | Rol `ADMINISTRADOR_CRM` **y** `FIDELIZACION_CONFIGURAR`: ajuste manual del saldo (SCRUM-424, regla anterior a la genérica). El historial (`GET`) sigue la regla genérica de `PUNTOS_CONSULTAR` |
| `/api/fidelizacion/reportes/**` | Todos | Denegado: consultas internas de servicio-segmentacion para el corte de niveles del reporte (SCRUM-464) y los puntos del panel (SCRUM-476), no para usuarios |
| `/api/fidelizacion/**` | GET | `PUNTOS_CONSULTAR` (incluye `/api/fidelizacion/caducidad/configuracion`, SCRUM-416) |
| `/api/fidelizacion/**` | POST, PUT, PATCH, DELETE | `FIDELIZACION_CONFIGURAR` (incluye `POST /api/fidelizacion/caducidad/ejecutar`, que el servicio limita además al rol `ADMINISTRADOR_CRM`, SCRUM-415) |
| `/api/interacciones/clientes/*/historial` | GET | Rol `ADMINISTRADOR_CRM` o `AGENTE_ATENCION`, con `INTERACCIONES_CONSULTAR` e `INDICADORES_CONSULTAR`: historial del cliente con sus compras (SCRUM-53) |
| `/api/interacciones/**` | GET | `INTERACCIONES_CONSULTAR` |
| `/api/interacciones/**` | POST, PUT, PATCH, DELETE | `INTERACCIONES_REGISTRAR` (servicio-interacciones exige además el rol `ADMINISTRADOR_CRM` o `AGENTE_ATENCION`, y un Agente solo modifica las interacciones que registró; SCRUM-435. El estado y el tipo los actualiza cualquier Agente o el Administrador, y solo el Administrador reabre una interacción cerrada; SCRUM-55. Vincular o desvincular una compra (`…/interacciones/*/compra`) exige además `INDICADORES_CONSULTAR`, y la compra elegible se busca en `GET /api/interacciones/clientes/*/compras` con los mismos permisos que el historial del cliente; SCRUM-54. Asignar o reasignar el responsable (`…/interacciones/*/responsable`) y listar los agentes (`GET /api/interacciones/agentes`) lo hace solo el rol `ADMINISTRADOR_CRM`; las notificaciones (`/api/interacciones/notificaciones`) son las del propio usuario; SCRUM-56) |
| Cualquier otra | — | Denegado |

## Respuestas

- Sin token o con token inválido o vencido: **401 No autenticado**.
- Con token válido pero sin el permiso requerido: **403 Acceso denegado**.

## Clientes de servicio (SCRUM-545)

Los sistemas del ERP que consultan el CRM no son usuarios internos: obtienen un **token JWT de servicio** con
`POST /api/auth/servicio/token` (cliente y secreto) y tienen el rol `SISTEMA` con los permisos de su
configuración (`crm.seguridad.servicios.clientes` en el Gateway). Hoy existe uno:

| Cliente | Sistema | Secreto | Permisos |
|---|---|---|---|
| `marketplace-ventas` | Marketplace y Ventas | Variable `SERVICIO_VENTAS_SECRETO` (mínimo 32 caracteres; sin ella queda deshabilitado) | `PUNTOS_CONSULTAR_VENTAS` |

`PUNTOS_CONSULTAR_VENTAS` no está en la tabla `seguridad.permiso`, así que no se puede asignar a un rol interno.
El token lleva `tipo=SERVICIO` y el Gateway lo valida contra los clientes habilitados, no contra los usuarios.
El contrato está en `docs/contratos-eventos/RIO-CRM-04-consulta-puntos.md`.

## Identidad hacia los microservicios

Cuando la petición pasa el control de acceso, el Gateway la reenvía al microservicio con estas cabeceras. Las que traiga el cliente se descartan para que no puedan falsificarse:

| Cabecera | Contenido |
|---|---|
| `X-Usuario` | Nombre de usuario autenticado, o el cliente de servicio |
| `X-Usuario-Rol` | Código del rol |
| `X-Usuario-Permisos` | Permisos separados por comas |
