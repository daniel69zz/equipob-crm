# Reporte comparativo entre periodos (SCRUM-62)

El Analista Comercial compara los indicadores de clientes de la cartera, o de un segmento, entre dos periodos
para medir la evolución de la cartera (RF-54, EPIC-10). La consulta por periodo es SCRUM-497; los indicadores
y las reglas de comparación, SCRUM-498; la selección de los periodos, SCRUM-494; la generación, SCRUM-496; la
exportación, SCRUM-499; los permisos, SCRUM-500; la presentación, SCRUM-495; las pruebas, SCRUM-501.
Diagramas en [reporte-comparativo-diagramas.md](reporte-comparativo-diagramas.md) (SCRUM-612).

## Consulta por periodo (SCRUM-497)

Cada periodo es **el reporte de comportamiento de compra de SCRUM-58** ([reportes-comportamiento.md](reportes-comportamiento.md))
de ese periodo y ese alcance: las compras vigentes vinculadas a un perfil, de toda la cartera o de los clientes
del segmento. El comparativo no tiene consultas propias: llama dos veces al reporte de SCRUM-58, así que cada
columna coincide con ese reporte para el mismo periodo, y por lo tanto con los indicadores del cliente.

## Indicadores y reglas de comparación (SCRUM-498)

| Código | Indicador | En el reporte de SCRUM-58 |
|---|---|---|
| `COMPRAS` | Compras | `frecuencia.compras` |
| `CLIENTES_CON_COMPRAS` | Clientes que compraron | `frecuencia.clientesConCompras` |
| `COMPRAS_POR_CLIENTE` | Compras por cliente | `frecuencia.comprasPorCliente` |
| `VALOR_TOTAL` | Valor total (Bs) | `valor.total` |
| `TICKET_PROMEDIO` | Ticket promedio (Bs) | `valor.ticketPromedio` |
| `VALOR_POR_CLIENTE` | Valor por cliente (Bs) | `valor.valorPorCliente` |

**Periodos.** El periodo **A es la base** y el **B el comparado**; no hace falta que A sea anterior a B. Cada uno
son dos fechas inclusive, completas, en orden y de hasta cinco años, y **no pueden superponerse**: una compra que
cayera en los dos contaría dos veces y la variación no diría nada (criterio 2). Es la misma regla de la
comparación de SCRUM-40 (`comparacion.PeriodosAComparar`), que se reutiliza tal cual.

**Variación.** La de SCRUM-40 (`comparacion.VariacionConsumo`): absoluta `B − A`, porcentual `(B − A) / A × 100` a
dos decimales, y tendencia:

| Tendencia | Cuándo |
|---|---|
| `CRECE` / `DECAE` | B mayor / menor que A |
| `ESTABLE` | B igual a A (también 0 y 0) |
| `NUEVO` | A es 0 y B no: hay variación absoluta pero no porcentaje, porque no hay base |
| sin tendencia | Un promedio no existe en un periodo sin compras (es nulo, no 0): no se compara, y la variación queda vacía |

## Pantalla (SCRUM-494, SCRUM-495)

**Análisis → Comparativo entre periodos** (`/analisis/comparativo`). Solo aparece para quien tiene
`REPORTES_CONSULTAR`. Por defecto compara el mes anterior (A) con el mes en curso hasta hoy (B).

| Situación | Qué se ve |
|---|---|
| Periodos superpuestos | «Los periodos se superponen (…). Elija periodos que no compartan días.» **sin consultar al servidor** (criterio 2) |
| Un periodo al revés, incompleto o de más de cinco años | El mismo aviso de SCRUM-58, con «Periodo A:» o «Periodo B:» delante, sin consultar |
| Ninguno de los dos periodos tiene compras | «Sin datos para comparar» con el motivo; no es un error |
| Un promedio sin base | «—» en el valor y la variación, y «Sin base» en la tendencia |
| Rol sin permiso | Sin enlace en el menú; la ruta lleva a «Acceso denegado» y el API responde 403 |

El servidor vuelve a validar los periodos y responde 400 con el motivo.

## Exportación (SCRUM-499)

**Exportar CSV** descarga el archivo que genera el servidor. Se escribe a partir **del mismo objeto** del reporte
que devuelve el JSON, sin volver a calcular nada, así que conserva los mismos valores (criterio 3); las pruebas
lo comparan celda por celda.

- Formato RFC 4180: separador coma, punto decimal, sin separador de miles, campos con comas o comillas entre
  comillas dobles, fin de línea CRLF y marca UTF-8 al inicio para que Excel muestre bien las tildes.
- Primero el encabezado (alcance, periodos, zona horaria, fecha de generación) y luego una fila por indicador:
  `Indicador,Codigo,Periodo A,Periodo B,Variacion,Variacion %,Tendencia`. Los valores van con la misma escala
  que el JSON; un valor nulo va vacío.
- Nombre del archivo: `reporte-comparativo_<desdeA>_<hastaA>_vs_<desdeB>_<hastaB>.csv`.

La exportación genérica de los reportes a CSV y PDF es SCRUM-60. Esta es solo la de este reporte, la que pide
SCRUM-499; si SCRUM-60 trae un exportador común, este puede pasar a usarlo.

## API

```
GET /api/comportamiento/reportes/comparativo?desdeA=2026-08-01&hastaA=2026-08-31&desdeB=2026-09-01&hastaB=2026-09-30&segmento=
GET /api/comportamiento/reportes/comparativo/csv?…mismos parámetros…
```

```json
{
  "generadoEn": "2026-11-10T12:00:00Z",
  "periodoA": { "desde": "2026-08-01", "hasta": "2026-08-31" },
  "periodoB": { "desde": "2026-09-01", "hasta": "2026-09-30" },
  "zonaHoraria": "America/La_Paz",
  "alcance": { "segmento": null, "clientesDelSegmento": null },
  "sinDatos": false,
  "mensaje": null,
  "indicadores": [
    { "codigo": "COMPRAS", "nombre": "Compras", "periodoA": 2, "periodoB": 4,
      "variacionAbsoluta": 2, "variacionPorcentual": 100.00, "tendencia": "CRECE" },
    { "codigo": "VALOR_TOTAL", "nombre": "Valor total (Bs)", "periodoA": 250.00, "periodoB": 1180.00,
      "variacionAbsoluta": 930.00, "variacionPorcentual": 372.00, "tendencia": "CRECE" }
  ]
}
```

(El ejemplo muestra dos de los seis indicadores.)

## Permisos (SCRUM-500)

Usa el permiso de los reportes, `REPORTES_CONSULTAR` (Administrador de CRM y Gerente Comercial), para las dos
rutas: el Gateway lo exige con la regla de `GET /api/comportamiento/reportes/**` (SCRUM-58) y servicio-comportamiento
lo vuelve a comprobar en `reporte.PermisoReportes`, ahora también para `comparativo` y `comparativo/csv`. El
Agente de Atención recibe 403 en ambas.

## Pruebas (SCRUM-501)

| Prueba | Qué cubre |
|---|---|
| `ReporteComparativoIntegracionTest` | Con PostgreSQL real: criterio 1 con sus variaciones, cada periodo igual al reporte de SCRUM-58, sin datos y sin base, criterio 2 (superpuestos e incompletos, también en el CSV), **criterio 3 leyendo el CSV y comparándolo con el JSON con decimales exactos**, 401 y 403 |
| `ExportacionComparativoCsvTest` | Formato del CSV (BOM, CRLF, comillas, vacíos) y variación sin base o desde cero |
| `AccesoPorRolTest` (Gateway) | El comparativo y su CSV para Gerente y Administrador, no para el Agente |
| `e2e/scrum62.spec.ts` (navegador) | Criterio 1 con cartera y segmento, sin base, **criterio 2 comprobando que no sale ninguna petición**, rechazo del servidor, **criterio 3 descargando el archivo y comparándolo con la pantalla y el API**, Agente y sin sesión |

```
herramientas/pruebas-reportes/ejecutar.sh e2e/scrum62.spec.ts
```

El entorno aislado de los reportes ahora siembra también dos compras de agosto (Ana Bs 150, Bruno Bs 100), por
eventos, para tener dos periodos que comparar. No cambian los segmentos ni los puntos de las pruebas de SCRUM-58
y SCRUM-59, que siguen pasando.
