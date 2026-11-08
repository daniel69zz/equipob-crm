-- SCRUM-541 · Historial de cambios de nivel de cada cliente, con su origen: el movimiento de puntos que lo
-- causó (por ejemplo, la reversa que lo hace descender) o un recálculo (cambio de umbrales o recálculo
-- programado de SCRUM-49). Detalle en docs/fidelizacion/reversa-puntos.md.
CREATE TABLE fidelizacion.cambio_nivel (
    id               BIGSERIAL    PRIMARY KEY,
    cliente_id       BIGINT       NOT NULL,
    nivel_anterior   VARCHAR(20)  CHECK (nivel_anterior IN ('BRONCE', 'PLATA', 'ORO')),
    nivel_nuevo      VARCHAR(20)  CHECK (nivel_nuevo IN ('BRONCE', 'PLATA', 'ORO')),
    puntos_periodo   BIGINT       NOT NULL CHECK (puntos_periodo >= 0),
    origen           VARCHAR(20)  NOT NULL CHECK (origen IN ('MOVIMIENTO', 'RECALCULO')),
    id_transaccion   VARCHAR(64),
    tipo_movimiento  VARCHAR(20)  CHECK (tipo_movimiento IN ('ACUMULACION', 'REDENCION', 'REVERSA')),
    cambiado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (nivel_anterior IS DISTINCT FROM nivel_nuevo),
    CHECK (origen = 'MOVIMIENTO' OR (id_transaccion IS NULL AND tipo_movimiento IS NULL))
);
CREATE INDEX idx_cambio_nivel_cliente ON fidelizacion.cambio_nivel (cliente_id, cambiado_en DESC);
