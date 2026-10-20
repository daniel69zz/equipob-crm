-- SCRUM-369 / SCRUM-368: actualización del perfil de preferencias por eventos de venta (SCRUM-42).
-- Detalle en docs/preferencias/actualizacion-por-eventos.md.

-- Qué originó la versión vigente del perfil: la construcción manual de SCRUM-41 o un evento de venta.
ALTER TABLE comportamiento.perfil_preferencias
    ADD COLUMN origen VARCHAR(30) NOT NULL DEFAULT 'CONSTRUCCION_MANUAL'
        CHECK (origen IN ('CONSTRUCCION_MANUAL', 'COMPRA_CONFIRMADA', 'COMPRA_ANULADA')),
    ADD COLUMN id_evento_origen VARCHAR(64);

-- Una fila por evento de venta aplicado al perfil: trazabilidad (fecha, evento, versiones y
-- categorías antes y después) e idempotencia (un evento reentregado no actualiza dos veces).
CREATE TABLE comportamiento.actualizacion_preferencias (
    id_evento             VARCHAR(64)  PRIMARY KEY,
    cliente_id            BIGINT       NOT NULL CHECK (cliente_id > 0),
    tipo_evento_origen    VARCHAR(30)  NOT NULL CHECK (tipo_evento_origen IN ('COMPRA_CONFIRMADA', 'COMPRA_ANULADA')),
    version_anterior      BIGINT       NOT NULL CHECK (version_anterior >= 0),
    version_nueva         BIGINT       NOT NULL CHECK (version_nueva > version_anterior),
    categorias_anteriores TEXT[]       NOT NULL,
    categorias_nuevas     TEXT[]       NOT NULL,
    actualizado_en        TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_actualizacion_preferencias_cliente
    ON comportamiento.actualizacion_preferencias (cliente_id, actualizado_en DESC);
