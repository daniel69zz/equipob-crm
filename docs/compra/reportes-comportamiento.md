# Reporte de comportamiento de compra (SCRUM-58)

El Analista Comercial o el Gerente Comercial obtiene la frecuencia, el valor y las categorías de compra de la
cartera o de un segmento en un periodo, para analizar el comportamiento de compra (RF-50, EPIC-10). La
consulta base es SCRUM-468; los cortes, SCRUM-466 (frecuencia), SCRUM-467 (valor) y SCRUM-465
(categorías); los filtros, SCRUM-469; la validación de resultados, SCRUM-470. Diagramas en
[reportes-comportamiento-diagramas.md](reportes-comportamiento-diagramas.md) (SCRUM-610).

## Consulta base (SCRUM-468)

**Qué compras entran.** Las compras **vigentes** (monto total menos lo revertido mayor que cero) con fecha
dentro del periodo y **vinculadas a un perfil** de cliente (`compra.id_cliente`):

| Filtro | Clientes del grupo | Compras |
|---|---|---|
| Con segmento | Los que tiene asignados hoy, según servicio-segmentacion (`GET /api/segmentacion/segmentos/miembros`, SCRUM-357) | Las vinculadas a esos perfiles |
| Sin segmento | Toda la cartera | Todas las vinculadas a algún perfil |

Es la misma regla que la comparación entre periodos por segmento (SCRUM-40). Una compra todavía pendiente de
vinculación no tiene perfil y no entra en ninguno de los dos modos. Una compra anulada por completo no cuenta,
y una devolución parcial descuenta lo devuelto.

**El periodo** son dos fechas calendario, ambas inclusive, en la zona horaria del negocio
(`America/La_Paz`, `CONSUMO_PERIODO_ZONA_HORARIA`), con un máximo de cinco años (`periodo.RangoPeriodo`, el
mismo del valor por periodo, SCRUM-39). Se calcula al consultarlo: una compra, devolución o anulación nueva se
refleja en el reporte siguiente.

**No tiene cálculos propios.** Reutiliza los agregados que ya usan otros reportes e indicadores:

| Corte | De dónde sale |
|---|---|
| Compras y valor | `periodo.AgregadoConsumoPeriodo` (`resumirPerfiles`, de SCRUM-40, y `resumirVinculadas`, nuevo) |
| Clientes con compras | `AgregadoConsumoPeriodo.clientesConCompras` / `clientesConComprasVinculadas`: perfiles distintos con alguna compra vigente |
| Categorías | `compra.AgregadoConsumoCategorias` (`consumirPerfiles` / `consumirVinculadas`, nuevos) + `indicador.RankingCategorias` |
| Valor total y ticket promedio | `indicador.ValorAcumulado.de` y `indicador.TicketPromedio.de` |

`AgregadoConsumoCategorias` ahora acepta tres alcances: los identificadores de un cliente (lo de siempre, que
usan la evolución del consumo y las preferencias), un grupo de perfiles por lotes de 1000, o todas las compras
vinculadas. Para un cliente su comportamiento no cambia.

## Qué muestra

| Corte | Campos |
|---|---|
| Frecuencia (SCRUM-466) | `compras` vigentes del periodo, `clientesConCompras` y `comprasPorCliente` (compras ÷ clientes que compraron) |
| Valor (SCRUM-467) | `total` vigente, `ticketPromedio` (total ÷ compras) y `valorPorCliente` (total ÷ clientes que compraron) |
| Categorías (SCRUM-465) | por categoría: `compras`, `unidades`, `monto` vigente y `porcentaje` del monto de todas las categorías |

Los promedios van con dos decimales y redondeo al más cercano, y son nulos si nadie compró (no un cero ni una
división entre cero). Las categorías siguen el orden del ranking de SCRUM-18: monto, compras y nombre; una
categoría devuelta por completo desaparece y las líneas sin categoría van como «Sin categoría».

## Coincidencia con los indicadores del cliente (criterio 3, SCRUM-470)

El reporte usa las **mismas funciones** que los indicadores de un cliente, no una copia:

| Indicador del cliente (`GET /api/comportamiento/clientes/{id}/indicadores`) | Campo del reporte |
|---|---|
| `frecuencia` (contador de compras vigentes, SCRUM-37) | `frecuencia.compras` |
| `valorAcumulado.valor` | `valor.total` |
| `ticketPromedio.valor` | `valor.ticketPromedio` |
| Ranking de categorías (`RankingCategorias`, el de las preferencias) | `categorias` |

Por eso, para un segmento de un solo cliente y un periodo que abarque todo su historial, el reporte da lo
mismo que sus indicadores; y para la cartera, la suma de los indicadores de sus clientes. Las pruebas lo
comprueban así, no con números escritos a mano. La única diferencia posible es una compra todavía sin vincular:
el indicador la cuenta (busca por identificador de origen) y el reporte no (busca por perfil).

## Pantalla

**Análisis → Comportamiento de compra** (`/analisis/comportamiento`). Solo aparece para quien tiene
`REPORTES_CONSULTAR` (Administrador de CRM y Gerente Comercial).

1. Elija el periodo (desde, hasta) y, si quiere, un **segmento**; sin él, el reporte cubre toda la cartera.
   Por defecto es el mes en curso hasta hoy.
2. Pulse **Generar**.

| Situación | Qué se ve |
|---|---|
| Periodo invertido | «La fecha «desde» no puede ser posterior a «hasta». Corrija el periodo.» **sin consultar al servidor** (criterio 2) |
| Falta una fecha | «Indique las fechas desde y hasta del periodo.», sin consultar |
| Más de cinco años | Aviso con los días del periodo, sin consultar |
| Sin compras o segmento sin clientes | «Sin datos para este filtro» con el motivo; no es un error |
| Rol sin permiso | Sin enlace en el menú; la ruta lleva a «Acceso denegado» y el API responde 403 |

El servidor vuelve a validar el periodo (400 con el motivo): la validación de la pantalla evita la consulta
inútil, no reemplaza a la del servidor.

## API

```
GET /api/comportamiento/reportes/comportamiento?desde=2026-09-01&hasta=2026-09-30&segmento=Alto%20valor
```

```json
{
  "generadoEn": "2026-11-10T12:00:00Z",
  "desde": "2026-09-01",
  "hasta": "2026-09-30",
  "zonaHoraria": "America/La_Paz",
  "alcance": { "segmento": "Alto valor", "clientesDelSegmento": 2 },
  "sinDatos": false,
  "mensaje": null,
  "frecuencia": { "compras": 3, "clientesConCompras": 2, "comprasPorCliente": 1.50 },
  "valor": { "total": 1100.00, "ticketPromedio": 366.67, "valorPorCliente": 550.00 },
  "categorias": [
    { "categoria": "Hogar", "compras": 2, "unidades": 2, "monto": 700.00, "porcentaje": 63.64, "sinCategoria": false },
    { "categoria": "Electrónica", "compras": 1, "unidades": 1, "monto": 300.00, "porcentaje": 27.27, "sinCategoria": false },
    { "categoria": "Accesorios", "compras": 1, "unidades": 2, "monto": 100.00, "porcentaje": 9.09, "sinCategoria": false }
  ]
}
```

Sin `segmento`, `alcance.segmento` y `alcance.clientesDelSegmento` son `null`. Un segmento sin clientes o un
periodo sin compras responde **200** con `sinDatos: true` y el `mensaje`.

## Permisos

Usa el permiso de los reportes, `REPORTES_CONSULTAR` (SCRUM-462), y no `INDICADORES_CONSULTAR`: el Agente de
Atención consulta los indicadores de un cliente, pero no los reportes de la cartera.

- El API Gateway exige `REPORTES_CONSULTAR` en `GET /api/comportamiento/reportes/**`, con una regla anterior a la
  genérica de `/api/comportamiento/**`, y deniega los métodos de escritura.
- servicio-comportamiento lo vuelve a comprobar (`reporte.PermisoReportes`): sin identidad responde **401** y sin
  el permiso **403**, con el mismo cuerpo de error que el resto del servicio.

## Pruebas

| Prueba | Qué cubre |
|---|---|
| `ReporteComportamientoIntegracionTest` | Con PostgreSQL real y compras por la ingesta: criterio 1, cartera sin compras sin vincular, anulación y devolución, sin datos, periodo inválido, coincidencia con indicadores y ranking (criterio 3), compra nueva reflejada, 401 y 403 |
| `ConsultaReporteComportamientoTest` | Segmento en blanco = cartera, zona horaria del periodo, segmento demasiado largo, promedios nulos sin compras |
| `AccesoPorRolTest` (Gateway) | `REPORTES_CONSULTAR` en la ruta; el Agente sigue viendo los indicadores de un cliente |
| `e2e/scrum58.spec.ts` (navegador) | Criterio 1 por segmento y cartera, sin datos, **criterio 2 comprobando que no sale ninguna petición**, criterio 3 contra los indicadores reales, Agente y sin sesión |

La prueba de navegador corre contra un entorno aislado:

```
herramientas/pruebas-reportes/ejecutar.sh e2e/scrum58.spec.ts
```

Levanta PostgreSQL y RabbitMQ desechables y los servicios, y siembra **solo por eventos**: las altas y las
compras con `herramientas/simulador-eventos/publicar.py` y el criterio «Alto valor» por la API, así que el
segmento lo asigna segmentación con sus reglas. Al terminar borra los contenedores y detiene los procesos.
Necesita Docker, JDK 26, Maven y las dependencias del frontend; en Windows el equivalente sigue el modelo de
`herramientas/pruebas-scrum43/ejecutar.ps1`.
