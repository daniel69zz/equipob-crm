# Exportación de los reportes de clientes a CSV y PDF (SCRUM-60)

**Historia:** Como Gerente Comercial quiero exportar los reportes de clientes a CSV y PDF para usarlos en otras
herramientas de análisis. **Trazabilidad:** RF-52 · EPIC-10 Consultas y Reportes.

## Alcance

Se exportan los reportes de clientes que tienen pantalla en el CRM y que genera servicio-comportamiento:

| Reporte | Historia | Pantalla | Identificador en la auditoría |
|---|---|---|---|
| Comportamiento de compra (frecuencia, valor y categorías) | SCRUM-58 | Análisis › Comportamiento de compra | `COMPORTAMIENTO_COMPRA` |
| Comparativo entre periodos | SCRUM-62 | Análisis › Comparativo entre periodos | `COMPARATIVO_PERIODOS` |

El reporte de distribución de clientes (SCRUM-57) no tiene pantalla propia: sus datos se ven resumidos en el panel
de indicadores (SCRUM-59), que no es un reporte exportable.

## Formatos estándar (SCRUM-479)

Los dos formatos se escriben **a partir del mismo objeto** del reporte que devuelve el JSON de la pantalla, sin volver
a consultar ni recalcular nada, así que el archivo contiene los mismos datos que se ven (criterio de aceptación 1).

### CSV

Pensado para Excel, LibreOffice, Power BI, pandas o cualquier herramienta que lea CSV.

- RFC 4180: separador coma, fin de línea CRLF, campos con coma, comillas o saltos de línea entre comillas dobles
  (las comillas internas se duplican).
- Codificación UTF-8 **con BOM** (`EF BB BF`) para que Excel muestre bien las tildes y la «ñ».
- Números con punto decimal, sin separador de miles ni notación científica, tal como en el JSON. Un valor nulo
  (un promedio sin compras, una variación sin base) es un campo vacío, no `0`.
- Fechas en ISO 8601 (`2026-09-30`), la generación con su desplazamiento (`2026-11-10T09:15:00-04:00`).
- Tipo de contenido `text/csv;charset=UTF-8`, descargado como adjunto.

El archivo empieza con un **bloque de encabezado** de pares `clave,valor` que hace que se pueda leer solo, una línea
en blanco y después las tablas de datos. Cada tabla tiene su propia fila de títulos de columna.

#### Comportamiento de compra

```
Reporte de comportamiento de compra
Alcance,Toda la cartera | Segmento <nombre> (<n> clientes)
Periodo,<desde> a <hasta>
Zona horaria,America/La_Paz
Generado,<fecha y hora>
Sin datos,<mensaje>                       ← solo si el reporte no tiene datos

Indicador,Codigo,Valor
Compras,COMPRAS,<n>
Clientes que compraron,CLIENTES_CON_COMPRAS,<n>
Compras por cliente,COMPRAS_POR_CLIENTE,<decimal>
Valor total (Bs),VALOR_TOTAL,<decimal>
Ticket promedio (Bs),TICKET_PROMEDIO,<decimal>
Valor por cliente (Bs),VALOR_POR_CLIENTE,<decimal>

Categoria,Compras,Unidades,Monto (Bs),Porcentaje del monto,Sin categoria
<categoría>,<n>,<n>,<decimal>,<decimal>,SI|NO
```

Los códigos de indicador son los mismos del reporte comparativo (SCRUM-498), así que las dos exportaciones se pueden
cruzar por la columna `Codigo`.

#### Comparativo entre periodos

El que ya define SCRUM-499 en [reporte-comparativo.md](reporte-comparativo.md), sin cambios:
encabezado (alcance, periodo A, periodo B, zona horaria, generado) y la tabla
`Indicador,Codigo,Periodo A,Periodo B,Variacion,Variacion %,Tendencia`.

### PDF

Pensado para distribuir o imprimir el reporte.

- Tamaño A4 vertical, márgenes de 36 pt, tipografía Helvetica (codificación Windows-1252, con tildes y «ñ»).
- Título del reporte, bloque de encabezado con los mismos pares del CSV y las mismas tablas, con los números
  alineados a la derecha.
- Los números se muestran con dos decimales (los conteos sin decimales) y separador de miles, como en pantalla;
  un valor nulo se muestra como «—».
- Pie de página con «Generado por <usuario> · <fecha y hora>» y el número de página, para que una copia impresa
  diga quién la exportó.
- Metadatos del documento: título, autor (el usuario) y creador «CRM MaxiConecta».
- Tipo de contenido `application/pdf`, descargado como adjunto.

## Nombre del archivo

`<reporte>_<periodo>[_<segmento>].<csv|pdf>`, sin espacios ni caracteres fuera de `[A-Za-z0-9._-]`:

| Reporte | Ejemplo |
|---|---|
| Comportamiento de compra | `reporte-comportamiento_2026-09-01_2026-09-30_alto-valor.pdf` |
| Comparativo | `reporte-comparativo_2026-08-01_2026-08-31_vs_2026-09-01_2026-09-30.csv` |

## API

| Método y ruta | Formatos |
|---|---|
| `GET /api/comportamiento/reportes/comportamiento/exportar?formato=csv\|pdf&desde=&hasta=[&segmento=]` | CSV y PDF |
| `GET /api/comportamiento/reportes/comparativo/exportar?formato=csv\|pdf&desdeA=&hastaA=&desdeB=&hastaB=[&segmento=]` | CSV y PDF |
| `GET /api/comportamiento/reportes/comparativo/csv?…` | CSV (ruta de SCRUM-499, se conserva) |

Los parámetros y sus validaciones son los del reporte en pantalla: un periodo inválido responde 400 con el motivo.
Un `formato` ausente o distinto de `csv` y `pdf` (sin distinguir mayúsculas) responde 400.

## Permiso (SCRUM-482)

Exportar exige el permiso nuevo **`REPORTES_EXPORTAR`** además de `REPORTES_CONSULTAR`: quien no puede ver el reporte
tampoco puede sacarlo del CRM, y el Administrador puede quitar la exportación a un rol sin quitarle la consulta. Lo
tienen el Administrador de CRM y el Gerente Comercial (que cubre el perfil de Analista de Datos). Sin él la respuesta
es **403** en el API Gateway y otra vez en el servicio (criterio de aceptación 2).

## Auditoría (SCRUM-484)

Cada exportación se registra en `auditoria.evento` **antes** de entregar el archivo; si el registro falla, el archivo
no se entrega (criterio de aceptación 3):

| Campo | Valor |
|---|---|
| `usuario` | Quién exportó (`X-Usuario` del Gateway) |
| `ocurrido_en` | Cuándo |
| `operacion` | `REPORTE_EXPORTADO` |
| `entidad` / `entidad_id` | `REPORTE` / `COMPORTAMIENTO_COMPRA` o `COMPARATIVO_PERIODOS` |
| `detalle` | JSON con `formato`, `archivo`, `bytes`, los periodos, el segmento y `sinDatos`. Sin datos personales |

Se consulta con `GET /api/admin/auditoria` (permiso `AUDITORIA_CONSULTAR`).
