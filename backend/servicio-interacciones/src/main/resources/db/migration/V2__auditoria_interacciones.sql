-- SCRUM-436 · Auditoría de la creación y de cada modificación de una interacción (SCRUM-52).
-- Modelo en docs/interacciones/registro-interacciones.md (SCRUM-433).

CREATE TABLE interacciones.interaccion_auditoria (
    id              BIGSERIAL     PRIMARY KEY,
    interaccion_id  BIGINT        NOT NULL REFERENCES interacciones.interaccion (id),
    operacion       VARCHAR(20)   NOT NULL CHECK (operacion IN ('CREADA', 'MODIFICADA')),
    usuario         VARCHAR(100)  NOT NULL,
    rol             VARCHAR(60),
    ocurrido_en     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL CHECK (version > 0),
    -- Cada campo cambiado con su valor anterior y el nuevo: {"canal": {"anterior": null, "nuevo": "CHAT"}}.
    cambios         JSONB         NOT NULL,
    UNIQUE (interaccion_id, version)
);

CREATE INDEX idx_interaccion_auditoria ON interacciones.interaccion_auditoria (interaccion_id, id);

-- Solo inserción, como la auditoría compartida: ni el servicio puede corregir lo que ya quedó registrado.
CREATE FUNCTION interacciones.impedir_cambio_auditoria() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La auditoría de las interacciones es de solo lectura';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER interaccion_auditoria_solo_lectura
    BEFORE UPDATE OR DELETE ON interacciones.interaccion_auditoria
    FOR EACH ROW EXECUTE FUNCTION interacciones.impedir_cambio_auditoria();
