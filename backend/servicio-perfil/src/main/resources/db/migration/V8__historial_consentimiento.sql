-- SCRUM-184 · Historial de cambios del consentimiento
-- Modelo: docs/perfil/consentimiento-datos.md

-- Una foto completa del consentimiento después de cada otorgamiento, actualización o revocación.
CREATE TABLE perfil.consentimiento_historial (
    id                 BIGSERIAL    PRIMARY KEY,
    id_cliente         BIGINT       NOT NULL REFERENCES perfil.cliente (id),
    fecha              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    operacion          VARCHAR(15)  NOT NULL CHECK (operacion IN ('OTORGAMIENTO', 'ACTUALIZACION', 'REVOCACION')),
    estado             VARCHAR(10)  NOT NULL CHECK (estado IN ('OTORGADO', 'REVOCADO')),
    canal              VARCHAR(15)  NOT NULL,
    alcances           VARCHAR(200) NOT NULL,
    fecha_otorgamiento TIMESTAMPTZ  NOT NULL,
    vigencia_desde     DATE         NOT NULL,
    vigencia_hasta     DATE,
    fecha_revocacion   TIMESTAMPTZ,
    motivo_revocacion  VARCHAR(300),
    responsable        VARCHAR(100) NOT NULL
);

CREATE INDEX idx_consentimiento_historial_cliente ON perfil.consentimiento_historial (id_cliente, fecha DESC);

-- El historial es de solo inserción, como el registro de cambios del perfil.
CREATE OR REPLACE FUNCTION perfil.impedir_modificacion_consentimiento() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'El historial del consentimiento es de solo lectura';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER consentimiento_historial_solo_lectura
    BEFORE UPDATE OR DELETE ON perfil.consentimiento_historial
    FOR EACH ROW EXECUTE FUNCTION perfil.impedir_modificacion_consentimiento();

CREATE TRIGGER consentimiento_historial_sin_truncate
    BEFORE TRUNCATE ON perfil.consentimiento_historial
    FOR EACH STATEMENT EXECUTE FUNCTION perfil.impedir_modificacion_consentimiento();
