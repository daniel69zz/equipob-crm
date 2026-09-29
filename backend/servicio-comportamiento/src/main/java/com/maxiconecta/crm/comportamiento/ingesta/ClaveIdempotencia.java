package com.maxiconecta.crm.comportamiento.ingesta;

import java.io.Serializable;

/**
 * Clave de idempotencia de un evento de venta: tipo de evento e identificador de la transacción
 * en Marketplace y Ventas. Ver docs/ingesta/idempotencia-eventos-venta.md.
 */
public record ClaveIdempotencia(String tipoEvento, String idTransaccion) implements Serializable {

    @Override
    public String toString() {
        return tipoEvento + " " + idTransaccion;
    }
}
