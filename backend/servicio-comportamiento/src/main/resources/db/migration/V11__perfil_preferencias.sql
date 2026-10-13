-- SCRUM-359 / SCRUM-363: una instantánea por cliente CRM, incluso si no tiene preferencias.
-- El identificador se verifica por API: no se escriben ni se agregan FKs sobre el esquema perfil.
CREATE TABLE comportamiento.perfil_preferencias (
    cliente_id BIGINT PRIMARY KEY CHECK (cliente_id > 0),
    construido_en TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    criterio VARCHAR(50) NOT NULL
);

CREATE TABLE comportamiento.preferencia_categoria (
    cliente_id BIGINT NOT NULL REFERENCES comportamiento.perfil_preferencias(cliente_id) ON DELETE CASCADE,
    categoria VARCHAR(100) NOT NULL CHECK (length(trim(categoria)) > 0),
    posicion INTEGER NOT NULL CHECK (posicion > 0),
    compras BIGINT NOT NULL CHECK (compras > 0),
    unidades BIGINT NOT NULL CHECK (unidades >= 0),
    monto NUMERIC(20,2) NOT NULL CHECK (monto >= 0),
    PRIMARY KEY (cliente_id, categoria),
    UNIQUE (cliente_id, posicion),
    CHECK (unidades > 0 OR monto > 0)
);
