# Retención de clientes por cohortes (SCRUM-36)

Análisis de retención por cohorte de primera compra: agrupa a los clientes por el mes en que
compraron por primera vez y muestra qué cantidad y qué porcentaje vuelve a comprar en cada mes
posterior, para medir la retención y detectar en qué momento se pierden los clientes (RF-28, EPIC-05
Análisis del Comportamiento). Usa el historial de compras (SCRUM-15/16) con el mismo criterio de
vigencia que el resto de los indicadores. Diagramas en `docs/compra/retencion-cohortes-diagramas.md`.

## Cohortes (SCRUM-327)

- **Granularidad: mensual.** La cohorte de un cliente es el mes calendario de su **primera compra
  vigente** (criterio de aceptación 1).
- **Zona horaria:** los meses se cuentan en la hora de Bolivia (`America/La_Paz`, configurable con
  `COHORTES_ZONA`), no en UTC. Una compra del 31 de julio a las 22:00 en La Paz (1 de agosto 02:00 UTC)
  es de julio.
- **Periodo `n`:** el mes que está `n` meses después del de la cohorte. El periodo 0 es el propio mes
  de la cohorte (siempre 100 %).
- **Retenido en el periodo `n`:** el cliente tiene al menos una compra vigente en ese mes. Varias
  compras en el mismo mes cuentan una vez. Un cliente puede no comprar un mes y volver al siguiente:
  cuenta en los meses en que compra (retención "por periodo", no acumulada).
- **Porcentaje:** `retenidos del periodo / clientes de la cohorte × 100`, con dos decimales.
- **Periodos futuros:** solo se calculan los periodos hasta el mes en curso. Los meses que aún no
  llegaron no se muestran (no son un 0 %). El mes en curso está incompleto y puede seguir subiendo.
- **Cliente:** se identifica por `idClienteOrigen`, igual que inactivos y nivel de consumo.

## Datos históricos (SCRUM-328)

- **Anulaciones (criterio de aceptación 3):** una compra anulada por completo no cuenta ni como
  primera compra ni como retención. Si la primera compra de un cliente se anuló, su cohorte es la de
  su primera compra vigente. Una devolución parcial deja la compra vigente y sí cuenta.
- Las compras con fecha posterior al momento del cálculo (fecha de origen errónea) se ignoran.
- La agregación se hace en la base (`HistorialCohortes`), que devuelve una fila por cohorte y mes con
  la cantidad de clientes distintos, sin traer el historial a memoria. La cohorte se calcula sobre
  **todo** el historial, aunque el rango pedido empiece después.

## Matriz de retención (SCRUM-330, SCRUM-329)

```
GET /api/comportamiento/cohortes?desde=2026-07&hasta=2026-10
```

- `desde` y `hasta` son meses de cohorte (`AAAA-MM`). Por defecto, los últimos 12 meses hasta el mes
  en curso. Como máximo 36 meses, y `hasta` no puede ser posterior al mes en curso. Un formato
  inválido o un rango incoherente responden 400.
- Cada cohorte del rango aparece aunque no tenga clientes nuevos (con `clientes: 0` y sin periodos),
  para que la matriz no tenga huecos.
- Sin cambios de permisos: ya aplica `INDICADORES_CONSULTAR` a todo `GET /api/comportamiento/**`
  (Administrador de CRM, Gerente Comercial y Agente de Atención).

```json
{
  "granularidad": "MENSUAL",
  "zonaHoraria": "America/La_Paz",
  "desde": "2026-07",
  "hasta": "2026-10",
  "mesActual": "2026-10",
  "cohortes": [
    {
      "cohorte": "2026-07",
      "clientes": 4,
      "periodos": [
        { "periodo": 0, "mes": "2026-07", "clientes": 4, "porcentaje": 100.00 },
        { "periodo": 1, "mes": "2026-08", "clientes": 2, "porcentaje": 50.00 },
        { "periodo": 2, "mes": "2026-09", "clientes": 1, "porcentaje": 25.00 },
        { "periodo": 3, "mes": "2026-10", "clientes": 1, "porcentaje": 25.00 }
      ]
    }
  ]
}
```

## Cálculo manual de referencia (DoD)

Datos de prueba, con octubre de 2026 como mes en curso. Las pruebas (`CalculadoraRetencionTest` y
`RetencionCohortesIntegracionTest`) comprueban que el sistema da exactamente esta matriz.

| Cliente | Compras vigentes (mes) | Nota | Cohorte |
|---|---|---|---|
| A | jul, ago, oct | | 2026-07 |
| B | jul, sep | | 2026-07 |
| C | jul | | 2026-07 |
| D | jul, ago (dos compras) | dos compras en agosto cuentan una vez | 2026-07 |
| E | ago, sep | | 2026-08 |
| F | ago | su compra de septiembre fue anulada | 2026-08 |
| G | ago | su compra de julio fue anulada: no es su primera compra | 2026-08 |
| H | sep, oct | | 2026-09 |
| I | oct | | 2026-10 |

| Cohorte | Clientes | Mes 0 | Mes 1 | Mes 2 | Mes 3 |
|---|---:|---|---|---|---|
| 2026-07 | 4 | 4 (100 %) | 2 (50 %): A, D | 1 (25 %): B | 1 (25 %): A |
| 2026-08 | 3 | 3 (100 %) | 1 (33,33 %): E | 0 (0 %) | — |
| 2026-09 | 1 | 1 (100 %) | 1 (100 %): H | — | — |
| 2026-10 | 1 | 1 (100 %) | — | — | — |

## Visualización (SCRUM-331)

Pantalla **Retención por cohortes** (`/analisis/cohortes`, menú "Análisis", permiso
`INDICADORES_CONSULTAR`): una matriz con una fila por cohorte y una columna por periodo (Mes 0, Mes 1,
…), cada celda con el porcentaje y la cantidad de clientes, coloreada con más intensidad cuanto mayor
es la retención (criterio de aceptación 4). Permite elegir el rango de meses.

## Pruebas (SCRUM-332)

- `CalculadoraRetencionTest`: la matriz del cálculo manual, porcentajes con decimales, cohortes vacías
  y periodos que aún no llegaron, sin base de datos.
- `RetencionCohortesIntegracionTest` (PostgreSQL real): el mismo cálculo manual a partir de eventos de
  compra y anulación reales, zona horaria en el borde del mes, compras con fecha futura, cohortes
  anteriores al rango y validación de parámetros.
