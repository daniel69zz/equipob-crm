-- SCRUM-319 · Registro de las detecciones de cambio de comportamiento de compra (SCRUM-34, RF-26)
-- Criterios, umbrales y ventana: docs/compra/cambios-comportamiento.md

-- Una fila por variación relevante detectada. No se modifica ni se borra: es el registro auditable de
-- cada detección y, a la vez, la alerta que se muestra en la ficha del cliente.
CREATE TABLE comportamiento.deteccion_cambio_comportamiento (
    id                BIGSERIAL     PRIMARY KEY,
    id_cliente_origen VARCHAR(64)   NOT NULL,
    tipo              VARCHAR(12)   NOT NULL CHECK (tipo IN ('FRECUENCIA', 'MONTO')),
    direccion         VARCHAR(12)   NOT NULL CHECK (direccion IN ('AUMENTO', 'DISMINUCION')),
    valor_actual      NUMERIC(14,2) NOT NULL CHECK (valor_actual >= 0),
    valor_habitual    NUMERIC(14,2) NOT NULL CHECK (valor_habitual > 0),
    variacion         NUMERIC(10,4) NOT NULL,
    umbral            NUMERIC(5,4)  NOT NULL CHECK (umbral > 0 AND umbral <= 1),
    periodo_desde     TIMESTAMPTZ   NOT NULL,
    periodo_hasta     TIMESTAMPTZ   NOT NULL,
    detectado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (periodo_desde < periodo_hasta)
);

-- Para las alertas de un cliente, de la más reciente a la más antigua, y para no duplicar una
-- variación ya detectada en el periodo (mismo cliente, tipo y dirección).
CREATE INDEX idx_deteccion_cambio_cliente
    ON comportamiento.deteccion_cambio_comportamiento (id_cliente_origen, tipo, direccion, detectado_en DESC);

-- Resultado de la última evaluación de cada cliente con compras vigentes. Marca a los clientes
-- "sin línea base" (historial insuficiente), que no generan detecciones.
CREATE TABLE comportamiento.estado_comportamiento_cliente (
    id_cliente_origen  VARCHAR(64) PRIMARY KEY,
    estado             VARCHAR(16) NOT NULL CHECK (estado IN ('SIN_LINEA_BASE', 'ESTABLE', 'CON_VARIACION')),
    compras_linea_base INTEGER     NOT NULL CHECK (compras_linea_base >= 0),
    evaluado_en        TIMESTAMPTZ NOT NULL DEFAULT now()
);
