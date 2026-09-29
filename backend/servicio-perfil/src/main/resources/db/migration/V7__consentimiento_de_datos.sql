-- SCRUM-183 · Consentimiento de tratamiento de datos y su alcance por cliente
-- Modelo: docs/perfil/consentimiento-datos.md

-- Estado actual del consentimiento: una fila por cliente.
CREATE TABLE perfil.consentimiento (
    id_cliente         BIGINT       PRIMARY KEY REFERENCES perfil.cliente (id),
    estado             VARCHAR(10)  NOT NULL CHECK (estado IN ('OTORGADO', 'REVOCADO')),
    canal              VARCHAR(15)  NOT NULL
        CHECK (canal IN ('MARKETPLACE', 'VENTAS', 'PRESENCIAL', 'TELEFONICO', 'CORREO')),
    fecha_otorgamiento TIMESTAMPTZ  NOT NULL,
    vigencia_desde     DATE         NOT NULL,
    vigencia_hasta     DATE,
    fecha_revocacion   TIMESTAMPTZ,
    motivo_revocacion  VARCHAR(300),
    actualizado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_por    VARCHAR(100) NOT NULL,
    CONSTRAINT ck_consentimiento_vigencia CHECK (vigencia_hasta IS NULL OR vigencia_hasta >= vigencia_desde),
    CONSTRAINT ck_consentimiento_revocacion CHECK ((estado = 'REVOCADO') = (fecha_revocacion IS NOT NULL))
);

CREATE INDEX idx_consentimiento_estado ON perfil.consentimiento (estado, vigencia_hasta);

-- Finalidades para las que el cliente autorizó el uso de sus datos.
CREATE TABLE perfil.consentimiento_alcance (
    id_cliente BIGINT      NOT NULL REFERENCES perfil.consentimiento (id_cliente),
    alcance    VARCHAR(30) NOT NULL CHECK (alcance IN ('GESTION_CLIENTE', 'ANALISIS_COMPORTAMIENTO', 'SEGMENTACION',
                                                       'FIDELIZACION', 'COMUNICACIONES_COMERCIALES')),
    CONSTRAINT pk_consentimiento_alcance PRIMARY KEY (id_cliente, alcance)
);
