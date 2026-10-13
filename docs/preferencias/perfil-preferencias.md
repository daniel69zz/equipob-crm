# Perfil de preferencias — SCRUM-41 / RF-33

Construye y persiste las categorías preferidas de un cliente a partir de todo su historial vigente.
El dueño del cálculo y de las tablas es `servicio-comportamiento`, esquema `comportamiento` de la
base existente `crm_maxiconecta`. El servicio de Perfil sigue siendo dueño de la identidad del cliente.

## Trazabilidad y base reutilizada

| Subtarea | Implementación |
|---|---|
| SCRUM-359 | Modelo y criterio descritos aquí; `PerfilPreferencias` |
| SCRUM-360 | `ConstructorPreferencias`, `PerfilesPreferencias` |
| SCRUM-361 | `RankingCategorias` de SCRUM-18 adaptado al agregado actual `AgregadoConsumoCategorias` |
| SCRUM-362 | Vigencia y devoluciones del agregado; exclusión por `sinCategoria` antes de asignar posiciones |
| SCRUM-363 | Migración V11 y `PerfilPreferenciasRepository` |
| SCRUM-364 | Pruebas unitarias, de contrato HTTP, integración PostgreSQL y acceso en Gateway |
| SCRUM-365 | Identificadores obtenidos del servicio de Perfil; validación del consumo antes de guardar |
| SCRUM-588 | [Diagramas de clases y secuencia](diagramas.md), con fuentes Mermaid editables |

Al iniciar esta implementación, `main` estaba en `3cd8005`. El ranking de SCRUM-18 no estaba en
ese árbol; se consultó su implementación en `origin/feature/SCRUM-18-categorias-mas-consumidas`
(lógica de `cc8b028`). Se reutiliza su algoritmo y regla de ordenamiento, con las filas del agregado
actual. No se incorporan las antiguas estructuras de dos orígenes: hoy la integración es un solo
módulo, Marketplace y Ventas.

## Regla de relevancia

1. Monto vigente acumulado por categoría, descendente.
2. Cantidad de compras distintas que conservan esa categoría, descendente.
3. Nombre alfabético sin distinguir mayúsculas; el nombre exacto resuelve el último empate.

Se identifica como `MONTO_DESC_COMPRAS_DESC_NOMBRE_ASC_V1`. No hay ponderaciones arbitrarias,
porcentajes ni ventana temporal. Comprar muchas unidades en una venta cuenta como una compra.
Se quitan espacios laterales; `Hogar` y `hogar` siguen siendo categorías diferentes, como en SCRUM-18.

Una compra con monto vigente cero no participa. Las devoluciones parciales descuentan unidades
y monto en su categoría. Si una categoría se devuelve por completo, desaparece de esa compra.
Los nombres nulos o en blanco son datos sin asignar: se excluyen del perfil, aunque el ranking base
pueda representarlos para otros reportes. Se usa el indicador `sinCategoria`, no una comparación
con el texto de presentación; una categoría explícitamente llamada «Sin categoría» conserva su identidad.

Ejemplo: Electrónica Bs 300 en una compra, Hogar Bs 120 en dos compras y Deportes Bs 40 en una
compra producen ese mismo orden. Si dos montos empatan, gana la categoría con más compras.

## Modelo persistido

`comportamiento.perfil_preferencias` guarda una cabecera por `cliente_id` del CRM: fecha de
construcción UTC, versión consecutiva y versión del criterio. La cabecera existe también para
un perfil construido sin categorías. `comportamiento.preferencia_categoria` guarda nombre,
posición desde 1, compras, unidades y monto; cliente/categoría y cliente/posición son únicos.

La migración `V11__perfil_preferencias.sql` es aditiva. No modifica compras ni cuentas ni crea
una base de aplicación adicional. No hay una FK entre esquemas: la identidad se verifica por API.

| Estado | `construido` | `sinPreferencias` | `version` | `construidoEn` |
|---|---|---|---|---|
| Aún no se construyó | false | true | 0 | null |
| Construido sin categorías válidas | true | true | 1 o más | Fecha |
| Construido con preferencias | true | false | 1 o más | Fecha |

La primera categoría es la preferencia principal. No se duplica esa información en otra columna.

## Construcción, consistencia y concurrencia

1. Obtener `GET /api/perfil/clientes/{clienteId}` desde el backend. Verificar que la respuesta
   corresponda al cliente y que no haya sido absorbido por otro perfil.
2. Usar todos sus `identificadoresOrigen`, sin duplicados. La API no acepta identificadores
   externos ni categorías ni montos enviados por el usuario para elegir qué compras guardar.
3. Abrir una transacción PostgreSQL `REPEATABLE READ` y bloquear la construcción por cliente.
4. Leer compras, líneas y devoluciones en una misma instantánea. Verificar montos y unidades
   no negativos, pertenencia de líneas, devoluciones compatibles y suma de detalles igual al monto vigente.
5. Calcular el ranking reutilizado, excluir datos sin categoría y numerar las preferencias.
6. Reemplazar cabecera y categorías en una única transacción. Un error revierte todo.

Un fallo del servicio de Perfil devuelve 503 y conserva la instantánea anterior; no se interpreta
como un cliente sin compras. Datos inconsistentes devuelven 409. Un cliente inexistente devuelve
404 y un identificador no positivo, 400. Un perfil absorbido devuelve 409 indicando el cliente destino.

Las construcciones concurrentes del mismo cliente se serializan con un bloqueo transaccional.
Si PostgreSQL invalida una instantánea, se reintenta la transacción completa hasta tres veces;
si persiste el conflicto, se devuelve 409. La consulta lee cabecera y detalles en la misma instantánea.
Repetir la construcción genera una nueva versión, sin duplicar las categorías.

La identidad remota se resuelve antes de abrir la transacción de compras; no existe una transacción
distribuida entre servicios. Una consolidación de identidad posterior requiere reconstruir el perfil
del cliente destino. El perfil representa la instantánea de compras de su última construcción.

## API y acceso

Siempre acceder por el Gateway (`http://localhost:8080`) con JWT.

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/comportamiento/clientes/{clienteId}/preferencias/construir` | Rol `ADMINISTRADOR_CRM` y permiso `CLIENTE_EDITAR` |
| GET | `/api/comportamiento/clientes/{clienteId}/preferencias` | Rol `ADMINISTRADOR_CRM` o `AGENTE_ATENCION`, y `FICHA_INTEGRAL_CONSULTAR` |

POST no lleva cuerpo y devuelve el perfil guardado. GET consulta la instantánea, no recalcula.
Las reglas específicas se evalúan antes de `INDICADORES_CONSULTAR`. El Gateway audita ambas
rutas con la convención de cliente existente. Los servicios internos conservan la frontera de red
del proyecto y no deben exponerse públicamente saltándose el Gateway.

```json
{
  "clienteId": 42,
  "construido": true,
  "sinPreferencias": false,
  "construidoEn": "2026-10-13T20:00:00Z",
  "version": 1,
  "criterio": "MONTO_DESC_COMPRAS_DESC_NOMBRE_ASC_V1",
  "categorias": [
    {"posicion": 1, "categoria": "Electrónica", "compras": 1, "unidades": 1, "monto": 300.00}
  ]
}
```

## Alcance y cierre

SCRUM-41 entrega construcción explícita, persistencia y contrato de lectura reutilizable.
La actualización al procesar eventos y su trazabilidad por evento corresponden a SCRUM-42.
La pantalla de consulta corresponde a SCRUM-43; las recomendaciones, a SCRUM-44.
No se modifican Angular ni sus archivos de configuración en esta entrega.

El cierre en Jira requiere, además de las pruebas, revisión de un par, merge y adjuntar los
diagramas a la historia. Los archivos de diagramas se entregan en el repositorio; su existencia
local no significa que ya estén adjuntos a Jira.

Ver [prueba manual y pruebas automatizadas](prueba-manual.md).

Resultados de la ejecución: [verificación](verificacion.md).
