-- SCRUM-165 · Estado y motivos de las inconsistencias del perfil.
ALTER TABLE perfil.cliente DROP CONSTRAINT cliente_estado_check;
ALTER TABLE perfil.cliente ALTER COLUMN estado TYPE VARCHAR(15);
ALTER TABLE perfil.cliente ADD CONSTRAINT cliente_estado_check
    CHECK (estado IN ('COMPLETO', 'INCOMPLETO', 'INCONSISTENTE'));
ALTER TABLE perfil.cliente ADD COLUMN motivos_inconsistencia TEXT;
