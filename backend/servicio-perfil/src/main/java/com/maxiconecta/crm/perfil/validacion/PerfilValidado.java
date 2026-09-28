package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.Direccion;

import java.util.List;

/**
 * Datos del perfil listos para guardarse. Los datos inválidos quedan vacíos y su motivo en {@code motivos}.
 */
public record PerfilValidado(String nombres, String apellidos, String tipoDocumento, String numeroDocumento,
                             String email, String telefono, List<Direccion.DatosDireccion> direcciones,
                             List<String> motivos) {

    public boolean completo() {
        return motivos.isEmpty();
    }

    public String motivosComoTexto() {
        return String.join("; ", motivos);
    }
}
