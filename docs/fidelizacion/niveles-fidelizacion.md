# Niveles de fidelización (SCRUM-49)

El Administrador de CRM define los niveles **Bronce, Plata y Oro** con sus umbrales de puntos para
reconocer a los clientes según su lealtad (RF-41, EPIC-08). El CRM asigna el nivel automáticamente y lo
muestra en la consulta de puntos y en la ficha integral. Depende de la acumulación (SCRUM-45) y de las
reglas de acumulación (SCRUM-46). Diagramas en `docs/fidelizacion/niveles-fidelizacion-diagramas.md`.

## Reglas y umbrales (SCRUM-410)

El nivel se calcula con los **puntos del periodo de evaluación**, no con el saldo:

```
puntosPeriodo = puntos de las ACUMULACIONES con fecha de transacción en los últimos N meses
              − puntos de las REVERSAS del mismo periodo (SCRUM-524), nunca menos de 0
nivel         = el umbral de la configuración vigente que contiene puntosPeriodo (o ninguno)
```

- Las **redenciones no bajan el nivel**: un cliente no pierde su nivel por usar sus puntos.
- Con el paso del tiempo, las compras antiguas salen del periodo y el cliente puede bajar de nivel.

Configuración inicial (migración `V4__niveles_fidelizacion.sql`, usuario `sistema`):

| Nivel | Puntos del periodo | Equivale con la regla inicial (Bs 10 por punto) a |
|---|---|---|
| Bronce | 0 – 499 | hasta Bs 4.999 consumidos |
| Plata | 500 – 1.999 | Bs 5.000 a Bs 19.999 |
| Oro | 2.000 o más | Bs 20.000 o más |

**Periodo de evaluación:** 12 meses (configurable entre 1 y 120).

### Validación de los umbrales (criterio de aceptación 2)

Una configuración se rechaza con `400`, indicando el conflicto en el mensaje y en `campos`, si:

1. Falta o se repite alguno de los tres niveles.
2. Un mínimo es negativo o un máximo es menor que su mínimo.
3. Bronce o Plata no tienen máximo, u Oro tiene máximo (solo el nivel más alto queda abierto).
4. **Los rangos se superponen.** Los límites están incluidos: Bronce 0–500 y Plata 500–1.999 chocan en el
   500. El mensaje indica los dos niveles, sus rangos y el mínimo correcto:
   `Conflicto en los umbrales de niveles: los umbrales de Bronce (0–500) y Plata (500–1999) se superponen; el mínimo de Plata debe ser 501`.
5. **Los rangos dejan un hueco** entre un nivel y el siguiente (por ejemplo Bronce 0–499 y Plata 600–1.999
   dejarían sin nivel a los clientes con 500–599 puntos).

Bronce puede empezar por encima de cero: los clientes con menos puntos quedan **sin nivel**.

## Asignación automática (SCRUM-411, criterio de aceptación 1)

`fidelizacion.saldo_puntos` guarda, junto al saldo, el `nivel`, los `puntos_periodo` con los que se asignó
y `nivel_actualizado_en` (fecha del último cambio de nivel). El nivel se recalcula:

- **Con cada cambio de saldo**: al acreditar un movimiento, en la misma transacción
  (`ProcesadorMovimientos` → `AsignacionNiveles.recalcular`).
- **Al guardar una configuración nueva**: se reclasifica a todos los clientes en la misma transacción y la
  respuesta indica cuántos cambiaron de nivel.
- **Cada noche** (`fidelizacion.niveles.recalculo-cron`, por defecto 02:30 hora de La Paz), para quienes
  ya no tienen puntos dentro del periodo.
- **A pedido** del administrador: `POST /api/fidelizacion/niveles/recalcular`.

El cálculo es una sola sentencia SQL (`AsignacionNiveles`) que suma los puntos del periodo y busca el
umbral vigente, para un cliente o para todos.

## Versiones e historial

Cada configuración guardada es una versión nueva en `fidelizacion.configuracion_niveles` (periodo,
motivo, usuario, fecha) con sus umbrales en `fidelizacion.umbral_nivel`. Las versiones no se modifican ni
se borran. Guardar exige la `version` vigente que se estaba editando (si otra persona guardó antes:
`409`), y una configuración igual a la vigente se rechaza con `400`.

## API (SCRUM-413)

| Método | Ruta | Permiso (Gateway) | Además en el servicio |
|---|---|---|---|
| GET | `/api/fidelizacion/niveles` | `PUNTOS_CONSULTAR` | — |
| PUT | `/api/fidelizacion/niveles` | `FIDELIZACION_CONFIGURAR` | Rol `ADMINISTRADOR_CRM` |
| GET | `/api/fidelizacion/niveles/historial` | `PUNTOS_CONSULTAR` | Rol `ADMINISTRADOR_CRM` y `FIDELIZACION_CONFIGURAR` |
| POST | `/api/fidelizacion/niveles/recalcular` | `FIDELIZACION_CONFIGURAR` | Rol `ADMINISTRADOR_CRM` |

```json
PUT /api/fidelizacion/niveles
{
  "periodoMeses": 12,
  "motivo": "Umbrales más exigentes",
  "version": 1,
  "niveles": [
    { "nivel": "BRONCE", "puntosMinimos": 0,    "puntosMaximos": 999 },
    { "nivel": "PLATA",  "puntosMinimos": 1000, "puntosMaximos": 4999 },
    { "nivel": "ORO",    "puntosMinimos": 5000, "puntosMaximos": null }
  ]
}

200 OK
{ "configuracion": { "version": 2, "periodoMeses": 12, "umbrales": [ ... ], "modificadoPor": "admin", ... },
  "clientesReclasificados": 14 }
```

## Consulta del nivel (criterio de aceptación 3)

`GET /api/fidelizacion/clientes/{clienteId}/puntos` (RIO-CRM-04) devuelve el nivel actual:

```json
{ "saldo": 3100, "nivel": "ORO", "actualizadoEn": "...", "sinDatos": false,
  "puntosPeriodo": 2500, "nivelActualizadoEn": "2026-11-07T14:00:00Z" }
```

La ficha integral (servicio-perfil) toma el `nivel` de esa consulta y la Aplicación Web lo muestra como
insignia (Bronce, Plata u Oro) en el bloque *Puntos de fidelización*.

## Interfaz

*Administración → Niveles de fidelización* (`/fidelizacion/niveles`, solo Administrador de CRM): muestra
los niveles vigentes, permite editar el periodo y los umbrales marcando las superposiciones y los huecos
antes de enviar, reclasificar a pedido y ver el historial de versiones.

## Pruebas (SCRUM-412)

- `ReglasNivelesTest`: clasificación por puntos en cada límite y rechazo de superposiciones, huecos y
  niveles faltantes o repetidos.
- `ServicioNivelesTest`: versión nueva con reclasificación, conflicto de versiones y configuración sin cambios.
- `NivelesControllerTest`: conflicto indicado por campo, validación y acceso solo del Administrador de CRM.
- `FidelizacionControllerTest`: la consulta de puntos muestra el nivel actual.
- `NivelesIntegracionTest` (PostgreSQL real, requiere Docker): el nivel sube con cada acumulación, las
  compras fuera del periodo no cuentan, el cambio de umbrales y el recálculo reclasifican a los clientes,
  una configuración en conflicto no se guarda y la consulta de puntos muestra el nivel.
