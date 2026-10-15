-- SCRUM-277 · Listado de clientes por segmento o nivel de consumo (SCRUM-28).
-- Detalle en docs/segmentacion/criterio-consulta-clientes.md.

-- Nivel de consumo y última compra vigente que publica servicio-comportamiento con las métricas del
-- cliente. Quedan nulos si no tiene compras vigentes o si su última foto llegó antes que estos campos.
ALTER TABLE segmentacion.metricas_cliente
    ADD COLUMN nivel_consumo VARCHAR(10) CHECK (nivel_consumo IN ('BAJO', 'MEDIO', 'ALTO')),
    ADD COLUMN ultima_compra TIMESTAMPTZ;

-- El listado filtra por segmento y por nivel de consumo.
CREATE INDEX idx_segmento_cliente_segmento ON segmentacion.segmento_cliente (segmento);
CREATE INDEX idx_metricas_cliente_nivel_consumo ON segmentacion.metricas_cliente (nivel_consumo);

COMMENT ON COLUMN segmentacion.metricas_cliente.nivel_consumo IS
    'SCRUM-277. Nivel de consumo calculado por servicio-comportamiento (SCRUM-27).';
COMMENT ON COLUMN segmentacion.metricas_cliente.ultima_compra IS
    'SCRUM-277. Compra vigente más reciente del cliente (SCRUM-19).';
