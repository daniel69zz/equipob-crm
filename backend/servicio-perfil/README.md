# servicio-perfil — Perfil del Cliente

Mantiene la ficha única de cada cliente.

- **Guarda:** datos personales, contacto, direcciones, consentimiento, identificadores de origen (Marketplace y Ventas) e histórico de cambios.
- **Consume de RabbitMQ:** altas y cambios de datos del cliente.
- **Esquema en la base:** `perfil`

Historias del Sprint 1: crear y actualizar perfil, actualización ante cambios, identificadores de origen, perfiles incompletos, unificación de duplicados, consentimiento, histórico de cambios y ficha integral.
