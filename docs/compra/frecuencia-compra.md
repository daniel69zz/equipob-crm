# Frecuencia de compra (SCRUM-37)

La frecuencia es la **cantidad acumulada de compras confirmadas vigentes** de un cliente. Es un
contador entero, no una tasa temporal: no usa ventanas, promedios ni clasificaciones comerciales.

## Persistencia e identificación

`comportamiento.frecuencia_compra` mantiene un contador por `id_cliente_origen`, que es el dato
que siempre trae la compra aunque todavía no esté vinculada a un perfil unificado. Al consultar
`GET /api/comportamiento/clientes/{clienteId}/indicadores`, el servicio suma los contadores de
todos los parámetros `identificador` pertenecientes al perfil y responde el campo `frecuencia`.
Un cliente sin registros obtiene `0`.

La migración inicial cuenta las compras que conservan monto vigente: `CONFIRMADA` y
`DEVOLUCION_PARCIAL` cuentan; `ANULADA` no cuenta.

## Actualización e idempotencia

`ProcesadorCompras` incrementa el contador después de guardar una compra válida, dentro de la
misma transacción. La clave de idempotencia se reserva antes de crear la compra, por lo que un
evento duplicado termina antes del incremento. Si la transacción falla, compra, clave y contador
se revierten juntos.

Marketplace y Ventas recorren el mismo flujo y cuentan de igual forma. Una compra pendiente de
vinculación también cuenta mediante su `id_cliente_origen`.

## Anulaciones (SCRUM-523)

Una devolución parcial no modifica el contador: la compra sigue vigente. Cuando una anulación
deja la compra en `ANULADA`, `ProcesadorAnulaciones` descuenta 1 del contador de su
`id_cliente_origen` dentro de la misma transacción, sin bajar nunca de cero. Una anulación repetida
se descarta por idempotencia antes de descontar, y una rechazada no toca el contador. Un cliente
cuya única compra se anuló vuelve a `0`. Ver `docs/compra/recalculo-por-anulacion.md`.
