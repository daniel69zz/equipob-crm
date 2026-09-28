# servicio-perfil — Perfil del Cliente

Mantiene la ficha única de cada cliente.

- **Guarda:** datos personales, contacto, direcciones, consentimiento, identificadores de origen (Marketplace y Ventas) e histórico de cambios.
- **Consume de RabbitMQ:** altas y cambios de datos del cliente.
- **Esquema en la base:** `perfil`

Historias del Sprint 1: crear y actualizar perfil, actualización ante cambios, identificadores de origen, perfiles incompletos, unificación de duplicados, consentimiento, histórico de cambios y ficha integral.

## Ejecutar en local

Requiere PostgreSQL y RabbitMQ (ver los comandos de Docker en `backend/servicio-comportamiento/README.md`). Con las variables de `.env.example`:

```bash
mvn spring-boot:run
```

El servicio escucha en el puerto **8081**. La aplicación web llega a él a través del API Gateway (`/api/perfil/**`).

## Pruebas

```bash
mvn test
```

Las pruebas de integración levantan PostgreSQL y RabbitMQ con **Testcontainers**; si Docker no está disponible, se omiten.
