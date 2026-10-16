# Documentación

- `preferencias/`: construcción y persistencia del perfil de preferencias (SCRUM-41), regla de relevancia, diagramas y prueba manual por API, y su actualización automática con cada compra o anulación con trazabilidad por evento (SCRUM-42).

- `mensajeria/`: Google Cloud Pub/Sub como broker alternativo a RabbitMQ, elegible por configuración (perfil `pubsub`).
- `contratos-eventos/`: contratos de los eventos que intercambian el CRM y el módulo Marketplace y Ventas, su única integración.
- `seguridad/`: catálogo de roles, matriz de permisos, alcance de la auditoría y convención de rutas para datos de clientes.
- `ingesta/`: estrategia de idempotencia de los eventos de venta.
- `perfil/`: modelo de datos del perfil, identificadores de origen, mapeo de datos con su política de reintentos, histórico de cambios, y catálogo de reglas de validación (perfiles incompletos o inconsistentes).
- `compra/`: diseño de la consulta del historial de compras del cliente (SCRUM-16), regla de cálculo del ticket promedio (SCRUM-17) reglas de recálculo de los indicadores ante anulaciones y devoluciones (SCRUM-523) nivel de consumo del cliente con sus umbrales y diagramas (SCRUM-27) y detección de cambios en el comportamiento de compra con sus criterios, alertas y diagramas (SCRUM-34), y retención de clientes por cohortes de primera compra con su cálculo manual de referencia (SCRUM-36).

- `segmentacion/`: modelo y API de criterios (SCRUM-25), diagramas y verificación de backend/frontend, y asignación automática de segmento por RabbitMQ con su regla de prioridad, idempotencia, auditoría y diagramas (SCRUM-26).
