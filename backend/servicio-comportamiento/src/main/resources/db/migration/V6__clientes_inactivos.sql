-- SCRUM-296 · Persistencia de los clientes inactivos detectados (SCRUM-31, RF-23)
-- Diseño: docs/compra/clientes-inactivos.md

CREATE TABLE comportamiento.cliente_inactivo (
    id                BIGSERIAL    PRIMARY KEY,
    origen            VARCHAR(15)  NOT NULL CHECK (origen IN ('MARKETPLACE', 'VENTAS')),
    id_cliente_origen VARCHAR(64)  NOT NULL,
    ultima_compra     TIMESTAMPTZ  NOT NULL,
    detectado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_cliente_inactivo UNIQUE (origen, id_cliente_origen)
);

-- Para listar del que lleva más tiempo sin comprar al que lleva menos (GET .../clientes?estado=inactivo).
CREATE INDEX idx_cliente_inactivo_ultima_compra ON comportamiento.cliente_inactivo (ultima_compra);
