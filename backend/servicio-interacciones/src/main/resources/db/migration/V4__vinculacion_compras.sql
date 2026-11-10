-- SCRUM-445 · Compra del historial del cliente vinculada a una interacción (SCRUM-54).
-- Regla de asociación en docs/interacciones/vinculacion-compras.md (SCRUM-443).

-- Como máximo una compra por interacción. Se guardan los datos de la compra tal como estaban al vincularla, para
-- mostrarla sin volver a consultar servicio-comportamiento.
ALTER TABLE interacciones.interaccion
    ADD COLUMN compra_referencia     VARCHAR(64),
    ADD COLUMN compra_fecha          TIMESTAMPTZ,
    ADD COLUMN compra_monto_total    NUMERIC(14, 2),
    ADD COLUMN compra_estado         VARCHAR(30),
    ADD COLUMN compra_vinculada_por  VARCHAR(100),
    ADD COLUMN compra_vinculada_en   TIMESTAMPTZ,
    -- Sin compra, ninguno de sus datos; con compra, siempre quién la vinculó y cuándo.
    ADD CONSTRAINT interaccion_compra_completa CHECK (
        (compra_referencia IS NULL AND compra_fecha IS NULL AND compra_monto_total IS NULL
            AND compra_estado IS NULL AND compra_vinculada_por IS NULL AND compra_vinculada_en IS NULL)
        OR (compra_referencia IS NOT NULL AND compra_vinculada_por IS NOT NULL AND compra_vinculada_en IS NOT NULL));

-- Interacciones de un cliente vinculadas a una compra.
CREATE INDEX idx_interaccion_compra ON interacciones.interaccion (cliente_id, compra_referencia)
    WHERE compra_referencia IS NOT NULL;

-- Cada vinculación y desvinculación queda en la auditoría de la interacción (SCRUM-448).
ALTER TABLE interacciones.interaccion_auditoria DROP CONSTRAINT interaccion_auditoria_operacion_check;
ALTER TABLE interacciones.interaccion_auditoria
    ADD CONSTRAINT interaccion_auditoria_operacion_check
        CHECK (operacion IN ('CREADA', 'MODIFICADA', 'SEGUIMIENTO', 'COMPRA_VINCULADA', 'COMPRA_DESVINCULADA'));
