# Detección de cambios en el comportamiento de compra (SCRUM-34)

Detección automática de variaciones relevantes en la **frecuencia** o el **monto** de compra de cada
cliente respecto a su comportamiento habitual, para que el Analista Comercial reaccione antes de que
un cliente que compra menos (o distinto) se convierta en una pérdida (RF-26, EPIC-05 Análisis del
Comportamiento). Usa las mismas compras vigentes que la frecuencia (SCRUM-37), el ticket promedio
(SCRUM-17) y los clientes inactivos (SCRUM-31), y es la base del riesgo de abandono (SCRUM-35).
Diagramas en `docs/compra/cambios-comportamiento-diagramas.md`.

## Criterios, umbrales y ventana (SCRUM-316)

Se compara el **periodo actual** del cliente con su **línea base** (lo habitual), ambos medidos sobre
sus compras vigentes:

| Concepto | Definición | Variable de entorno (defecto) |
|---|---|---|
| Ventana | Duración de un periodo, en días | `CAMBIO_COMPORTAMIENTO_VENTANA_DIAS` (90) |
| Periodo actual | Los últimos `ventana` días, hasta el momento de la evaluación | — |
| Línea base | Los `periodos-base` periodos inmediatamente anteriores al actual | `CAMBIO_COMPORTAMIENTO_PERIODOS_BASE` (3, es decir 270 días) |
| Historial suficiente | Al menos `minimo-compras-base` compras vigentes en la línea base y al menos un periodo completo de antigüedad antes del periodo actual | `CAMBIO_COMPORTAMIENTO_MINIMO_COMPRAS_BASE` (3) |
| Umbral de variación | Desvío relativo, en valor absoluto, desde el que la variación es relevante | `CAMBIO_COMPORTAMIENTO_UMBRAL` (0.5, es decir 50 %) |

Con los valores por defecto: se compara lo comprado en los últimos 90 días contra el promedio por
periodo de 90 días de los 270 días anteriores.

### Qué se compara

| Tipo | Valor actual | Valor habitual |
|---|---|---|
| `FRECUENCIA` | Cantidad de compras vigentes del periodo actual | Compras vigentes de la línea base, llevadas a un periodo |
| `MONTO` | Suma de lo vigente (`montoTotal - montoRevertido`) de las compras del periodo actual | Monto vigente de la línea base, llevado a un periodo |

"Llevado a un periodo" significa: `total de la línea base × ventana / días de línea base observados`.
Los días observados son los de la línea base, salvo que la primera compra del cliente sea posterior
a su inicio: en ese caso se cuentan desde la primera compra hasta el inicio del periodo actual. Así,
un cliente que empezó a comprar a mitad de la línea base no tiene un habitual artificialmente bajo y
no genera un falso "aumento".

### Cuándo una variación es relevante

```
variacion = (actual - habitual) / habitual
relevante si |variacion| >= umbral
```

- `variacion > 0` es un **AUMENTO**; `variacion < 0` es una **DISMINUCION**.
- Un valor exactamente en el umbral es relevante: con 50 %, pasar de 4 compras habituales a 2 se
  detecta, y pasar a 2.01 no.
- No comprar nada en el periodo actual teniendo línea base es una disminución del 100 %: siempre
  relevante.
- Frecuencia y monto se evalúan por separado: un cliente puede tener una detección de cada tipo en
  la misma evaluación (por ejemplo, compra igual de seguido pero gasta la mitad).
- El umbral debe ser mayor que 0 y como máximo 1 (100 %): una caída nunca supera el 100 %, así que con un
  umbral mayor no se detectarían disminuciones. El servicio no arranca con una configuración inválida.

### Resultado de la evaluación de un cliente

| Estado | Cuándo |
|---|---|
| `SIN_LINEA_BASE` | Historial insuficiente: no se genera ninguna detección (criterio de aceptación 2) |
| `ESTABLE` | Hay línea base y ni la frecuencia ni el monto superan el umbral |
| `CON_VARIACION` | Hay línea base y al menos uno de los dos supera el umbral (criterio de aceptación 1) |

**Pendiente de validación:** los umbrales y la ventana por defecto son una propuesta; la definición de
terminado exige que el Product Owner los valide antes de darlos por definitivos.

## Datos históricos (SCRUM-318)

- Solo cuentan las compras **vigentes**: una compra anulada no participa y una devolución parcial
  participa con lo que sigue vigente (mismo criterio que el resto de los indicadores,
  `docs/compra/recalculo-por-anulacion.md`).
- Las compras con fecha posterior al momento de la evaluación se ignoran (fecha de origen errónea en
  Marketplace y Ventas): no deben inflar el periodo actual.
- La agregación se hace en la base (`AgregadoComprasCliente.comprasPorPeriodo`), una fila por
  `idClienteOrigen`, sin traer el historial a memoria.
- Un cliente sin ninguna compra vigente no se evalúa; si había quedado marcado de antes, se quita su
  estado.

## Detecciones y alertas (SCRUM-314, SCRUM-317, SCRUM-315, SCRUM-319)

Cada variación relevante se registra en `comportamiento.deteccion_cambio_comportamiento` (migración
`V10__cambios_comportamiento.sql`) con: cliente (`idClienteOrigen`), tipo (`FRECUENCIA` o `MONTO`),
dirección (`AUMENTO` o `DISMINUCION`), valor actual, valor habitual, variación relativa, umbral
aplicado, inicio y fin del periodo actual y fecha de detección. Las filas no se modifican ni se borran:
son el registro auditable de cada detección (SCRUM-319) y, a la vez, la alerta que se muestra en la
ficha del cliente (SCRUM-315). Cada detección también se escribe en el log del servicio.

El estado de la última evaluación de cada cliente se guarda en
`comportamiento.estado_comportamiento_cliente` (`SIN_LINEA_BASE`, `ESTABLE` o `CON_VARIACION`, con
la fecha de evaluación y las compras de su línea base). Así queda marcado el cliente "sin línea base".

### Sin duplicados (criterio de aceptación 4)

Si ya existe una detección del mismo cliente, tipo y dirección con fecha dentro del periodo actual
(los últimos `ventana` días), la variación no se vuelve a registrar. Como la evaluación corre todos los
días con una ventana móvil, una misma caída produce **una** alerta por periodo, no una por día. Si el
comportamiento cambia de sentido (por ejemplo, de una disminución a un aumento), sí se registra,
porque es otra variación.

## Cuándo se evalúa

`DeteccionCambiosScheduler` evalúa a todos los clientes con compras vigentes una vez al día
(`CAMBIO_COMPORTAMIENTO_CRON`, por defecto 04:00, después de inactivos y nivel de consumo). No se
evalúa al recibir cada compra: una disminución se manifiesta justamente por la **ausencia** de
compras, así que solo una evaluación periódica la puede detectar.

## Consultas

### Alertas de un cliente

```
GET /api/comportamiento/clientes/{clienteId}/cambios-comportamiento?identificador=CLI-5521
```

`identificador` es repetible (todos los identificadores de origen del cliente, como en los
indicadores). Devuelve el estado de la última evaluación y las detecciones, de la más reciente a la
más antigua (máximo 50):

```json
{
  "estado": "CON_VARIACION",
  "evaluadoEn": "2026-10-10T04:00:00Z",
  "alertas": [
    {
      "id": 18,
      "idClienteOrigen": "CLI-5521",
      "tipo": "FRECUENCIA",
      "direccion": "DISMINUCION",
      "valorActual": 1,
      "valorHabitual": 4,
      "variacion": -0.75,
      "umbral": 0.5,
      "periodoDesde": "2026-07-12T04:00:00Z",
      "periodoHasta": "2026-10-10T04:00:00Z",
      "detectadoEn": "2026-10-10T04:00:00Z",
      "mensaje": "La frecuencia de compra bajó un 75 % respecto a lo habitual (1 frente a 4 por periodo de 90 días)"
    }
  ]
}
```

- `estado` combina los identificadores del cliente: `CON_VARIACION` si alguno lo está, si no
  `ESTABLE`, si no `SIN_LINEA_BASE`; `SIN_EVALUAR` si ninguno se evaluó todavía (o no se enviaron
  identificadores). No es un error.
- Sin cambios de permisos: ya aplica `INDICADORES_CONSULTAR` a todo `GET /api/comportamiento/**`.

### Ficha integral (SCRUM-567)

`GET /api/perfil/clientes/{clienteId}/ficha-integral` suma el bloque `cambiosComportamiento`
(`estado`, `evaluadoEn`, `alertas`, `sinDatos`), consultado a `servicio-comportamiento` con los
identificadores de origen del cliente. Llega con `sinDatos: true` si el cliente no tiene
identificadores o el servicio no responde, y el resto de la ficha carga igual (mismo criterio que
segmento, puntos y nivel de consumo). La pantalla de la ficha muestra cada alerta con su fecha, el
tipo de variación, la dirección y los valores comparados (criterio de aceptación 3); solo la ven los
roles con `FICHA_INTEGRAL_CONSULTAR`.

## Limitaciones

- **Perfiles unificados:** igual que inactivos y nivel de consumo, la evaluación se hace por
  `idClienteOrigen`. Un cliente con varios identificadores puede tener detecciones de cada uno; la
  consulta y la ficha las muestran juntas.
- **Clientes sin compras recientes:** si todas las compras vigentes son anteriores a la línea base, el
  cliente queda `SIN_LINEA_BASE`. Ese caso ya lo cubren los clientes inactivos (SCRUM-31).
- **Estacionalidad:** la línea base es un promedio simple de los periodos anteriores; no distingue
  temporadas (por ejemplo, fin de año). Un umbral más alto o una ventana más larga reducen las falsas
  alertas.

## Pruebas (SCRUM-320)

- `CriterioCambioComportamientoTest`: ventanas, umbral exacto, validación de la configuración.
- `DetectorCambiosComportamientoTest`: línea base insuficiente, cliente nuevo a mitad de la línea
  base, aumento, caída, estable y no duplicación, sin base de datos.
- `CambiosComportamientoIntegracionTest` (PostgreSQL real): historiales simulados de aumento, caída
  y comportamiento estable, anulaciones, compras con fecha futura, no duplicación y la consulta.
