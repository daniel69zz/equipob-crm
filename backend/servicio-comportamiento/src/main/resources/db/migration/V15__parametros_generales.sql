-- SCRUM-519: parámetros generales del CRM que son de servicio-comportamiento (SCRUM-65). Cada fila guarda el valor
-- vigente y su versión, que evita que un cambio pise otro hecho al mismo tiempo. Los cambios quedan en la auditoría
-- compartida (auditoria.evento) con el valor anterior y el nuevo. Las filas las crea el servicio al arrancar con
-- los valores de su configuración, así que un despliegue existente conserva los suyos.
CREATE TABLE comportamiento.parametro_general (
    codigo          VARCHAR(60)    PRIMARY KEY,
    valor           NUMERIC(14, 2) NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 1 CHECK (version > 0),
    modificado_por  VARCHAR(100)   NOT NULL,
    modificado_en   TIMESTAMPTZ    NOT NULL DEFAULT now()
);
