-- SCRUM-524 · Reversa de puntos por compra anulada o devuelta (RIO-CRM-03 REVERSA y RIO-CRM-05).
-- Detalle en docs/fidelizacion/reversa-puntos.md.

-- SCRUM-539: cada reversa apunta a la acumulación que revierte, para auditar su origen. Guarda el monto
-- revertido (valor_consumido), los puntos que le corresponden (puntos) y los que no se pudieron descontar
-- porque el cliente ya los había redimido (puntos_no_recuperados).
ALTER TABLE fidelizacion.movimiento_puntos
    ADD COLUMN id_transaccion_revertida   VARCHAR(64),
    ADD COLUMN tipo_movimiento_revertido  VARCHAR(20),
    ADD COLUMN puntos_no_recuperados      BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT movimiento_puntos_revertido_fk
        FOREIGN KEY (id_transaccion_revertida, tipo_movimiento_revertido)
        REFERENCES fidelizacion.movimiento_puntos (id_transaccion, tipo_movimiento),
    ADD CONSTRAINT movimiento_puntos_reversa_completa CHECK (
        tipo_movimiento <> 'REVERSA' OR (id_transaccion_revertida IS NOT NULL
                                         AND tipo_movimiento_revertido = 'ACUMULACION'
                                         AND valor_consumido IS NOT NULL)),
    ADD CONSTRAINT movimiento_puntos_solo_reversa_revierte CHECK (
        tipo_movimiento = 'REVERSA' OR (id_transaccion_revertida IS NULL AND tipo_movimiento_revertido IS NULL)),
    ADD CONSTRAINT movimiento_puntos_no_recuperados_validos CHECK (
        puntos_no_recuperados >= 0 AND puntos_no_recuperados <= puntos
        AND (tipo_movimiento = 'REVERSA' OR puntos_no_recuperados = 0));

-- Reversas anteriores de una compra (para calcular lo que sigue vigente) y auditoría por compra.
CREATE INDEX idx_movimiento_puntos_revertido
    ON fidelizacion.movimiento_puntos (id_transaccion_revertida, tipo_movimiento_revertido);
