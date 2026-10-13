# Valor consumido por un cliente en un periodo (SCRUM-39)

Consulta del valor total y del valor promedio por compra que consumió un cliente en un rango de
fechas, para que el Analista Comercial mida su aporte en un periodo concreto y no solo en todo su
historial (RF-31, EPIC-06 Frecuencia y Valor de Compra). Depende del historial de compras (SCRUM-15)
y del valor acumulado (SCRUM-33, `docs/compra/valor-acumulado.md`); habilita la comparación entre
periodos (SCRUM-40) y la acumulación de puntos (SCRUM-45). Diagramas en
`docs/compra/consumo-por-periodo-diagramas.md`.

## Criterios funcionales (SCRUM-343)

| # | Criterio | Cómo se cumple |
|---|---|---|
| 1 | Un cliente con compras en el periodo ve el valor total y el valor promedio por compra | `total`, `promedio` y `compras` de la respuesta |
| 2 | Las compras anuladas del periodo no se incluyen | solo cuentan las compras con algo vigente (`montoTotal - montoRevertido > 0`) |
| 3 | Un periodo sin compras muestra valor cero con un mensaje, no un error | responde 200 con `total` y `promedio` en 0, `sinDatos: true` y `mensaje` |
| 4 | Un rango de fechas inválido o un usuario sin permiso se rechaza | 400 por validación; 401/403 en el Gateway |

## Lógica de cálculo (SCRUM-345)

```
total    = suma de lo vigente de las compras del cliente con fecha dentro del periodo
compras  = cantidad de esas compras
promedio = total / compras, redondeado a 2 decimales (mitad hacia arriba)
```

- **Vigente** es lo mismo que usan el ticket promedio y el valor acumulado
  (`ComprasDelCliente.vigente`): una compra `ANULADA` no aporta nada y una devolución parcial aporta
  lo que le queda (`montoTotal - montoRevertido`) y sigue contando como una compra.
- **A qué periodo pertenece una compra** lo decide la fecha de la propia compra, no la de su
  anulación. Una compra de septiembre anulada en octubre deja de sumar en septiembre en la consulta
  siguiente: el valor de un periodo siempre refleja lo que hoy sigue vigente de él.
- **Zona horaria:** las fechas del rango son días calendario del negocio (por defecto
  `America/La_Paz`, UTC-4; `comportamiento.consumo-periodo.zona-horaria`,
  variable `CONSUMO_PERIODO_ZONA_HORARIA`). El periodo va de las 00:00 de `desde` (inclusive) a las
  00:00 del día siguiente a `hasta` (exclusivo). Una compra de las 22:30 del 30 de septiembre en La
  Paz entra en septiembre aunque en UTC ya sea el 1 de octubre.
- **Cliente con varios identificadores** (perfiles unificados): se suman las compras de todos los
  `identificador` recibidos; uno repetido no duplica compras.
- Se calcula al consultarlo, sobre `comportamiento.compra`, con una sola agregación en la base
  (`AgregadoConsumoPeriodo`): no se persiste, así que una compra nueva, una devolución o una anulación
  se reflejan en la consulta siguiente, igual que el resto de los indicadores.

## Validaciones de entrada (SCRUM-346)

| Entrada | Regla | Respuesta |
|---|---|---|
| `desde`, `hasta` | obligatorias | 400 `Falta la fecha 'desde' del periodo (formato AAAA-MM-DD)` |
| `desde`, `hasta` | formato ISO `AAAA-MM-DD` y fecha real (no `30/09/2026` ni `2026-02-30`) | 400 `Valor no válido para 'desde': ...` |
| rango | `desde` no puede ser posterior a `hasta` | 400 |
| rango | como máximo 1830 días (5 años) | 400 |
| `identificador` | no vacío y de hasta 64 caracteres (igual que en el historial) | 400 |

Un periodo en el futuro o sin compras **no** es inválido: responde en cero con su mensaje.

## Formato de la respuesta (SCRUM-349)

```
GET /api/comportamiento/clientes/{clienteId}/consumo-periodo
      ?desde=2026-09-01&hasta=2026-09-30
      &identificador=mp-user-3307
      &identificador=CLI-5521
```

```json
{
  "desde": "2026-09-01",
  "hasta": "2026-09-30",
  "zonaHoraria": "America/La_Paz",
  "total": 481.00,
  "promedio": 240.50,
  "compras": 2,
  "sinDatos": false,
  "mensaje": null
}
```

Periodo sin compras vigentes:

```json
{
  "desde": "2026-01-01",
  "hasta": "2026-01-31",
  "zonaHoraria": "America/La_Paz",
  "total": 0,
  "promedio": 0,
  "compras": 0,
  "sinDatos": true,
  "mensaje": "El cliente no tiene compras vigentes entre 2026-01-01 y 2026-01-31."
}
```

`sinDatos` solo avisa que ese 0 no viene de compras reales, para que el frontend no lo confunda con
"no se pudo calcular" (el mismo criterio que `valorAcumulado.sinDatos`). Error de validación:

```json
{
  "fecha": "2026-10-10T20:15:00Z",
  "estado": 400,
  "error": "Bad Request",
  "mensaje": "La fecha 'desde' (2026-09-30) no puede ser posterior a 'hasta' (2026-09-01)",
  "ruta": "/api/comportamiento/clientes/42/consumo-periodo"
}
```

## Permisos por rol (SCRUM-348)

No se agrega un permiso nuevo: la ruta cuelga de `/api/comportamiento/clientes/{clienteId}/...` y
hereda la regla del Gateway para todo `GET /api/comportamiento/**`, que exige `INDICADORES_CONSULTAR`
y audita el acceso con el `{clienteId}` de la ruta (`docs/seguridad/convencion-rutas-clientes.md`,
`docs/seguridad/matriz-permisos.md`).

| Rol | Consulta el valor por periodo |
|---|:---:|
| Administrador de CRM | ✅ |
| Agente de Atención | ✅ |
| Gerente Comercial | ✅ |
| Sin `INDICADORES_CONSULTAR` | ❌ 403 |
| Sin sesión | ❌ 401 |

## Pantalla (SCRUM-585)

En la vista **Compras e indicadores** del cliente, debajo de los indicadores, el bloque "Consumo por
periodo" propone el mes en curso y permite elegir otras fechas. Un rango incompleto o al revés se
avisa en pantalla sin llamar al servidor; un periodo sin compras muestra el valor en cero con el
mensaje del servicio.

## Pruebas (SCRUM-350)

- `RangoPeriodoTest`: rango válido, de un día, incompleto, al revés, demasiado largo y fronteras en la
  zona del negocio.
- `ConsultaValorPorPeriodoTest`: total, promedio y redondeo, periodo vacío, sin identificadores,
  identificadores repetidos y rango inválido sin consultar la base.
- `ValorPorPeriodoControllerTest`: forma de la respuesta y los 400 de fecha mal formada, faltante o al
  revés.
- `ConsumoPeriodoIntegracionTest` (PostgreSQL real): distintos periodos del mismo cliente, compras
  anuladas (en el mismo periodo y después), devolución parcial, varios identificadores, periodo sin
  compras, días extremos y compras de noche en la zona del negocio, y rangos inválidos.
- `AccesoPorRolTest` (Gateway): la ruta se permite a los tres roles con `INDICADORES_CONSULTAR` y se
  deniega sin él.
