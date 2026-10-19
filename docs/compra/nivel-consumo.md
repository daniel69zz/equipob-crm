# Nivel de consumo del cliente (SCRUM-27)

Clasificación automática de cada cliente en un nivel de consumo **bajo, medio o alto** según su
valor acumulado, para que el Analista Comercial priorice la atención y las campañas según lo que
cada cliente aporta (RF-19, EPIC-04 Categorización y Segmentación de Clientes). Depende del valor
acumulado (SCRUM-33, `docs/compra/valor-acumulado.md`) y del recálculo por anulación (SCRUM-523,
`docs/compra/recalculo-por-anulacion.md`). Diagramas en `docs/compra/nivel-consumo-diagramas.md`.

## Reglas y umbrales (SCRUM-270)

La base de la clasificación es el **valor acumulado**: la suma de lo vigente de las compras del
cliente (`montoTotal - montoRevertido`), en bolivianos. No se define un cálculo propio: se reutiliza
`AgregadoComprasCliente.resumir` y `ValorAcumulado` tal como los usa el indicador (SCRUM-271).

| Nivel | Valor acumulado | Variable de entorno |
|---|---|---|
| `BAJO` | mayor que 0 y menor que el umbral medio | — |
| `MEDIO` | desde el umbral medio (inclusive) y menor que el umbral alto | `NIVEL_CONSUMO_UMBRAL_MEDIO` (500) |
| `ALTO` | desde el umbral alto (inclusive) | `NIVEL_CONSUMO_UMBRAL_ALTO` (5000) |
| sin nivel | sin compras vigentes (valor 0) | — |

- **Umbrales por defecto:** 500 y 5000 bolivianos, los mismos cortes con los que se documentaron los
  rangos de valor de los criterios de segmentación (`docs/segmentacion/criterios.md`: hasta 499.99,
  500–4999.99 y 5000 o más). Se pueden cambiar sin recompilar con las variables de arriba
  (`comportamiento.nivel-consumo.umbral-medio` y `umbral-alto`). El servicio no arranca si el
  umbral medio no es positivo o el alto no es mayor que el medio.
- **Valor exactamente en el umbral:** pertenece al nivel superior. 499.99 es `BAJO`, 500 es `MEDIO`,
  4999.99 es `MEDIO` y 5000 es `ALTO`.
- **Sin compras vigentes** (nunca compró, o todas sus compras quedaron anuladas): el cliente queda
  **sin nivel**, no `BAJO` ni un error (criterio de aceptación 3). Es un estado de la consulta
  (`sinNivel: true`), no un valor del enum, y no se guarda ninguna fila para ese cliente.
- **Pendiente de validación:** los umbrales por defecto son una propuesta; la definición de terminado
  exige que el Product Owner los valide antes de darlos por definitivos.

## Cuándo se recalcula (SCRUM-268)

| Evento | Qué pasa |
|---|---|
| Llega una compra nueva | `ProcesadorCompras` llama a `ClasificadorConsumo.reclasificar(idClienteOrigen)` en la misma transacción. |
| Se anula o se devuelve en parte una compra | `ProcesadorAnulaciones` reclasifica al cliente en la misma transacción: tanto la anulación total como la devolución parcial cambian el valor acumulado. |
| Ejecución programada | `ClasificacionConsumoScheduler` recalcula a **todos** los clientes (por defecto una vez al día, 03:30; `NIVEL_CONSUMO_CRON`). Corrige lo que se haya escapado y aplica un cambio de umbrales sin esperar nuevas compras. |
| Consulta de indicadores | El nivel de `GET .../indicadores` se calcula en el momento sobre el valor acumulado de todos los identificadores del cliente, así que nunca está desactualizado. |

Una compra o anulación que cruza un umbral actualiza el nivel en la misma operación (criterio de
aceptación 2). Si la transacción de la compra o de la anulación se revierte, la clasificación también.

## Persistencia (SCRUM-267)

Tabla `comportamiento.cliente_nivel_consumo` (migración `V9__nivel_consumo_cliente.sql`): una fila
por `idClienteOrigen` con compras vigentes, con su `nivel`, el `valor_acumulado` con el que se
clasificó y `actualizado_en`. El nivel se sobrescribe al reclasificar y la fila se elimina cuando el
cliente se queda sin compras vigentes (queda "sin nivel"). Sirve para listar clientes por nivel sin
recalcular la agregación en cada consulta; igual que `cliente_inactivo`, se identifica por
`idClienteOrigen`.

### Perfiles unificados

Un cliente con perfiles unificados puede tener compras bajo más de un `idClienteOrigen`. La fila
persistida y el listado clasifican **cada identificador por separado** (la misma limitación que ya
documentan los clientes inactivos en `docs/compra/clientes-inactivos.md`). En cambio, el nivel que
devuelve la consulta de indicadores y la ficha integral se calcula sobre el valor acumulado del
**conjunto** de identificadores del cliente, que es el valor que ve el usuario en la ficha.

## Consultas (SCRUM-269, SCRUM-566)

### Nivel de un cliente

Se suma como un campo hermano más de `IndicadoresCliente`, sobre el endpoint que ya usan el resto de
los indicadores:

```
GET /api/comportamiento/clientes/{clienteId}/indicadores?identificador=CLI-5521
```

```json
{
  "valorAcumulado": { "valor": 5230.00, "compras": 12, "sinDatos": false },
  "nivelConsumo": { "nivel": "ALTO", "valorAcumulado": 5230.00, "sinNivel": false }
}
```

Sin compras vigentes:

```json
{
  "nivelConsumo": { "nivel": null, "valorAcumulado": 0, "sinNivel": true }
}
```

### Clientes por nivel

```
GET /api/comportamiento/clientes?nivelConsumo=ALTO&pagina=0&tamanio=20
```

Paginado (`pagina`, `tamanio`, por defecto 20, máx. 100), del cliente con mayor valor acumulado al
de menor. `nivelConsumo` acepta `BAJO`, `MEDIO` o `ALTO`; cualquier otro valor responde 400. Igual que
el listado de inactivos, un listado filtrado usa un parámetro y no un segmento propio
(`docs/seguridad/convencion-rutas-clientes.md`).

```json
{
  "umbrales": { "medio": 500, "alto": 5000 },
  "content": [
    { "idClienteOrigen": "CLI-5521", "nivel": "ALTO", "valorAcumulado": 5230.00,
      "actualizadoEn": "2026-10-10T20:15:00Z" }
  ],
  "pagina": 0,
  "tamanio": 20,
  "total": 1
}
```

Sin cambios de permisos: ya aplica `INDICADORES_CONSULTAR` a todo `GET /api/comportamiento/**`
(`docs/seguridad/matriz-permisos.md`).

### Ficha integral

`GET /api/perfil/clientes/{clienteId}/ficha-integral` suma el bloque `nivelConsumo`, consultado a
`servicio-comportamiento` con los identificadores de origen del cliente (`nivel`, `valorAcumulado`,
`sinDatos`). El bloque llega con `sinDatos: true` cuando el cliente no tiene compras vigentes ("sin
nivel"), no tiene identificadores de origen o el servicio no responde; el resto de la ficha carga igual
(mismo criterio que historial, segmento y puntos). La pantalla de la ficha muestra el nivel junto al segmento y a los puntos; solo
la ven los roles que ya tienen acceso a la ficha integral (`FICHA_INTEGRAL_CONSULTAR`, administrador
de CRM y agente de atención al cliente).

## Pruebas (SCRUM-272)

- `CriterioNivelConsumoTest`: clasificación y casos límite exactamente en cada umbral.
- `ClasificadorConsumoTest`: reclasificación individual y recálculo completo, sin base de datos.
- `NivelConsumoIntegracionTest` (PostgreSQL real): compra nueva que cruza un umbral, anulación que baja
  de nivel, devolución parcial, cliente sin compras vigentes, listado por nivel y valores límite.
