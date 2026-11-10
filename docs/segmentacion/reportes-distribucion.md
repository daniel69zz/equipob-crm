# Reporte de distribución de clientes (SCRUM-57)

El Gerente Comercial o el Analista de Datos ve cuántos clientes hay en cada segmento, nivel de
consumo y nivel de fidelización, con su porcentaje, para orientar las campañas comerciales (RF-49,
EPIC-10). Los criterios y las fuentes de datos de este reporte son SCRUM-461; los cortes, SCRUM-463
(segmento), SCRUM-460 (nivel de consumo) y SCRUM-464 (nivel de fidelización); los permisos,
SCRUM-462. Diagramas en
[reportes-distribucion-diagramas.md](reportes-distribucion-diagramas.md) (SCRUM-609).

## Qué cuenta el reporte (SCRUM-461)

**Población.** Todos los clientes que la segmentación conoce: la unión de `segmentacion.segmento_cliente`
y `segmentacion.metricas_cliente`. Se cruzan con `FULL JOIN`, igual que en el listado por segmento,
porque un cliente puede tener métricas sin segmento (no cumple ningún criterio) o segmento sin
métricas (asignaciones anteriores a SCRUM-26). Un cliente cuenta una sola vez en cada corte.

**Los tres cortes se calculan sobre la misma población**, así que sus cantidades suman el `total` del
reporte y sus porcentajes 100 % (salvo el redondeo). Para que eso se cumpla, cada corte tiene un grupo
**sin asignar** (`valor: null`):

| Corte | De dónde sale | Valores | Grupo sin asignar |
|---|---|---|---|
| Segmento | `segmentacion.segmento_cliente.segmento`, que asigna SCRUM-26 con cada compra | los segmentos vigentes, incluso de criterios ya desactivados | cliente sin segmento asignado |
| Nivel de consumo | `segmentacion.metricas_cliente.nivel_consumo`, que publica servicio-comportamiento (SCRUM-27) | `BAJO`, `MEDIO`, `ALTO` | cliente sin métricas o sin nivel calculado |
| Nivel de fidelización | `fidelizacion.saldo_puntos.nivel`, que asigna SCRUM-49 | `BRONCE`, `PLATA`, `ORO` | cliente sin puntos, o con puntos pero sin alcanzar el umbral más bajo |

El nivel de fidelización **no está en la segmentación**: lo da servicio-fidelizacion en una consulta
interna entre servicios (ver más abajo). Por eso es el único corte que puede faltar, y el reporte lo
dice en `fidelizacionDisponible`.

**Orden de los grupos.** Los segmentos van de mayor a menor cantidad y, a igual cantidad, por nombre;
los niveles de consumo de `BAJO` a `ALTO`; los de fidelización del más bajo al más alto. En los tres,
el grupo sin asignar va al final.

**Porcentaje.** Sobre el `total` del reporte, con dos decimales y redondeo al más cercano. Con un total
de 3 clientes, un grupo de 1 es `33.33`.

## Pantalla (pendiente)

SCRUM-57 solo tiene subtareas de backend y de análisis: la vista todavía no está construida. Esto es
el diseño acordado para cuando se haga, y el API de abajo ya lo soporta.

**Reportes → Distribución de clientes** (`/segmentacion/reportes/distribucion`). Debe aparecer solo en
el menú de quien tiene `REPORTES_CONSULTAR` (Administrador de CRM y Gerente Comercial).

1. Sin elegir nada, el reporte cubre a **todos** los clientes conocidos.
2. Opcional: limite por **segmento**, por **nivel de consumo**, o los dos; el cliente debe cumplir
   ambos.

| Situación | Qué se ve |
|---|---|
| El filtro no deja ningún cliente | «No hay datos para el filtro elegido.», sin error (criterio de aceptación 2) |
| Grupo sin asignar | «Sin segmento», «Sin nivel de consumo» o «Sin nivel de fidelización» |
| servicio-fidelizacion no responde | Los otros dos cortes se muestran igual, con el aviso de que los niveles no están disponibles |
| Rol sin permiso | La ruta lleva a «Acceso denegado»; el API responde 403 (criterio de aceptación 3) |

## API

```
GET /api/segmentacion/reportes/distribucion?segmento=Alto%20valor&nivelConsumo=ALTO
```

Los dos parámetros son opcionales. Un `nivelConsumo` que no sea `BAJO`, `MEDIO` ni `ALTO` responde
**400** con el motivo.

```json
{
  "generadoEn": "2026-11-09T15:30:00Z",
  "filtro": { "segmento": null, "nivelConsumo": null },
  "total": 8,
  "sinDatos": false,
  "segmentos": [
    { "valor": "Alto valor", "clientes": 5, "porcentaje": 62.50 },
    { "valor": "Frecuente", "clientes": 2, "porcentaje": 25.00 },
    { "valor": null, "clientes": 1, "porcentaje": 12.50 }
  ],
  "nivelesConsumo": [
    { "valor": "BAJO", "clientes": 1, "porcentaje": 12.50 },
    { "valor": "MEDIO", "clientes": 3, "porcentaje": 37.50 },
    { "valor": "ALTO", "clientes": 4, "porcentaje": 50.00 }
  ],
  "nivelesFidelizacion": [
    { "valor": "BRONCE", "clientes": 4, "porcentaje": 50.00 },
    { "valor": "PLATA", "clientes": 2, "porcentaje": 25.00 },
    { "valor": "ORO", "clientes": 1, "porcentaje": 12.50 },
    { "valor": null, "clientes": 1, "porcentaje": 12.50 }
  ],
  "fidelizacionDisponible": true
}
```

Cuando el filtro no deja clientes, la respuesta es **200** con `sinDatos: true`, `total: 0` y los tres
cortes vacíos: no es un error (criterio de aceptación 2).

## Permisos (SCRUM-462)

El reporte tiene su **permiso propio**, `REPORTES_CONSULTAR`, de Administrador de CRM y Gerente
Comercial (`docs/seguridad/matriz-permisos.md`). No se reutiliza `SEGMENTOS_CONSULTAR` porque el
reporte cruza segmentación con fidelización y es para los perfiles de análisis comercial, no para
quien atiende a un cliente.

- El API Gateway exige `REPORTES_CONSULTAR` en `GET /api/segmentacion/reportes/**`, con una regla
  anterior a la genérica de `/api/segmentacion/**`, y deniega los métodos de escritura.
- servicio-segmentacion lo vuelve a comprobar con las cabeceras `X-Usuario` y `X-Usuario-Permisos`
  que añade el Gateway: sin identidad responde **401** y sin el permiso **403**.
- El **Agente de Atención al Cliente** no tiene ese permiso, así que recibe 403 aunque pueda
  consultar clientes (`CLIENTE_CONSULTAR`) y puntos (`PUNTOS_CONSULTAR`) (criterio de aceptación 3).

## Consulta interna de los niveles (SCRUM-464)

```
POST /api/fidelizacion/reportes/distribucion-niveles
{ "clientes": [501, 502] }
```

Devuelve cuántos clientes hay en cada nivel. Sin cuerpo, o con la lista vacía, cuenta a todos los
clientes con saldo de puntos; con la lista, solo a esos.

- Es una consulta **entre servicios**, como `GET /api/segmentacion/segmentos/miembros`: va por la red
  interna, sin la identidad del Gateway. El Gateway **deniega** `/api/fidelizacion/reportes/**` para
  que ningún usuario la alcance por ahí, porque el Agente de Atención tiene `PUNTOS_CONSULTAR` y el
  reporte no es para él.
- Es `POST` y no `GET` porque el reporte filtrado manda el grupo de clientes en el cuerpo: son
  demasiados para la línea de consulta. No modifica nada.
- Admite hasta **1000 clientes por petición**; servicio-segmentacion parte el grupo en lotes de ese
  tamaño y suma los conteos.
- Sin filtro, el reporte no enumera a nadie: pide el conteo de todos y es una sola petición.
- Si servicio-fidelizacion no responde, el reporte se entrega con los otros dos cortes y
  `fidelizacionDisponible: false`, como el listado por segmento ante servicio-perfil caído.

## Auditoría

Cada reporte generado queda en la auditoría compartida de solo inserción
(`docs/seguridad/alcance-auditoria.md`) con la operación `REPORTE_DISTRIBUCION_GENERADO`, entidad
`REPORTE` e id `DISTRIBUCION_CLIENTES`. El detalle no lleva datos personales: el filtro, el tamaño de
la población, si quedó sin datos y si fidelización respondió. Se registra **antes** de responder: si
el registro falla, el reporte falla y los datos no se muestran, igual que en el listado de clientes.

## Configuración

| Variable | Para qué | Por defecto |
|---|---|---|
| `SERVICIO_FIDELIZACION_URL` | Dónde está servicio-fidelizacion para el corte de niveles | `http://localhost:8084` |
| `FIDELIZACION_TIEMPO_ESPERA` | Espera máxima de esa consulta | `2s` |

## Pruebas

| Prueba | Qué cubre |
|---|---|
| `ServicioReporteDistribucionTest` | Cantidades y porcentajes, suma de cada corte, redondeo, sin datos, fidelización caída, filtro que pide solo sus clientes |
| `PermisoReportesTest` | Permiso propio del reporte: gerente 200, agente 403, sin identidad 401, nivel inválido 400 |
| `ReporteDistribucionIntegracionTest` | Flujo con PostgreSQL real: conteos desde la base, filtros, sin datos, acceso denegado y auditoría |
| `DistribucionNivelesControllerTest` (fidelización) | Conteo por nivel de todos o de un grupo, orden, repetidos y tope por petición |
| `AccesoPorRolTest` (Gateway) | `REPORTES_CONSULTAR` por ruta y denegación de `/api/fidelizacion/reportes/**` |

Las de integración necesitan Docker (Testcontainers), como el resto de las del repositorio.
