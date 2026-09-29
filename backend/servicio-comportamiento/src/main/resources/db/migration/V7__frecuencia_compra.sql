-- SCRUM-37 · Cantidad acumulada de compras confirmadas vigentes por identificador de cliente.
-- El contador se mantiene separado del perfil: una compra puede estar pendiente de vinculación y
-- aun así siempre conserva id_cliente_origen. SCRUM-523 podrá ajustar este valor ante anulaciones.

CREATE TABLE comportamiento.frecuencia_compra (
    id_cliente_origen VARCHAR(64) PRIMARY KEY,
    cantidad          BIGINT      NOT NULL DEFAULT 0 CHECK (cantidad >= 0),
    actualizada_en    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Inicializa el indicador para compras existentes que todavía conservan monto vigente.
INSERT INTO comportamiento.frecuencia_compra (id_cliente_origen, cantidad, actualizada_en)
SELECT id_cliente_origen, COUNT(*), now()
FROM comportamiento.compra
WHERE monto_total - monto_revertido > 0
GROUP BY id_cliente_origen;
