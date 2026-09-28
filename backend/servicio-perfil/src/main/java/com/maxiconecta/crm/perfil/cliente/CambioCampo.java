package com.maxiconecta.crm.perfil.cliente;

import java.util.Objects;

/**
 * Cambio de un campo del perfil, para el registro de cambios.
 */
public record CambioCampo(String campo, String anterior, String nuevo) {

    /** Agrega el cambio a la lista solo si el valor realmente cambió. */
    public static void siCambio(java.util.List<CambioCampo> cambios, String campo, Object anterior, Object nuevo) {
        String textoAnterior = anterior != null ? anterior.toString() : null;
        String textoNuevo = nuevo != null ? nuevo.toString() : null;
        if (!Objects.equals(textoAnterior, textoNuevo)) {
            cambios.add(new CambioCampo(campo, textoAnterior, textoNuevo));
        }
    }
}
