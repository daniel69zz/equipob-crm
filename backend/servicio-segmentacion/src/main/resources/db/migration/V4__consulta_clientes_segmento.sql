-- SCRUM-28: localizar asignaciones por identificador estable o por nombre anterior.
CREATE INDEX idx_segmento_cliente_criterio ON segmentacion.segmento_cliente (criterio_id, cliente_id);
CREATE INDEX idx_segmento_cliente_legado ON segmentacion.segmento_cliente (segmento, cliente_id)
    WHERE criterio_id IS NULL;
