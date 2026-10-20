# Comparación de frecuencia y valor de compra entre dos periodos (SCRUM-40)

Comparación de la frecuencia y el valor de compra de un cliente, o de los clientes de un segmento,
entre dos periodos, para que el Analista Comercial sepa si crece o decae y actúe a tiempo (RF-32,
EPIC-06 Frecuencia y Valor de Compra). Depende del valor consumido por periodo (SCRUM-39,
`docs/compra/consumo-por-periodo.md`), cuya agregación reutiliza, y de los segmentos asignados a cada
cliente (`servicio-segmentacion`). Diagramas en `docs/compra/comparacion-periodos-diagramas.md`.

## Criterios funcionales

| # | Criterio | Cómo se cumple |
|---|---|---|
| 1 | Dos periodos y un cliente: la frecuencia y el valor de cada periodo con su variación absoluta y porcentual | `frecuencia` y `valor`, cada uno con `periodoA`, `periodoB`, `variacionAbsoluta` y `variacionPorcentual` |
| 2 | Un segmento: los resultados agregan solo a los clientes de ese segmento | la lista de clientes sale de `servicio-segmentacion` y solo sus compras entran en la suma |
| 3 | Un periodo sin compras aparece en cero y la variación se calcula sin error | responde 200; con el periodo base en cero el porcentaje es nulo (`NUEVO`) |
| 4 | Un usuario sin permiso no puede comparar | el Gateway responde 401/403 (ver "Permisos por rol") |

## Reglas de comparación (SCRUM-354)

Cada periodo tiene dos métricas, ambas sobre lo **vigente** de las compras con fecha dentro del periodo
(la misma definición del ticket promedio, del valor acumulado y del valor por periodo: una compra
`ANULADA` no cuenta y una devolución parcial aporta lo que le queda):

```
frecuencia = cantidad de compras vigentes del periodo
valor      = suma de lo vigente de esas compras
```

El **periodo A es la base** y el **B el comparado**; no hace falta que A sea anterior a B. Para cada
métrica:

```
variación absoluta   = B - A
variación porcentual = (B - A) / A * 100, a 2 decimales (mitad hacia arriba)
```

| Caso | Variación absoluta | Variación porcentual | Tendencia |
|---|---|---|---|
| B mayor que A | positiva | positiva | `CRECE` |
| B menor que A (incluido B en cero) | negativa | negativa (−100 % si B es 0) | `DECAE` |
| B igual que A | 0 | 0.00 | `ESTABLE` |
| A en cero y B mayor que cero | B | **nula**: no hay base sobre la cual calcular un porcentaje | `NUEVO` |
| A y B en cero | 0 | 0.00 | `ESTABLE` |

- **Un periodo sin compras no es un error.** Aparece en cero y la variación se calcula con las reglas de
  arriba. `sinDatos` es verdadero solo cuando **ninguno** de los dos periodos tiene compras vigentes, y
  entonces `mensaje` lo explica.
- **Los periodos no pueden superponerse:** una compra que entrara en ambos contaría dos veces y la
  variación no diría nada. Dos periodos consecutivos (el B empieza el día siguiente al fin del A) son
  válidos.
- **Longitudes distintas:** se permiten (por ejemplo un mes contra un trimestre), pero los valores no se
  normalizan: la variación compara totales, no promedios por día.
- Cada periodo cumple las mismas validaciones y la misma zona horaria que el valor por periodo: fechas
  `AAAA-MM-DD` reales, `desde` no posterior a `hasta`, como máximo 5 años y días completos en
  `America/La_Paz` (`comportamiento.consumo-periodo.zona-horaria`).
- Se calcula al consultarlo, con dos agregaciones en la base (una por periodo): una compra, devolución o
  anulación nueva se refleja en la consulta siguiente.

## Alcance (SCRUM-353, SCRUM-357)

### Un cliente

Igual que el resto de los indicadores, se suman las compras de todos los `identificador` recibidos (los
identificadores del cliente en Marketplace y Ventas); uno repetido no duplica compras.

```
GET /api/comportamiento/clientes/{clienteId}/comparacion-periodos
      ?desdeA=2026-08-01&hastaA=2026-08-31
      &desdeB=2026-09-01&hastaB=2026-09-30
      &identificador=mp-user-3307&identificador=CLI-5521
```

### Un segmento

```
GET /api/comportamiento/comparacion-periodos
      ?segmento=FRECUENTE
      &desdeA=2026-08-01&hastaA=2026-08-31&desdeB=2026-09-01&hastaB=2026-09-30
```

1. `servicio-comportamiento` pide a `servicio-segmentacion` los clientes del segmento, página a página
   (`GET /api/segmentacion/segmentos/miembros?segmento=FRECUENTE&pagina=0&tamanio=1000`, que devuelve los
   identificadores de perfil con `clienteId` y `asignadoEn`). La ruta sigue la convención de listados
   filtrados por parámetro (`docs/seguridad/convencion-rutas-clientes.md`).
2. Agrega solo las compras vinculadas a esos perfiles (`compra.id_cliente`), en lotes de 1000 clientes.
   Una compra todavía **pendiente de vinculación** al perfil no tiene `id_cliente` y no entra en la
   comparación por segmento.
3. Un segmento sin clientes (o un código que no existe) responde en cero con un mensaje; un segmento de
   más de 100.000 clientes se rechaza con 400.
4. Si `servicio-segmentacion` no responde, la comparación responde **503**: a diferencia de la ficha
   integral, no puede calcularse a medias y un resultado en cero parecería un dato real.

La ruta del segmento va fuera de `/clientes` porque devuelve solo agregados, sin datos de clientes.

## Formato de la respuesta

```json
{
  "alcance": { "tipo": "CLIENTE", "segmento": null, "clientes": null },
  "zonaHoraria": "America/La_Paz",
  "periodoA": { "desde": "2026-08-01", "hasta": "2026-08-31" },
  "periodoB": { "desde": "2026-09-01", "hasta": "2026-09-30" },
  "frecuencia": {
    "periodoA": 2, "periodoB": 5,
    "variacionAbsoluta": 3, "variacionPorcentual": 150.00, "tendencia": "CRECE"
  },
  "valor": {
    "periodoA": 480.50, "periodoB": 900.00,
    "variacionAbsoluta": 419.50, "variacionPorcentual": 87.30, "tendencia": "CRECE"
  },
  "sinDatos": false,
  "mensaje": null
}
```

Con el periodo base en cero:

```json
{
  "valor": {
    "periodoA": 0, "periodoB": 250.00,
    "variacionAbsoluta": 250.00, "variacionPorcentual": null, "tendencia": "NUEVO"
  }
}
```

Para un segmento, `alcance` es `{ "tipo": "SEGMENTO", "segmento": "FRECUENTE", "clientes": 12 }`. Errores
de validación (400) con el formato habitual (`fecha`, `estado`, `error`, `mensaje`, `ruta`), por ejemplo
`Los periodos a comparar no pueden superponerse: 2026-08-01 a 2026-09-15 y 2026-09-01 a 2026-09-30` o
`Periodo A: La fecha 'desde' (...) no puede ser posterior a 'hasta' (...)`.

## Permisos por rol (SCRUM-356)

| Consulta | Permiso en el Gateway | Administrador | Agente de Atención | Gerente Comercial |
|---|---|:---:|:---:|:---:|
| Un cliente | `INDICADORES_CONSULTAR` | ✅ | ✅ | ✅ |
| Un segmento | `INDICADORES_CONSULTAR` **y** `SEGMENTOS_CONSULTAR` | ✅ | ❌ 403 | ✅ |

La de un cliente hereda la regla de todo `GET /api/comportamiento/**` y se audita con el `{clienteId}`
de la ruta. La del segmento tiene su propia regla, anterior a la genérica, porque agrega a todo un
segmento y el agente de atención no tiene permiso de consulta de segmentos
(`docs/seguridad/matriz-permisos.md`). Sin sesión, 401. En la pantalla, la opción "Un segmento" solo se
ofrece a quien tiene `SEGMENTOS_CONSULTAR`.

## Pantalla (SCRUM-352, SCRUM-355)

En la vista **Compras e indicadores** del cliente, el bloque "Comparar dos periodos" propone el mes
anterior como periodo A y el mes en curso como B, permite elegir otras fechas y, con el permiso
adecuado, comparar un segmento en vez del cliente. Muestra una tabla con la frecuencia y el valor de
cada periodo, su variación absoluta y porcentual y la tendencia ("Sin base de comparación" cuando el
periodo A está en cero). Fechas incompletas, al revés o superpuestas se avisan sin llamar al servidor.

## Pruebas (SCRUM-358)

- `VariacionConsumoTest`: crece, decae, estable, redondeo, −100 %, periodo base en cero y ambos en cero.
- `PeriodosACompararTest`: periodos válidos, base posterior al comparado, consecutivos, incompletos, al
  revés y superpuestos.
- `ConsultaComparacionPeriodosTest`: cliente, periodo vacío, sin compras en ninguno, identificadores
  repetidos, segmento (solo sus clientes), segmento vacío, segmento inválido y segmentación caída.
- `ClientesDelSegmentoTest`: lectura paginada de segmentación y 503 ante una falla.
- `ComparacionPeriodosControllerTest`: forma de la respuesta y los 400 y 503.
- `ComparacionPeriodosIntegracionTest` (PostgreSQL real): un cliente, varios identificadores,
  anulaciones y devoluciones, periodos en cero, segmento con clientes de dentro y de fuera, compras sin
  perfil vinculado, segmento vacío, segmentación caída y rangos inválidos.
- `ClientesDeSegmentoControllerTest` (`servicio-segmentacion`) y `AccesoPorRolTest` (Gateway): la
  consulta de clientes por segmento y los permisos por rol de las dos rutas.
- Regresión: las suites completas de `servicio-comportamiento`, `servicio-segmentacion` y `api-gateway`
  siguen en verde.
