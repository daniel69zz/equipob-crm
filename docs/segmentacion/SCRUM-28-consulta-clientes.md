# SCRUM-28 — Consulta de clientes por segmento o nivel de consumo

## Integración y alcance

Funcionalidad del CRM existente, en Angular y servicio-segmentacion. Acceso desde Inicio o
**Análisis → Clientes por segmento**. Ruta: `/segmentacion/clientes`.

Se consultan las asignaciones vigentes de SCRUM-26 y las compras del CRM. Consultar no asigna ni
recalcula segmentos, no modifica perfiles y no ejecuta campañas. La consulta por nivel funciona
también cuando el cliente no tiene segmento. Una fila representa un perfil CRM no absorbido.

## Reglas funcionales (SCRUM-276)

- Elegir un segmento o nivel; ambos se combinan con AND. Nombre es un filtro adicional.
- Nombre busca una subcadena literal sin distinguir mayúsculas; % y _ no son comodines.
- Nivel: BAJO, MEDIO, ALTO. SIN_NIVEL es un filtro para ausencia de compras vigentes, no un nuevo
  valor del enum de Comportamiento.
- Se suman todos los identificadores actualmente vinculados en perfil.cliente_origen. No se usa
  el id_cliente copiado en compras, que puede quedar desactualizado después de una unificación.
- Valor vigente = monto_total - monto_revertido, solo cuando es positivo.
- Última compra = máxima fecha de las compras vigentes. Una devolución parcial cuenta;
  una anulación total no. Sin compras: nivel y fecha nulos.
- Los umbrales los entrega Comportamiento desde su CriterioNivelConsumo. No se mantienen umbrales
  independientes en Segmentación. Exactamente en el umbral se pertenece al nivel superior.
- El segmento se filtra por criterio_id, estable ante cambios de nombre. Se muestra el nombre
  actual del criterio. Un criterio inactivo sigue siendo consultable y se etiqueta como tal:
  SCRUM-28 no borra ni recalcula asignaciones.
- El catálogo incluye criterios sin asignaciones y asignaciones anteriores sin criterio_id.
  Estas últimas se consultan por su nombre exacto mediante el parámetro segmento.
- Los perfiles absorbidos se excluyen. No se transfieren asignaciones antiguas de un perfil
  absorbido al sobreviviente: las asignaciones corresponden al proceso de SCRUM-26.
- Página desde 0; tamaño por defecto 20, permitido entre 1 y 100. Fuera de rango: lista vacía
  con el total real y opción de volver a la primera página.
- Ordenamiento global por nombre, segmento, nivelConsumo o ultimaCompra. Desempate por clienteId.
  Nulos al final en ambas direcciones. Nivel ordenado BAJO, MEDIO, ALTO.
- Cambiar filtros, orden o tamaño vuelve a página 0. Cambiar página conserva filtros.
- Los parámetros quedan en la URL; recargar o retroceder restaura la consulta.
- Un fallo del servicio se muestra como error con reintento, nunca como lista vacía.

## Contrato HTTP (SCRUM-277, SCRUM-273)

Todas las peticiones públicas pasan por el API Gateway con Bearer JWT.

### Catálogo

`GET /api/segmentacion/segmentos`

Devuelve una lista de `{id, nombre, activo, legado}`. id puede ser null para asignaciones
anteriores; en ese caso se usa nombre como filtro segmento. No devuelve reglas administrativas.

### Listado

`GET /api/segmentacion/clientes?segmentoId=12&nivelConsumo=ALTO&pagina=0&tamanio=20&orden=nombre&direccion=asc`

| Parámetro | Regla |
|---|---|
| segmentoId | Entero positivo, ID del criterio. |
| segmento | Nombre exacto de una asignación anterior sin criterio_id. Excluyente con segmentoId; hasta 100 caracteres. |
| nivelConsumo | BAJO, MEDIO, ALTO o SIN_NIVEL. |
| nombre | Subcadena del nombre completo, hasta 200 caracteres. |
| pagina | Entero desde 0. Predeterminado 0. |
| tamanio | Entero 1–100. Predeterminado 20. |
| orden | nombre, segmento, nivelConsumo o ultimaCompra. Predeterminado nombre. |
| direccion | asc o desc. Predeterminado asc. |

Se exige al menos uno de segmentoId, segmento o nivelConsumo.

Respuesta:
```json
{
  "clientes": [{
    "clienteId": 42,
    "nombre": "Ana Pérez",
    "segmentoId": 12,
    "segmento": "Clientes frecuentes",
    "nivelConsumo": "ALTO",
    "ultimaCompra": "2026-10-16T14:30:00-04:00"
  }],
  "pagina": 0,
  "tamanio": 20,
  "total": 1
}
```

Nombre vacío se presenta como Sin nombre; segmento, nivel y fecha pueden ser null.
La interfaz presenta las fechas en la zona horaria del navegador.

| Estado | Significado |
|---|---|
| 200 | Consulta correcta, también si clientes está vacío. |
| 400 | Parámetros inválidos, filtro ausente, tamaño/orden no permitido. |
| 401 | Sin autenticación. |
| 403 | Sin SEGMENTOS_CONSULTAR. |
| 404 | Segmento solicitado inexistente. |
| 503 | No se pudo obtener umbrales o ejecutar la consulta de clientes. |

### Contrato interno de umbrales

`GET servicio-comportamiento/internal/consumo/umbrales` devuelve `{medio, alto}`.
Esta ruta no está en las rutas del Gateway. Los puertos de los microservicios deben permanecer
en red interna, siguiendo la arquitectura existente. Segmentación usa conexión de 2 s y lectura
de 3 s; no sustituye umbrales en caso de fallo.

## Implementación y datos

- Angular: ConsultaSegmentosComponent y ConsultaSegmentosService.
- API: ClientesPorSegmentoController, FiltroConsultaClientes y RepositorioConsultaClientes.
- Umbrales: UmbralesConsumo → UmbralesConsumoController → CriterioNivelConsumo.
- Consulta SQL parametrizada, lista cerrada de órdenes y LIMIT/OFFSET en PostgreSQL.
- COUNT y filas se leen en una transacción REPEATABLE_READ para mantener consistencia.
- No hay una llamada HTTP por cliente ni filtrado posterior a paginar.
- Índices propios de Segmentación en V4__consulta_clientes_segmento.sql.
- Se reutilizan los índices de perfil.cliente_origen(id_cliente) y
  comportamiento.compra(id_cliente_origen, fecha DESC).

### Decisión de arquitectura

El repositorio usa una base PostgreSQL centralizada con esquemas por dominio. Esta funcionalidad
lee perfil, comportamiento y segmentacion mediante una consulta especializada en Segmentación.
No escribe en esquemas ajenos. La ventaja es filtrar, contar y ordenar globalmente antes de
paginar, sin descargar todos los perfiles a memoria ni multiplicar llamadas por fila.

La dependencia entre esquemas es explícita: cambios en las columnas leídas requieren revisar
la consulta y las pruebas con migraciones reales. Si se separan las bases, se deberá reemplazar
este repositorio por una proyección de lectura o contrato de búsqueda equivalente.
El usuario de base de datos necesita SELECT sobre las tablas consultadas.

El listado anterior de SCRUM-27 conserva su contrato por identificador externo. SCRUM-28 se
alinea con la ficha integral al sumar identificadores del perfil CRM.

## Roles y auditoría (SCRUM-278, SCRUM-568)

Permiso: SEGMENTOS_CONSULTAR. Administrador y gerente ya lo tienen. La migración V7 del Gateway
agrega ANALISTA_DATOS con ese permiso; no le concede configuración, administración ni ficha
integral. El administrador puede asignarlo desde la administración de usuarios existente.

El Gateway autoriza y sanea cabeceras de identidad. El nuevo controlador también verifica
X-Usuario y X-Usuario-Permisos. Angular usa permisoGuard y oculta enlaces no autorizados.
El enlace Ver ficha solo aparece con FICHA_INTEGRAL_CONSULTAR.

AuditoriaAccesoFilter mantiene un evento por consulta con usuario, fecha, método, ruta, estado
HTTP e IP. Solo en /api/segmentacion/clientes agrega segmentoId, segmento y nivelConsumo si
se enviaron. Valores limitados a 100 caracteres y sin caracteres de control. Nunca incluye
nombre, parámetros arbitrarios, tokens ni contenido de las filas.
Los intentos 401/403 también quedan registrados. Se conserva la política existente: un fallo
de escritura de auditoría queda en logs y no cambia la respuesta.

## Ejecución en el sistema existente

1. Compilar los servicios Gateway, Perfil, Comportamiento y Segmentación.
2. Iniciar Gateway para aplicar V7 y Segmentación para aplicar V4 mediante Flyway.
3. Mantener Perfil y Comportamiento migrados en la misma base.
4. Configurar SERVICIO_COMPORTAMIENTO_URL en Segmentación si Comportamiento no usa localhost:8082.
5. Compilar/iniciar frontend/crm-web con su proxy habitual al Gateway.
6. Iniciar sesión y abrir Análisis → Clientes por segmento.

No se requiere cargar datos ficticios en la base de operación. Si todavía no hay segmentos
asignados, el catálogo permite consultar listas vacías y el filtro por nivel usa las compras existentes.

## Prueba manual y automatizada (SCRUM-279)

Prueba manual con datos existentes:
1. Iniciar sesión como administrador y abrir Clientes por segmento desde el menú.
2. Seleccionar un segmento y comprobar nombre, segmento, nivel y última compra.
3. Filtrar por nombre/nivel, ordenar una columna y cambiar de página.
4. Recargar y usar Atrás: filtros y página deben restaurarse.
5. Consultar un segmento vacío y comprobar el mensaje.
6. Limpiar; consultar solo un nivel y verificar clientes sin segmento.
7. Revisar Auditoría: usuario, fecha, segmento y nivel; sin nombre de búsqueda.
8. Repetir con gerente y analista; ambos consultan sin administrar criterios.
9. Entrar como agente sin permiso: enlace ausente y ruta/API denegadas.

Pruebas backend desde cada carpeta de servicio:
```powershell
mvn.cmd test
```

Pruebas frontend integradas y reproducibles:
```powershell
./herramientas/pruebas-scrum28/ejecutar.ps1
```

El script compila los mismos servicios y frontend del sistema, levanta una base desechable con
migraciones reales y ejecuta Playwright contra APIs reales, sin interceptar respuestas.
Carga datos de pruebas solo en esa base aislada. Requiere Docker, Java 17, Maven, Node y npm.
Usa puertos 18080–18083 y 14200; limpia sus procesos y su contenedor al terminar.
SinCompilar permite reutilizar compilaciones y dependencias ya preparadas.
ConservarEntorno conserva la ejecución exitosa para inspección; acceso y PIDs quedan en
logs/scrum28/entorno-local.json, ignorado por Git. Ese entorno no es la base de operación.

## Trazabilidad de las diez subtareas

| Subtarea | Entregable |
|---|---|
| SCRUM-276 | Reglas y contrato en este documento; FiltroConsultaClientes. |
| SCRUM-576 | scrum28-clases.mmd y scrum28-secuencia.mmd, con exportaciones SVG. |
| SCRUM-277 | Controlador, catálogo, consulta y respuesta paginada. |
| SCRUM-273 | Filtros globales, orden cerrado y migración V4. |
| SCRUM-278 | Permiso, comprobación de identidad, ruta/menú y migración V7. |
| SCRUM-568 | Contexto de selección en auditoría y pruebas de trazabilidad. |
| SCRUM-274 | Pantalla integrada en Inicio y menú Análisis. |
| SCRUM-275 | Paginación en servidor y controles con estado en URL. |
| SCRUM-279 | ConsultaClientesIntegracionTest, pruebas Gateway y scrum28.spec.ts. |
| SCRUM-280 | Este documento, READMEs y documentos de seguridad actualizados. |

La revisión por un compañero y el merge pertenecen al cierre de la historia; no se consideran
realizados por compilar o pasar pruebas.
