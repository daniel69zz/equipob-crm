-- SCRUM-264 · Segmento asignado automáticamente (SCRUM-26), con el criterio y el evento que lo decidieron.
-- Detalle en docs/segmentacion/asignacion-automatica.md.

-- El segmento toma el nombre del criterio, que admite hasta 100 caracteres (V2).
ALTER TABLE segmentacion.segmento_cliente
    ALTER COLUMN segmento TYPE VARCHAR(100),
    ADD COLUMN criterio_id BIGINT REFERENCES segmentacion.criterio(id),
    ADD COLUMN indicador VARCHAR(20) CHECK (indicador IN ('FRECUENCIA', 'VALOR', 'CATEGORIA')),
    ADD COLUMN id_evento_origen VARCHAR(64);

-- Última foto de métricas aplicada a cada cliente, también si no cumple ningún criterio:
-- permite descartar un mensaje calculado antes que la foto vigente.
CREATE TABLE segmentacion.metricas_cliente (
    cliente_id       BIGINT        PRIMARY KEY,
    frecuencia       BIGINT        NOT NULL CHECK (frecuencia >= 0),
    valor            NUMERIC(14,2) NOT NULL CHECK (valor >= 0),
    categorias       TEXT[]        NOT NULL DEFAULT '{}',
    calculadas_en    TIMESTAMPTZ   NOT NULL,
    id_evento_origen VARCHAR(64)   NOT NULL,
    actualizadas_en  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- SCRUM-241 · Idempotencia: cada evento de venta aplicado una sola vez.
CREATE TABLE segmentacion.evento_procesado (
    id_evento    VARCHAR(64)  PRIMARY KEY,
    cliente_id   BIGINT       NOT NULL,
    resultado    VARCHAR(20)  NOT NULL CHECK (resultado IN ('APLICADO', 'DESACTUALIZADO')),
    procesado_en TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_evento_procesado_cliente ON segmentacion.evento_procesado (cliente_id, procesado_en DESC);

COMMENT ON TABLE segmentacion.metricas_cliente IS
    'SCRUM-26. Métricas publicadas por servicio-comportamiento tras cada compra o anulación.';
COMMENT ON TABLE segmentacion.evento_procesado IS
    'SCRUM-241. idEvento de venta ya aplicado; una reentrega se descarta sin recalcular.';
