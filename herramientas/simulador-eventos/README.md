# simulador-eventos

Publica en RabbitMQ eventos de ejemplo de Marketplace y Ventas, para probar los consumidores del CRM sin depender del sistema real.

Eventos a simular (según los contratos de `docs/contratos-eventos/`):

- Alta y cambio de datos de un cliente
- Compra confirmada
- Anulación o devolución de compra (RIO-CRM-05)
- Redención de puntos (RIO-CRM-03)
- Casos de prueba: evento duplicado, evento con datos inválidos, evento que falla y va a la cola de fallidos
