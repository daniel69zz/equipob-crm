-- SCRUM-15 · Historial de compras y vinculación al perfil del cliente.
-- Los registros anteriores quedan pendientes, conservando sus identificadores de origen.
ALTER TABLE comportamiento.compra
    ADD COLUMN id_cliente BIGINT,
    ADD COLUMN estado_vinculacion VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE',
    ADD CONSTRAINT compra_vinculacion_check CHECK (
        (estado_vinculacion = 'PENDIENTE' AND id_cliente IS NULL)
        OR (estado_vinculacion = 'VINCULADA' AND id_cliente IS NOT NULL AND id_cliente > 0)
    ),
    ADD CONSTRAINT compra_monto_positivo CHECK (monto_total > 0),
    ADD CONSTRAINT compra_identificador_no_vacio CHECK (id_compra_origen ~ '[^[:space:]]'),
    ADD CONSTRAINT compra_cliente_no_vacio CHECK (id_cliente_origen ~ '[^[:space:]]');

ALTER TABLE comportamiento.compra_item
    ADD CONSTRAINT compra_item_categoria_no_vacia CHECK (categoria ~ '[^[:space:]]'),
    ADD CONSTRAINT compra_item_cantidad_positiva CHECK (cantidad > 0),
    ADD CONSTRAINT compra_item_monto_positivo CHECK (monto > 0);

CREATE INDEX idx_compra_perfil_fecha ON comportamiento.compra (id_cliente, fecha DESC, id DESC);
CREATE INDEX idx_compra_vinculacion_pendiente ON comportamiento.compra (origen, id_cliente_origen)
    WHERE estado_vinculacion = 'PENDIENTE';
