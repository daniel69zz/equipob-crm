# Matriz de permisos por rol

Los permisos son la unidad de control de acceso. El API Gateway valida el permiso requerido por cada ruta a partir del token JWT del usuario.

## Permisos

| Permiso | Qué habilita |
|---|---|
| `CLIENTE_CONSULTAR` | Consultar perfil, ficha integral e historial de cambios del cliente |
| `CLIENTE_EDITAR` | Modificar perfiles, consentimiento y unificar duplicados |
| `INDICADORES_CONSULTAR` | Consultar historial de compras e indicadores de comportamiento |
| `EVENTOS_REPROCESAR` | Ver la bitácora de ingesta y reprocesar eventos fallidos |
| `SEGMENTOS_CONSULTAR` | Consultar segmentos y clientes por segmento |
| `SEGMENTACION_CONFIGURAR` | Configurar criterios y reglas de segmentación |
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
| `EVENTOS_REPROCESAR` | ✅ | | |
| `SEGMENTOS_CONSULTAR` | ✅ | | ✅ |
| `SEGMENTACION_CONFIGURAR` | ✅ | | |
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
| `/api/auth/login` | POST | Público |
| `/api/auth/**` | Todos | Usuario autenticado |
| `/api/admin/auditoria/**` | Todos | `AUDITORIA_CONSULTAR` |
| `/api/admin/**` | Todos | `USUARIOS_ADMINISTRAR` |
| `/api/comportamiento/eventos/**` | Todos | `EVENTOS_REPROCESAR` |
| `/api/perfil/**` | GET | `CLIENTE_CONSULTAR` |
| `/api/perfil/**` | POST, PUT, PATCH, DELETE | `CLIENTE_EDITAR` |
| `/api/comportamiento/**` | GET | `INDICADORES_CONSULTAR` |
| `/api/segmentacion/**` | GET | `SEGMENTOS_CONSULTAR` |
| `/api/segmentacion/**` | POST, PUT, PATCH, DELETE | `SEGMENTACION_CONFIGURAR` |
| `/api/fidelizacion/**` | GET | `PUNTOS_CONSULTAR` |
| `/api/fidelizacion/**` | POST, PUT, PATCH, DELETE | `FIDELIZACION_CONFIGURAR` |
| `/api/interacciones/**` | GET | `INTERACCIONES_CONSULTAR` |
| `/api/interacciones/**` | POST, PUT, PATCH, DELETE | `INTERACCIONES_REGISTRAR` |
| Cualquier otra | — | Denegado |

## Respuestas

- Sin token o con token inválido o vencido: **401 No autenticado**.
- Con token válido pero sin el permiso requerido: **403 Acceso denegado**.

## Identidad hacia los microservicios

Cuando la petición pasa el control de acceso, el Gateway la reenvía al microservicio con estas cabeceras. Las que traiga el cliente se descartan para que no puedan falsificarse:

| Cabecera | Contenido |
|---|---|
| `X-Usuario` | Nombre de usuario autenticado |
| `X-Usuario-Rol` | Código del rol |
| `X-Usuario-Permisos` | Permisos separados por comas |
