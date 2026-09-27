-- SCRUM-505 · Usuarios internos del CRM
-- Cada usuario tiene exactamente un rol. La contraseña se guarda como hash BCrypt.
-- El primer administrador lo crea la aplicación al arrancar (ADMIN_USUARIO / ADMIN_PASSWORD).

CREATE TABLE seguridad.usuario (
    id              BIGSERIAL    PRIMARY KEY,
    nombre_usuario  VARCHAR(60)  NOT NULL UNIQUE,
    nombre_completo VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    rol_id          INTEGER      NOT NULL REFERENCES seguridad.rol (id),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_usuario_rol ON seguridad.usuario (rol_id);
