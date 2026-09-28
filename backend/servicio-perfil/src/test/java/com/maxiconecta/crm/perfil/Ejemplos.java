package com.maxiconecta.crm.perfil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Eventos de ejemplo del simulador (herramientas/simulador-eventos/eventos), compartidos con las pruebas.
 */
public final class Ejemplos {

    static final Path CARPETA = Path.of("../../herramientas/simulador-eventos/eventos");

    private Ejemplos() {
    }

    public static String ejemplo(String archivo) {
        try {
            return Files.readString(CARPETA.resolve(archivo));
        } catch (IOException ex) {
            throw new IllegalStateException("No se encontró el ejemplo " + archivo, ex);
        }
    }
}
