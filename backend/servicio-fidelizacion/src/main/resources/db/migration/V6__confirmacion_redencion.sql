-- SCRUM-406 · Confirmación de una redención por su transacción de origen en Marketplace y Ventas.
-- Detalle en docs/fidelizacion/redencion-puntos.md.

CREATE INDEX idx_evento_movimiento_transaccion
    ON fidelizacion.evento_movimiento (id_transaccion, tipo_movimiento, id DESC);
