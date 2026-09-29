# Identificación de clientes inactivos (SCRUM-31)

Diseño de la identificación automática de clientes sin compras recientes, para que el Analista
Comercial pueda actuar antes de perderlos (RF-23, EPIC-05 Análisis del Comportamiento). Depende de
la recencia de compra (HU-13/RF-11, `docs/compra/recencia-compra.md`, SCRUM-19): reutiliza el mismo
concepto de "última compra vigente" que ya calcula esa historia, extendido para recorrer a todos
los clientes en vez de a uno solo.

## Criterio de cliente inactivo (SCRUM-295)

Un cliente, identificado por su `idClienteOrigen` en `comportamiento.compra` (el mismo
identificador del módulo Marketplace y Ventas que usan el historial de compras y los demás
indicadores, ver `docs/compra/historial-compras.md`), se considera **inactivo** cuando su última
compra **vigente** tiene más de `comportamiento.inactividad.umbral-dias` días (por defecto **90**,
configurable por variable de entorno `INACTIVIDAD_UMBRAL_DIAS`).

"Vigente" es el mismo concepto que usan el ticket promedio y la recencia
(`ComprasDelCliente.vigente`: `montoTotal - montoRevertido > 0`): una devolución parcial sigue
contando como última compra, y una compra anulada por completo no, para no dar por activo a un
cliente cuya única compra reciente terminó revertida.

Un cliente **sin ninguna compra vigente** —porque nunca compró o porque su única compra terminó
anulada— no tiene una "última compra" que mostrar (criterio de aceptación 3 lo exige) y por lo
tanto **no aparece** en el listado de inactivos: la inactividad se mide desde una compra real, no
desde el alta del cliente. Es una decisión explícita del criterio, cubierta por las pruebas de
SCRUM-299 (`ClientesInactivosIntegracionTest`).

En cuanto el cliente vuelve a comprar, deja de figurar como inactivo de inmediato (criterio de
aceptación 2): no hace falta esperar a la próxima ejecución programada (ver más abajo).

### Un cliente con perfiles unificados puede aparecer más de una vez

Un mismo cliente puede tener compras bajo más de un `idClienteOrigen` si se unificaron dos
perfiles duplicados (igual que en el historial, ver `docs/compra/historial-compras.md`). La
detección recorre toda `comportamiento.compra` sin conocer esas unificaciones —no tiene, como sí
tiene el historial, la lista de identificadores ya resuelta que trae el perfil del cliente—, así
que agrupa por `idClienteOrigen` y no por el perfil: si ambos identificadores quedan inactivos,
aparecen como dos filas del listado. Es la misma limitación que ya asumía "por qué no se usa
`compra.id_cliente`" (ver más abajo), y no afecta al criterio de aceptación 3: cada fila sigue
mostrando un identificador real con su propia última compra.

### Por qué no se usa `compra.id_cliente` (la vinculación al perfil de SCRUM-15)

`compra.id_cliente` (el perfil unificado, resuelto en la ingesta contra `servicio-perfil`) queda
`null` cuando ese servicio no estaba disponible al procesar la compra (`estado_vinculacion =
PENDIENTE`). Agrupar por `id_cliente` dejaría fuera del listado a esos clientes sin avisar — un
defecto serio en un reporte pensado justamente para no perder clientes. Por eso la detección
agrupa por `idClienteOrigen`, igual que el resto de los indicadores, que no dependen de que la
vinculación al perfil haya podido resolverse.

## Consulta de detección (SCRUM-294)

Se agrupan las compras vigentes por `idClienteOrigen` y se toma la fecha máxima; los grupos cuyo
máximo es anterior a la fecha de corte (`ahora - umbralDias`) son los candidatos a inactivos:

```sql
SELECT id_cliente_origen, MAX(fecha) AS ultima_compra
FROM comportamiento.compra
WHERE monto_total - monto_revertido > 0
GROUP BY id_cliente_origen
HAVING MAX(fecha) < :fechaCorte
```

Implementado como `AgregadoComprasCliente.ultimaCompraVigentePorClienteAnteriorA(corte)`, con la
Criteria API y las mismas condiciones (`ComprasDelCliente.vigente`) que ya usan el ticket promedio
y la recencia, para no duplicar la definición de "vigente" en un tercer lugar.

## Persistencia (SCRUM-296)

El resultado se guarda en `comportamiento.cliente_inactivo` (una fila por `idClienteOrigen`
inactivo, con su última compra y cuándo se detectó — migración `V8__clientes_inactivos.sql`), para
que el listado (SCRUM-298) no tenga que recalcular la agregación en cada consulta.
`DetectorClientesInactivos.detectar()` hace un upsert de los candidatos vigentes y quita a los que
ya no cumplen el criterio.

## Ejecución automática (SCRUM-297)

`ClientesInactivosScheduler` corre `DetectorClientesInactivos.detectar()` con un cron configurable
(`comportamiento.inactividad.cron`, por defecto `0 0 3 * * *`: una vez al día, de madrugada).
Además, `ProcesadorCompras` llama a `DetectorClientesInactivos.reactivar(idClienteOrigen)` justo
después de guardar cada compra: así un cliente que vuelve a comprar sale del listado sin esperar a
la siguiente ejecución programada (criterio de aceptación 2), y la ejecución programada corrige
cualquier caso que se le escape a esa reactivación puntual (por ejemplo, si el umbral se reduce y
un cliente que ya estaba en el límite pasa a cumplir el criterio sin haber comprado).

Cuando una anulación total deja una compra en `ANULADA` (SCRUM-523),
`DetectorClientesInactivos.reevaluar(idClienteOrigen)` vuelve a evaluar a ese cliente al momento,
con el mismo criterio de la detección: el listado no espera a la siguiente ejecución para quitar a
quien se quedó sin compras vigentes ni para reflejar una última compra anterior. Ver
`docs/compra/recalculo-por-anulacion.md`.

## Listado para usuarios de negocio (SCRUM-298)

```
GET /api/comportamiento/clientes?estado=inactivo&pagina=0&tamanio=20
```

Paginado (`pagina`, `tamanio`, por defecto 20, máx. 100), del cliente que lleva más tiempo sin
comprar al que lleva menos. Tal como lo exige `docs/seguridad/convencion-rutas-clientes.md`, un
listado filtrado usa el parámetro `estado=inactivo` y no un segmento propio (`/clientes/inactivos`
quedaría auditado como si "inactivos" fuera un identificador de cliente).

```json
{
  "umbralDias": 90,
  "content": [
    {
      "idClienteOrigen": "CLI-5521",
      "ultimaCompra": "2026-03-13T10:00:00Z",
      "diasTranscurridos": 200
    }
  ],
  "pagina": 0,
  "tamanio": 20,
  "total": 1
}
```

Responde el cliente (su `idClienteOrigen`), su última compra y los días transcurridos — criterio
de aceptación 3. Sin cambios de permisos: ya aplica `INDICADORES_CONSULTAR` a todo
`GET /api/comportamiento/**` (`docs/seguridad/matriz-permisos.md`).
