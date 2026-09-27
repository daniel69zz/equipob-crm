# api-gateway

Punto de entrada único del CRM (**Spring Boot 3 + Spring Cloud Gateway MVC**).

- Enruta las peticiones de la aplicación web a cada microservicio.
- Emite y valida el token **JWT** del inicio de sesión y aplica el control de acceso por rol y permiso.
- Administra los usuarios internos, los roles y sus permisos (esquema `seguridad`).
- Expondrá la consulta síncrona de saldo y nivel de puntos para Marketplace y Ventas (RIO-CRM-04), que resuelve el servicio de Fidelización.

## Rutas hacia los microservicios

| Ruta | Microservicio | Puerto por defecto |
|---|---|---|
| `/api/perfil/**` | servicio-perfil | 8081 |
| `/api/comportamiento/**` | servicio-comportamiento | 8082 |
| `/api/segmentacion/**` | servicio-segmentacion | 8083 |
| `/api/fidelizacion/**` | servicio-fidelizacion | 8084 |
| `/api/interacciones/**` | servicio-interacciones | 8085 |

## Ejecutar en local

Requisitos: Java 17, Maven y PostgreSQL con la base `crm_maxiconecta`.

1. Copiar `.env.example` como `.env` y completar los valores.
2. Exportar las variables y arrancar:

```bash
set -a; source .env; set +a
mvn spring-boot:run
```

Las migraciones de Flyway crean el esquema `seguridad` al arrancar.
