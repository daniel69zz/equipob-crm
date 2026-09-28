-- SCRUM-223 · Almacenamiento estructurado de los eventos de venta
-- Contrato del evento: docs/contratos-eventos/RIO-CRM-02-compra-confirmada.md

CREATE SCHEMA IF NOT EXISTS comportamiento;

-- Bitácora de ingesta: cada mensaje recibido, con su contenido original y su estado.
-- El contenido se guarda como texto para conservar también los mensajes que no son JSON válido.
CREATE TABLE comportamiento.evento_recibido (
    id               BIGSERIAL    PRIMARY KEY,
    id_evento_origen VARCHAR(64),
    tipo_evento      VARCHAR(40),
    origen           VARCHAR(15),
    estado           VARCHAR(15)  NOT NULL
        CHECK (estado IN ('RECIBIDO', 'PROCESADO', 'FALLIDO', 'DESCARTADO')),
    causa            VARCHAR(500),
    contenido        TEXT         NOT NULL,
    recibido_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    procesado_en     TIMESTAMPTZ
);

CREATE INDEX idx_evento_recibido_fecha  ON comportamiento.evento_recibido (recibido_en DESC);
CREATE INDEX idx_evento_recibido_estado ON comportamiento.evento_recibido (estado, recibido_en DESC);

-- Compra confirmada. Una compra se identifica por su canal y su identificador en ese canal.
CREATE TABLE comportamiento.compra (
    id                BIGSERIAL     PRIMARY KEY,
    origen            VARCHAR(15)   NOT NULL CHECK (origen IN ('MARKETPLACE', 'VENTAS')),
    id_compra_origen  VARCHAR(64)   NOT NULL,
    id_cliente_origen VARCHAR(64)   NOT NULL,
    fecha             TIMESTAMPTZ   NOT NULL,
    monto_total       NUMERIC(12,2) NOT NULL,
    estado            VARCHAR(12)   NOT NULL DEFAULT 'CONFIRMADA',
    id_evento         BIGINT        NOT NULL REFERENCES comportamiento.evento_recibido (id),
    registrada_en     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_compra_origen UNIQUE (origen, id_compra_origen)
);

CREATE INDEX idx_compra_cliente ON comportamiento.compra (origen, id_cliente_origen, fecha DESC);

-- Detalle de la compra por categoría. La categoría se guarda con el nombre que traía el evento.
CREATE TABLE comportamiento.compra_item (
    id        BIGSERIAL     PRIMARY KEY,
    id_compra BIGINT        NOT NULL REFERENCES comportamiento.compra (id) ON DELETE CASCADE,
    categoria VARCHAR(100)  NOT NULL,
    cantidad  INTEGER       NOT NULL,
    monto     NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_compra_item_compra ON comportamiento.compra_item (id_compra);
