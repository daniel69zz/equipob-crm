# Criterio de consulta de clientes por segmento o nivel de consumo (SCRUM-276)

Definición de qué devuelve la consulta de SCRUM-28 (RF-20, EPIC-04): el Gerente Comercial o el
Analista de Datos elige un grupo de clientes para dirigirle una acción comercial. El uso del API y de
la pantalla está en `docs/segmentacion/clientes-por-segmento.md`; los diagramas, en
`docs/segmentacion/clientes-por-segmento-diagramas.md`.

## Qué es un grupo

La consulta exige **al menos uno** de estos dos criterios; si llegan los dos, el cliente debe cumplir
ambos (por ejemplo, los clientes del segmento «Habituales» con nivel de consumo alto).

| Criterio | Parámetro | De dónde sale |
|---|---|---|
| Segmento | `segmento` | Segmento vigente del cliente (`segmentacion.segmento_cliente`), asignado por SCRUM-26 con los criterios de SCRUM-25. Se compara con el nombre exacto que muestra la ficha integral. |
| Nivel de consumo | `nivelConsumo` | `BAJO`, `MEDIO` o `ALTO`, con los umbrales de SCRUM-27 (`docs/compra/nivel-consumo.md`). |

- **Por categoría:** los segmentos de indicador `CATEGORIA` (por ejemplo, «Lectores» para Libros) son
  la forma de consultar por categoría. No se agrega un filtro de categoría aparte: cada cliente tiene
  **un solo segmento** (regla de prioridad de SCRUM-260), y la consulta muestra ese segmento.
  Un cliente que compra Libros pero quedó en «Alto valor» por prioridad aparece en «Alto valor».
- **Un segmento sin clientes** (criterio activo que nadie cumple todavía) no es un error: la consulta
  responde una lista vacía y la pantalla lo explica (criterio de aceptación 3).
- **Sin criterio** (ni segmento ni nivel): se rechaza con 400. La consulta es para dirigir acciones a
  un grupo, no para recorrer toda la cartera; para eso está la búsqueda de clientes del perfil.

## Columnas

| Columna | Origen |
|---|---|
| Nombre | Nombres y apellidos del perfil del cliente (servicio-perfil). |
| Segmento | Segmento vigente y el indicador del criterio que lo asignó. |
| Nivel de consumo | Nivel calculado sobre el valor acumulado vigente de **todos** los identificadores del cliente, igual que en la ficha integral. |
| Valor acumulado | El valor con el que se clasificó el nivel y se evaluaron los criterios de valor. |
| Fecha de última compra | Compra vigente más reciente (una anulada no cuenta; una devuelta en parte sí), igual que la recencia de SCRUM-19. |

Un cliente sin nivel o sin fecha (por ejemplo, porque no tuvo compras ni anulaciones desde que se
publican esos datos, o porque todas sus compras quedaron anuladas) se muestra con «—», no se excluye.

## Filtros y ordenamiento (SCRUM-273)

| Filtro | Parámetro | Regla |
|---|---|---|
| Nivel de consumo | `nivelConsumo` | Uno de `BAJO`, `MEDIO`, `ALTO` (sin importar mayúsculas). Otro valor responde 400. |
| Última compra desde | `ultimaCompraDesde` | Fecha (`2026-09-01`), día completo incluido, en la zona horaria del servidor. |
| Última compra hasta | `ultimaCompraHasta` | Fecha, día completo incluido. `desde` posterior a `hasta` responde 400. |

Con un filtro de fecha, los clientes sin fecha de última compra quedan fuera: no se puede saber si la
cumplen.

| Orden | Parámetro `orden` | Uso comercial |
|---|---|---|
| Valor acumulado (**por defecto**, de mayor a menor) | `valor` | Primero los que más aportan. |
| Nivel de consumo | `nivelConsumo` | `ALTO` > `MEDIO` > `BAJO`. |
| Fecha de última compra | `ultimaCompra` | Los que hace más tiempo no compran (ascendente) o los más recientes. |
| Frecuencia | `frecuencia` | Compras vigentes. |
| Segmento | `segmento` | Útil al listar por nivel, que mezcla segmentos. |
| Asignación al segmento | `asignadoEn` | Los que entraron hace poco al segmento. |

- `direccion` es `asc` o `desc` (por defecto `desc`). Los clientes sin dato van **siempre al final**,
  en los dos sentidos. A igual valor se desempata por número de cliente, para que la paginación sea
  estable.
- Cambiar el orden o los filtros vuelve a la primera página y **conserva el segmento elegido**
  (criterio de aceptación 2).
- **No se ordena ni se filtra por nombre:** el nombre pertenece a servicio-perfil y se consulta solo
  para la página que se muestra. Ordenar por nombre exigiría traer el perfil de todo el segmento en
  cada consulta.

## Paginación

`pagina` empieza en 0; `tamanio` es 20 por defecto, como mínimo 1 y como máximo 100. La respuesta trae
el total de clientes del grupo con los filtros aplicados.

## De dónde se leen los datos

Cada servicio es dueño de su esquema (`database/README.md`) y ninguno lee el esquema de otro, así que
la consulta vive en servicio-segmentacion (ruta `GET /api/segmentacion/clientes`, ya prevista en
`docs/seguridad/convencion-rutas-clientes.md`) y completa lo que no tiene así:

- **Nivel de consumo y fecha de última compra:** servicio-comportamiento ya publica una foto de las
  métricas del cliente con cada compra o anulación (`metricas.actualizadas`, SCRUM-26). Se le suman
  los campos `nivelConsumo` y `ultimaCompra`, calculados por comportamiento con sus propias reglas
  (`CriterioNivelConsumo` y la recencia de SCRUM-19), y segmentación los guarda junto con la foto en
  `segmentacion.metricas_cliente`. Así se puede filtrar y ordenar en la base sin duplicar umbrales ni
  llamar a comportamiento por cada cliente. Los campos nuevos son opcionales: un mensaje que no los
  trae se sigue aplicando igual.
- **Nombre:** se pide a servicio-perfil en **una sola llamada por página**
  (`GET /api/perfil/clientes?id=..&id=..`). Si perfil no responde, el listado se muestra igual, sin
  nombres y con un aviso (mismo criterio que la ficha integral ante un servicio caído).

### Vigencia

El nivel y la fecha son los del último evento del cliente, igual que su segmento: un cambio de los
umbrales de nivel de consumo o de los criterios de segmentación se refleja en el listado con la
siguiente compra o anulación de cada cliente (`docs/segmentacion/asignacion-automatica.md`, Alcance).

## Seguridad y auditoría

- Permiso `SEGMENTOS_CONSULTAR` (Administrador de CRM y Gerente Comercial; el Agente de Atención no
  lo tiene). Lo exige el API Gateway y lo vuelve a comprobar servicio-segmentacion con la identidad
  que reenvía el Gateway (SCRUM-278).
- Cada consulta queda en `auditoria.evento` dos veces: el Gateway registra el acceso
  (`CLIENTE_CONSULTADO`, o `ACCESO_DENEGADO` si se rechaza) y segmentación registra **qué grupo** se
  consultó y qué clientes se mostraron (`CLIENTES_SEGMENTO_CONSULTADOS`, SCRUM-568).
