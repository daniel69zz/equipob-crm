# Historial de compras del cliente

Estructura con la que el CRM guarda cada compra confirmada y cómo la asocia al perfil del cliente (SCRUM-15). El evento de origen está descrito en `docs/contratos-eventos/RIO-CRM-02-compra-confirmada.md`.

## Tablas

### `comportamiento.compra`

| Columna | Contenido |
|---|---|
| `id` | Identificador interno |
| `origen`, `id_compra_origen` | Canal (`MARKETPLACE` o `VENTAS`) e identificador de la compra en ese canal. Son únicos en conjunto (`uq_compra_origen`) |
| `id_cliente_origen` | Identificador del cliente en el canal, tal como llegó en el evento |
| `id_cliente` | Perfil del cliente en el CRM. `NULL` mientras la vinculación esté pendiente |
| `estado_vinculacion` | `PENDIENTE` o `VINCULADA` |
| `fecha` | Fecha y hora de la compra (`TIMESTAMPTZ`) |
| `monto_total` | Monto total, mayor que cero |
| `estado` | `CONFIRMADA` |
| `id_evento` | Mensaje de la bitácora de ingesta que la originó |
| `registrada_en` | Momento en que el CRM la registró |

### `comportamiento.compra_item`

Un ítem por categoría de producto: `id_compra`, `categoria` (no vacía), `cantidad` (mayor que cero) y `monto` (mayor que cero). La suma de los montos coincide con `monto_total` (regla de validación previa al guardado).

## Vinculación con el perfil

Al procesar la compra se consulta `GET /api/perfil/clientes?origen=…&idClienteOrigen=…` en el servicio de Perfil.

- Si responde **un único perfil**, la compra queda `VINCULADA` con su `id_cliente`.
- Si no existe el perfil, hay más de uno, la respuesta no es válida, el servicio no responde o se agota el tiempo de espera (`PERFIL_TIEMPO_ESPERA`, 2 s por defecto), la compra se guarda igual como `PENDIENTE`, conservando `origen` e `id_cliente_origen` para vincularla más adelante.

El servicio de Comportamiento nunca crea perfiles ni escribe en el esquema de Perfil.

La restricción `compra_vinculacion_check` mantiene coherentes ambos campos: `PENDIENTE` exige `id_cliente` nulo y `VINCULADA` exige un `id_cliente` positivo. Las compras anteriores a `V4` quedan como `PENDIENTE`.

## Integridad

- **Sin duplicados:** un reenvío de la misma compra se descarta por la clave de idempotencia (`docs/ingesta/idempotencia-eventos-venta.md`) y no modifica el historial, ni siquiera su vinculación.
- **Sin registros parciales:** la compra, sus ítems y la clave de idempotencia se guardan en una sola transacción; si algo falla no queda nada y el evento queda `FALLIDO` para reproceso.
- **Restricciones en la base:** montos y cantidades positivos, y identificadores y categorías no vacíos, aunque se escriba sin pasar por el procesador.
- **Consulta por perfil:** el índice `idx_compra_perfil_fecha` (`id_cliente`, `fecha DESC`) sirve al historial ordenado del cliente.
