-- SCRUM-617: instantanea transaccional; no depende de que Pub/Sub este disponible.
CREATE TABLE fidelizacion.salida_saldo_puntos (
    secuencia BIGSERIAL PRIMARY KEY,
    id_evento UUID NOT NULL UNIQUE,
    cliente_id BIGINT NOT NULL,
    id_transaccion VARCHAR(64),
    tipo_movimiento VARCHAR(20) NOT NULL,
    contenido JSONB NOT NULL,
    intentos INTEGER NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    proximo_intento TIMESTAMPTZ NOT NULL DEFAULT now(),
    publicado_en TIMESTAMPTZ,
    id_mensaje VARCHAR(200),
    ultimo_error VARCHAR(1000),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_salida_saldo_pendiente ON fidelizacion.salida_saldo_puntos (proximo_intento, secuencia)
    WHERE publicado_en IS NULL;
CREATE INDEX idx_salida_saldo_cliente ON fidelizacion.salida_saldo_puntos (cliente_id, secuencia)
    WHERE publicado_en IS NULL;
