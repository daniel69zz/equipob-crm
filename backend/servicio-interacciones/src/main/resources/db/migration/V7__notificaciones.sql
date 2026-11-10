-- SCRUM-459 · Notificación al agente cuando se le asigna una interacción (SCRUM-56, criterio de aceptación 1).
-- Reglas en docs/interacciones/asignacion-interacciones.md.

CREATE TABLE interacciones.notificacion (
    id              BIGSERIAL     PRIMARY KEY,
    destinatario    VARCHAR(100)  NOT NULL,
    tipo            VARCHAR(30)   NOT NULL CHECK (tipo IN ('INTERACCION_ASIGNADA')),
    interaccion_id  BIGINT        NOT NULL REFERENCES interacciones.interaccion (id),
    cliente_id      BIGINT        NOT NULL CHECK (cliente_id > 0),
    mensaje         VARCHAR(200)  NOT NULL,
    comentario      VARCHAR(500),
    creada_por      VARCHAR(100)  NOT NULL,
    creada_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    leida_en        TIMESTAMPTZ
);

-- Las notificaciones de un usuario, de la más reciente a la más antigua, y las no leídas.
CREATE INDEX idx_notificacion_destinatario ON interacciones.notificacion (destinatario, id DESC);
CREATE INDEX idx_notificacion_no_leida ON interacciones.notificacion (destinatario) WHERE leida_en IS NULL;
