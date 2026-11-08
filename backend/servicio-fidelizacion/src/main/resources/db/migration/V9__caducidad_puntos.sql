-- SCRUM-50 · Caducidad de los puntos no redimidos al vencer su vigencia.
-- Regla y casos en docs/fidelizacion/caducidad-puntos.md.

-- SCRUM-417: la caducidad es un movimiento más del historial. No viene de un evento de Marketplace y Ventas,
-- así que no tiene evento en la bitácora; los demás tipos lo siguen exigiendo.
ALTER TABLE fidelizacion.movimiento_puntos
    DROP CONSTRAINT movimiento_puntos_tipo_movimiento_check,
    ADD CONSTRAINT movimiento_puntos_tipo_movimiento_check
        CHECK (tipo_movimiento IN ('ACUMULACION', 'REDENCION', 'REVERSA', 'CADUCIDAD')),
    ALTER COLUMN id_evento_movimiento DROP NOT NULL,
    ADD CONSTRAINT movimiento_puntos_evento_obligatorio CHECK (
        id_evento_movimiento IS NOT NULL OR tipo_movimiento = 'CADUCIDAD'),
    -- Una caducidad descuenta todo el saldo: tiene puntos, deja el saldo en 0 y no aplica regla de acumulación.
    ADD CONSTRAINT movimiento_puntos_caducidad_completa CHECK (
        tipo_movimiento <> 'CADUCIDAD' OR (puntos > 0 AND saldo_resultante = 0 AND id_evento_movimiento IS NULL
                                           AND valor_consumido IS NULL AND version_regla IS NULL));

-- El historial de niveles admite la caducidad como movimiento de origen.
ALTER TABLE fidelizacion.cambio_nivel
    DROP CONSTRAINT cambio_nivel_tipo_movimiento_check,
    ADD CONSTRAINT cambio_nivel_tipo_movimiento_check
        CHECK (tipo_movimiento IN ('ACUMULACION', 'REDENCION', 'REVERSA', 'CADUCIDAD'));
