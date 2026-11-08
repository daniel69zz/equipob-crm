-- SCRUM-449 · Estado de seguimiento de cada interacción (SCRUM-55).
-- Catálogo y transiciones en docs/interacciones/seguimiento-interacciones.md (SCRUM-451).

-- Las interacciones registradas antes de esta historia quedan abiertas.
ALTER TABLE interacciones.interaccion
    ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'ABIERTA'
        CHECK (estado IN ('ABIERTA', 'EN_PROCESO', 'CERRADA')),
    ADD COLUMN estado_actualizado_por VARCHAR(100),
    ADD COLUMN estado_actualizado_en  TIMESTAMPTZ,
    ADD CHECK ((estado_actualizado_por IS NULL) = (estado_actualizado_en IS NULL));

-- Filtro del historial por estado (criterio de aceptación 3).
CREATE INDEX idx_interaccion_estado ON interacciones.interaccion (cliente_id, estado);

-- Cada cambio de estado o de tipo queda en la auditoría de la interacción como SEGUIMIENTO.
ALTER TABLE interacciones.interaccion_auditoria DROP CONSTRAINT interaccion_auditoria_operacion_check;
ALTER TABLE interacciones.interaccion_auditoria
    ADD CONSTRAINT interaccion_auditoria_operacion_check
        CHECK (operacion IN ('CREADA', 'MODIFICADA', 'SEGUIMIENTO'));

-- El comentario del cambio de seguimiento (obligatorio al reabrir).
ALTER TABLE interacciones.interaccion_auditoria ADD COLUMN comentario VARCHAR(500);
