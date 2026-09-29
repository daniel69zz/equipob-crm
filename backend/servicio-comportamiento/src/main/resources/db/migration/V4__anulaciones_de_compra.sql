-- SCRUM-530 · Anulaciones y devoluciones de compra
-- Contrato del evento: docs/contratos-eventos/RIO-CRM-05-anulacion-compra.md

-- La compra conserva su monto total; lo revertido se acumula aparte para conocer lo que sigue vigente.
ALTER TABLE comportamiento.compra ALTER COLUMN estado TYPE VARCHAR(20);
ALTER TABLE comportamiento.compra ADD COLUMN monto_revertido NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE comportamiento.compra ADD CONSTRAINT ck_compra_estado
    CHECK (estado IN ('CONFIRMADA', 'DEVOLUCION_PARCIAL', 'ANULADA'));
ALTER TABLE comportamiento.compra ADD CONSTRAINT ck_compra_monto_revertido
    CHECK (monto_revertido >= 0 AND monto_revertido <= monto_total);

-- Una anulación total o devolución parcial. Se identifica por su canal y su identificador en ese canal.
CREATE TABLE comportamiento.anulacion (
    id                  BIGSERIAL     PRIMARY KEY,
    origen              VARCHAR(15)   NOT NULL CHECK (origen IN ('MARKETPLACE', 'VENTAS')),
    id_anulacion_origen VARCHAR(64)   NOT NULL,
    id_compra           BIGINT        NOT NULL REFERENCES comportamiento.compra (id),
    tipo                VARCHAR(10)   NOT NULL CHECK (tipo IN ('TOTAL', 'PARCIAL')),
    fecha               TIMESTAMPTZ   NOT NULL,
    monto_revertido     NUMERIC(12,2) NOT NULL CHECK (monto_revertido > 0),
    motivo              VARCHAR(300)  NOT NULL,
    id_evento           BIGINT        NOT NULL REFERENCES comportamiento.evento_recibido (id),
    registrada_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_anulacion_origen UNIQUE (origen, id_anulacion_origen)
);

CREATE INDEX idx_anulacion_compra ON comportamiento.anulacion (id_compra, fecha);

-- Ítems devueltos en una devolución parcial, por categoría.
CREATE TABLE comportamiento.anulacion_item (
    id            BIGSERIAL     PRIMARY KEY,
    id_anulacion  BIGINT        NOT NULL REFERENCES comportamiento.anulacion (id) ON DELETE CASCADE,
    categoria     VARCHAR(100)  NOT NULL,
    cantidad      INTEGER       NOT NULL CHECK (cantidad > 0),
    monto         NUMERIC(12,2) NOT NULL CHECK (monto > 0)
);

CREATE INDEX idx_anulacion_item_anulacion ON comportamiento.anulacion_item (id_anulacion);
