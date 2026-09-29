-- SCRUM-154 · Saldo y nivel de puntos de cada cliente, consultados para la ficha integral (SCRUM-10)

CREATE SCHEMA IF NOT EXISTS fidelizacion;

CREATE TABLE fidelizacion.saldo_puntos (
    cliente_id     BIGINT       PRIMARY KEY,
    saldo          INTEGER      NOT NULL,
    nivel          VARCHAR(60)  NOT NULL,
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT now()
);
