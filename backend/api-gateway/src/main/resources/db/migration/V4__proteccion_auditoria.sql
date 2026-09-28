-- SCRUM-513 · Almacenamiento seguro de los eventos de auditoría
-- Completa la protección de V3 (UPDATE y DELETE) y agrega los índices que usa la consulta
-- de auditoría por usuario y por cliente afectado.

-- TRUNCATE no dispara los triggers FOR EACH ROW: se bloquea con uno por sentencia.
CREATE TRIGGER evento_sin_truncate
    BEFORE TRUNCATE ON auditoria.evento
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria.impedir_modificacion();

CREATE INDEX idx_evento_usuario ON auditoria.evento (usuario, ocurrido_en DESC);
CREATE INDEX idx_evento_entidad ON auditoria.evento (entidad, entidad_id, ocurrido_en DESC);

COMMENT ON TABLE auditoria.evento IS
    'Registro de auditoría de solo inserción. Alcance y campos en docs/seguridad/alcance-auditoria.md';
