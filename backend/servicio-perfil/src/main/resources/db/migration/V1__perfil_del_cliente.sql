-- SCRUM-142 · Modelo de datos del perfil del cliente
-- Documentación: docs/perfil/modelo-datos-perfil.md
-- Contrato del evento: docs/contratos-eventos/RIO-CRM-01-datos-cliente.md

CREATE SCHEMA IF NOT EXISTS perfil;

-- Bitácora de sincronización: cada mensaje recibido, con su contenido original y su estado.
CREATE TABLE perfil.evento_cliente (
    id                BIGSERIAL    PRIMARY KEY,
    id_evento_origen  VARCHAR(64),
    tipo_evento       VARCHAR(40),
    origen            VARCHAR(15),
    id_cliente_origen VARCHAR(64),
    id_cliente        BIGINT,
    estado            VARCHAR(15)  NOT NULL
        CHECK (estado IN ('RECIBIDO', 'PROCESADO', 'INCOMPLETO', 'DESCARTADO', 'FALLIDO')),
    causa             VARCHAR(1000),
    contenido         TEXT         NOT NULL,
    recibido_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    procesado_en      TIMESTAMPTZ
);

CREATE INDEX idx_evento_cliente_fecha   ON perfil.evento_cliente (recibido_en DESC);
CREATE INDEX idx_evento_cliente_estado  ON perfil.evento_cliente (estado, recibido_en DESC);
CREATE INDEX idx_evento_cliente_cliente ON perfil.evento_cliente (id_cliente, recibido_en DESC);

-- Perfil único del cliente.
CREATE TABLE perfil.cliente (
    id                     BIGSERIAL     PRIMARY KEY,
    nombres                VARCHAR(100),
    apellidos              VARCHAR(100),
    tipo_documento         VARCHAR(15)   CHECK (tipo_documento IN ('CI', 'NIT', 'PASAPORTE', 'CE')),
    numero_documento       VARCHAR(20),
    email                  VARCHAR(150),
    telefono               VARCHAR(30),
    estado                 VARCHAR(12)   NOT NULL CHECK (estado IN ('COMPLETO', 'INCOMPLETO')),
    motivos_incompleto     VARCHAR(1000),
    creado_en              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_por_origen VARCHAR(15),
    actualizado_por        VARCHAR(100)
);

CREATE INDEX idx_cliente_documento ON perfil.cliente (tipo_documento, numero_documento);
CREATE INDEX idx_cliente_estado    ON perfil.cliente (estado);

-- Identificadores del cliente en Marketplace y Ventas (RF-62). Varios pueden apuntar al mismo perfil.
CREATE TABLE perfil.cliente_origen (
    origen                      VARCHAR(15)  NOT NULL CHECK (origen IN ('MARKETPLACE', 'VENTAS')),
    id_cliente_origen           VARCHAR(64)  NOT NULL,
    id_cliente                  BIGINT       NOT NULL REFERENCES perfil.cliente (id),
    fecha_vinculacion           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    motivo_vinculacion          VARCHAR(30)  NOT NULL DEFAULT 'ALTA_AUTOMATICA',
    ultima_actualizacion_origen TIMESTAMPTZ,
    CONSTRAINT pk_cliente_origen PRIMARY KEY (origen, id_cliente_origen)
);

CREATE INDEX idx_cliente_origen_cliente ON perfil.cliente_origen (id_cliente);

-- Direcciones del cliente, identificadas por su código en el sistema de origen.
CREATE TABLE perfil.direccion (
    id                  BIGSERIAL    PRIMARY KEY,
    id_cliente          BIGINT       NOT NULL REFERENCES perfil.cliente (id),
    origen              VARCHAR(15)  NOT NULL,
    id_direccion_origen VARCHAR(64)  NOT NULL,
    tipo                VARCHAR(15)  NOT NULL CHECK (tipo IN ('ENTREGA', 'FACTURACION', 'OTRA')),
    calle               VARCHAR(200) NOT NULL,
    numero              VARCHAR(20),
    zona                VARCHAR(100),
    ciudad              VARCHAR(100) NOT NULL,
    referencia          VARCHAR(300),
    principal           BOOLEAN      NOT NULL DEFAULT false,
    activa              BOOLEAN      NOT NULL DEFAULT true,
    actualizada_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_direccion_origen UNIQUE (id_cliente, origen, id_direccion_origen)
);

-- Registro de cambios del perfil: fecha, sistema de origen, responsable y campos modificados.
CREATE TABLE perfil.cambio_perfil (
    id          BIGSERIAL    PRIMARY KEY,
    id_cliente  BIGINT       NOT NULL REFERENCES perfil.cliente (id),
    fecha       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    tipo        VARCHAR(15)  NOT NULL CHECK (tipo IN ('CREACION', 'ACTUALIZACION')),
    origen      VARCHAR(15)  NOT NULL,
    responsable VARCHAR(100) NOT NULL,
    cambios     TEXT         NOT NULL,
    id_evento   BIGINT       REFERENCES perfil.evento_cliente (id)
);

CREATE INDEX idx_cambio_perfil_cliente ON perfil.cambio_perfil (id_cliente, fecha DESC);

-- El registro de cambios es de solo inserción.
CREATE OR REPLACE FUNCTION perfil.impedir_modificacion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'El registro de cambios del perfil es de solo lectura';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER cambio_perfil_solo_lectura
    BEFORE UPDATE OR DELETE ON perfil.cambio_perfil
    FOR EACH ROW EXECUTE FUNCTION perfil.impedir_modificacion();

CREATE TRIGGER cambio_perfil_sin_truncate
    BEFORE TRUNCATE ON perfil.cambio_perfil
    FOR EACH STATEMENT EXECUTE FUNCTION perfil.impedir_modificacion();
