-- SCRUM-165 · Conserva por separado los motivos de ambos tipos de incidencia.
ALTER TABLE perfil.cliente RENAME COLUMN motivos_incidencia TO motivos_incompleto;
ALTER TABLE perfil.cliente ALTER COLUMN estado TYPE VARCHAR(15);
ALTER TABLE perfil.cliente ADD COLUMN motivos_inconsistencia TEXT;

-- Conserva los motivos de perfiles inconsistentes registrados antes de esta migración.
UPDATE perfil.cliente
SET motivos_inconsistencia = motivos_incompleto,
    motivos_incompleto = NULL
WHERE estado = 'INCONSISTENTE';
