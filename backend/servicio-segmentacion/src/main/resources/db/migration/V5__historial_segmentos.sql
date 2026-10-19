-- SCRUM-569. V4 está reservada por SCRUM-38 en main remoto.
-- El historial funcional enlaza la auditoría existente, sin duplicar eventos de auditoría.
CREATE TABLE segmentacion.historial_segmento (
    id BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL CHECK (cliente_id > 0),
    fecha TIMESTAMPTZ NOT NULL,
    operacion VARCHAR(60) NOT NULL CHECK (operacion IN
        ('SEGMENTO_ASIGNADO', 'SEGMENTO_CAMBIADO', 'SEGMENTO_RETIRADO')),
    segmento_anterior VARCHAR(100),
    segmento_nuevo VARCHAR(100),
    regla JSONB,
    criterios_evaluados JSONB,
    metricas JSONB NOT NULL,
    politica VARCHAR(100),
    motivo VARCHAR(100) NOT NULL,
    id_evento VARCHAR(64) NOT NULL,
    tipo_evento_origen VARCHAR(100),
    regla_completa BOOLEAN NOT NULL,
    auditoria_evento_id BIGINT NOT NULL UNIQUE REFERENCES auditoria.evento(id),
    CONSTRAINT historial_un_cambio_por_evento UNIQUE (cliente_id, id_evento)
);
CREATE INDEX idx_historial_segmento_cliente_fecha
    ON segmentacion.historial_segmento (cliente_id, fecha DESC, id DESC);

-- Los registros previos conservan exactamente la información disponible al cambiar.
-- No se consulta criterio: su versión actual no explica necesariamente un cambio pasado.
INSERT INTO segmentacion.historial_segmento
    (cliente_id, fecha, operacion, segmento_anterior, segmento_nuevo, regla, criterios_evaluados,
     metricas, politica, motivo, id_evento, tipo_evento_origen, regla_completa, auditoria_evento_id)
SELECT entidad_id::bigint, ocurrido_en, operacion, detalle::jsonb #>> '{anterior,segmento}',
       detalle::jsonb #>> '{nuevo,segmento}',
       COALESCE(detalle::jsonb -> 'regla', detalle::jsonb -> 'nuevo'),
       detalle::jsonb -> 'criteriosEvaluados', COALESCE(detalle::jsonb -> 'metricas', '{}'::jsonb),
       detalle::jsonb ->> 'politica',
       CASE WHEN operacion = 'SEGMENTO_RETIRADO' THEN 'SIN_CRITERIO_COINCIDENTE'
            ELSE 'CRITERIO_COINCIDENTE' END,
       detalle::jsonb ->> 'idEvento', detalle::jsonb ->> 'tipoEventoOrigen', false, id
FROM auditoria.evento
WHERE entidad = 'SEGMENTO_CLIENTE'
  AND operacion IN ('SEGMENTO_ASIGNADO', 'SEGMENTO_CAMBIADO', 'SEGMENTO_RETIRADO');

CREATE FUNCTION segmentacion.impedir_modificacion_historial() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'El historial de segmentos es de solo lectura';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER historial_segmento_solo_insercion
    BEFORE UPDATE OR DELETE ON segmentacion.historial_segmento
    FOR EACH ROW EXECUTE FUNCTION segmentacion.impedir_modificacion_historial();
CREATE TRIGGER historial_segmento_sin_truncate
    BEFORE TRUNCATE ON segmentacion.historial_segmento
    FOR EACH STATEMENT EXECUTE FUNCTION segmentacion.impedir_modificacion_historial();

COMMENT ON TABLE segmentacion.historial_segmento IS
    'SCRUM-30. Un cambio por evento y cliente, con regla histórica y protección de solo inserción.';
