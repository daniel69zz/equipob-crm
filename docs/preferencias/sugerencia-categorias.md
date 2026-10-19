# Sugerencia de categorías afines (SCRUM-44 / RF-36)

El CRM sugiere a cada cliente las categorías que compran los clientes de su mismo segmento y que él
todavía no compra, para que el Gerente Comercial le proponga productos que probablemente le
interesen. Combina el perfil de preferencias (SCRUM-41, actualizado con cada compra por SCRUM-42) con
el segmento asignado automáticamente (SCRUM-26). Diagramas en
`docs/preferencias/sugerencia-categorias-diagramas.md`.

## Trazabilidad con las subtareas

| Subtarea | Implementación |
|---|---|
| SCRUM-380 | Criterio de afinidad descrito en este documento |
| SCRUM-378 | Modelo de datos (sección siguiente) y endpoint de miembros del segmento |
| SCRUM-379 | `AgregadoSegmento`: consumo por categoría de los clientes del segmento |
| SCRUM-382 | `MotorSugerencias`: exclusión, afinidad, umbrales y orden |
| SCRUM-381 | `SugerenciasCategorias` y `GET .../sugerencias-categorias` |
| SCRUM-383 | Permiso `SUGERENCIAS_CONSULTAR` en el API Gateway |
| SCRUM-385 | Pestaña «Sugerencias» del cliente en el frontend |
| SCRUM-384 | `MotorSugerenciasTest`, `SugerenciasIntegracionTest`, pruebas del segmento y del gateway |
| SCRUM-591 | Diagramas de clases y secuencia |

## Modelo de datos (SCRUM-378)

No se agregan tablas: las sugerencias se calculan al consultarlas a partir de datos que ya se
mantienen al día con cada compra o anulación, así que nunca quedan desactualizadas.

| Dato | Dónde está | Quién lo mantiene |
|---|---|---|
| Perfil de preferencias del cliente (categorías, compras, monto) | `comportamiento.perfil_preferencias` y `comportamiento.preferencia_categoria` | SCRUM-41 y SCRUM-42 |
| Segmento vigente de cada cliente | `segmentacion.segmento_cliente` | SCRUM-26 |
| Clientes del mismo segmento | `GET /api/segmentacion/clientes/{id}/segmento/miembros` (nuevo) | servicio-segmentacion |

Cada servicio sigue siendo dueño de su esquema: servicio-comportamiento pide a servicio-segmentacion
el segmento del cliente y sus compañeros de segmento, y agrega los perfiles de esos compañeros en su
propio esquema. La migración V6 de segmentación agrega un índice por `segmento` para listar los
miembros sin recorrer la tabla.

## Criterio de afinidad (SCRUM-380)

Para el cliente C del segmento S:

1. **Compañeros de segmento:** los demás clientes con el segmento S vigente. Solo cuentan los que
   tienen perfil de preferencias con al menos una categoría (`clientesDelSegmento`).
2. **Afinidad de una categoría:** proporción de esos compañeros que la tienen en su perfil.

   ```
   afinidad = clientes del segmento que compran la categoría / clientesDelSegmento
   ```

   Se redondea a 4 decimales (0.6667 = 66,67 %). Contar clientes y no montos evita que un solo
   cliente con compras grandes defina lo que se sugiere a todo el segmento.
3. **Exclusión (criterio de aceptación 2):** no se sugiere ninguna categoría que ya figure en el perfil
   de C, la compre con frecuencia o una sola vez. La comparación ignora mayúsculas y espacios
   (`Hogar` y ` hogar ` son la misma categoría).
4. **Umbrales** (configurables):

   | Propiedad | Por defecto | Para qué |
   |---|---|---|
   | `comportamiento.sugerencias.minimo-clientes` (`SUGERENCIAS_MINIMO_CLIENTES`) | 2 | Una categoría que compra un solo compañero no es un patrón del segmento |
   | `comportamiento.sugerencias.afinidad-minima` (`SUGERENCIAS_AFINIDAD_MINIMA`) | 0.20 | Al menos el 20 % del segmento la compra |
   | `comportamiento.sugerencias.maximo` (`SUGERENCIAS_MAXIMO`) | 5 | Cantidad de sugerencias por cliente |

   El servicio no arranca si `minimo-clientes` es menor que 1, `afinidad-minima` no está entre 0 y 1
   o `maximo` es menor que 1.
5. **Orden (criterio de aceptación 1):** afinidad descendente; a igual afinidad, el monto total que el
   segmento gastó en la categoría, descendente; después el nombre, alfabético. Así el resultado es
   siempre el mismo para los mismos datos.

### Ejemplo

Segmento «Clientes frecuentes» con 4 compañeros de Ana que tienen perfil:

| Categoría | Compañeros que la compran | Afinidad | ¿Ana la compra? | Resultado |
|---|---|---|---|---|
| Hogar | 4 | 1.0000 | Sí | Excluida (CA2) |
| Electrónica | 3 | 0.7500 | No | Sugerencia 1 |
| Deportes | 2 | 0.5000 | No | Sugerencia 2 |
| Juguetes | 1 | 0.2500 | No | No llega al mínimo de 2 clientes |

## Cuándo no hay sugerencias (criterio de aceptación 3)

La respuesta es 200 con `sugerencias` vacío, `sinSugerencias = true` y el motivo:

| `motivo` | Situación |
|---|---|
| `SIN_PERFIL` | El cliente no tiene perfil de preferencias construido |
| `SIN_PREFERENCIAS` | El perfil existe pero no tiene categorías (sin compras vigentes) |
| `SIN_SEGMENTO` | El cliente no tiene segmento asignado |
| `SIN_DATOS_SEGMENTO` | Ningún otro cliente del segmento tiene perfil con categorías |
| `SIN_CATEGORIAS_NUEVAS` | El segmento solo compra categorías que el cliente ya compra o que no llegan a los umbrales |

Si faltan a la vez el perfil y el segmento, se informa `SIN_PERFIL`. Los errores de identidad siguen
la consulta del perfil: 400 identificador inválido, 404 cliente inexistente, 409 cliente consolidado y
503 si el servicio de Perfil o el de Segmentación no responden (no se confunde con «sin sugerencias»).

## API (SCRUM-381)

`GET /api/comportamiento/clientes/{clienteId}/sugerencias-categorias`

```json
{
  "clienteId": 42,
  "segmento": "Clientes frecuentes",
  "generadoEn": "2026-10-19T15:20:00Z",
  "criterio": "AFINIDAD_SEGMENTO_V1",
  "clientesDelSegmento": 4,
  "sugerencias": [
    { "posicion": 1, "categoria": "Electrónica", "afinidad": 0.7500, "clientesQueCompran": 3, "montoSegmento": 4200.00 },
    { "posicion": 2, "categoria": "Deportes", "afinidad": 0.5000, "clientesQueCompran": 2, "montoSegmento": 380.00 }
  ],
  "sinSugerencias": false,
  "motivo": null,
  "mensaje": null
}
```

Consultar no modifica el perfil ni el segmento. Para ver el efecto de una compra nueva basta con
volver a consultar: el perfil y el segmento ya se actualizaron por eventos.

### Miembros del segmento (servicio-segmentacion)

`GET /api/segmentacion/clientes/{clienteId}/segmento/miembros` devuelve el segmento vigente del
cliente y los demás clientes con ese segmento (sin incluirlo a él). Sin segmento, `sinDatos = true` y
la lista vacía.

```json
{ "segmento": "Clientes frecuentes", "asignadoEn": "2026-10-18T21:03:10Z", "sinDatos": false, "clientes": [7, 15, 23, 31] }
```

## Permisos (SCRUM-383)

Permiso propio `SUGERENCIAS_CONSULTAR`, para el Administrador de CRM y el Gerente Comercial (el perfil
de análisis comercial; el Analista de Datos usa ese rol). El Agente de Atención no lo tiene. El API
Gateway exige el permiso para la ruta de sugerencias antes de la regla general de
`/api/comportamiento/**` (`docs/seguridad/matriz-permisos.md`). El Sistema genera las sugerencias en
el servidor: el navegador no envía categorías, montos ni miembros del segmento.

## Frontend (SCRUM-385)

Pestaña «Sugerencias» en las vistas del cliente, visible solo con `SUGERENCIAS_CONSULTAR`. Muestra el
segmento, cuántos compañeros se analizaron y cada categoría sugerida con su afinidad; si no hay
sugerencias, el motivo en lenguaje del negocio.

## Pruebas (SCRUM-384)

- `MotorSugerenciasTest`: exclusión (también con mayúsculas distintas), afinidad, umbrales, orden y
  máximo.
- `SugerenciasIntegracionTest` (PostgreSQL real): segmentos de prueba con perfiles construidos; cada
  motivo de «sin sugerencias»; una compra nueva del cliente quita la categoría de las sugerencias;
  errores de Perfil y Segmentación.
- `MiembrosSegmentoTest` (servicio-segmentacion): miembros del segmento sin el propio cliente.
- `AccesoPorRolTest` (API Gateway): Administrador y Gerente acceden; el Agente no.
