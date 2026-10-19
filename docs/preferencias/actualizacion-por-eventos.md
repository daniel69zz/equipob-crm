# Actualización del perfil de preferencias por eventos — SCRUM-42 / RF-34

El perfil de preferencias de SCRUM-41 se recalcula solo, cada vez que Marketplace y Ventas notifica
una compra confirmada (RIO-CRM-02) o una anulación o devolución (RIO-CRM-05), para que refleje lo
que el cliente compra hoy. Usa el mismo flujo asíncrono de métricas que la asignación de segmento
(SCRUM-26, `docs/segmentacion/asignacion-automatica.md`). Diagramas en
`docs/preferencias/actualizacion-por-eventos-diagramas.md`.

## Trazabilidad con las subtareas

| Subtarea | Implementación |
|---|---|
| SCRUM-367 | Lógica descrita en este documento |
| SCRUM-370 | `ReceptorPreferencias`, cola `crm.comportamiento.preferencias` |
| SCRUM-366 | `PerfilesPreferencias.actualizarPorEvento`: valida y recalcula con todo el historial |
| SCRUM-369 | Migración V12, `PerfilPreferenciasRepository.guardar` con origen y evento |
| SCRUM-368 | Tabla `actualizacion_preferencias` y `GET .../preferencias/actualizaciones` |
| SCRUM-371 | `ActualizacionPreferenciasIntegracionTest`, `ReceptorPreferenciasTest` |
| SCRUM-589 | Diagramas de clases y secuencia |

## Disparador (SCRUM-370)

1. `servicio-comportamiento` registra la compra o la anulación como hasta ahora (bitácora,
   idempotencia de SCRUM-23, recálculo de SCRUM-523).
2. Si la transacción se confirma y la compra está vinculada a un cliente del CRM, publica las
   métricas del cliente en `crm.comportamiento.eventos` con la routing key `metricas.actualizadas`
   (SCRUM-26).
3. Además de `servicio-segmentacion`, el propio `servicio-comportamiento` escucha esas métricas en la
   cola `crm.comportamiento.preferencias` y actualiza el perfil de preferencias del `idCliente`.

Así la actualización ocurre **después** de que la compra o la anulación quedó registrada (criterio de
aceptación 1) y no la retrasa ni la hace fallar.

## Lógica de actualización (SCRUM-367, SCRUM-366)

- El perfil se **recalcula completo** con todo el historial vigente del cliente, con la misma regla,
  la misma validación del consumo y los mismos identificadores (los del servicio de Perfil) que la
  construcción manual de SCRUM-41. No se suma ni se resta la compra al perfil anterior: así no se
  acumulan errores y el orden en que lleguen los mensajes no importa.
- Campos afectados: posición, compras, unidades y monto de cada categoría; la cabecera toma una
  nueva versión, la fecha de actualización (`construidoEn`), el `origen` (`COMPRA_CONFIRMADA` o
  `COMPRA_ANULADA`) y el `idEventoOrigen`.
- **Compra confirmada:** su consumo suma a sus categorías y puede cambiar el orden.
- **Anulación total:** la compra deja de contar (criterio de aceptación 2). **Devolución parcial:** se
  descuentan las unidades y el monto devueltos; una categoría devuelta por completo desaparece.
- Si el cliente nunca tuvo el perfil construido, el primer evento lo construye.
- Un evento que falló en la bitácora (FALLIDO) o que se descartó por duplicado no publica métricas,
  por eso no modifica el perfil.

## Idempotencia (criterio de aceptación 3)

- **Evento de venta reentregado:** Comportamiento lo descarta por su clave de idempotencia y no
  vuelve a publicar métricas.
- **Mensaje de métricas reentregado** (RabbitMQ entrega al menos una vez): cada `idEvento` aplicado
  queda en `comportamiento.actualizacion_preferencias`; un segundo mensaje con el mismo `idEvento` no
  actualiza el perfil. Las actualizaciones del mismo cliente se serializan con el bloqueo de SCRUM-41.

## Trazabilidad (criterio de aceptación 4, SCRUM-368)

Cada actualización por evento guarda, en la misma transacción que el perfil:

| Campo | Contenido |
|---|---|
| `idEvento` | Evento de venta que la originó |
| `tipoEventoOrigen` | `COMPRA_CONFIRMADA` o `COMPRA_ANULADA` |
| `versionAnterior` / `versionNueva` | Versión del perfil antes (0 si no existía) y después |
| `categoriasAnteriores` / `categoriasNuevas` | Categorías en orden de preferencia, antes y después |
| `actualizadoEn` | Fecha de la actualización |

`GET /api/comportamiento/clientes/{clienteId}/preferencias/actualizaciones` las lista de la más
reciente a la más antigua, con el mismo acceso que la consulta del perfil (Administrador o Agente, y
`FICHA_INTEGRAL_CONSULTAR`). La consulta del perfil también informa `origen` e `idEventoOrigen` de la
versión vigente; una construcción manual queda con `origen = CONSTRUCCION_MANUAL`.

```json
[
  {
    "idEvento": "8d2f6a4c-1b3e-4c5d-9e7f-0a1b2c3d4e5f",
    "tipoEventoOrigen": "COMPRA_ANULADA",
    "versionAnterior": 1,
    "versionNueva": 2,
    "categoriasAnteriores": ["Electrónica", "Accesorios"],
    "categoriasNuevas": ["Electrónica"],
    "actualizadoEn": "2026-10-16T17:02:11Z"
  }
]
```

## Errores y cola de respaldo

| Situación | Resultado |
|---|---|
| Mensaje no JSON, sin `idEvento`/`idCliente` o con tipo desconocido | Va directo a `crm.comportamiento.preferencias.respaldo` |
| Cliente inexistente o consolidado, consumo inconsistente | Va directo a respaldo; se conserva el perfil anterior |
| El servicio de Perfil no responde | Se reintenta 3 veces; después va a respaldo |

`POST /api/comportamiento/eventos/respaldo/reinyectar?cola=preferencias` devuelve un mensaje de
respaldo a la cola para reprocesarlo, igual que con compras y anulaciones.

## Prueba con eventos simulados

Con `servicio-comportamiento` y `servicio-perfil` levantados y el perfil del cliente creado
(`cliente-ana-alta.json`):

```bash
python3 publicar.py eventos/compra-ana.json            # el perfil se actualiza (versión nueva)
python3 publicar.py eventos/compra-ana.json            # reentrega: DESCARTADO, el perfil no cambia
python3 publicar.py eventos/anulacion-parcial.json     # Accesorios deja de figurar
```

Después de cada paso: `GET /api/comportamiento/clientes/{id}/preferencias` y
`GET .../preferencias/actualizaciones`. Las pruebas automáticas reproducen esta secuencia con
PostgreSQL y RabbitMQ reales.
