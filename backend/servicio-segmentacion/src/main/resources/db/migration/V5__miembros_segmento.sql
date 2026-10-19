-- SCRUM-378 · Miembros de un segmento para la sugerencia de categorías afines (SCRUM-44).
-- Detalle en docs/preferencias/sugerencia-categorias.md.

CREATE INDEX idx_segmento_cliente_segmento ON segmentacion.segmento_cliente (segmento, cliente_id);
