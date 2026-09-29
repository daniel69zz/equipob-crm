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
python3 publicar.py eventos/compra-ana.json
python3 publicar.py eventos/compra-ana.json --veces 2      # el segundo queda DESCARTADO
python3 publicar.py eventos/compra-carlos.json --nuevo   # idEvento e idCompra nuevos
python3 publicar.py eventos/cliente-ana-alta.json --ahora  # mismo cliente, cambio con fecha actual
```

Opciones: `--nuevo` (identificadores nuevos; en una anulación cambia `idAnulacion`, no la compra a la que apunta), `--ahora` (fecha del cambio = ahora), `--url` (por defecto `http://localhost:15672`), `--usuario`, `--password`, `--vhost`.

## Eventos de ejemplo

| Archivo | Resultado esperado en la bitácora |
|---|---|
| `compra-ana.json` | `PROCESADO` (compra V-100234 con 2 ítems) |
| `compra-carlos.json` | `PROCESADO` (compra MP-88120 con 2 ítems) |
| `compra-ana.json` publicado dos veces | El segundo, `DESCARTADO` por compra duplicada |
| `compra-sin-cliente.json` | `FALLIDO`: falta `compra.idCliente` |
| `mensaje-ilegible.txt` | `FALLIDO`: no es JSON; el texto original queda en la bitácora |

### Anulaciones y devoluciones (RIO-CRM-05)

Publicar primero la compra a la que apuntan (`compra-ana.json` o `compra-carlos.json`):

```bash
python3 publicar.py eventos/compra-ana.json eventos/anulacion-parcial.json eventos/anulacion-total.json
```

| Archivo | Resultado esperado en la bitácora |
|---|---|
| `anulacion-parcial.json` | `PROCESADO`: devuelve 2 accesorios (50.50) de V-100234, que queda `DEVOLUCION_PARCIAL` |
| `anulacion-total.json` (después de la parcial) | `PROCESADO`: anula los 300.00 restantes; V-100234 queda `ANULADA` |
| `anulacion-parcial.json` publicado dos veces | El segundo, `DESCARTADO` por anulación duplicada |
| `anulacion-total-carlos.json` | `PROCESADO`: anula MP-88120 completa |
| `anulacion-compra-inexistente.json` | `FALLIDO`: la compra V-999999 no está registrada |
| `anulacion-montos-no-cuadran.json` | `FALLIDO`: la suma de los ítems no coincide con `montoRevertido` |

### Clientes (RIO-CRM-01)

Publicarlos en este orden para ver la creación, la actualización y el descarte:

| Archivo | Resultado esperado |
|---|---|
| `cliente-ana-alta.json` | `PROCESADO`: se crea el perfil de Ana María Pérez con la dirección D-1 |
| `cliente-ana-actualizacion.json` | `PROCESADO`: el mismo perfil cambia de correo, D-1 cambia de referencia y se agrega D-2 como principal |
| `cliente-ana-obsoleto.json` | `DESCARTADO`: su cambio (22/09) es anterior al último aplicado (25/09); el perfil no cambia |
| `cliente-carlos-alta.json` | `PROCESADO`: se crea el perfil de Carlos Quispe |
| `cliente-incompleto.json` | `INCOMPLETO`: sin número de documento, correo mal formado y una dirección sin ciudad |
| `cliente-sin-id.json` | `FALLIDO`: falta `cliente.idCliente` |

### Un mismo cliente con dos identificadores (SCRUM-526)

Después de `cliente-ana-alta.json`:

| Paso | Resultado esperado |
|---|---|
| Publicar `cliente-ana-otra-cuenta.json` (otro identificador, mismo CI 4455667) | `PENDIENTE`: no se crea otro perfil; aparece en `GET /api/perfil/vinculaciones` con el perfil de Ana como sugerido |
| El administrador vincula: `POST /api/perfil/clientes/{idAna}/identificadores` con `{"idCliente": "mp-user-9001"}` | El evento pendiente se aplica al perfil de Ana, que queda con los dos identificadores |
| Publicar `cliente-ana-otra-cuenta.json --ahora` y `cliente-ana-alta.json --ahora` | Los dos se aplican al **mismo** perfil |

### Actualizaciones parciales (SCRUM-11)

Después de vincular la otra cuenta de Ana (sección anterior):

| Archivo | Resultado esperado |
|---|---|
| `cliente-ana-cambio-correo.json` | `PROCESADO`: solo cambia el correo (normalizado a minúsculas); nombre, documento, teléfono y direcciones se conservan |
| `cliente-ana-otra-cuenta-cambios.json` | `PROCESADO`: el nombre `Anita` y el teléfono `+59171122333` se aplican al mismo perfil de Ana |


