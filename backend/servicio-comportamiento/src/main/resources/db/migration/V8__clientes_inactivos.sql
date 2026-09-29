-- SCRUM-296 · Persistencia de los clientes inactivos detectados (SCRUM-31, RF-23)
-- Diseño: docs/compra/clientes-inactivos.md
-- Se identifica solo por id_cliente_origen: desde V6__un_solo_modulo_marketplace_y_ventas.sql el
-- CRM se integra con un único módulo (Marketplace y Ventas), sin distinción de canal.

CREATE TABLE comportamiento.cliente_inactivo (
    id                BIGSERIAL    PRIMARY KEY,
    id_cliente_origen VARCHAR(64)  NOT NULL,
    ultima_compra     TIMESTAMPTZ  NOT NULL,
    detectado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_cliente_inactivo UNIQUE (id_cliente_origen)
);

-- Para listar del que lleva más tiempo sin comprar al que lleva menos (GET .../clientes?estado=inactivo).
CREATE INDEX idx_cliente_inactivo_ultima_compra ON comportamiento.cliente_inactivo (ultima_compra);
