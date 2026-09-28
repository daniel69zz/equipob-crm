-- SCRUM-11 · Actualización automática del perfil ante cambios
-- Políticas: docs/perfil/mapeo-datos-perfil.md

-- Qué sistema puso cada campo del perfil y cuándo (fecha del cambio en ese sistema).
-- Sirve para detectar conflictos entre Marketplace y Ventas y resolverlos con la regla de prioridad.
CREATE TABLE perfil.campo_origen (
    id_cliente     BIGINT       NOT NULL REFERENCES perfil.cliente (id),
    campo          VARCHAR(40)  NOT NULL,
    origen         VARCHAR(15)  NOT NULL CHECK (origen IN ('MARKETPLACE', 'VENTAS', 'CRM')),
    actualizado_en TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_campo_origen PRIMARY KEY (id_cliente, campo)
);

-- Los perfiles existentes toman como origen de sus datos el sistema de su último cambio.
INSERT INTO perfil.campo_origen (id_cliente, campo, origen, actualizado_en)
SELECT c.id, v.campo, c.actualizado_por_origen,
       COALESCE((SELECT max(o.ultima_actualizacion_origen)
                 FROM perfil.cliente_origen o
                 WHERE o.id_cliente = c.id AND o.origen = c.actualizado_por_origen), c.actualizado_en)
FROM perfil.cliente c
CROSS JOIN LATERAL (VALUES ('nombres', c.nombres), ('apellidos', c.apellidos),
                           ('tipoDocumento', c.tipo_documento), ('numeroDocumento', c.numero_documento),
                           ('email', c.email), ('telefono', c.telefono)) AS v (campo, valor)
WHERE v.valor IS NOT NULL AND c.actualizado_por_origen IN ('MARKETPLACE', 'VENTAS');

-- Traza de cada conflicto entre sistemas y de la decisión tomada.
CREATE TABLE perfil.conflicto_perfil (
    id              BIGSERIAL    PRIMARY KEY,
    id_cliente      BIGINT       NOT NULL REFERENCES perfil.cliente (id),
    campo           VARCHAR(40)  NOT NULL,
    valor_actual    VARCHAR(200),
    origen_actual   VARCHAR(15)  NOT NULL,
    valor_recibido  VARCHAR(200),
    origen_recibido VARCHAR(15)  NOT NULL,
    decision        VARCHAR(12)  NOT NULL CHECK (decision IN ('APLICADO', 'CONSERVADO')),
    regla           VARCHAR(20)  NOT NULL CHECK (regla IN ('PRIORIDAD_SISTEMA', 'MAS_RECIENTE')),
    fecha           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    id_evento       BIGINT       REFERENCES perfil.evento_cliente (id)
);

CREATE INDEX idx_conflicto_perfil_cliente ON perfil.conflicto_perfil (id_cliente, fecha DESC);

-- Cantidad de intentos con que terminó cada mensaje (reintentos ante errores técnicos).
ALTER TABLE perfil.evento_cliente ADD COLUMN intentos INTEGER NOT NULL DEFAULT 1;
