# UML del Sprint 2 - CRM MaxiConecta

**30 diagramas: 15 de clases y 15 de secuencia.** Entrega en [PlantUML editable](fuentes/), [PNG de alta resolución](png/) y [SVG vectorial](svg/). El [PDF](CRM-MaxiConecta-Sprint-2-UML.pdf) tiene un diagrama por página y marcadores por funcionalidad. [Paquete para el LMS](CRM-MaxiConecta-Sprint-2-LMS.zip).

Las secuencias indican quién inicia la operación, los datos intercambiados y el resultado. Cada interacción lleva una descripción en español y, cuando aporta claridad, el método real. Las vistas de clases seleccionan los miembros esenciales y abrevian parámetros con `(...)`. Los nombres y tipos proceden del código; los comentarios `Fuente:` de cada archivo permiten ubicarlos.

Las solicitudes de los usuarios pasan por Angular y el API Gateway antes de llegar al controlador mostrado. La autenticación, el enrutamiento y los colaboradores secundarios se omiten del detalle para concentrar cada vista en su funcionalidad. La mensajería representada corresponde a RabbitMQ.

Código revisado: commit `1a3792f`, rama `feature/SCRUM-30-historial-segmentos`. Se conserva el alcance contrastado con el Sprint 2 de Jira (tablero 1, sprint 9).

| Historia / alcance | Funcionalidad | PNG | PlantUML |
|---|---|---|---|
| SCRUM-20 y SCRUM-23: dependencias del Sprint 1 | Registrar una compra confirmada | [Clases](png/01-recepcion-compras-clases.png) · [Secuencia](png/01-recepcion-compras-secuencia.png) | [Clases](fuentes/01-recepcion-compras-clases.puml) · [Secuencia](fuentes/01-recepcion-compras-secuencia.puml) |
| SCRUM-25 | Definir criterios de segmentación | [Clases](png/02-criterios-clases.png) · [Secuencia](png/02-criterios-secuencia.png) | [Clases](fuentes/02-criterios-clases.puml) · [Secuencia](fuentes/02-criterios-secuencia.puml) |
| SCRUM-26 | Actualizar el segmento después de una compra | [Clases](png/03-asignacion-asincrona-clases.png) · [Secuencia](png/03-asignacion-asincrona-secuencia.png) | [Clases](fuentes/03-asignacion-asincrona-clases.puml) · [Secuencia](fuentes/03-asignacion-asincrona-secuencia.puml) |
| SCRUM-38; SCRUM-19 y SCRUM-37 del Sprint 1 | Consultar recencia y frecuencia de compra | [Clases](png/04-recencia-frecuencia-clases.png) · [Secuencia](png/04-recencia-frecuencia-secuencia.png) | [Clases](fuentes/04-recencia-frecuencia-clases.puml) · [Secuencia](fuentes/04-recencia-frecuencia-secuencia.puml) |
| SCRUM-27 | Clasificar al cliente por nivel de consumo | [Clases](png/05-nivel-consumo-clases.png) · [Secuencia](png/05-nivel-consumo-secuencia.png) | [Clases](fuentes/05-nivel-consumo-clases.puml) · [Secuencia](fuentes/05-nivel-consumo-secuencia.puml) |
| SCRUM-29 | Simular una regla de segmentación | [Clases](png/06-simulacion-clases.png) · [Secuencia](png/06-simulacion-secuencia.png) | [Clases](fuentes/06-simulacion-clases.puml) · [Secuencia](fuentes/06-simulacion-secuencia.puml) |
| SCRUM-30 | Consultar el historial de segmentos del cliente | [Clases](png/07-historial-segmentos-clases.png) · [Secuencia](png/07-historial-segmentos-secuencia.png) | [Clases](fuentes/07-historial-segmentos-clases.puml) · [Secuencia](fuentes/07-historial-segmentos-secuencia.puml) |
| SCRUM-32 | Analizar la evolución del consumo por categoría | [Clases](png/08-evolucion-categoria-clases.png) · [Secuencia](png/08-evolucion-categoria-secuencia.png) | [Clases](fuentes/08-evolucion-categoria-clases.puml) · [Secuencia](fuentes/08-evolucion-categoria-secuencia.puml) |
| SCRUM-34 | Detectar cambios en el hábito de compra | [Clases](png/09-cambios-comportamiento-clases.png) · [Secuencia](png/09-cambios-comportamiento-secuencia.png) | [Clases](fuentes/09-cambios-comportamiento-clases.puml) · [Secuencia](fuentes/09-cambios-comportamiento-secuencia.puml) |
| SCRUM-36 | Consultar la retención de clientes por cohorte | [Clases](png/10-retencion-cohortes-clases.png) · [Secuencia](png/10-retencion-cohortes-secuencia.png) | [Clases](fuentes/10-retencion-cohortes-clases.puml) · [Secuencia](fuentes/10-retencion-cohortes-secuencia.puml) |
| SCRUM-39 | Consultar el consumo del cliente en un periodo | [Clases](png/11-consumo-periodo-clases.png) · [Secuencia](png/11-consumo-periodo-secuencia.png) | [Clases](fuentes/11-consumo-periodo-clases.puml) · [Secuencia](fuentes/11-consumo-periodo-secuencia.puml) |
| SCRUM-41 | Construir el perfil de preferencias del cliente | [Clases](png/12-preferencias-clases.png) · [Secuencia](png/12-preferencias-secuencia.png) | [Clases](fuentes/12-preferencias-clases.puml) · [Secuencia](fuentes/12-preferencias-secuencia.puml) |
| SCRUM-42 | Actualizar preferencias con cada compra | [Clases](png/12a-actualizacion-preferencias-clases.png) · [Secuencia](png/12a-actualizacion-preferencias-secuencia.png) | [Clases](fuentes/12a-actualizacion-preferencias-clases.puml) · [Secuencia](fuentes/12a-actualizacion-preferencias-secuencia.puml) |
| SCRUM-43 | Consultar las categorías preferidas del cliente | [Clases](png/12b-consulta-preferencias-clases.png) · [Secuencia](png/12b-consulta-preferencias-secuencia.png) | [Clases](fuentes/12b-consulta-preferencias-clases.puml) · [Secuencia](fuentes/12b-consulta-preferencias-secuencia.puml) |
| SCRUM-45 | Acumular puntos de fidelización | [Clases](png/13-acumulacion-puntos-clases.png) · [Secuencia](png/13-acumulacion-puntos-secuencia.png) | [Clases](fuentes/13-acumulacion-puntos-clases.puml) · [Secuencia](fuentes/13-acumulacion-puntos-secuencia.puml) |

## Alcance y limitaciones

- Recepción, recencia y contador de frecuencia pertenecen al Sprint 1 y se incluyen como dependencias. La clasificación ocasional/habitual/frecuente corresponde a SCRUM-38 del Sprint 2. Construcción, actualización y consulta de preferencias se separan en tres pares de diagramas.

- En el contraste previo con Jira, SCRUM-28, SCRUM-40 y SCRUM-44 figuraban finalizadas, pero este checkout no contiene su implementación completa; SCRUM-35 figuraba en curso. No se representan como funcionalidades entregadas. La consulta por nivel ya existente y la comparación por categoría no cubren por sí solas esas historias.

- Una compra sin perfil vinculado no publica métricas. Si el envío falla después de guardar la compra, el código registra el error y no tiene un reenvío automático persistente. La recencia devuelve una duración ISO-8601; las cohortes agrupan por identificador de origen; la acreditación de puntos requiere el evento RIO-CRM-03.

- Marketplace y Ventas es un único módulo externo. Su productor no está en este repositorio. Pub/Sub existe como [alternativa configurable](../../mensajeria/pubsub.md), pero no se representa en esta entrega centrada en RabbitMQ.

Se revisaron nombres y tipos contra el código, sintaxis, disposición visual y exportaciones. No se modificó código funcional ni se realizaron commits.
