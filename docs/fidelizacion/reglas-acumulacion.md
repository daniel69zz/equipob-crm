# Reglas de acumulación de puntos (SCRUM-46)

El Administrador de CRM configura la regla con la que se calculan los puntos de cada compra
(**bolivianos por punto** y **monto mínimo de compra**) para ajustar el programa de fidelización sin
cambiar el código (RF-38, EPIC-08). Reemplaza la tasa fija `FIDELIZACION_BOLIVIANOS_POR_PUNTO` de SCRUM-45
(`docs/fidelizacion/acumulacion-puntos.md`). Diagramas en `docs/fidelizacion/reglas-acumulacion-diagramas.md`.

## Regla de cálculo (SCRUM-394)

```
si valorConsumido < montoMinimo:  puntos = 0
si no:                             puntos = parte entera de (valorConsumido / bolivianosPorPunto)
```

| Parámetro | Regla | Valor inicial |
|---|---|---|
| `bolivianosPorPunto` | Obligatorio, **mayor que cero**, hasta 12 enteros y 2 decimales | 10,00 |
| `montoMinimo` | Obligatorio, **cero o mayor** (0 = sin mínimo), hasta 12 enteros y 2 decimales. El mínimo está **incluido**: una compra igual al mínimo sí suma | 0,00 |
| `motivo` | Opcional, hasta 250 caracteres. Queda en el historial | — |

- Redondeo hacia abajo y sin tope por transacción, como en SCRUM-45.
- Una compra por debajo del mínimo queda registrada en el detalle con **0 puntos** y no cambia el saldo.

Ejemplos con la regla *Bs 5 por punto, mínimo Bs 50*:

| Valor consumido | Puntos |
|---:|---:|
| 49,99 | 0 (no alcanza el mínimo) |
| 50,00 | 10 |
| 129,90 | 25 |

## Versiones y aplicación (criterios de aceptación 1 y 3)

- Cada regla guardada es una **versión nueva** en `fidelizacion.regla_acumulacion` (versión, tasa, monto
  mínimo, motivo, usuario y fecha). La vigente es la de mayor versión; las versiones no se modifican ni se
  borran, así que el historial conserva la regla anterior, **quién** la cambió y **cuándo**.
- La regla no se elimina: se reemplaza por una versión nueva.
- La versión 1 la crea la migración `V3__reglas_acumulacion.sql` con la regla de SCRUM-45 (Bs 10 por
  punto, sin mínimo) y el usuario `sistema`.
- Cada movimiento se calcula con la regla **vigente al procesarlo** y guarda en
  `fidelizacion.movimiento_puntos` los puntos, la tasa, el monto mínimo y la `version_regla` aplicados. Por
  eso un cambio de regla se aplica a los movimientos siguientes y **no recalcula los puntos ya otorgados**.
- Guardar exige la `version` vigente que se estaba editando: si otra persona guardó antes, la solicitud
  se rechaza con `409` en vez de pisar su cambio. Una regla igual a la vigente se rechaza con `400`.

## Validación (SCRUM-395, criterio de aceptación 2)

Una regla con valores vacíos, negativos, cero (para la tasa) o con más de 2 decimales se rechaza con `400`.
El mensaje y el objeto `campos` indican cada campo inválido:

```json
{
  "estado": 400,
  "error": "Bad Request",
  "mensaje": "La regla de acumulación no es válida: bolivianosPorPunto debe ser mayor que cero, montoMinimo no puede ser negativo",
  "ruta": "/api/fidelizacion/reglas/acumulacion",
  "campos": {
    "bolivianosPorPunto": "debe ser mayor que cero",
    "montoMinimo": "no puede ser negativo"
  }
}
```

## API (SCRUM-392)

| Método | Ruta | Permiso (Gateway) | Además en el servicio |
|---|---|---|---|
| GET | `/api/fidelizacion/reglas/acumulacion` | `PUNTOS_CONSULTAR` | — |
| PUT | `/api/fidelizacion/reglas/acumulacion` | `FIDELIZACION_CONFIGURAR` | Rol `ADMINISTRADOR_CRM` |
| GET | `/api/fidelizacion/reglas/acumulacion/historial` | `PUNTOS_CONSULTAR` | Rol `ADMINISTRADOR_CRM` y `FIDELIZACION_CONFIGURAR` |

```json
PUT /api/fidelizacion/reglas/acumulacion
{ "bolivianosPorPunto": 5.00, "montoMinimo": 50.00, "motivo": "Campaña de fin de año", "version": 1 }

200 OK
{ "version": 2, "bolivianosPorPunto": 5.00, "montoMinimo": 50.00, "motivo": "Campaña de fin de año",
  "modificadoPor": "admin", "vigenteDesde": "2026-11-07T14:00:00Z" }
```

El historial devuelve las versiones de la más reciente a la más antigua, cada una con los valores que
reemplazó (`anteriorBolivianosPorPunto`, `anteriorMontoMinimo`; nulos en la versión 1).

## Acceso (criterio de aceptación 4)

El API Gateway exige `FIDELIZACION_CONFIGURAR` para el PUT. El servicio, con la identidad que reenvía el
Gateway (`X-Usuario`, `X-Usuario-Rol`, `X-Usuario-Permisos`), exige además el rol **Administrador de
CRM**, aunque otro rol reciba por error ese permiso: sin identidad responde `401`; con otro rol o sin el
permiso, `403`. El acceso se comprueba antes de validar el contenido.

## Interfaz (SCRUM-391)

*Administración → Reglas de puntos* (`/fidelizacion/reglas`, solo Administrador de CRM): muestra la regla
vigente, permite editarla con una vista previa de los puntos de una compra de ejemplo con la regla vigente
y con la nueva, marca los campos rechazados y muestra el historial de versiones.

## Pruebas

- `ReglaAcumulacionTest`: cálculo con tasa y monto mínimo.
- `ServicioReglasAcumulacionTest`: versión nueva, conflicto de versiones y regla sin cambios.
- `ReglasAcumulacionControllerTest`: validación por campo (CA2), historial (CA3) y acceso (CA4).
- `ReglasAcumulacionIntegracionTest` (PostgreSQL real, requiere Docker): la regla nueva se aplica a los
  movimientos siguientes sin recalcular los anteriores (CA1) y el historial queda en la base (CA3).
