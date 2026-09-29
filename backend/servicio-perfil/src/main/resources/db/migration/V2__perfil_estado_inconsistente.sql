-- SCRUM-165 · Estado de validación del perfil: agrega INCONSISTENTE y generaliza el motivo
-- Reglas: docs/perfil/catalogo-reglas-validacion.md

ALTER TABLE perfil.cliente RENAME COLUMN motivos_incompleto TO motivos_incidencia;

-- 'INCONSISTENTE' (13 caracteres) no entra en el varchar(12) original.
ALTER TABLE perfil.cliente ALTER COLUMN estado TYPE VARCHAR(14);

ALTER TABLE perfil.cliente DROP CONSTRAINT cliente_estado_check;
ALTER TABLE perfil.cliente ADD CONSTRAINT cliente_estado_check
    CHECK (estado IN ('COMPLETO', 'INCOMPLETO', 'INCONSISTENTE'));
