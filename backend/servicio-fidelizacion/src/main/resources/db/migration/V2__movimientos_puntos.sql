-- SCRUM-45 · Acumulación de puntos por los movimientos RIO-CRM-03 de Marketplace y Ventas.
-- Detalle en docs/fidelizacion/acumulacion-puntos.md.

-- SCRUM-389: un cliente que acumula por primera vez todavía no tiene nivel (los niveles son SCRUM-49).
ALTER TABLE fidelizacion.saldo_puntos
    ALTER COLUMN nivel DROP NOT NULL,
    ALTER COLUMN saldo TYPE BIGINT,
    ADD CONSTRAINT saldo_puntos_no_negativo CHECK (saldo >= 0);

-- Bitácora de movimientos: cada mensaje recibido con su contenido original y su resultado (CA3).
CREATE TABLE fidelizacion.evento_movimiento (
    id               BIGSERIAL    PRIMARY KEY,
    contenido        TEXT         NOT NULL,
    id_evento        VARCHAR(64),
    id_transaccion   VARCHAR(64),
    tipo_movimiento  VARCHAR(20),
    estado           VARCHAR(20)  NOT NULL CHECK (estado IN ('RECIBIDO', 'PROCESADO', 'FALLIDO', 'DESCARTADO')),
    causa            TEXT,
    recibido_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    procesado_en     TIMESTAMPTZ,
    intentos         INTEGER      NOT NULL DEFAULT 1 CHECK (intentos > 0)
);
CREATE INDEX idx_evento_movimiento_estado ON fidelizacion.evento_movimiento (estado, recibido_en DESC);

-- SCRUM-390 / SCRUM-243: detalle de puntos por transacción. La clave primaria impide acreditar dos
-- veces la misma transacción, aunque llegue con otro idEvento o dos copias se procesen a la vez.
CREATE TABLE fidelizacion.movimiento_puntos (
    id_transaccion        VARCHAR(64)    NOT NULL,
    tipo_movimiento       VARCHAR(20)    NOT NULL CHECK (tipo_movimiento IN ('ACUMULACION', 'REDENCION', 'REVERSA')),
    cliente_id            BIGINT         NOT NULL,
    id_cliente_origen     VARCHAR(64)    NOT NULL,
    valor_consumido       NUMERIC(14,2)  NOT NULL CHECK (valor_consumido > 0),
    puntos                BIGINT         NOT NULL CHECK (puntos >= 0),
    bolivianos_por_punto  NUMERIC(14,2)  NOT NULL CHECK (bolivianos_por_punto > 0),
    saldo_resultante      BIGINT         NOT NULL CHECK (saldo_resultante >= 0),
    fecha_transaccion     TIMESTAMPTZ    NOT NULL,
    id_evento_movimiento  BIGINT         NOT NULL REFERENCES fidelizacion.evento_movimiento (id),
    registrado_en         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (id_transaccion, tipo_movimiento)
);
CREATE INDEX idx_movimiento_puntos_cliente ON fidelizacion.movimiento_puntos (cliente_id, registrado_en DESC);
