# Consulta del saldo de puntos de un cliente (SCRUM-47)

El Agente de Atención al Cliente consulta en una sola pantalla el saldo, el nivel y los últimos
movimientos de puntos de un cliente, para responder sus preguntas sobre el programa de fidelización
(RF-39, EPIC-08). Usa los datos que ya llevan la acumulación (SCRUM-45), la redención (SCRUM-48), la
reversa (SCRUM-524) y los niveles (SCRUM-49).

## Consulta (SCRUM-398)

```
GET /api/fidelizacion/clientes/{clienteId}/saldo?movimientos=10
```

| Parámetro | Regla |
|---|---|
| `clienteId` | Número del cliente en el CRM |
| `movimientos` | Opcional. Cuántos de los últimos movimientos devolver: de 1 a 50, por defecto 10. Fuera de ese rango responde `400` |

Respuesta de un cliente con puntos (criterio de aceptación 1):

```json
{
  "clienteId": 42,
  "tienePuntos": true,
  "mensaje": null,
  "saldo": 15,
  "nivel": "BRONCE",
  "puntosPeriodo": 15,
  "actualizadoEn": "2026-10-18T17:00:01Z",
  "nivelActualizadoEn": "2026-10-18T14:15:06Z",
  "totalMovimientos": 2,
  "ultimosMovimientos": [
    { "idTransaccion": "R-5001", "tipoMovimiento": "REDENCION", "puntos": 20, "variacionSaldo": -20,
      "saldoResultante": 15, "idTransaccionRevertida": null,
      "fechaTransaccion": "2026-10-18T16:59:40Z", "registradoEn": "2026-10-18T17:00:01Z" },
    { "idTransaccion": "V-100234", "tipoMovimiento": "ACUMULACION", "puntos": 35, "variacionSaldo": 35,
      "saldoResultante": 35, "idTransaccionRevertida": null,
      "fechaTransaccion": "2026-10-18T14:14:50Z", "registradoEn": "2026-10-18T14:15:06Z" }
  ]
}
```

- Los movimientos van del más reciente al más antiguo. `variacionSaldo` es lo que el movimiento sumó o
  restó del saldo; en una reversa, `idTransaccionRevertida` es la compra anulada o devuelta.
- `totalMovimientos` cuenta todos los movimientos del cliente, para indicar si hay más de los mostrados.
  El historial completo, con la regla aplicada a cada uno, sigue en
  `GET /api/fidelizacion/clientes/{clienteId}/movimientos`.

Un cliente **sin movimientos** (criterio de aceptación 2) responde `200`, no un error:

```json
{
  "clienteId": 7, "tienePuntos": false, "mensaje": "Este cliente aún no tiene puntos",
  "saldo": 0, "nivel": null, "puntosPeriodo": 0, "actualizadoEn": null, "nivelActualizadoEn": null,
  "totalMovimientos": 0, "ultimosMovimientos": []
}
```

La consulta no llama a otros servicios: no comprueba que el cliente exista en Perfil. Un número que no
corresponde a ningún cliente responde igual que un cliente sin puntos.

## Permisos por rol (criterio de aceptación 3)

| Rol | Permiso `PUNTOS_CONSULTAR` | Esta consulta |
|---|:---:|:---:|
| Administrador de CRM | ✅ | ✅ |
| Agente de Atención al Cliente | ✅ | ✅ |
| Gerente Comercial | ✅ | ❌ `403` |
| Rol sin `PUNTOS_CONSULTAR` | — | ❌ `403` en el API Gateway |

El control va en dos capas, como en la configuración de fidelización:

1. **API Gateway** (`SecurityConfig`): `GET /api/fidelizacion/**` exige `PUNTOS_CONSULTAR`. Sin sesión
   válida responde `401`; sin el permiso, `403`.
2. **servicio-fidelizacion**: exige la identidad que reenvía el Gateway (`X-Usuario`, `X-Usuario-Rol`,
   `X-Usuario-Permisos`; sin ella, `401`), el permiso `PUNTOS_CONSULTAR` y el rol de Administrador de
   CRM o de Agente de Atención (si no, `403`). El rol se exige aunque otro rol reciba por error el
   permiso. El Gerente Comercial conserva el saldo y el nivel agregados que ya consulta (por ejemplo en
   `GET /api/fidelizacion/clientes/{clienteId}/puntos`), pero no el detalle de movimientos del cliente.

## Trazabilidad

Cada consulta queda registrada dos veces en la auditoría compartida `auditoria.evento`
(`docs/seguridad/alcance-auditoria.md`): el Gateway registra el acceso a la ruta del cliente
(`CLIENTE_CONSULTADO` o `ACCESO_DENEGADO`), y fidelización registra `SALDO_PUNTOS_CONSULTADO` con el
usuario, el rol y cuántos movimientos se mostraron.
