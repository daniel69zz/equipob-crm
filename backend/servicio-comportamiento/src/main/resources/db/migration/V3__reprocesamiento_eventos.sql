-- SCRUM-24 · Historial de intentos manuales de reproceso

CREATE TABLE comportamiento.intento_reproceso (
    id                   BIGSERIAL    PRIMARY KEY,
    id_evento_recibido   BIGINT       NOT NULL
        REFERENCES comportamiento.evento_recibido (id),
    numero_intento       INTEGER      NOT NULL CHECK (numero_intento > 0),
    intentado_en         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resultado            VARCHAR(15)  NOT NULL
        CHECK (resultado IN ('EN_PROCESO', 'PROCESADO', 'FALLIDO', 'DESCARTADO')),
    causa                VARCHAR(500),
    usuario              VARCHAR(60),
    CONSTRAINT uq_intento_reproceso_numero UNIQUE (id_evento_recibido, numero_intento)
);

CREATE INDEX idx_intento_reproceso_evento
    ON comportamiento.intento_reproceso (id_evento_recibido, numero_intento DESC);

-- Solo puede existir un intento manual activo por evento.
CREATE UNIQUE INDEX uq_intento_reproceso_activo
    ON comportamiento.intento_reproceso (id_evento_recibido)
    WHERE resultado = 'EN_PROCESO';
