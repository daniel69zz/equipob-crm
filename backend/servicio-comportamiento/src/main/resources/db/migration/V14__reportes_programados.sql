CREATE TABLE comportamiento.reporte_programado (
    id UUID PRIMARY KEY,
    usuario VARCHAR(150) NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    frecuencia VARCHAR(10) NOT NULL CHECK (frecuencia IN ('DIARIA','SEMANAL','MENSUAL')),
    formato VARCHAR(3) NOT NULL CHECK (formato IN ('CSV','PDF')),
    dias INTEGER NOT NULL CHECK (dias BETWEEN 1 AND 1830),
    segmento VARCHAR(60),
    inicio TIMESTAMPTZ NOT NULL,
    fin TIMESTAMPTZ,
    proxima TIMESTAMPTZ NOT NULL,
    estado VARCHAR(10) NOT NULL CHECK (estado IN ('ACTIVA','PAUSADA','CANCELADA'))
);
CREATE INDEX reporte_programado_vencido ON comportamiento.reporte_programado(proxima) WHERE estado = 'ACTIVA';
CREATE TABLE comportamiento.ejecucion_reporte (
    id UUID PRIMARY KEY,
    programacion UUID NOT NULL REFERENCES comportamiento.reporte_programado(id),
    prevista TIMESTAMPTZ NOT NULL,
    ejecutada TIMESTAMPTZ NOT NULL,
    estado VARCHAR(10) NOT NULL CHECK (estado IN ('COMPLETADA','FALLIDA')),
    causa VARCHAR(500),
    nombre VARCHAR(250),
    formato VARCHAR(3),
    contenido BYTEA,
    leida BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (programacion, prevista)
);
