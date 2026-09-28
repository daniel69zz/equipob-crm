-- SCRUM-232 · Persistencia del histórico de cambios del cliente
-- Modelo: docs/perfil/historico-cambios.md

-- Una fila por campo modificado en cada operación registrada en cambio_perfil.
CREATE TABLE perfil.cambio_perfil_detalle (
    id             BIGSERIAL    PRIMARY KEY,
    id_cambio      BIGINT       NOT NULL REFERENCES perfil.cambio_perfil (id),
    orden          INTEGER      NOT NULL,
    campo          VARCHAR(100) NOT NULL,
    valor_anterior TEXT,
    valor_nuevo    TEXT,
    CONSTRAINT uq_cambio_perfil_detalle UNIQUE (id_cambio, orden)
);

CREATE INDEX idx_cambio_perfil_detalle_campo ON perfil.cambio_perfil_detalle (campo);

-- El historial ya registrado pasa a filas consultables.
INSERT INTO perfil.cambio_perfil_detalle (id_cambio, orden, campo, valor_anterior, valor_nuevo)
SELECT c.id, e.orden, e.valor ->> 'campo', e.valor ->> 'anterior', e.valor ->> 'nuevo'
FROM perfil.cambio_perfil c
CROSS JOIN LATERAL jsonb_array_elements(c.cambios::jsonb) WITH ORDINALITY AS e (valor, orden);

-- El detalle también es de solo lectura.
CREATE TRIGGER cambio_perfil_detalle_solo_lectura
    BEFORE UPDATE OR DELETE ON perfil.cambio_perfil_detalle
    FOR EACH ROW EXECUTE FUNCTION perfil.impedir_modificacion();

CREATE TRIGGER cambio_perfil_detalle_sin_truncate
    BEFORE TRUNCATE ON perfil.cambio_perfil_detalle
    FOR EACH STATEMENT EXECUTE FUNCTION perfil.impedir_modificacion();

-- Las acciones de un administrador se distinguen por su tipo, además de por el origen CRM.
ALTER TABLE perfil.cambio_perfil DROP CONSTRAINT cambio_perfil_tipo_check;
ALTER TABLE perfil.cambio_perfil ADD CONSTRAINT cambio_perfil_tipo_check
    CHECK (tipo IN ('CREACION', 'ACTUALIZACION', 'VINCULACION', 'UNIFICACION'));

CREATE INDEX idx_cambio_perfil_origen ON perfil.cambio_perfil (id_cliente, origen, fecha DESC);
