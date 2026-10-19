-- SCRUM-378 · Miembros de un segmento para la sugerencia de categorías afines (SCRUM-44).
-- Detalle en docs/preferencias/sugerencia-categorias.md. Es V6 porque V5 es el historial de segmentos (SCRUM-30).

CREATE INDEX IF NOT EXISTS idx_segmento_cliente_segmento ON segmentacion.segmento_cliente (segmento, cliente_id);
