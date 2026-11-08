-- SCRUM-49 · Niveles de fidelización (bronce, plata y oro) con sus umbrales de puntos.
-- Reglas y umbrales en docs/fidelizacion/niveles-fidelizacion.md.

-- Cada cambio de la configuración es una versión nueva: la vigente es la de mayor número y las
-- anteriores quedan como historial. Las versiones no se modifican ni se borran.
CREATE TABLE fidelizacion.configuracion_niveles (
    version         BIGINT        PRIMARY KEY CHECK (version > 0),
    -- Periodo de evaluación (SCRUM-409): el nivel se calcula con los puntos acumulados en los últimos N meses.
    periodo_meses   INTEGER       NOT NULL CHECK (periodo_meses BETWEEN 1 AND 120),
    motivo          VARCHAR(250),
    modificado_por  VARCHAR(100)  NOT NULL,
    vigente_desde   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Umbrales de cada nivel en una versión. Rango inclusivo [puntos_minimos, puntos_maximos]; el nivel más
-- alto no tiene máximo. La aplicación valida que los rangos no se superpongan ni dejen huecos.
CREATE TABLE fidelizacion.umbral_nivel (
    version         BIGINT       NOT NULL REFERENCES fidelizacion.configuracion_niveles (version),
    nivel           VARCHAR(20)  NOT NULL CHECK (nivel IN ('BRONCE', 'PLATA', 'ORO')),
    puntos_minimos  BIGINT       NOT NULL CHECK (puntos_minimos >= 0),
    puntos_maximos  BIGINT       CHECK (puntos_maximos IS NULL OR puntos_maximos >= puntos_minimos),
    PRIMARY KEY (version, nivel)
);

-- Configuración inicial (SCRUM-410): 12 meses; Bronce 0–499, Plata 500–1.999, Oro desde 2.000 puntos.
INSERT INTO fidelizacion.configuracion_niveles (version, periodo_meses, motivo, modificado_por)
VALUES (1, 12, 'Niveles iniciales del programa de fidelización (SCRUM-49)', 'sistema');
INSERT INTO fidelizacion.umbral_nivel (version, nivel, puntos_minimos, puntos_maximos) VALUES
    (1, 'BRONCE', 0, 499),
    (1, 'PLATA', 500, 1999),
    (1, 'ORO', 2000, NULL);

-- Puntos del periodo con los que se asignó el nivel y fecha del último cambio de nivel.
ALTER TABLE fidelizacion.saldo_puntos
    ADD COLUMN puntos_periodo        BIGINT NOT NULL DEFAULT 0 CHECK (puntos_periodo >= 0),
    ADD COLUMN nivel_actualizado_en  TIMESTAMPTZ;

-- Suma de los puntos de un cliente dentro del periodo de evaluación.
CREATE INDEX idx_movimiento_puntos_cliente_fecha ON fidelizacion.movimiento_puntos (cliente_id, fecha_transaccion);

-- Asigna el nivel a los clientes que ya tienen saldo (misma consulta que AsignacionNiveles).
WITH config AS (
    SELECT version, periodo_meses FROM fidelizacion.configuracion_niveles ORDER BY version DESC LIMIT 1
), periodo AS (
    SELECT s.cliente_id,
           GREATEST(0, COALESCE(SUM(CASE m.tipo_movimiento WHEN 'ACUMULACION' THEN m.puntos
                                                           WHEN 'REVERSA' THEN -m.puntos ELSE 0 END), 0)) AS puntos
    FROM fidelizacion.saldo_puntos s
    CROSS JOIN config c
    LEFT JOIN fidelizacion.movimiento_puntos m
           ON m.cliente_id = s.cliente_id AND m.fecha_transaccion >= now() - make_interval(months => c.periodo_meses)
    GROUP BY s.cliente_id
), calculado AS (
    SELECT p.cliente_id, p.puntos, u.nivel
    FROM periodo p
    CROSS JOIN config c
    LEFT JOIN fidelizacion.umbral_nivel u
           ON u.version = c.version AND p.puntos >= u.puntos_minimos
          AND (u.puntos_maximos IS NULL OR p.puntos <= u.puntos_maximos)
)
UPDATE fidelizacion.saldo_puntos s
SET puntos_periodo = k.puntos, nivel = k.nivel, nivel_actualizado_en = now()
FROM calculado k
WHERE s.cliente_id = k.cliente_id;

ALTER TABLE fidelizacion.saldo_puntos
    ADD CONSTRAINT saldo_puntos_nivel_valido CHECK (nivel IS NULL OR nivel IN ('BRONCE', 'PLATA', 'ORO'));
