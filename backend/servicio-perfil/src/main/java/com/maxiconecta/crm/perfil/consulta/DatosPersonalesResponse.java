package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.consulta.PerfilResponse.DireccionResponse;

import java.util.List;

/**
 * Datos personales, de contacto y direcciones del cliente (SCRUM-152), base del bloque
 * "datos personales" de la ficha integral (SCRUM-10).
 */
public record DatosPersonalesResponse(String nombres, String apellidos, String tipoDocumento, String numeroDocumento,
                                      String email, String telefono, List<DireccionResponse> direcciones) {

    static DatosPersonalesResponse de(Cliente cliente) {
        return new DatosPersonalesResponse(cliente.getNombres(), cliente.getApellidos(), cliente.getTipoDocumento(),
                cliente.getNumeroDocumento(), cliente.getEmail(), cliente.getTelefono(),
                cliente.getDireccionesActivas().stream().map(DireccionResponse::de).toList());
    }
}
