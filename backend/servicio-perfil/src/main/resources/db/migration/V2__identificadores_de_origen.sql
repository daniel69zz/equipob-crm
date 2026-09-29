-- SCRUM-550 · Relación entre los identificadores de Marketplace y Ventas y el cliente del CRM (RF-62)
-- Modelo: docs/perfil/identificadores-origen.md

-- Cómo y quién vinculó cada identificador.
ALTER TABLE perfil.cliente_origen
    ADD COLUMN vinculado_por VARCHAR(100) NOT NULL DEFAULT 'sincronizacion-automatica',
    ADD CONSTRAINT ck_cliente_origen_motivo
        CHECK (motivo_vinculacion IN ('ALTA_AUTOMATICA', 'VINCULACION_MANUAL', 'UNIFICACION'));

-- Perfil absorbido en una unificación: apunta al perfil que se conserva.
ALTER TABLE perfil.cliente
    ADD COLUMN id_cliente_consolidado BIGINT REFERENCES perfil.cliente (id),
    ADD CONSTRAINT ck_cliente_no_consolidado_en_si_mismo CHECK (id_cliente_consolidado <> id);

-- Identificadores desconocidos que coinciden con un perfil existente y esperan la decisión de un administrador.
CREATE TABLE perfil.vinculacion_pendiente (
    origen              VARCHAR(15)  NOT NULL CHECK (origen IN ('MARKETPLACE', 'VENTAS')),
    id_cliente_origen   VARCHAR(64)  NOT NULL,
    id_cliente_sugerido BIGINT       REFERENCES perfil.cliente (id),
    motivo              VARCHAR(300) NOT NULL,
    estado              VARCHAR(15)  NOT NULL CHECK (estado IN ('PENDIENTE', 'VINCULADO', 'NUEVO_PERFIL')),
    detectada_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resuelta_por        VARCHAR(100),
    resuelta_en         TIMESTAMPTZ,
    CONSTRAINT pk_vinculacion_pendiente PRIMARY KEY (origen, id_cliente_origen)
);

CREATE INDEX idx_vinculacion_pendiente_estado ON perfil.vinculacion_pendiente (estado, detectada_en);

-- Los eventos de un identificador pendiente quedan en espera.
ALTER TABLE perfil.evento_cliente DROP CONSTRAINT evento_cliente_estado_check;
ALTER TABLE perfil.evento_cliente ADD CONSTRAINT evento_cliente_estado_check
    CHECK (estado IN ('RECIBIDO', 'PROCESADO', 'INCOMPLETO', 'PENDIENTE', 'DESCARTADO', 'FALLIDO'));

CREATE INDEX idx_evento_cliente_origen ON perfil.evento_cliente (origen, id_cliente_origen, estado);
