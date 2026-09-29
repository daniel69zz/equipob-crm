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

SCRUM-37 no modifica el contador al recibir una anulación posterior. Ese ajuste corresponde a
SCRUM-523; la tabla separada y actualizable permite incorporarlo sin cambiar el contrato del
indicador.
