-- SCRUM-571 · Riesgo de abandono de cada cliente (SCRUM-35, RF-27)
-- Fórmula, ponderaciones y niveles: docs/compra/riesgo-abandono.md

-- Una fila por cliente del CRM con compras vigentes. Se sobrescribe en cada recálculo y se elimina
-- cuando el cliente se queda sin compras vigentes. Guarda los datos de entrada y los puntos de cada
-- factor para poder explicar el nivel en la ficha sin recalcularlo.
CREATE TABLE comportamiento.riesgo_abandono_cliente (
    cliente_id               BIGINT        PRIMARY KEY,
    puntaje                  NUMERIC(5,2)  NOT NULL CHECK (puntaje BETWEEN 0 AND 100),
    nivel                    VARCHAR(5)    NOT NULL CHECK (nivel IN ('BAJO', 'MEDIO', 'ALTO')),
    dias_sin_comprar         INTEGER       NOT NULL CHECK (dias_sin_comprar >= 0),
    ultima_compra            TIMESTAMPTZ   NOT NULL,
    compras                  BIGINT        NOT NULL CHECK (compras > 0),
    valor_acumulado          NUMERIC(14,2) NOT NULL CHECK (valor_acumulado >= 0),
    clasificacion_frecuencia VARCHAR(10)   NOT NULL CHECK (clasificacion_frecuencia IN ('OCASIONAL', 'HABITUAL', 'FRECUENTE')),
    nivel_consumo            VARCHAR(5)    NOT NULL CHECK (nivel_consumo IN ('BAJO', 'MEDIO', 'ALTO')),
    inactivo                 BOOLEAN       NOT NULL,
    cambio_detectado         BOOLEAN       NOT NULL,
    puntos_recencia          NUMERIC(5,2)  NOT NULL CHECK (puntos_recencia >= 0),
    puntos_frecuencia        NUMERIC(5,2)  NOT NULL CHECK (puntos_frecuencia >= 0),
    puntos_valor             NUMERIC(5,2)  NOT NULL CHECK (puntos_valor >= 0),
    puntos_cambio            NUMERIC(5,2)  NOT NULL CHECK (puntos_cambio >= 0),
    -- SCRUM-326: desde cuándo está en riesgo ALTO, para el seguimiento comercial; nulo en otro nivel.
    en_seguimiento_desde     TIMESTAMPTZ,
    calculado_en             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK ((nivel = 'ALTO') = (en_seguimiento_desde IS NOT NULL))
);

-- Listado de clientes por nivel de riesgo, del puntaje más alto al más bajo.
CREATE INDEX idx_riesgo_abandono_nivel ON comportamiento.riesgo_abandono_cliente (nivel, puntaje DESC, cliente_id);

COMMENT ON TABLE comportamiento.riesgo_abandono_cliente IS
    'SCRUM-35. Riesgo de abandono vigente por cliente del CRM; detalle en docs/compra/riesgo-abandono.md';
