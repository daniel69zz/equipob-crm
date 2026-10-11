-- SCRUM-25: criterios configurables. Indicadores acumulados del Sprint 1.
CREATE TABLE segmentacion.criterio (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL CHECK (btrim(nombre) <> ''),
    nombre_clave VARCHAR(100) NOT NULL UNIQUE,
    descripcion VARCHAR(500) NOT NULL DEFAULT '',
    indicador VARCHAR(20) NOT NULL CHECK (indicador IN ('FRECUENCIA', 'VALOR', 'CATEGORIA')),
    condicion VARCHAR(20) NOT NULL CHECK (condicion IN ('ENTRE', 'MAYOR_IGUAL', 'MENOR_IGUAL', 'IGUAL', 'CONTIENE')),
    minimo NUMERIC(14,2),
    maximo NUMERIC(14,2),
    categoria VARCHAR(100),
    categoria_clave VARCHAR(100),
    activo BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_por VARCHAR(100) NOT NULL,
    CONSTRAINT criterio_rango_valido CHECK (
        (minimo IS NULL OR minimo >= 0) AND (maximo IS NULL OR maximo >= 0)
        AND (minimo IS NULL OR maximo IS NULL OR minimo <= maximo)),
    CONSTRAINT criterio_forma_valida CHECK (
        (indicador = 'CATEGORIA' AND condicion = 'CONTIENE'
            AND categoria IS NOT NULL AND btrim(categoria) <> '' AND categoria_clave IS NOT NULL
            AND minimo IS NULL AND maximo IS NULL)
        OR (indicador IN ('FRECUENCIA', 'VALOR') AND categoria IS NULL AND categoria_clave IS NULL AND (
            (condicion = 'ENTRE' AND minimo IS NOT NULL AND maximo IS NOT NULL)
            OR (condicion = 'IGUAL' AND minimo IS NOT NULL AND maximo = minimo)
            OR (condicion = 'MAYOR_IGUAL' AND minimo IS NOT NULL AND maximo IS NULL)
            OR (condicion = 'MENOR_IGUAL' AND minimo IS NULL AND maximo IS NOT NULL)))),
    CONSTRAINT criterio_frecuencia_entera CHECK (indicador <> 'FRECUENCIA' OR
        ((minimo IS NULL OR minimo = trunc(minimo)) AND (maximo IS NULL OR maximo = trunc(maximo))))
);
CREATE INDEX idx_criterio_indicador_activo ON segmentacion.criterio(indicador, activo);
COMMENT ON TABLE segmentacion.criterio IS
    'SCRUM-25. Rangos inclusivos; cambios y sus fotos en auditoria.evento. Configuracion serializada para evitar solapamientos.';
