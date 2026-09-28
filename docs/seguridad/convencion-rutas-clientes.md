# Convención de rutas para datos de clientes

Guía para los microservicios. El API Gateway audita las peticiones **según la forma de la ruta** (ver `alcance-auditoria.md`). Si una ruta que trae o cambia datos de un cliente no sigue esta convención, **no queda registrada en la auditoría**.

## La regla

Toda ruta que consulte o modifique datos de clientes va bajo `clientes`, justo después del prefijo del servicio:

```
/api/{servicio}/clientes                  → listados y búsquedas de clientes
/api/{servicio}/clientes/{clienteId}      → un cliente concreto
/api/{servicio}/clientes/{clienteId}/...  → cualquier dato de ese cliente
```

| Servicio | Prefijo | Puerto local |
|---|---|---|
| Perfil del Cliente | `/api/perfil` | 8081 |
| Comportamiento de Compra | `/api/comportamiento` | 8082 |
| Segmentación | `/api/segmentacion` | 8083 |
| Fidelización | `/api/fidelizacion` | 8084 |
| Interacciones | `/api/interacciones` | 8085 |

El Gateway reenvía la **ruta completa**, sin quitar el prefijo: el controlador del servicio de perfil debe mapear `/api/perfil/clientes/{clienteId}`, no `/clientes/{clienteId}`.

## Reglas de detalle

1. **Después de `/clientes/` siempre va un identificador.** El Gateway toma ese segmento como el cliente afectado. Una ruta `/clientes/inactivos` quedaría registrada como si `inactivos` fuera un cliente. Los listados filtrados usan parámetros: `/clientes?estado=inactivo`.
2. **`{clienteId}` es el identificador interno del CRM** (el del perfil), de hasta 60 caracteres. **Nunca** un dato personal como el documento, el correo o el teléfono, porque la ruta se guarda en la auditoría.
3. **Las búsquedas por datos personales van como parámetros** (`?documento=...`, `?nombre=...`). La auditoría guarda la ruta pero no los parámetros, así que esos datos no se duplican.
4. **El método HTTP indica la operación.** `GET` se registra como consulta. `POST`, `PUT`, `PATCH` y `DELETE` se registran como modificación y requieren el permiso de edición del servicio (`docs/seguridad/matriz-permisos.md`).
5. **Lo que no es de un cliente va fuera de `/clientes`**: configuración, reglas, catálogos, reproceso de eventos. Esas rutas se controlan por permiso, pero no se auditan como acceso a clientes.

## Ejemplos por servicio

| Servicio | Operación | Ruta |
|---|---|---|
| Perfil | Buscar clientes | `GET /api/perfil/clientes?documento=4455667` |
| Perfil | Crear un cliente | `POST /api/perfil/clientes` |
| Perfil | Ficha integral | `GET /api/perfil/clientes/{clienteId}` |
| Perfil | Actualizar datos | `PUT /api/perfil/clientes/{clienteId}` |
| Perfil | Histórico de cambios | `GET /api/perfil/clientes/{clienteId}/historial-cambios` |
| Perfil | Registrar consentimiento | `PUT /api/perfil/clientes/{clienteId}/consentimiento` |
| Perfil | Unificar duplicados | `POST /api/perfil/clientes/{clienteId}/unificaciones` |
| Comportamiento | Historial de compras | `GET /api/comportamiento/clientes/{clienteId}/compras` |
| Comportamiento | Indicadores | `GET /api/comportamiento/clientes/{clienteId}/indicadores` |
| Comportamiento | Clientes inactivos | `GET /api/comportamiento/clientes?estado=inactivo` |
| Segmentación | Segmento de un cliente | `GET /api/segmentacion/clientes/{clienteId}/segmento` |
| Segmentación | Clientes de un segmento | `GET /api/segmentacion/clientes?segmento={codigo}` |
| Fidelización | Saldo y nivel | `GET /api/fidelizacion/clientes/{clienteId}/puntos` |
| Fidelización | Ajuste manual de puntos | `POST /api/fidelizacion/clientes/{clienteId}/ajustes` |
| Interacciones | Historial de interacciones | `GET /api/interacciones/clientes/{clienteId}/interacciones` |
| Interacciones | Registrar una interacción | `POST /api/interacciones/clientes/{clienteId}/interacciones` |
| Interacciones | Cambiar el estado de una interacción | `PATCH /api/interacciones/clientes/{clienteId}/interacciones/{interaccionId}` |

### Rutas que no son de clientes (no se auditan como acceso)

| Servicio | Ruta |
|---|---|
| Comportamiento | `/api/comportamiento/eventos/...` (bitácora y reproceso) |
| Segmentación | `/api/segmentacion/criterios`, `/api/segmentacion/segmentos` |
| Fidelización | `/api/fidelizacion/reglas`, `/api/fidelizacion/niveles` |

## Formas incorrectas

| ❌ Incorrecta | Problema | ✅ Correcta |
|---|---|---|
| `GET /api/segmentacion/segmentos/{codigo}/clientes` | Devuelve clientes y no se audita | `GET /api/segmentacion/clientes?segmento={codigo}` |
| `POST /api/interacciones` con el cliente en el cuerpo | Se registra sin saber a qué cliente afecta | `POST /api/interacciones/clientes/{clienteId}/interacciones` |
| `GET /api/perfil/clientes/documento/4455667` | Guarda el documento en la auditoría y toma `documento` como cliente | `GET /api/perfil/clientes?documento=4455667` |
| `GET /api/comportamiento/clientes/inactivos` | Registra `inactivos` como cliente | `GET /api/comportamiento/clientes?estado=inactivo` |
| `GET /api/perfil/cliente/{clienteId}` | `cliente` en singular no coincide y no se audita | `GET /api/perfil/clientes/{clienteId}` |

## Fuera del alcance

Los eventos que llegan por RabbitMQ no pasan por el Gateway y no tienen un usuario detrás, así que no se registran en esta auditoría. Su trazabilidad es la bitácora de ingesta de Comportamiento de Compra.

## Lista de revisión para el PR

- [ ] Toda ruta con datos de clientes empieza por `/api/{servicio}/clientes`.
- [ ] Después de `/clientes/` va solo el `clienteId` interno.
- [ ] No hay datos personales en la ruta; las búsquedas van como parámetros.
- [ ] Los listados filtrados usan parámetros, no segmentos como `/clientes/inactivos`.
- [ ] La configuración y los catálogos están fuera de `/clientes`.
