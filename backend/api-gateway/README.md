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

## Inicio de sesión y administración (SCRUM-63)

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| POST | `/api/auth/login` | Público | Inicia sesión y devuelve el token JWT con el rol y los permisos |
| GET | `/api/auth/yo` | Autenticado | Sesión actual |
| GET | `/api/admin/usuarios` | `USUARIOS_ADMINISTRAR` | Lista los usuarios internos |
| POST | `/api/admin/usuarios` | `USUARIOS_ADMINISTRAR` | Crea un usuario con su rol |
| PUT | `/api/admin/usuarios/{id}/rol` | `USUARIOS_ADMINISTRAR` | Asigna otro rol a un usuario |
| PATCH | `/api/admin/usuarios/{id}/desactivar` | `USUARIOS_ADMINISTRAR` | Desactiva un usuario |
| GET | `/api/admin/roles` | `USUARIOS_ADMINISTRAR` | Lista los roles con sus permisos |
| POST | `/api/admin/roles` | `USUARIOS_ADMINISTRAR` | Crea un rol |
| PUT | `/api/admin/roles/{id}` | `USUARIOS_ADMINISTRAR` | Cambia el nombre y los permisos de un rol |
| PATCH | `/api/admin/roles/{id}/desactivar` | `USUARIOS_ADMINISTRAR` | Desactiva un rol |
| GET | `/api/admin/permisos` | `USUARIOS_ADMINISTRAR` | Lista los permisos disponibles |
| GET | `/api/admin/auditoria` | `AUDITORIA_CONSULTAR` | Últimos 100 eventos de auditoría (`?operacion=` opcional) |

El permiso requerido por cada ruta hacia los microservicios está en `docs/seguridad/matriz-permisos.md`.

Ejemplo de inicio de sesión:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usuario":"admin","password":"<ADMIN_PASSWORD>"}'
```

## Base de datos

| Migración | Contenido |
|---|---|
| `V1__roles_y_permisos.sql` | Esquema `seguridad`: roles, permisos y matriz inicial |
| `V2__usuarios.sql` | Usuarios internos (contraseña con BCrypt) |
| `V3__auditoria.sql` | Esquema `auditoria`: tabla `evento` de solo lectura |

## Pruebas

```bash
mvn test
```

## Ejecutar en local

Requisitos: Java 17, Maven y PostgreSQL con la base `crm_maxiconecta`.

1. Copiar `.env.example` como `.env` y completar los valores.
2. Exportar las variables y arrancar:

```bash
set -a; source .env; set +a
mvn spring-boot:run
```

Las migraciones de Flyway crean el esquema `seguridad` al arrancar.
