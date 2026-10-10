# SCRUM-25 · Criterios de segmentación

La historia permite al Administrador de CRM crear, editar y desactivar criterios basados en
frecuencia, valor acumulado y categoría consumida. Los criterios activos se consultan para la
asignación posterior. La asignación automática de clientes pertenece a SCRUM-26; la simulación
masiva y su impacto pertenecen a SCRUM-29. La comprobación de un caso de esta pantalla evalúa
solo el dato introducido contra una regla persistida, sin modificar clientes ni simular un padrón.

## Decisiones basadas en la documentación del proyecto

- `docs/compra/frecuencia-compra.md`: frecuencia acumulada de compras vigentes, entera; cero sin compras.
- `docs/compra/valor-acumulado.md` y `ticket-promedio.md`: suma de monto total menos revertido,
  en bolivianos; no se utiliza el ticket promedio como sustituto del valor acumulado.
- `docs/contratos-eventos/RIO-CRM-02-compra-confirmada.md`: categoría por nombre del catálogo,
  sin ID de producto. La evaluación recibe las categorías con consumo vigente del llamador;
  este módulo configura la regla y no recalcula el historial ni consume eventos en SCRUM-25.
- `database/README.md`: segmentación es dueña de su esquema; solo inserta y consulta en la
  auditoría compartida. No escribe en perfil ni comportamiento.
- `docs/seguridad/catalogo-roles.md` y `matriz-permisos.md`: rol `ADMINISTRADOR_CRM` y permiso
  `SEGMENTACION_CONFIGURAR`. Se exigen ambos, aunque un rol diferente obtenga ese permiso.
- `docs/seguridad/alcance-auditoria.md`: se reutiliza `auditoria.evento` y su protección contra
  UPDATE, DELETE y TRUNCATE. Los cambios de configuración se registran transaccionalmente,
  separados de la auditoría de acceso a datos personales.

## Modelo y reglas

`segmentacion.criterio` se crea con Flyway V2, conservando `segmento_cliente` de V1.

| Campo | Regla |
|---|---|
| id | Secuencia PostgreSQL, identificador interno |
| nombre | Obligatorio, hasta 100 caracteres; único incluso entre inactivos |
| descripcion | Opcional, hasta 500 caracteres |
| indicador | `FRECUENCIA`, `VALOR`, `CATEGORIA` |
| condicion | `ENTRE`, `MAYOR_IGUAL`, `MENOR_IGUAL`, `IGUAL`, `CONTIENE` |
| minimo / maximo | No negativos; hasta 12 enteros y 2 decimales; frecuencia sin fracciones |
| categoria | Nombre obligatorio de hasta 100 caracteres solo para CATEGORIA |
| activo | Verdadero al crear; desactivar conserva datos e historial |
| version | Aumenta al editar/cambiar estado; evita sobrescrituras desde formularios antiguos |
| creado_en / actualizado_en / actualizado_por | Fechas de servidor e identidad vigente del Gateway |

Los límites numéricos son **inclusivos**. `ENTRE` requiere ambos; `MAYOR_IGUAL` solo el mínimo;
`MENOR_IGUAL` solo el máximo; `IGUAL` usa mínimo y se normaliza a dos límites iguales.
`CONTIENE` exige categoría y prohíbe límites numéricos; significa coincidencia de un nombre
completo dentro del conjunto consumido, no búsqueda parcial de texto.

Se normalizan espacios, mayúsculas y tildes para comparar nombres y categorías. Se conserva
el texto legible para mostrarlo. Dos reglas activas se superponen si son del mismo indicador y
sus rangos comparten al menos un valor; para categorías, si sus nombres normalizados coinciden.
Por ejemplo, [0, 5] y [5, 10] se rechazan; [0, 5] y [6, 10] son válidos para frecuencia.
Para valor decimal, [0, 499.99] y [500, 1000] no se superponen. Indicadores distintos son
independientes: cómo resolver clientes que cumplen reglas de varios indicadores corresponde
al trabajo de prioridad y asignación de SCRUM-26.

Al editar se excluye el propio criterio de la búsqueda de conflictos. Los inactivos pueden
conservar rangos coincidentes; al reactivarlos vuelve a validarse la superposición. No hay borrado.
La validación y la escritura se serializan con un bloqueo transaccional de la tabla, por lo que
incluso dos altas simultáneas sobre una tabla vacía no pueden superar ambas la validación.

## API pública, siempre mediante el Gateway

| Método | Ruta | Resultado |
|---|---|---|
| GET | `/api/segmentacion/criterios?activo=true&indicador=FRECUENCIA` | Listado; filtros opcionales |
| POST | `/api/segmentacion/criterios` | 201 y criterio persistido |
| PUT | `/api/segmentacion/criterios/{id}` | Edición; requiere version vigente |
| PATCH | `/api/segmentacion/criterios/{id}/estado` | `{activo:false, version:0}`; también permite activar |
| GET | `/api/segmentacion/criterios/{id}/historial` | Cambios del más reciente al más antiguo |
| POST | `/api/segmentacion/criterios/{id}/comprobar` | Caso explícito: `{frecuencia:5}`, `{valor:500.01}` o `{categorias:["Libros"]}` |

Ejemplo de creación:

```json
{"nombre":"Habituales","descripcion":"De cuatro a nueve compras vigentes","indicador":"FRECUENCIA","condicion":"ENTRE","minimo":4,"maximo":9}
```

Los errores siguen `ApiError`: `fecha`, `estado`, `error`, `mensaje`, `ruta`. Se responde 400 por
valores incompletos/inválidos, 409 por superposición, nombre repetido o versión obsoleta, 404 por
ID inexistente, 401 sin sesión y 403 por rol o permiso insuficiente. El frontend muestra `mensaje`.

El Gateway descarta cabeceras de identidad suministradas desde el navegador. El servicio vuelve
a comprobar las cabeceras inyectadas por el Gateway. Como el resto de los microservicios del
repositorio, su puerto debe permanecer en la red interna; la entrada pública es el Gateway.

## Auditoría visible

Cada alta, edición, activación o desactivación agrega una fila de `auditoria.evento`, entidad
`CRITERIO_SEGMENTACION`, con usuario, fecha e ID del criterio. `detalle` es JSON con fotos
`anterior` y `nuevo`; creación tiene anterior nulo. La misma transacción guarda criterio y
registro: si falla la auditoría, no se guarda el cambio. Una solicitud de estado que ya coincide
no inventa un nuevo cambio; una operación rechazada tampoco genera una modificación exitosa.

En la pantalla, **Historial y prueba** muestra estas fotos una al lado de la otra, con usuario,
fecha, operación y versión. Los cuatro tipos de operación también están disponibles como filtros
en **Administración → Auditoría**.

## Ejecución sobre el entorno del proyecto

1. Arrancar PostgreSQL (`crm-postgres`) y el API Gateway con sus variables habituales de BD/JWT.
   El Gateway aplica sus migraciones y crea la auditoría compartida.
2. Ejecutar `mvn spring-boot:run` en `backend/servicio-segmentacion`: aplica Flyway V2 y escucha
   en 8083. Si no se arrancó antes el Gateway, las escrituras fallarán al no existir la auditoría;
   no se guardarán cambios incompletos.
3. Ejecutar `npm ci` y `npm start` en `frontend/crm-web`; su proxy apunta al Gateway 8080.
4. Ingresar como Administrador de CRM y abrir **Criterios de segmentación** desde Inicio o el menú.
   Ruta: `http://localhost:4200/segmentacion/criterios`.

`angular.json` fue recuperado de la versión de Git porque faltaba en el directorio de trabajo.
No se cambiaron las migraciones ya existentes.

## Datos de ejemplo

`herramientas/pruebas-scrum25/cargar-ejemplos.ps1` crea 13 criterios a través del API con un token
administrador. Omite nombres ya existentes; cualquier conflicto real se informa, no se fuerza.
Los umbrales son ejemplos editables de prueba, no decisiones comerciales definitivas.

Los ejemplos cubren frecuencia cero, 1–3, 4–9 y 10 o más; valor hasta 499.99, 500–4999.99 y
5000 o más; y las seis categorías del conjunto de `database/semillas/scrum25_100_clientes.sql`.
Los 100 clientes se cargan con ese SQL en una base de desarrollo con sus migraciones de perfil
y comportamiento. Configurar criterios no requiere duplicar ni modificar esos clientes.

## Verificación reproducible y trazabilidad

Ejecutar desde la raíz del repositorio en PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\herramientas\pruebas-scrum25\ejecutar.ps1
```

Requiere Docker activo, Java 17, Maven, Node y npm. Ejecuta pruebas de Gateway y segmentación,
compila Angular y prueba Chromium contra servidores reales. Levanta **su propio contenedor
PostgreSQL**, con contraseña generada, sin tocar `crm-postgres` ni usar los datos del proyecto.
Usa 18080 (Gateway), 18083 (segmentación) y 14200 (frontend). Al terminar detiene los procesos y
elimina únicamente ese contenedor efímero. Los logs y reportes quedan ignorados por Git.

El script es solo para pruebas automatizadas y siempre cierra sus procesos y su contenedor al
terminar. Se retiró el modo de demostración persistente el 10 de octubre de 2026. Para usar el
proyecto y probarlo a mano, utilizar el frontend habitual en 4200 y la base `crm_maxiconecta`;
ver la [guía de prueba manual](prueba-manual.md). No ejecutar el script de pruebas para abrir
el proyecto ni reinstalar dependencias mientras el frontend está ejecutándose.

| Historia / subtarea | Implementación y comprobación |
|---|---|
| CA1 / 251, 252, 255, 256, 257, 258 | Modelo V2, API, semántica común en ReglasCriterio, formulario por indicador, persistencia al recargar |
| CA2 / 254 | Validación de campos/rangos, comparación normalizada, control de concurrencia; mensaje preciso en formulario |
| CA3 / 565 | Fotos anterior/nueva y reversión si falla auditoría; historial visible al editar o desactivar |
| CA4 | Gateway con rol + permiso, verificación de identidad interna, guard y menú Angular; agente/gerente reciben 403 incluso falsificando cabeceras |
| 253 | Pantalla adaptable: filtros, estados, formulario, confirmación, prueba del criterio y comparación de historial |
| 259 | 16 pruebas de integración PostgreSQL, pruebas de seguridad Gateway y 5 recorridos Playwright sin interceptar ni simular respuestas |
| 573 | Diagramas de clases y secuencia en este directorio |

Los tests de integración también cubren límites inclusivos, igualdad a cero, categorías con
tildes, campos faltantes, reactivación conflictiva, versión obsoleta, inmutabilidad de auditoría
y altas simultáneas. Los recorridos de navegador cubren recarga/persistencia, edición, baja,
mensajes, valor decimal, categoría, permisos y ancho móvil de 390 px.

La revisión por un par y el merge son pasos posteriores de la DoD; ejecutar estas pruebas no
los sustituye ni cambia la historia de Jira a Finalizado.

Resultados y capturas de la ejecución: [evidencias de verificación](verificacion.md).
