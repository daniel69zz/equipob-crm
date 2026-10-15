# Asignación automática de segmento (SCRUM-26)

Cada compra confirmada (RIO-CRM-02) y cada anulación o devolución (RIO-CRM-05) que llega de
Marketplace y Ventas vuelve a evaluar el segmento del cliente con los criterios activos de SCRUM-25,
sin intervención manual (RF-18, EPIC-04). Es la historia de integración asíncrona del Sprint 2.
Diagramas en `docs/segmentacion/asignacion-automatica-diagramas.md`.

## Flujo (SCRUM-263, SCRUM-262)

1. Marketplace y Ventas publica la venta o la anulación en el exchange `ventas.eventos`
   (`compra.confirmada` / `compra.anulada`) y no espera respuesta.
2. `servicio-comportamiento` la registra como hasta ahora (bitácora, idempotencia, recálculo de
   SCRUM-523). En la misma transacción calcula las **métricas vigentes del cliente** y, cuando la
   transacción se confirma, las publica en el exchange `crm.comportamiento.eventos` con la routing
   key `metricas.actualizadas`.
3. `servicio-segmentacion` las consume de la cola `crm.segmentacion.metricas`, evalúa los criterios
   activos, aplica la regla de prioridad y guarda el segmento en `segmentacion.segmento_cliente`,
   que es lo que muestra la ficha integral del cliente.

El mensaje es una foto completa de las métricas, no un incremento: aplicarlo de nuevo da el mismo
segmento.

```json
{
  "idEvento": "5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c",
  "tipoEventoOrigen": "COMPRA_CONFIRMADA",
  "idCliente": 42,
  "identificadoresOrigen": ["CLI-5521"],
  "frecuencia": 5,
  "valor": 1250.50,
  "categorias": ["Electrónica", "Accesorios"],
  "nivelConsumo": "MEDIO",
  "ultimaCompra": "2026-10-13T15:18:40Z",
  "calculadasEn": "2026-10-13T15:20:11.482Z"
}
```

| Campo | Significado |
|---|---|
| `idEvento` | `idEvento` del evento de venta o de anulación que provocó el cálculo. Clave de idempotencia. |
| `tipoEventoOrigen` | `COMPRA_CONFIRMADA` o `COMPRA_ANULADA`. |
| `idCliente` | Cliente del CRM (perfil unificado). |
| `identificadoresOrigen` | Identificadores del cliente en Marketplace y Ventas con compras vinculadas a ese perfil. |
| `frecuencia` | Compras vigentes (`docs/compra/frecuencia-compra.md`). |
| `valor` | Valor acumulado vigente, en bolivianos (`docs/compra/valor-acumulado.md`). |
| `categorias` | Categorías con consumo vigente, de mayor a menor monto (SCRUM-18). |
| `nivelConsumo` | `BAJO`, `MEDIO` o `ALTO` sobre `valor`, con los umbrales de SCRUM-27; `null` sin compras vigentes. Solo lo usa el listado de clientes por segmento (SCRUM-28), no la asignación. |
| `ultimaCompra` | Fecha de la compra vigente más reciente (SCRUM-19); `null` sin compras vigentes. Solo para el listado de SCRUM-28. |
| `calculadasEn` | Momento del cálculo; ordena los mensajes del mismo cliente. |

`nivelConsumo` y `ultimaCompra` se agregaron con SCRUM-277 y son opcionales: un mensaje que no los
trae se aplica igual, y un nivel desconocido se guarda como "sin nivel" en vez de rechazar el mensaje.

Las métricas se calculan sobre **todas** las compras vinculadas al perfil del cliente, igual que los
indicadores de la ficha. Una compra cuyo cliente todavía no tiene perfil (vinculación pendiente) no
publica métricas: no hay perfil donde guardar el segmento.

## Regla de prioridad (SCRUM-260)

Los criterios de un mismo indicador no se superponen (SCRUM-25), pero un cliente puede cumplir a la
vez uno de frecuencia, uno de valor y uno o varios de categoría. Se asigna **un solo segmento**:

1. **Valor** antes que **frecuencia**, y frecuencia antes que **categoría**. El valor acumulado es la
   medida más directa del aporte del cliente; la frecuencia describe su hábito; la categoría, su
   preferencia.
2. Entre varios criterios de categoría, gana el de la categoría en la que el cliente **más consumió**
   (el orden de `categorias`).
3. Si aun así hay empate, gana el criterio más antiguo (menor `id`).

Si no cumple ningún criterio activo, el cliente queda **sin segmento** (la ficha muestra el bloque
vacío, como en SCRUM-10).

| Cliente | Criterios que cumple | Segmento |
|---|---|---|
| 12 compras, Bs 6.200, Electrónica | Frecuentes (FRECUENCIA ≥ 10), Alto valor (VALOR ≥ 5000), Tecnología (CATEGORIA Electrónica) | Alto valor |
| 5 compras, Bs 300, Libros | Habituales (FRECUENCIA 4–9), Lectores (CATEGORIA Libros) | Habituales |
| 1 compra, Bs 80: Libros (Bs 50) y Juguetes (Bs 30) | Lectores, Infantil | Lectores |

## Persistencia y orden de los mensajes (SCRUM-264)

- `segmentacion.segmento_cliente` guarda, además del nombre del segmento, el criterio que lo
  asignó, su indicador, el `idEvento` que lo provocó y el momento del cálculo.
- `segmentacion.metricas_cliente` guarda la última foto de métricas de cada cliente, también
  cuando no cumple ningún criterio.
- Los mensajes de un cliente se aplican bajo un bloqueo por cliente. Si llega un mensaje calculado
  **antes** que la foto ya aplicada (por ejemplo, un reintento atrasado), se registra pero no
  cambia el segmento.

## Compras nuevas y anulaciones (SCRUM-261, SCRUM-536)

- **Compra confirmada:** sube la frecuencia y el valor; el cliente puede pasar a un segmento de mayor
  prioridad o a otro rango del mismo indicador.
- **Anulación total:** la compra deja de contar (frecuencia y valor bajan, y sus categorías dejan de
  aportar). **Devolución parcial:** baja el valor y lo consumido en las categorías devueltas.
  En ambos casos el segmento se reevalúa y cambia si corresponde, o se retira si ya no cumple
  ningún criterio (criterio de aceptación 4).

## Idempotencia y cola de respaldo (SCRUM-241)

- **Evento de venta reentregado:** Comportamiento ya lo descarta por su clave de idempotencia
  (SCRUM-23), así que no vuelve a publicar métricas.
- **Mensaje de métricas reentregado** (RabbitMQ entrega al menos una vez): Segmentación registra cada
  `idEvento` aplicado en `segmentacion.evento_procesado`; una segunda entrega se descarta sin
  recalcular (criterio de aceptación 3).
- **Mensaje que no se puede procesar** (JSON inválido o sin `idEvento`/`idCliente`): va directo a la
  cola de respaldo `crm.segmentacion.metricas.respaldo`. Un error transitorio (por ejemplo, la base
  no responde) se reintenta 3 veces y después también va a respaldo.
- **Reproceso:** `POST /api/segmentacion/respaldo/reinyectar` devuelve un mensaje de la cola de
  respaldo a la cola principal (permiso `SEGMENTACION_CONFIGURAR`, Administrador de CRM). Responde
  204 si la cola de respaldo está vacía.

## Auditoría (SCRUM-265)

Cada cambio de segmento se registra en `auditoria.evento` (solo inserción), en la misma transacción,
con usuario `sistema` y entidad `SEGMENTO_CLIENTE`:

| Operación | Cuándo |
|---|---|
| `SEGMENTO_ASIGNADO` | El cliente no tenía segmento y ahora tiene uno. |
| `SEGMENTO_CAMBIADO` | Pasa de un segmento a otro. |
| `SEGMENTO_RETIRADO` | Ya no cumple ningún criterio activo. |

El detalle guarda el segmento anterior y el nuevo, el criterio que decidió, las métricas usadas y el
`idEvento`. Si el segmento no cambia, no se audita. Es la base del historial de cambios de segmento
(SCRUM-30).

## Alcance

- Un cambio en los criterios (SCRUM-25) no reasigna de inmediato a todos los clientes: cada cliente
  se reevalúa con los criterios vigentes en su siguiente compra o anulación.
- La publicación ocurre después del commit. Si RabbitMQ no está disponible en ese momento, el error
  queda en el log y el segmento se corrige con el siguiente evento del cliente.

## Configuración

| Variable | Servicio | Por defecto |
|---|---|---|
| `RABBIT_HOST`, `RABBIT_PUERTO`, `RABBIT_USUARIO`, `RABBIT_PASSWORD` | ambos | `localhost`, `5672`, `guest`, `guest` |

## Prueba con eventos simulados (SCRUM-266)

Con los servicios levantados, el simulador publica compras y anulaciones en `ventas.eventos`
(`herramientas/simulador-eventos`). Después de cada una se consulta
`GET /api/segmentacion/clientes/{id}/segmento`. Las pruebas automáticas
(`AsignacionSegmentoIntegracionTest` con PostgreSQL y RabbitMQ reales, `PublicacionMetricasIntegracionTest`
y `ReglaPrioridadSegmentoTest`) cubren compra, reentrega, anulación, devolución parcial, mensajes
fuera de orden, mensajes inválidos y la regla de prioridad.
