package com.maxiconecta.crm.comportamiento.validacion;

import com.maxiconecta.crm.comportamiento.ingesta.EventoCompraConfirmada;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Aplica todas las reglas de validación registradas. Ningún evento se almacena sin pasar por aquí.
 * Las reglas de SCRUM-131 se ejecutan antes de construir y guardar la compra.
 */
@Component
public class ValidadorEventos {

    private final List<ReglaValidacionEvento> reglas;

    public ValidadorEventos(ObjectProvider<ReglaValidacionEvento> reglas) {
        this.reglas = reglas.orderedStream().toList();
    }

    public void validar(EventoCompraConfirmada evento) {
        reglas.forEach(regla -> regla.validar(evento));
    }

    public int cantidadDeReglas() {
        return reglas.size();
    }
}
