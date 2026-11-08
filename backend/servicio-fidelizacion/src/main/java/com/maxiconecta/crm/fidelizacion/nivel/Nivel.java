package com.maxiconecta.crm.fidelizacion.nivel;

/** Niveles del programa de fidelización (SCRUM-49), del más bajo al más alto. */
public enum Nivel {
    BRONCE("Bronce"),
    PLATA("Plata"),
    ORO("Oro");

    private final String nombre;

    Nivel(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }
}
