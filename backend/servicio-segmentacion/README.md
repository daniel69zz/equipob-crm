# servicio-segmentacion — Segmentación de Clientes

Servicio Spring Boot del CRM MaxiConecta, propietario del esquema `segmentacion`.

- Consulta del segmento asignado para la ficha integral del cliente (SCRUM-149).
- **SCRUM-25:** alta, edición, activación y desactivación de criterios por frecuencia, valor acumulado y categoría.
- Rechazo de rangos inválidos y superposiciones, con control de concurrencia y versiones.
- Auditoría transaccional con fotos antes/después en el esquema compartido de solo inserción.
- Pantalla Angular en `/segmentacion/criterios`, disponible al Administrador de CRM.
- **SCRUM-26:** asignación automática del segmento con cada compra o anulación. Consume las métricas
  que publica servicio-comportamiento por RabbitMQ (`crm.segmentacion.metricas`), aplica la regla de
  prioridad (valor, frecuencia, categoría), descarta reentregas por `idEvento`, audita cada cambio y
  envía los mensajes inválidos a `crm.segmentacion.metricas.respaldo`
  (`POST /api/segmentacion/respaldo/reinyectar` los reprocesa).

Modelo, API, decisiones, diagramas, pruebas y puesta en marcha:
[Documentación de SCRUM-25](../../docs/segmentacion/criterios.md) y
[asignación automática de SCRUM-26](../../docs/segmentacion/asignacion-automatica.md).

## Ejecutar

PostgreSQL disponible y migraciones del API Gateway aplicadas (crean la auditoría compartida).
Variables `DB_URL`, `DB_USUARIO`, `DB_PASSWORD`; puerto predeterminado 8083, configurable con `PUERTO`.
RabbitMQ con `RABBIT_HOST`, `RABBIT_PUERTO`, `RABBIT_USUARIO`, `RABBIT_PASSWORD`.

```powershell
mvn spring-boot:run
```

## Verificar

```powershell
mvn test
```

Las pruebas de integración requieren Docker y usan PostgreSQL real mediante Testcontainers.
No se omiten si falta Docker: el fallo indica que no pudo verificarse esa parte.
El historial de cambios de segmento (SCRUM-30) es otra historia; usa la auditoría
`SEGMENTO_CLIENTE` que registra la asignación automática.

## Mensajería con Google Cloud Pub/Sub

Por defecto usa RabbitMQ. Con `SPRING_PROFILES_ACTIVE=pubsub` consume y publica sus eventos en Google
Cloud Pub/Sub, con los mismos nombres de tópicos y suscripciones. Ver `docs/mensajeria/pubsub.md`.
