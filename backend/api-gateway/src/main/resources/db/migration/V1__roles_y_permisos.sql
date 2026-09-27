-- SCRUM-506 · Roles y permisos del CRM
-- Catálogo de roles: docs/seguridad/catalogo-roles.md
-- Matriz de permisos: docs/seguridad/matriz-permisos.md

CREATE SCHEMA IF NOT EXISTS seguridad;

CREATE TABLE seguridad.permiso (
    id          SERIAL       PRIMARY KEY,
    codigo      VARCHAR(60)  NOT NULL UNIQUE,
    descripcion VARCHAR(255) NOT NULL
);

CREATE TABLE seguridad.rol (
    id          SERIAL       PRIMARY KEY,
    codigo      VARCHAR(60)  NOT NULL UNIQUE,
    nombre      VARCHAR(120) NOT NULL,
    descripcion VARCHAR(255),
    activo      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE seguridad.rol_permiso (
    rol_id     INTEGER NOT NULL REFERENCES seguridad.rol (id),
    permiso_id INTEGER NOT NULL REFERENCES seguridad.permiso (id),
    PRIMARY KEY (rol_id, permiso_id)
);

INSERT INTO seguridad.permiso (codigo, descripcion) VALUES
    ('CLIENTE_CONSULTAR',       'Consultar perfil, ficha integral e historial de cambios del cliente'),
    ('CLIENTE_EDITAR',          'Modificar perfiles, consentimiento y unificar duplicados'),
    ('INDICADORES_CONSULTAR',   'Consultar historial de compras e indicadores de comportamiento'),
    ('EVENTOS_REPROCESAR',      'Ver la bitacora de ingesta y reprocesar eventos fallidos'),
    ('SEGMENTOS_CONSULTAR',     'Consultar segmentos y clientes por segmento'),
    ('SEGMENTACION_CONFIGURAR', 'Configurar criterios y reglas de segmentacion'),
    ('PUNTOS_CONSULTAR',        'Consultar saldo y nivel de puntos'),
    ('FIDELIZACION_CONFIGURAR', 'Configurar reglas de puntos, niveles y ajustes manuales'),
    ('INTERACCIONES_CONSULTAR', 'Consultar el historial de interacciones'),
    ('INTERACCIONES_REGISTRAR', 'Registrar, clasificar y asignar interacciones'),
    ('USUARIOS_ADMINISTRAR',    'Crear usuarios internos, asignar roles y administrar permisos'),
    ('AUDITORIA_CONSULTAR',     'Consultar los registros de auditoria');

INSERT INTO seguridad.rol (codigo, nombre, descripcion) VALUES
    ('ADMINISTRADOR_CRM', 'Administrador de CRM',          'Acceso completo a la operacion, configuracion y seguridad del CRM'),
    ('AGENTE_ATENCION',   'Agente de Atencion al Cliente', 'Consulta clientes y registra interacciones de atencion'),
    ('GERENTE_COMERCIAL', 'Gerente Comercial',             'Consulta clientes, indicadores y segmentos para el analisis comercial');

-- Administrador de CRM: todos los permisos
INSERT INTO seguridad.rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM seguridad.rol r CROSS JOIN seguridad.permiso p
WHERE r.codigo = 'ADMINISTRADOR_CRM';

-- Agente de Atencion al Cliente
INSERT INTO seguridad.rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM seguridad.rol r CROSS JOIN seguridad.permiso p
WHERE r.codigo = 'AGENTE_ATENCION'
  AND p.codigo IN ('CLIENTE_CONSULTAR', 'INDICADORES_CONSULTAR', 'PUNTOS_CONSULTAR',
                   'INTERACCIONES_CONSULTAR', 'INTERACCIONES_REGISTRAR');

-- Gerente Comercial
INSERT INTO seguridad.rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM seguridad.rol r CROSS JOIN seguridad.permiso p
WHERE r.codigo = 'GERENTE_COMERCIAL'
  AND p.codigo IN ('CLIENTE_CONSULTAR', 'INDICADORES_CONSULTAR', 'SEGMENTOS_CONSULTAR',
                   'PUNTOS_CONSULTAR', 'INTERACCIONES_CONSULTAR');
