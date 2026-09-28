package com.maxiconecta.crm.comportamiento.validacion;

import com.maxiconecta.crm.comportamiento.ingesta.EventoCompraConfirmada;

/**
 * Regla de validación que debe cumplir un evento de compra antes de almacenarse.
 * <p>
 * Las reglas son de la historia SCRUM-131: cada una se implementa
 * como un bean de Spring con esta interfaz y la ingesta las aplica todas, en el orden de
 * {@link org.springframework.core.annotation.Order}, antes de guardar la compra.
 */
public interface ReglaValidacionEvento {

    /**
     * @throws EventoInvalidoException si el evento no cumple la regla
     */
    void validar(EventoCompraConfirmada evento);
}
