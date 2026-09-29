package com.maxiconecta.crm.comportamiento.ingesta;

/**
 * La anulación cumple el contrato, pero no cuadra con la compra registrada (no existe, es de otro
 * cliente, ya está anulada o revierte más de lo que sigue vigente). La causa queda en la bitácora
 * y el evento puede reprocesarse, por ejemplo cuando llegue la compra.
 */
public class AnulacionInconsistenteException extends RuntimeException {

    public AnulacionInconsistenteException(String mensaje) {
        super(mensaje);
    }
}
