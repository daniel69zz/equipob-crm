-- SCRUM-432 · Consultas, reclamos y notas de atención de cada cliente (SCRUM-52).
-- Modelo en docs/interacciones/registro-interacciones.md (SCRUM-433).

CREATE SCHEMA IF NOT EXISTS interacciones;

CREATE TABLE interacciones.interaccion (
    id                 BIGSERIAL     PRIMARY KEY,
    cliente_id         BIGINT        NOT NULL CHECK (cliente_id > 0),
    tipo               VARCHAR(20)   NOT NULL CHECK (tipo IN ('CONSULTA', 'RECLAMO', 'NOTA')),
    canal              VARCHAR(20)   CHECK (canal IN ('TELEFONO', 'CORREO', 'PRESENCIAL', 'CHAT', 'WHATSAPP', 'OTRO')),
    asunto             VARCHAR(120),
    descripcion        TEXT          NOT NULL CHECK (char_length(btrim(descripcion)) BETWEEN 1 AND 4000),
    referencia_compra  VARCHAR(64),
    registrado_por     VARCHAR(100)  NOT NULL,
    registrado_rol     VARCHAR(60),
    registrado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    modificado_por     VARCHAR(100),
    modificado_en      TIMESTAMPTZ,
    -- Sube con cada cambio: una edición hecha sobre una versión anterior se rechaza.
    version            BIGINT        NOT NULL DEFAULT 1 CHECK (version > 0),
    CHECK ((modificado_por IS NULL) = (modificado_en IS NULL))
);

-- Historial de un cliente, del más reciente al más antiguo, y filtro por tipo.
CREATE INDEX idx_interaccion_cliente ON interacciones.interaccion (cliente_id, registrado_en DESC, id DESC);
CREATE INDEX idx_interaccion_tipo ON interacciones.interaccion (cliente_id, tipo);
