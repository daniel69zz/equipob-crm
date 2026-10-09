# Evolución del consumo por categoría (SCRUM-32)

Lógica con la que el CRM muestra cómo evoluciona, periodo a periodo, el consumo de un cliente en
cada categoría de producto, para que el Analista Comercial identifique en qué categorías crece, se
mantiene o deja de comprar (RF-24, EPIC-05 Análisis del Comportamiento). Parte del historial de
compras (SCRUM-15, `docs/compra/historial-compras.md`), de la categoría que trae cada ítem
(SCRUM-18) y de la misma definición de compra vigente que usan los demás indicadores
(`docs/compra/ticket-promedio.md`, `docs/compra/recalculo-por-anulacion.md`).

## Qué compras entran (SCRUM-304)

- Cuentan las compras `CONFIRMADA` y `DEVOLUCION_PARCIAL`. Una compra `ANULADA` no aporta nada a
  ningún periodo ni a ninguna categoría (criterio de aceptación 4). Es la misma condición
  `ComprasDelCliente.vigente` (`montoTotal - montoRevertido > 0`) que usan el ticket promedio, el
  valor acumulado y la recencia.
- De una compra con devolución parcial se descuenta, **por categoría**, lo devuelto: `unidades =
  comprado - devuelto` y `monto = comprado - devuelto`. Si de una categoría no queda nada (ni
  unidades ni monto), esa compra deja de contar para esa categoría, aunque siga vigente por otras.
- Lo devuelto se descuenta **en el periodo de la compra original**, no en el de la devolución. La
  evolución mide qué compró el cliente en cada periodo y sigue siendo coherente con el valor
  acumulado: la suma de todas las categorías y periodos es el monto vigente de las compras del
  rango.
- Solo entran las compras cuya fecha cae dentro del rango consultado (`desde` y `hasta`
  incluidos, días completos en la zona horaria del negocio, ver más abajo).

## Periodos

| `periodo` | Clave | Ejemplo |
|---|---|---|
| `MENSUAL` (por defecto) | `AAAA-MM` | `2026-09` |
| `TRIMESTRAL` | `AAAA-Tn` | `2026-T3` (julio a septiembre) |
| `ANUAL` | `AAAA` | `2026` |

- Cada compra pertenece al periodo de su fecha **en la zona horaria del negocio**,
  `comportamiento.evolucion.zona-horaria` (por defecto `America/La_Paz`, variable de entorno
  `EVOLUCION_ZONA_HORARIA`). Los eventos de Marketplace y Ventas llegan con `-04:00` y la base
  guarda la fecha en UTC: una compra del 30/09 a las 22:00 en La Paz es de septiembre aunque en UTC
  ya sea 1/10. La agrupación no depende de la zona horaria del servidor.
- El rango va de `desde` a `hasta`, ambos incluidos. Sin fechas, son los **últimos 12 meses**:
  desde el primer día del mes de hace 11 meses hasta hoy.
- Aparecen **todos** los periodos del rango, con o sin compras (criterio 3).
- El primer y el último periodo se recortan al rango: con `desde=2026-01-15`, el periodo `2026-01`
  va del 15 al 31 de enero y se marca `parcial: true`, para no compararlo como si fuera un mes
  completo sin avisar.
- Una consulta abarca como máximo **60 periodos** (5 años en meses). Para rangos más largos se
  elige un periodo trimestral o anual.

## Métricas por categoría y periodo

| Campo | Significado |
|---|---|
| `monto` | Monto vigente de la categoría en las compras del periodo |
| `compras` | En cuántas compras distintas del periodo aparece la categoría (frecuencia) |
| `unidades` | Unidades vigentes de la categoría en el periodo |

Un periodo sin compras de una categoría aparece con `monto: 0`, `compras: 0` y `unidades: 0`: no
se omite (criterio 3).

Cada categoría trae además sus totales en el rango (`monto`, `compras`, `unidades`). Las categorías
se ordenan de mayor a menor consumo: monto, luego compras y por último nombre, sin distinguir
mayúsculas.

`totales` resume cada periodo con las categorías mostradas: la suma de sus montos y cuántas compras
distintas las incluyen. Una compra con dos categorías cuenta una vez en el total del periodo y una
vez en cada categoría.

## Comparación entre periodos (SCRUM-303)

Cada punto de la evolución trae dos variaciones del monto:

| Campo | Contra qué compara |
|---|---|
| `variacionAnterior` | El periodo inmediatamente anterior. El primer periodo del rango no tiene anterior: `null` |
| `variacionBase` | El periodo base, `periodoBase` (por defecto, el primero del rango) |

Cada variación tiene `monto` (actual menos referencia) y `porcentaje` (`monto / referencia × 100`,
con 2 decimales). Si la referencia es `0` no hay base para un porcentaje y `porcentaje` es `null`:
pasar de nada a algo es crecer, pero no un porcentaje finito.

### Tendencia de cada categoría

Para resumir si la categoría crece, se mantiene o se abandona, se compara el monto de la **primera
mitad** de los periodos completos del rango con el de la **segunda mitad**. Con un número impar,
el periodo central no entra en ninguna mitad, para que las dos tengan el mismo tamaño.

Los periodos **parciales no entran en las mitades**: el mes en curso, que el rango por defecto
incluye hasta hoy, tendría menos compras solo por estar incompleto, y un cliente que compra lo mismo
cada mes aparecería decreciendo. El periodo parcial final solo se mira para no dar por perdido a
quien volvió a comprar en él.

| `tendencia` | Cuándo |
|---|---|
| `SIN_COMPARACION` | El rango tiene menos de dos periodos completos: no hay con qué comparar |
| `SIN_CONSUMO` | La categoría no tiene consumo en todo el rango (solo pasa si se pidió por filtro) |
| `DEJO_DE_COMPRAR` | Hubo consumo, pero ninguno en la segunda mitad ni en el periodo parcial final |
| `CRECE` | La segunda mitad supera a la primera en más de 10 %, o la primera no tuvo consumo (también quien empezó a comprar recién en el periodo parcial final) |
| `DECRECE` | La segunda mitad queda más de 10 % por debajo de la primera, o no tuvo consumo pero el cliente volvió a comprar en el periodo parcial final |
| `ESTABLE` | La diferencia entre las dos mitades no supera el 10 % |

El umbral de 10 % evita llamar "crecimiento" a variaciones menores, propias de compras puntuales.

## Filtros (SCRUM-302)

| Parámetro | Uso |
|---|---|
| `identificador` | Repetible. Los identificadores del cliente en Marketplace y Ventas, como en el historial (`docs/compra/historial-compras.md`). Los repetidos se consultan una sola vez. Sin identificadores, el resultado viene vacío |
| `desde`, `hasta` | `AAAA-MM-DD`, ambos incluidos. Sin ellos, los últimos 12 meses |
| `periodo` | `MENSUAL` (por defecto), `TRIMESTRAL` o `ANUAL` |
| `categoria` | Repetible. Nombre exacto de la categoría, sin espacios a los lados. Sin filtro se muestran todas las categorías con consumo en el rango; con filtro, solo esas (criterio 2), aunque no tengan consumo |
| `periodoBase` | Clave de un periodo del rango (`2026-07`, `2026-T3`, `2026`) contra el que se calcula `variacionBase` |

`categoriasDisponibles` lista siempre todas las categorías con consumo en el rango, sin aplicar el
filtro de categorías, para que la pantalla pueda ofrecerlas como opciones.

Igual que en las categorías más consumidas (SCRUM-18), no se unifican mayúsculas ni tildes:
`Hogar` y `hogar` son categorías distintas. Por eso el filtro compara el nombre exacto.

Se responde `400` con un mensaje claro cuando `desde` es posterior a `hasta`, el rango supera los
60 periodos, `periodo` no es uno de los tres tipos, una categoría viene vacía o supera los 100
caracteres, se piden más de 50 categorías o `periodoBase` no es un periodo del rango.

## Calidad de los datos (SCRUM-306)

La ingesta ya rechaza los eventos incompletos y la base impide categorías vacías, montos no
positivos y compras duplicadas (`docs/ingesta/historial-compras.md`,
`docs/ingesta/idempotencia-eventos-venta.md`). Aun así el cálculo no depende de eso y verifica lo
que usa:

- **Categoría no asignada:** un registro con la categoría nula o en blanco se agrupa como
  **«Sin categoría»**, igual que en SCRUM-18. Los espacios a los lados se quitan: `"Hogar "` y
  `"Hogar"` son la misma categoría.
- **Monto faltante:** un monto nulo cuenta como cero.
- **Duplicados:** un identificador repetido en la consulta se busca una sola vez; los ítems de la
  misma categoría dentro de una compra se suman en un solo registro; una misma compra no puede
  registrarse dos veces (restricción única y control de idempotencia de SCRUM-23).
- **Devoluciones incoherentes:** lo devuelto de una categoría nunca resta más de lo comprado, y una
  devolución de una categoría que la compra no tenía no resta nada.
- **Desglose incoherente:** se comprueba que la suma de las categorías de cada compra coincida con
  su monto vigente (`montoTotal - montoRevertido`).

El resultado de esas comprobaciones viaja en `calidadDatos`:

| Campo | Significado |
|---|---|
| `comprasAnalizadas` | Compras vigentes del rango que entraron al cálculo |
| `comprasAnuladasExcluidas` | Compras anuladas del rango que no se incluyeron (criterio 4) |
| `registrosSinCategoria` | Registros agrupados como «Sin categoría» |
| `registrosSinMonto` | Registros con el monto nulo, contados como cero |
| `devolucionesInconsistentes` | Devoluciones de una categoría mayores a lo comprado o de una categoría que la compra no tenía |
| `comprasConDesgloseInconsistente` | Compras cuyo desglose por categoría no suma su monto vigente |
| `consistente` | Verdadero si los cuatro contadores anteriores están en cero |

Con `consistente: false` la pantalla avisa que algunos datos se corrigieron al calcular, para que
el analista lo tenga en cuenta antes de sacar conclusiones.

## Cuándo se actualiza

Igual que el ticket promedio y el valor acumulado, la evolución **se calcula al consultarla** sobre
`compra`, `compra_item` y `anulacion_item`; no se guarda en otra tabla. Una compra nueva, una
devolución o una anulación se reflejan en la consulta siguiente.

## Consulta

```
GET /api/comportamiento/clientes/{clienteId}/evolucion-consumo
      ?identificador=CLI-5521
      &identificador=mp-user-3307
      &desde=2026-07-01
      &hasta=2026-09-30
      &periodo=MENSUAL
      &categoria=Electrónica
      &categoria=Hogar
```

Sigue la misma composición que el historial y los indicadores: el frontend obtiene
`identificadoresOrigen` del perfil y los pasa como `identificador`; `{clienteId}` solo identifica
al cliente para la auditoría del Gateway (`docs/seguridad/convencion-rutas-clientes.md`).

```json
{
  "desde": "2026-07-01",
  "hasta": "2026-09-30",
  "periodo": "MENSUAL",
  "zonaHoraria": "America/La_Paz",
  "periodoBase": "2026-07",
  "periodos": [
    { "clave": "2026-07", "inicio": "2026-07-01", "fin": "2026-07-31", "parcial": false },
    { "clave": "2026-08", "inicio": "2026-08-01", "fin": "2026-08-31", "parcial": false },
    { "clave": "2026-09", "inicio": "2026-09-01", "fin": "2026-09-30", "parcial": false }
  ],
  "categoriasDisponibles": ["Accesorios", "Electrónica", "Hogar", "Limpieza"],
  "categorias": [
    {
      "categoria": "Electrónica",
      "monto": 450.00,
      "compras": 2,
      "unidades": 2,
      "tendencia": "CRECE",
      "evolucion": [
        { "periodo": "2026-07", "monto": 0, "compras": 0, "unidades": 0,
          "variacionAnterior": null,
          "variacionBase": { "monto": 0, "porcentaje": null } },
        { "periodo": "2026-08", "monto": 150.00, "compras": 1, "unidades": 1,
          "variacionAnterior": { "monto": 150.00, "porcentaje": null },
          "variacionBase": { "monto": 150.00, "porcentaje": null } },
        { "periodo": "2026-09", "monto": 300.00, "compras": 1, "unidades": 1,
          "variacionAnterior": { "monto": 150.00, "porcentaje": 100.00 },
          "variacionBase": { "monto": 300.00, "porcentaje": null } }
      ]
    },
    {
      "categoria": "Hogar",
      "monto": 180.00,
      "compras": 3,
      "unidades": 3,
      "tendencia": "DEJO_DE_COMPRAR",
      "evolucion": ["…"]
    }
  ],
  "totales": [
    { "periodo": "2026-07", "monto": 120.00, "compras": 2 },
    { "periodo": "2026-08", "monto": 210.00, "compras": 2 },
    { "periodo": "2026-09", "monto": 300.00, "compras": 1 }
  ],
  "calidadDatos": {
    "comprasAnalizadas": 5,
    "comprasAnuladasExcluidas": 1,
    "registrosSinCategoria": 0,
    "registrosSinMonto": 0,
    "devolucionesInconsistentes": 0,
    "comprasConDesgloseInconsistente": 0,
    "consistente": true
  },
  "sinDatos": false
}
```

Un cliente sin compras vigentes en el rango recibe todos los periodos, `categorias: []` (o las
categorías filtradas, en cero) y `sinDatos: true`, no un error.

## Permisos

Exige `EVOLUCION_CONSUMO_CONSULTAR` (SCRUM-305), que tienen el Administrador de CRM y el Gerente
Comercial, el rol que el catálogo asigna al análisis de clientes
(`docs/seguridad/catalogo-roles.md`, `docs/seguridad/matriz-permisos.md`). Como la ruta está bajo
`/clientes/{clienteId}`, el Gateway registra cada consulta en la auditoría de accesos.

## Pruebas

- `CalculoEvolucionConsumoTest`, `ComparacionPeriodosTest`, `DepuracionConsumoTest` y
  `FiltroEvolucionConsumoTest`: el cálculo, la comparación, la depuración y los filtros sin base
  de datos.
- `EvolucionConsumoControllerTest`: lectura de parámetros y forma de la respuesta.
- `EvolucionConsumoIntegracionTest` (PostgreSQL real): clientes con compras de varias categorías y
  periodos, periodos sin compras, devoluciones parciales y compras anuladas, por el endpoint
  (criterios 1 a 4).
- `AccesoPorRolTest` (API Gateway): acceso del Administrador y del Gerente Comercial, y rechazo del
  Agente de Atención.

Diagramas de clases y de secuencia: `docs/compra/diagramas-evolucion-consumo-categoria.md`.
