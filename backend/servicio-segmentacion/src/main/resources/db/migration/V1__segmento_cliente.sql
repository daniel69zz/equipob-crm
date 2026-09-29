-- SCRUM-149 · Segmento asignado a cada cliente, consultado para la ficha integral (SCRUM-10)

CREATE SCHEMA IF NOT EXISTS segmentacion;

CREATE TABLE segmentacion.segmento_cliente (
    cliente_id     BIGINT       PRIMARY KEY,
    segmento       VARCHAR(60)  NOT NULL,
    asignado_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
