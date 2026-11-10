package com.maxiconecta.crm.comportamiento.reporte;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Formatos estándar en que se exportan los reportes de clientes (SCRUM-60, SCRUM-479,
 * docs/compra/exportacion-reportes.md).
 */
public enum FormatoExportacion {

    CSV("csv", new MediaType("text", "csv", StandardCharsets.UTF_8)),
    PDF("pdf", MediaType.APPLICATION_PDF);

    private final String extension;
    private final MediaType tipo;

    FormatoExportacion(String extension, MediaType tipo) {
        this.extension = extension;
        this.tipo = tipo;
    }

    public String extension() {
        return extension;
    }

    public MediaType tipo() {
        return tipo;
    }

    /** Lee el parámetro {@code formato} sin distinguir mayúsculas; ausente o desconocido responde 400. */
    public static FormatoExportacion de(String valor) {
        if (valor != null) {
            for (FormatoExportacion formato : values()) {
                if (formato.extension.equals(valor.strip().toLowerCase(Locale.ROOT))) {
                    return formato;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Formato de exportación inválido: '" + valor + "' (se espera csv o pdf)");
    }
}
