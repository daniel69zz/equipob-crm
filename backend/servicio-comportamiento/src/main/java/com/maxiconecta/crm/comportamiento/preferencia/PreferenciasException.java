package com.maxiconecta.crm.comportamiento.preferencia;

import org.springframework.http.HttpStatus;

public class PreferenciasException extends RuntimeException {
    private final HttpStatus estado;
    public PreferenciasException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }
    public HttpStatus estado() { return estado; }
}
