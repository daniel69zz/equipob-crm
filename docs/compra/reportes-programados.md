# Programación de reportes (SCRUM-61 · RF-53)

En **Análisis → Reportes programados**, el usuario crea sus programaciones de comportamiento
de compra (SCRUM-58), descargables en CSV o PDF mediante la exportación de SCRUM-60.
Se guardan nombre, frecuencia diaria/semanal/mensual, formato, segmento opcional, cantidad de
días del reporte (1–1830), inicio futuro y fin opcional inclusivo. El periodo es móvil: los N días
calendario completos anteriores a la fecha prevista, en `America/La_Paz`; ambos extremos incluidos.
Un segmento vacío incluye toda la cartera. Los archivos sin compras se generan con el formato
existente de reporte sin datos. No se incorporan datos personales al archivo.

La recurrencia conserva la hora de Bolivia. La mensual conserva el día del inicio: un inicio el
31 ejecuta el último día de febrero y vuelve al 31 en marzo. La semanal avanza siete días.
El fin limita las fechas previstas; al agotarse la vigencia, la programación queda pausada.
Editar requiere un nuevo inicio futuro y conserva el estado. Pausar impide nuevas ejecuciones;
activar salta las fechas pasadas. Cancelar es definitivo y conserva el historial y los archivos.

## Ejecución y avisos

`PlanificadorReportes` consulta cada minuto hasta 50 programaciones vencidas. Se puede configurar
`comportamiento.reportes.programacion.intervalo-ms` (predeterminado 60000) o deshabilitarlo con
`comportamiento.reportes.programacion.habilitada=false` para mantenimiento o pruebas.

Cada ejecución bloquea su programación con `FOR UPDATE SKIP LOCKED` en una transacción. Dos
instancias no procesan la misma fecha; una restricción única sobre programación/fecha prevista
respalda esta regla. La edición y cancelación toman el mismo bloqueo: si la cancelación confirma
primero no se genera; si la ejecución ya tomó el bloqueo, esa ejecución termina antes de cancelar
las futuras. Tras una caída se procesa una sola fecha pendiente y se avanza al futuro, evitando
una ráfaga de archivos antiguos. Si el proceso cae antes de confirmar, la fecha sigue pendiente.

La generación y su auditoría usan una transacción independiente: un fallo SQL al generar no
invalida la transacción que guarda el fallo. Se conserva el tipo y mensaje de la excepción
(máximo 500 caracteres), fecha prevista, fecha real y estado `FALLIDA`. Las ejecuciones exitosas
guardan nombre, formato y contenido binario en PostgreSQL, estado `COMPLETADA` y aviso no leído.
El fallo de generación de un reporte no impide generar los demás; no se reintenta automáticamente
esa fecha, pero la siguiente recurrencia se mantiene. Si falla la persistencia del resultado,
la transacción revierte y la programación sigue pendiente para el próximo sondeo.

Los avisos de éxito y error aparecen en la cabecera del CRM (sondeo cada 30 segundos), y se
consultan y marcan leídos en la pantalla de programaciones. El archivo se descarga desde allí.
La entrega es dentro del CRM; no requiere correo ni un proveedor externo. El historial muestra
50 filas por página. Archivos e historial se conservan; no hay purga automática en esta versión.

## Acceso y API

Gateway y servicio exigen conjuntamente `REPORTES_CONSULTAR`, `REPORTES_EXPORTAR` y el nuevo
`REPORTES_PROGRAMAR`. La migración V10 del Gateway asigna este último al Administrador y Gerente
Comercial, que representa también el perfil de análisis de datos en la matriz actual. Un rol
adicional de analista puede recibir los tres permisos desde la administración de roles.
Todas las operaciones se limitan al usuario autenticado por el Gateway; ni otro usuario con el
mismo rol puede modificar programaciones, consultar avisos o descargar sus archivos (404).
Los servicios deben seguir expuestos únicamente al Gateway, como las demás APIs internas.

Base: `/api/comportamiento/reportes/programaciones`.

- `GET /`: lista propia; `POST /`: crear, responde 201 con `id`.
- `PUT /{id}`: editar filtros y recurrencia; `PUT /{id}/estado`: `{ "estado": "ACTIVA" | "PAUSADA" | "CANCELADA" }`.
- `GET /ejecuciones?pagina=0`: historial propio, 50 filas; `GET /avisos`: contador `noLeidas`.
- `GET /ejecuciones/{id}/archivo`: CSV/PDF como adjunto; 404 si falló o pertenece a otro usuario.
- `PUT /ejecuciones/{id}/leida`: marca el aviso propio como leído.

Ejemplo de creación:

```json
{
  "nombre": "Compras semanales",
  "frecuencia": "SEMANAL",
  "formato": "CSV",
  "dias": 7,
  "segmento": null,
  "inicio": "2026-11-17T08:00:00-04:00",
  "fin": "2027-11-17T08:00:00-04:00"
}
```

La migración V14 de comportamiento crea las dos tablas y el índice de vencimientos. Desplegar
primero las migraciones del Gateway, por la auditoría compartida y el permiso, y después el servicio
y frontend. Las sesiones consultan los permisos vigentes del Gateway.

## Verificación

`ProgramacionReportesIntegracionTest` usa PostgreSQL real y generación CSV/PDF real: verifica los
tres criterios de Jira, aislamiento por usuario, permisos, edición, pausa/reactivación, vigencia,
concurrencia entre instancias, recuperación tras caída y recurrencia mensual. `AccesoPorRolTest`
verifica los permisos en el Gateway. `e2e/scrum61.spec.ts` verifica el flujo de interfaz con una
API controlada: creación/edición/estados, avisos, descarga, causa de fallo y acceso denegado.
La batería existente de exportaciones comprueba la correspondencia entre los archivos y los datos.

Diagramas: [clases y secuencia](reportes-programados-diagramas.md).
