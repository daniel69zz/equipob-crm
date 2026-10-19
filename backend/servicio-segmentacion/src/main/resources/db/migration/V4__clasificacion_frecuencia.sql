-- SCRUM-340 · La clasificación por frecuencia (SCRUM-38) como indicador de los criterios de segmentación.
-- El nombre del indicador nuevo (CLASIFICACION_FRECUENCIA) necesita ampliar la columna indicador.
-- Detalle en docs/compra/clasificacion-frecuencia.md.

ALTER TABLE segmentacion.criterio
    DROP CONSTRAINT criterio_indicador_check,
    DROP CONSTRAINT criterio_forma_valida,
    ALTER COLUMN indicador TYPE VARCHAR(30),
    ADD COLUMN clasificacion VARCHAR(20) CHECK (clasificacion IN ('OCASIONAL', 'HABITUAL', 'FRECUENTE'));

ALTER TABLE segmentacion.criterio
    ADD CONSTRAINT criterio_indicador_check
        CHECK (indicador IN ('FRECUENCIA', 'VALOR', 'CATEGORIA', 'CLASIFICACION_FRECUENCIA')),
    ADD CONSTRAINT criterio_forma_valida CHECK (
        (indicador = 'CATEGORIA' AND condicion = 'CONTIENE'
            AND categoria IS NOT NULL AND btrim(categoria) <> '' AND categoria_clave IS NOT NULL
            AND minimo IS NULL AND maximo IS NULL AND clasificacion IS NULL)
        OR (indicador = 'CLASIFICACION_FRECUENCIA' AND condicion = 'IGUAL' AND clasificacion IS NOT NULL
            AND minimo IS NULL AND maximo IS NULL AND categoria IS NULL AND categoria_clave IS NULL)
        OR (indicador IN ('FRECUENCIA', 'VALOR') AND categoria IS NULL AND categoria_clave IS NULL
            AND clasificacion IS NULL AND (
            (condicion = 'ENTRE' AND minimo IS NOT NULL AND maximo IS NOT NULL)
            OR (condicion = 'IGUAL' AND minimo IS NOT NULL AND maximo = minimo)
            OR (condicion = 'MAYOR_IGUAL' AND minimo IS NOT NULL AND maximo IS NULL)
            OR (condicion = 'MENOR_IGUAL' AND minimo IS NULL AND maximo IS NOT NULL))));

ALTER TABLE segmentacion.segmento_cliente
    DROP CONSTRAINT segmento_cliente_indicador_check,
    ALTER COLUMN indicador TYPE VARCHAR(30),
    ADD CONSTRAINT segmento_cliente_indicador_check
        CHECK (indicador IN ('FRECUENCIA', 'VALOR', 'CATEGORIA', 'CLASIFICACION_FRECUENCIA'));

-- Clasificación que publica servicio-comportamiento con las métricas; NULL sin compras vigentes o
-- en métricas guardadas antes de esta historia.
ALTER TABLE segmentacion.metricas_cliente
    ADD COLUMN clasificacion_frecuencia VARCHAR(20)
        CHECK (clasificacion_frecuencia IN ('OCASIONAL', 'HABITUAL', 'FRECUENTE'));
