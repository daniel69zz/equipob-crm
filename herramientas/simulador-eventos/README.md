# simulador-eventos

Publica en RabbitMQ eventos de ejemplo de Marketplace y Ventas, para probar los consumidores del CRM sin depender del sistema real.

Eventos a simular (según los contratos de `docs/contratos-eventos/`):

- Alta y cambio de datos de un cliente (RIO-CRM-01) — **disponible**
- Compra confirmada (RIO-CRM-02) — **disponible**
- Anulación o devolución de compra (RIO-CRM-05) — **disponible**
- Redención de puntos (RIO-CRM-03)
- Casos de prueba: evento duplicado, evento con datos inválidos, evento que falla y va a la cola de fallidos

## Uso

Requiere Python 3 y RabbitMQ con el plugin de administración (imagen `rabbitmq:3.13-management`). El servicio consumidor (`servicio-comportamiento` para compras y anulaciones, `servicio-perfil` para clientes) debe haber arrancado al menos una vez para que existan el exchange y su cola.

```bash
python3 publicar.py eventos/compra-ventas.json
python3 publicar.py eventos/compra-ventas.json --veces 2      # el segundo queda DESCARTADO
python3 publicar.py eventos/compra-marketplace.json --nuevo   # idEvento e idCompra nuevos
python3 publicar.py eventos/cliente-ventas-alta.json --ahora  # mismo cliente, cambio con fecha actual
```

Opciones: `--nuevo` (identificadores nuevos; en una anulación cambia `idAnulacion`, no la compra a la que apunta), `--ahora` (fecha del cambio = ahora), `--url` (por defecto `http://localhost:15672`), `--usuario`, `--password`, `--vhost`.

## Eventos de ejemplo

| Archivo | Canal | Resultado esperado en la bitácora |
|---|---|---|
| `compra-ventas.json` | Ventas | `PROCESADO` (compra V-100234 con 2 ítems) |
| `compra-marketplace.json` | Marketplace | `PROCESADO` (compra MP-88120 con 2 ítems) |
| `compra-ventas.json` publicado dos veces | Ventas | El segundo, `DESCARTADO` por compra duplicada |
| `compra-sin-cliente.json` | Ventas | `FALLIDO`: falta `compra.idCliente` |
| `compra-origen-desconocido.json` | — | `FALLIDO`: origen desconocido |
| `mensaje-ilegible.txt` | — | `FALLIDO`: no es JSON; el texto original queda en la bitácora |

### Anulaciones y devoluciones (RIO-CRM-05)

Publicar primero la compra a la que apuntan (`compra-ventas.json` o `compra-marketplace.json`):

```bash
python3 publicar.py eventos/compra-ventas.json eventos/anulacion-parcial-ventas.json eventos/anulacion-total-ventas.json
```

| Archivo | Canal | Resultado esperado en la bitácora |
|---|---|---|
| `anulacion-parcial-ventas.json` | Ventas | `PROCESADO`: devuelve 2 accesorios (50.50) de V-100234, que queda `DEVOLUCION_PARCIAL` |
| `anulacion-total-ventas.json` (después de la parcial) | Ventas | `PROCESADO`: anula los 300.00 restantes; V-100234 queda `ANULADA` |
| `anulacion-parcial-ventas.json` publicado dos veces | Ventas | El segundo, `DESCARTADO` por anulación duplicada |
| `anulacion-total-marketplace.json` | Marketplace | `PROCESADO`: anula MP-88120 completa |
| `anulacion-compra-inexistente.json` | Ventas | `FALLIDO`: la compra V-999999 no está registrada |
| `anulacion-montos-no-cuadran.json` | Ventas | `FALLIDO`: la suma de los ítems no coincide con `montoRevertido` |

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

### Un mismo cliente en Marketplace y en Ventas (SCRUM-526)

Después de `cliente-ventas-alta.json`:

| Paso | Resultado esperado |
|---|---|
| Publicar `cliente-marketplace-ana.json` (otro identificador, mismo CI 4455667) | `PENDIENTE`: no se crea otro perfil; aparece en `GET /api/perfil/vinculaciones` con el perfil de Ana como sugerido |
| El administrador vincula: `POST /api/perfil/clientes/{idAna}/identificadores` con `{"origen": "MARKETPLACE", "idCliente": "mp-user-9001"}` | El evento pendiente se aplica al perfil de Ana, que queda con los dos identificadores |
| Publicar `cliente-marketplace-ana.json --ahora` y `cliente-ventas-alta.json --ahora` | Los dos se aplican al **mismo** perfil |

### Actualizaciones parciales y conflictos (SCRUM-11)

Después de vincular a Ana en Marketplace (sección anterior):

| Archivo | Resultado esperado |
|---|---|
| `cliente-ventas-cambio-correo.json` | `PROCESADO`: solo cambia el correo (normalizado a minúsculas); nombre, documento, teléfono y direcciones se conservan |
| `cliente-marketplace-ana-cambios.json` | `PROCESADO` con 2 conflictos: el nombre `Anita` se **conserva** como `Ana María` (Ventas tiene prioridad en identificación) y el teléfono `+59171122333` se **aplica** (es el cambio de contacto más reciente) |

Los conflictos se consultan en `GET /api/perfil/clientes/{id}/conflictos`.

