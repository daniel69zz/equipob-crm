package com.maxiconecta.crm.gateway.auth;

/**
 * Credenciales incorrectas, usuario desactivado o rol desactivado.
 * El mensaje es el mismo en todos los casos para no revelar qué usuarios existen.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Usuario o contraseña incorrectos");
    }
}
