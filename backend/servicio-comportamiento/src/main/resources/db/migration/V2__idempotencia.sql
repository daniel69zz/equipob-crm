-- SCRUM-239 · Registro de eventos procesados (idempotencia)
-- Estrategia: docs/ingesta/idempotencia-eventos-venta.md

-- Una fila por transacción procesada. La llave primaria es la clave de idempotencia:
-- impide registrar dos veces la misma transacción, incluso si dos copias llegan a la vez.
CREATE TABLE comportamiento.evento_procesado (
    tipo_evento        VARCHAR(40)  NOT NULL,
    origen             VARCHAR(15)  NOT NULL,
    id_transaccion     VARCHAR(64)  NOT NULL,
    id_evento_recibido BIGINT       NOT NULL REFERENCES comportamiento.evento_recibido (id),
    procesado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_evento_procesado PRIMARY KEY (tipo_evento, origen, id_transaccion)
);

-- Las compras registradas antes de esta migración ya ocupan su clave.
INSERT INTO comportamiento.evento_procesado (tipo_evento, origen, id_transaccion, id_evento_recibido, procesado_en)
SELECT 'COMPRA_CONFIRMADA', origen, id_compra_origen, id_evento, registrada_en
FROM comportamiento.compra;

-- Identificador de la transacción en la bitácora, para la traza de descartes y la búsqueda por transacción.
ALTER TABLE comportamiento.evento_recibido ADD COLUMN id_transaccion VARCHAR(64);

UPDATE comportamiento.evento_recibido e
SET id_transaccion = c.id_compra_origen
FROM comportamiento.compra c
WHERE c.id_evento = e.id;

CREATE INDEX idx_evento_recibido_transaccion ON comportamiento.evento_recibido (id_transaccion, recibido_en DESC);
