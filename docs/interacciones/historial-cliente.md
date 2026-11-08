# Historial del cliente: interacciones y compras (SCRUM-53)

El Agente de Atención al Cliente consulta, en una sola vista, las interacciones de un cliente junto con sus compras
para entender su situación antes de atenderlo (RF-45, EPIC-09). Se apoya en el registro (SCRUM-52) y el seguimiento
(SCRUM-55) de interacciones, y en el historial de compras de `servicio-comportamiento` (SCRUM-16). Diagramas en
`docs/interacciones/historial-cliente-diagramas.md` y pruebas funcionales en
`docs/interacciones/historial-cliente-pruebas.md`.

## Diseño de la consulta unificada (SCRUM-437)

```
GET /api/interacciones/clientes/{clienteId}/historial?limite=50
```

La atiende `servicio-interacciones`, que ya es dueño de las interacciones. Las compras no se copian: se consultan
en `servicio-comportamiento` en el momento, como hace la ficha integral (SCRUM-151).

1. **Perfil.** Busca el cliente en `servicio-perfil` (`GET /api/perfil/clientes/{clienteId}`):
   - Si no existe, responde `404`.
   - Si Perfil no responde, responde `503`, porque sin el perfil no se sabe de quién son las compras.
   - Del perfil toma los identificadores del cliente en Marketplace y Ventas.
2. **Interacciones.** Toma las `limite` más recientes del cliente con su estado de seguimiento, de
   `interacciones.interaccion`.
3. **Compras.** Pide las `limite` más recientes a `servicio-comportamiento`
   (`GET /api/comportamiento/clientes/{clienteId}/compras?identificador=…`):
   - Un cliente sin identificadores de Ventas no tiene compras, y no se consulta.
   - Si Comportamiento no responde, la consulta no falla: devuelve las interacciones con
     `comprasDisponibles: false` y un aviso.
4. **Línea de tiempo.** Une ambas en una sola lista de `eventos`, en orden cronológico **del más reciente al más
   antiguo** (criterio de aceptación 1):
   - Si una interacción y una compra tienen la misma fecha y hora, la compra va primero, porque suele originar la
     interacción.
   - Se devuelven los `limite` eventos más recientes, de 1 a 100, por defecto 50.
   - `totalInteracciones` y `totalCompras` dicen cuántos tiene el cliente en total.
   - `hayMas` indica si quedaron eventos fuera del límite. Para verlos completos están las vistas de Interacciones y
     de Compras e indicadores.

### Respuesta

```json
{
  "clienteId": 42,
  "eventos": [
    {
      "tipo": "INTERACCION",
      "fecha": "2026-11-08T14:02:10Z",
      "interaccion": {"id": 7, "tipo": "RECLAMO", "estado": "ABIERTA", "asunto": "Pedido incompleto", "...": "..."}
    },
    {
      "tipo": "COMPRA",
      "fecha": "2026-11-05T10:15:00Z",
      "compra": {"referencia": "V-100234", "montoTotal": 350.50, "estado": "CONFIRMADA", "items": ["..."]}
    }
  ],
  "totalInteracciones": 1,
  "totalCompras": 1,
  "sinInteracciones": false,
  "comprasDisponibles": true,
  "hayMas": false,
  "avisos": []
}
```

- **Cliente sin interacciones (criterio de aceptación 2):** responde `200`, sin error, con `sinInteracciones: true`,
  el aviso "El cliente no tiene interacciones registradas" y sus compras, si las tiene.
- **Cliente sin interacciones ni compras:** `eventos` llega vacío.

### Quién la consulta (criterio de aceptación 3)

Es la vista de atención: la ven el **Administrador de CRM** y el **Agente de Atención**.

| Rol | Acceso |
|---|---|
| Administrador de CRM | ✅ |
| Agente de Atención | ✅ |
| Gerente Comercial | ❌ `403`: consulta las interacciones y las compras por separado |
| Sin sesión | ❌ `401` |

- **Permisos que exige:** el rol y los permisos `INTERACCIONES_CONSULTAR` e `INDICADORES_CONSULTAR`, que es el que
  pide el historial de compras.
- **Dónde se controla:** el API Gateway lo exige y registra `ACCESO_DENEGADO` si falta. El servicio lo vuelve a
  exigir con la identidad que reenvía el Gateway.
- **Auditoría:** la consulta permitida queda como `CLIENTE_CONSULTADO`.
