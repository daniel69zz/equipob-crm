-- SCRUM-165 · Estado de validación del perfil: agrega INCONSISTENTE y generaliza el motivo
-- SCRUM-170 · El motor de detección también registra sus cambios en cambio_perfil
-- Reglas: docs/perfil/catalogo-reglas-validacion.md

ALTER TABLE perfil.cliente RENAME COLUMN motivos_incompleto TO motivos_incidencia;

-- 'INCONSISTENTE' (13 caracteres) no entra en el varchar(12) original.
ALTER TABLE perfil.cliente ALTER COLUMN estado TYPE VARCHAR(14);

ALTER TABLE perfil.cliente DROP CONSTRAINT cliente_estado_check;
ALTER TABLE perfil.cliente ADD CONSTRAINT cliente_estado_check
    CHECK (estado IN ('COMPLETO', 'INCOMPLETO', 'INCONSISTENTE'));

-- El motor de detección registra sus cambios con tipo DETECCION (origen CRM).
ALTER TABLE perfil.cambio_perfil DROP CONSTRAINT cambio_perfil_tipo_check;
ALTER TABLE perfil.cambio_perfil ADD CONSTRAINT cambio_perfil_tipo_check
    CHECK (tipo IN ('CREACION', 'ACTUALIZACION', 'VINCULACION', 'UNIFICACION', 'DETECCION'));
