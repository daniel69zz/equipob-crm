# SCRUM-25 · Evidencias de verificación

Verificación ejecutada el 9 de octubre de 2026, aproximadamente a las 22:55 (America/La_Paz),
sobre los cambios locales de la rama `feature/SCRUM-25-segmentacion`.

## Resultado

| Comprobación | Resultado |
|---|---|
| API Gateway: Maven | 50 pruebas, cero fallos, errores u omisiones |
| Servicio de segmentación: Maven | 18 pruebas, cero fallos, errores u omisiones; incluye 16 de integración con PostgreSQL |
| Angular: compilación de producción | Correcta |
| Chromium: Playwright | 5 recorridos aprobados contra Gateway, servicio y base reales |
| Ejemplos por API | 13 criterios activos creados y auditados |
| Vista móvil | 390 px, formulario y acciones visibles, sin desbordamiento horizontal |

Comando utilizado desde la raíz:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\herramientas\pruebas-scrum25\ejecutar.ps1 -ReutilizarDependencias
```

`-ReutilizarDependencias` omite únicamente `npm ci` cuando las dependencias ya están instaladas.
Para una primera ejecución, omitir ese modificador.

Se utilizó un contenedor PostgreSQL efímero propio, con migraciones reales. No se usó ni modificó
la base de datos del entorno habitual. Los recorridos no interceptan respuestas del API.
Las capturas corresponden a los datos creados por los recorridos, antes de cargar los 13 ejemplos.

## Evidencia por criterio de aceptación

- **CA1:** creación de reglas de frecuencia, valor decimal y categoría, persistencia después de
  recargar y comprobación de valores contra la regla guardada. [Categorías](evidencias/criterios-categorias.png).
- **CA2:** rango invertido, nombre vacío y superposición rechazada, incluyendo un límite compartido.
  [Mensaje de superposición](evidencias/error-superposicion.png).
- **CA3:** edición de 5–9 a 4–9 compras, usuario y fecha, valores anteriores y nuevos; posterior
  desactivación y exclusión del listado activo. [Historial de edición](evidencias/historial-edicion.png).
- **CA4:** agente y gerente no ven la gestión; la ruta redirige a acceso denegado. El API devuelve
  403 al crear, editar o desactivar, incluso con cabeceras de identidad falsificadas; sin sesión,
  devuelve 401. Se comprueba en el cuarto recorrido y en las pruebas de seguridad del Gateway.
- **Frontend adaptable:** [Vista móvil](evidencias/movil.png).

La integración también comprueba dos altas concurrentes, versiones obsoletas, reactivación
conflictiva, límites inclusivos, normalización de categorías, auditoría inmutable y reversión
de toda la escritura si falla el registro de auditoría.

Durante la comprobación se corrigió el manejo de fallos de conexión del proxy: un microservicio
no disponible devuelve 503 con un mensaje claro y conserva la sesión válida. La prueba del
Gateway verifica además que `/api/auth/yo` sigue respondiendo después del fallo.

## Prueba en el proyecto habitual: 10 de octubre de 2026

Se verificó desde el navegador del usuario en `http://localhost:4200`, autenticado como `admin`,
contra la base original `crm_maxiconecta`. Se crearon tres ejemplos: frecuencia entre 12 y 20
compras, valor desde Bs 20.000 y consumo de Alimentos. Permanecen activos para la prueba manual.

Resultados observados: persistencia al recargar; frecuencia inicialmente 10–20 con 10, 14 y 20
aceptados y 9 y 21 rechazados; nombre vacío, rango invertido y superposición rechazados; edición
a 12–20 con antes/después, usuario y fecha; 11 compras rechazadas con la versión editada;
desactivación, exclusión del filtro Activos y reactivación auditadas; Bs 22.776,25 aceptados y
Bs 19.999,99 rechazados; `ALIMENTOS, Deportes` aceptado y `Libros, Deportes` rechazado.

La prueba por otros roles corresponde a los recorridos automatizados anteriores; en esta sesión
del navegador se utilizó solamente la cuenta administradora existente.

Se comprobaron 100 clientes y 744 compras conservados en la base original. El contenedor de demo
ya no existe y se retiraron su imagen adicional `postgres:16-alpine`, el archivo de credenciales
de demo y la opción `-MantenerAbierto`. Las pruebas automatizadas conservan su entorno temporal,
que se elimina al terminar. No se ha creado otro repositorio ni worktree.

Pasos y resultados esperados: [guía de prueba manual](prueba-manual.md).

## Pendientes de entrega externa

Los diagramas de clases y secuencia están disponibles en este directorio en formatos Mermaid
y SVG. La carga de los SVG a Jira sigue pendiente de autorización explícita para exportar esos
dos archivos. La revisión por un par, el merge y el cambio de estado en Jira no se han realizado.
