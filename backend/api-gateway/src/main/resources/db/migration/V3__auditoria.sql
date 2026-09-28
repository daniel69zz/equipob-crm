-- SCRUM-508 · Auditoría de cambios de roles, permisos y asignaciones
-- Esquema compartido de auditoría: solo se insertan registros, nunca se modifican ni se borran.

CREATE SCHEMA IF NOT EXISTS auditoria;

CREATE TABLE auditoria.evento (
    id          BIGSERIAL    PRIMARY KEY,
    ocurrido_en TIMESTAMPTZ  NOT NULL DEFAULT now(),
    usuario     VARCHAR(100) NOT NULL,
    operacion   VARCHAR(60)  NOT NULL,
    entidad     VARCHAR(60)  NOT NULL,
    entidad_id  VARCHAR(60),
    detalle     TEXT
);

CREATE INDEX idx_evento_ocurrido_en ON auditoria.evento (ocurrido_en DESC);
CREATE INDEX idx_evento_operacion   ON auditoria.evento (operacion);

CREATE OR REPLACE FUNCTION auditoria.impedir_modificacion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Los registros de auditoria son de solo lectura';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER evento_solo_lectura
    BEFORE UPDATE OR DELETE ON auditoria.evento
    FOR EACH ROW EXECUTE FUNCTION auditoria.impedir_modificacion();
