-- SCRUM-48 · Redención de puntos notificada por Marketplace y Ventas (RIO-CRM-03, tipo REDENCION).
-- Detalle en docs/fidelizacion/redencion-puntos.md.

-- SCRUM-403: el historial de movimientos también guarda las redenciones. Una redención trae los puntos
-- redimidos; su valor en bolivianos es opcional y no se le aplica la tasa de acumulación.
ALTER TABLE fidelizacion.movimiento_puntos
    ALTER COLUMN valor_consumido DROP NOT NULL,
    ALTER COLUMN bolivianos_por_punto DROP NOT NULL,
    ADD CONSTRAINT movimiento_puntos_acumulacion_completa CHECK (
        tipo_movimiento <> 'ACUMULACION' OR (valor_consumido IS NOT NULL AND bolivianos_por_punto IS NOT NULL)),
    ADD CONSTRAINT movimiento_puntos_redencion_positiva CHECK (
        tipo_movimiento <> 'REDENCION' OR puntos > 0);

