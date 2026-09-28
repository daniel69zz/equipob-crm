package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;

import java.time.OffsetDateTime;

/**
 * Fila del resultado de una búsqueda de perfiles.
 */
public record ResumenPerfil(Long id, String nombres, String apellidos, String tipoDocumento, String numeroDocumento,
                            EstadoPerfil estado, OffsetDateTime actualizadoEn) {

    static ResumenPerfil de(Cliente cliente) {
        return new ResumenPerfil(cliente.getId(), cliente.getNombres(), cliente.getApellidos(),
                cliente.getTipoDocumento(), cliente.getNumeroDocumento(), cliente.getEstado(),
                cliente.getActualizadoEn());
    }
}
