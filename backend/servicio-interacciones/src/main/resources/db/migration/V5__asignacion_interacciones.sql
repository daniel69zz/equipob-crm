-- SCRUM-457 · Agente responsable de cada interacción (SCRUM-56).
-- Flujo y reglas en docs/interacciones/asignacion-interacciones.md (SCRUM-454).

-- Las interacciones registradas antes de esta historia quedan sin responsable.
ALTER TABLE interacciones.interaccion
    ADD COLUMN responsable              VARCHAR(100),
    ADD COLUMN responsable_asignado_por VARCHAR(100),
    ADD COLUMN responsable_asignado_en  TIMESTAMPTZ,
    ADD CONSTRAINT interaccion_responsable_completo CHECK (
        (responsable IS NULL AND responsable_asignado_por IS NULL AND responsable_asignado_en IS NULL)
        OR (responsable IS NOT NULL AND responsable_asignado_por IS NOT NULL AND responsable_asignado_en IS NOT NULL));

-- Las interacciones de un agente en el historial del cliente (filtro por responsable).
CREATE INDEX idx_interaccion_responsable ON interacciones.interaccion (cliente_id, responsable)
    WHERE responsable IS NOT NULL;
