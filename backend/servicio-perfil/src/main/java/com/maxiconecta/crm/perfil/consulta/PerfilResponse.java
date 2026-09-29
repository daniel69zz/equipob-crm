package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import com.maxiconecta.crm.perfil.cliente.Direccion;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoDireccion;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Perfil del cliente con sus identificadores de origen y sus direcciones activas.
 */
public record PerfilResponse(Long id, String nombres, String apellidos, String tipoDocumento, String numeroDocumento,
                             String email, String telefono, EstadoPerfil estado, String motivosIncidencia,
                             OffsetDateTime creadoEn, OffsetDateTime actualizadoEn, Origen actualizadoPorOrigen,
                             String actualizadoPor, List<IdentificadorOrigen> identificadoresOrigen,
                             List<DireccionResponse> direcciones) {

    static PerfilResponse de(Cliente cliente, List<ClienteOrigen> vinculos) {
        return new PerfilResponse(cliente.getId(), cliente.getNombres(), cliente.getApellidos(),
                cliente.getTipoDocumento(), cliente.getNumeroDocumento(), cliente.getEmail(), cliente.getTelefono(),
                cliente.getEstado(), cliente.getMotivosIncidencia(), cliente.getCreadoEn(), cliente.getActualizadoEn(),
                cliente.getActualizadoPorOrigen(), cliente.getActualizadoPor(),
                vinculos.stream().map(IdentificadorOrigen::de).toList(),
                cliente.getDireccionesActivas().stream().map(DireccionResponse::de).toList());
    }

    public record IdentificadorOrigen(Origen origen, String idCliente, OffsetDateTime fechaVinculacion) {

        static IdentificadorOrigen de(ClienteOrigen vinculo) {
            return new IdentificadorOrigen(vinculo.getOrigen(), vinculo.getIdClienteOrigen(),
                    vinculo.getFechaVinculacion());
        }
    }

    public record DireccionResponse(Origen origen, String idDireccion, TipoDireccion tipo, String calle, String numero,
                                    String zona, String ciudad, String referencia, boolean principal) {

        static DireccionResponse de(Direccion direccion) {
            return new DireccionResponse(direccion.getOrigen(), direccion.getIdDireccionOrigen(), direccion.getTipo(),
                    direccion.getCalle(), direccion.getNumero(), direccion.getZona(), direccion.getCiudad(),
                    direccion.getReferencia(), direccion.isPrincipal());
        }
    }
}
