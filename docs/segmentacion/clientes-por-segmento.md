# Clientes por segmento o nivel de consumo (SCRUM-28)

El Gerente Comercial o el Analista de Datos lista los clientes de un segmento o de un nivel de
consumo para dirigir acciones comerciales a ese grupo (RF-20, EPIC-04). Qué se considera un grupo, qué
filtros y órdenes hay y de dónde salen los datos está en
[criterio-consulta-clientes.md](criterio-consulta-clientes.md) (SCRUM-276). Diagramas en
[clientes-por-segmento-diagramas.md](clientes-por-segmento-diagramas.md) (SCRUM-576).

## Pantalla

**Clientes → Clientes por segmento** (`/segmentacion/clientes`). Solo aparece en el menú de quien
tiene `SEGMENTOS_CONSULTAR` (Administrador de CRM y Gerente Comercial).

1. Elija un **segmento** (cada opción muestra cuántos clientes tiene hoy) o un **nivel de consumo**, o
   los dos, y pulse **Consultar**. Los segmentos de criterios activos aparecen aunque todavía no
   tengan clientes; los de un criterio desactivado que algún cliente conserva se marcan como
   «criterio inactivo».
2. Opcional: limite por **fecha de última compra** (desde / hasta, días completos).
3. Pulse un encabezado (**Segmento**, **Nivel de consumo**, **Valor acumulado**, **Última compra**)
   para ordenar; pulsarlo de nuevo invierte el sentido. Ordenar, filtrar o cambiar de página conserva
   el grupo elegido y vuelve a pedir los datos al servidor.
4. **Anterior / Siguiente** recorren las páginas de 20 clientes; se muestra el rango, el total y la
   página actual (`app-paginacion`, SCRUM-275).
5. Cada fila tiene enlaces a la **Ficha** integral (si el rol tiene `FICHA_INTEGRAL_CONSULTAR`) y a las
   **Compras** del cliente.

| Situación | Qué se ve |
|---|---|
| Segmento sin clientes | «El segmento «X» no tiene clientes asignados.» (criterio de aceptación 3) |
| Ningún cliente cumple las fechas | «Ningún cliente del grupo cumple los filtros de fecha elegidos.» |
| Cliente sin nivel o sin fecha de última compra | «—» en esa columna |
| servicio-perfil no responde | Aviso: los clientes se muestran con su número, sin nombre |
| Rol sin permiso | La ruta lleva a «Acceso denegado»; el API responde 403 |

## API

Ambas rutas pasan por el API Gateway, que exige `SEGMENTOS_CONSULTAR` para `GET /api/segmentacion/**`
(`docs/seguridad/matriz-permisos.md`). servicio-segmentacion lo vuelve a comprobar con las cabeceras
`X-Usuario` y `X-Usuario-Permisos` que añade el Gateway (SCRUM-278): sin identidad responde **401** y
sin el permiso **403**.

### Segmentos para elegir

```
GET /api/segmentacion/segmentos
```

```json
[
  { "segmento": "Alto valor", "indicador": "VALOR", "criterioActivo": true, "clientes": 37 },
  { "segmento": "FRECUENTE", "indicador": null, "criterioActivo": null, "clientes": 3 },
  { "segmento": "Lectores", "indicador": "CATEGORIA", "criterioActivo": true, "clientes": 0 }
]
```

`criterioActivo` es `null` cuando ningún criterio tiene hoy ese nombre (asignaciones anteriores a
SCRUM-26 o a un cambio de nombre del criterio). Ordenados por nombre.

### Clientes del grupo

```
GET /api/segmentacion/clientes?segmento=Alto valor&nivelConsumo=ALTO&orden=ultimaCompra&direccion=asc&pagina=0&tamanio=20
```

| Parámetro | Obligatorio | Valores | Por defecto |
|---|---|---|---|
| `segmento` | uno de los dos | Nombre del segmento, tal como lo devuelve el catálogo | — |
| `nivelConsumo` | uno de los dos | `BAJO`, `MEDIO`, `ALTO` (sin importar mayúsculas) | — |
| `ultimaCompraDesde` | no | Fecha ISO `2026-09-01`, incluida | — |
| `ultimaCompraHasta` | no | Fecha ISO, incluida | — |
| `orden` | no | `valor`, `nivelConsumo`, `ultimaCompra`, `frecuencia`, `segmento`, `asignadoEn` | `valor` |
| `direccion` | no | `asc`, `desc` | `desc` |
| `pagina` | no | Desde 0 | `0` |
| `tamanio` | no | 1 a 100 (un valor mayor se recorta a 100) | `20` |

```json
{
  "content": [
    {
      "clienteId": 42,
      "nombres": "Ana",
      "apellidos": "Pérez",
      "segmento": "Alto valor",
      "indicador": "VALOR",
      "asignadoEn": "2026-10-10T19:05:12Z",
      "nivelConsumo": "ALTO",
      "valorAcumulado": 6200.00,
      "frecuencia": 12,
      "ultimaCompra": "2026-10-10T15:00:00Z"
    }
  ],
  "pagina": 0,
  "tamanio": 20,
  "total": 1,
  "orden": "ultimaCompra",
  "direccion": "asc",
  "nombresDisponibles": true
}
```

- `orden` y `direccion` son los que se aplicaron. Los clientes sin dato en la columna de orden van al
  final; a igual valor se ordena por `clienteId`.
- `nombresDisponibles: false` indica que servicio-perfil no respondió: `nombres` y `apellidos` llegan
  en `null` y el resto del listado es correcto.
- Un segmento sin clientes responde **200** con `content: []` y `total: 0`.

| Respuesta | Cuándo |
|---|---|
| 400 | Sin `segmento` ni `nivelConsumo`; nivel, orden o dirección desconocidos; fecha mal escrita; `ultimaCompraDesde` posterior a `ultimaCompraHasta`. El campo `mensaje` dice cuál. |
| 401 | Sin sesión (Gateway) o sin la identidad del Gateway (servicio). |
| 403 | El rol no tiene `SEGMENTOS_CONSULTAR`, por ejemplo el Agente de Atención al Cliente. |

## Datos que alimentan el listado (SCRUM-277)

| Pieza | Cambio |
|---|---|
| servicio-comportamiento | La foto de métricas que publica con cada compra o anulación suma `nivelConsumo` y `ultimaCompra` (`docs/segmentacion/asignacion-automatica.md`). |
| servicio-segmentacion | Migración `V7__listado_clientes_segmento.sql`: columnas `nivel_consumo` y `ultima_compra` en `metricas_cliente` e índices por segmento y por nivel. `RegistroSegmentos` las guarda con cada foto. |
| servicio-perfil | `GET /api/perfil/clientes?id=42&id=43` devuelve los resúmenes de esos perfiles (como máximo 100) en una sola llamada. |

Un cliente cuya última foto llegó antes de este cambio aparece con nivel y última compra vacíos hasta
su siguiente compra o anulación.

## Auditoría (SCRUM-568)

Cada consulta autorizada queda en `auditoria.evento`: el Gateway registra el acceso como
`CLIENTE_CONSULTADO` y segmentación registra `CLIENTES_SEGMENTO_CONSULTADOS` con el grupo, los
filtros, la página y los números de los clientes mostrados. Detalle en
`docs/seguridad/alcance-auditoria.md`. Si ese registro falla, la consulta responde error y no muestra
datos.

## Configuración

| Variable | Servicio | Por defecto |
|---|---|---|
| `SERVICIO_PERFIL_URL` | servicio-segmentacion | `http://localhost:8081` |
| `PERFIL_TIEMPO_ESPERA` | servicio-segmentacion | `2s` |

## Pruebas (SCRUM-279)

| Prueba | Qué cubre |
|---|---|
| `ClientesSegmentoIntegracionTest` (PostgreSQL real) | Listado con nombre, segmento, nivel y última compra desde métricas reales (CA1); filtros y orden que conservan el segmento, también solo por nivel (CA2); segmento vacío y catálogo (CA3); 401/403 sin auditoría y auditoría del grupo y de los clientes mostrados (CA4); segmento de 250 clientes paginado sin repetir ni perder clientes; servicio-perfil caído. |
| `FiltroClientesTest` | Reglas del grupo, fechas como días completos, orden por defecto y valores inválidos. |
| `PermisoConsultaClientesTest` | Control de permiso del servicio, sin afectar la consulta del segmento de un cliente para la ficha integral. |
| `TrazabilidadAuditoriaTest` (Gateway) | El listado se audita como consulta sin cliente y el intento del Agente queda como acceso denegado. |
| `PublicacionMetricasIntegracionTest` (comportamiento) | La foto publicada trae el nivel y la última compra, también tras una devolución parcial o una anulación total. |
| `ConsultaPerfilTest`, `PerfilControllerTest` (perfil) | Resúmenes de varios perfiles por identificador y límite de una página. |

```powershell
cd backend/servicio-segmentacion
mvn test
```

Las pruebas de integración requieren Docker (Testcontainers).
