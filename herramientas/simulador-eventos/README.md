# simulador-eventos

Publica en RabbitMQ eventos de ejemplo de Marketplace y Ventas, para probar los consumidores del CRM sin depender del sistema real.

Eventos a simular (según los contratos de `docs/contratos-eventos/`):

- Alta y cambio de datos de un cliente (RIO-CRM-01) — **disponible**
- Compra confirmada (RIO-CRM-02) — **disponible**
- Anulación o devolución de compra (RIO-CRM-05)
- Redención de puntos (RIO-CRM-03)
- Casos de prueba: evento duplicado, evento con datos inválidos, evento que falla y va a la cola de fallidos

## Uso

Requiere Python 3 y RabbitMQ con el plugin de administración (imagen `rabbitmq:3.13-management`). El servicio consumidor (`servicio-comportamiento` para compras, `servicio-perfil` para clientes) debe haber arrancado al menos una vez para que existan el exchange y su cola.

```bash
python3 publicar.py eventos/compra-ventas.json
python3 publicar.py eventos/compra-ventas.json --veces 2      # el segundo queda DESCARTADO
python3 publicar.py eventos/compra-marketplace.json --nuevo   # idEvento e idCompra nuevos
python3 publicar.py eventos/cliente-ventas-alta.json --ahora  # mismo cliente, cambio con fecha actual
```

Opciones: `--nuevo` (identificadores nuevos), `--ahora` (fecha del cambio = ahora), `--url` (por defecto `http://localhost:15672`), `--usuario`, `--password`, `--vhost`.

## Eventos de ejemplo

| Archivo | Canal | Resultado esperado en la bitácora |
|---|---|---|
| `compra-ventas.json` | Ventas | `PROCESADO` (compra V-100234 con 2 ítems) |
| `compra-marketplace.json` | Marketplace | `PROCESADO` (compra MP-88120 con 2 ítems) |
| `compra-ventas.json` publicado dos veces | Ventas | El segundo, `DESCARTADO` por compra duplicada |
| `compra-sin-cliente.json` | Ventas | `FALLIDO`: falta `compra.idCliente` |
| `compra-origen-desconocido.json` | — | `FALLIDO`: origen desconocido |
| `mensaje-ilegible.txt` | — | `FALLIDO`: no es JSON; el texto original queda en la bitácora |

### Clientes (RIO-CRM-01)

Publicarlos en este orden para ver la creación, la actualización y el descarte:

| Archivo | Canal | Resultado esperado |
|---|---|---|
| `cliente-ventas-alta.json` | Ventas | `PROCESADO`: se crea el perfil de Ana María Pérez con la dirección D-1 |
| `cliente-ventas-actualizacion.json` | Ventas | `PROCESADO`: el mismo perfil cambia de correo, D-1 cambia de referencia y se agrega D-2 como principal |
| `cliente-ventas-obsoleto.json` | Ventas | `DESCARTADO`: su cambio (22/09) es anterior al último aplicado (25/09); el perfil no cambia |
| `cliente-marketplace-alta.json` | Marketplace | `PROCESADO`: se crea el perfil de Carlos Quispe |
| `cliente-incompleto.json` | Ventas | `INCOMPLETO`: sin número de documento, correo mal formado y una dirección sin ciudad |
| `cliente-sin-id.json` | Ventas | `FALLIDO`: falta `cliente.idCliente` |
