-- SCRUM-46 / SCRUM-393 · Reglas de acumulación de puntos configurables por el Administrador de CRM.
-- Detalle en docs/fidelizacion/reglas-acumulacion.md.

-- Cada cambio de la regla es una versión nueva: la vigente es la de mayor número y las anteriores
-- quedan como historial (quién la cambió, cuándo y por qué). Las versiones no se modifican ni se borran.
CREATE TABLE fidelizacion.regla_acumulacion (
    version               BIGINT         PRIMARY KEY CHECK (version > 0),
    bolivianos_por_punto  NUMERIC(14,2)  NOT NULL CHECK (bolivianos_por_punto > 0),
    monto_minimo          NUMERIC(14,2)  NOT NULL CHECK (monto_minimo >= 0),
    motivo                VARCHAR(250),
    modificado_por        VARCHAR(100)   NOT NULL,
    vigente_desde         TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Regla inicial: la misma de SCRUM-45 (1 punto por cada Bs 10, sin monto mínimo).
INSERT INTO fidelizacion.regla_acumulacion (version, bolivianos_por_punto, monto_minimo, motivo, modificado_por)
VALUES (1, 10.00, 0.00, 'Regla inicial de la acumulación de puntos (SCRUM-45)', 'sistema');

-- Cada movimiento guarda la versión de la regla con la que se calculó: un cambio posterior de la regla
-- no recalcula los puntos ya otorgados (criterio de aceptación 1). Los movimientos anteriores a esta
-- migración se calcularon con la regla inicial.
ALTER TABLE fidelizacion.movimiento_puntos
    ADD COLUMN version_regla BIGINT REFERENCES fidelizacion.regla_acumulacion (version),
    ADD COLUMN monto_minimo  NUMERIC(14,2) CHECK (monto_minimo >= 0);

UPDATE fidelizacion.movimiento_puntos SET version_regla = 1, monto_minimo = 0.00;

ALTER TABLE fidelizacion.movimiento_puntos
    ALTER COLUMN version_regla SET NOT NULL,
    ALTER COLUMN monto_minimo SET NOT NULL;
