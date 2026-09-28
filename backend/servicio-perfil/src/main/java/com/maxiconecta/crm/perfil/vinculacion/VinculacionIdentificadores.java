package com.maxiconecta.crm.perfil.vinculacion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.perfil.cliente.CambioCampo;
import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigenRepository;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.EstadoVinculacion;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoCambio;
import com.maxiconecta.crm.perfil.cliente.VinculacionPendiente;
import com.maxiconecta.crm.perfil.cliente.VinculacionPendienteRepository;
import com.maxiconecta.crm.perfil.comun.ConflictoException;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import com.maxiconecta.crm.perfil.comun.ReglaNegocioException;
import com.maxiconecta.crm.perfil.sincronizacion.EstadoEventoCliente;
import com.maxiconecta.crm.perfil.sincronizacion.EventoCliente;
import com.maxiconecta.crm.perfil.sincronizacion.EventoClienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Decisiones de un administrador sobre los identificadores de origen: vincular un identificador a
 * un perfil existente o declarar que es otra persona. Devuelve los eventos pendientes de ese
 * identificador, que el llamador aplica después, en orden.
 */
@Service
public class VinculacionIdentificadores {

    private final ClienteRepository clientes;
    private final ClienteOrigenRepository origenes;
    private final VinculacionPendienteRepository vinculaciones;
    private final EventoClienteRepository eventos;
    private final CambioPerfilRepository cambiosPerfil;
    private final ObjectMapper objectMapper;

    public VinculacionIdentificadores(ClienteRepository clientes, ClienteOrigenRepository origenes,
                                      VinculacionPendienteRepository vinculaciones, EventoClienteRepository eventos,
                                      CambioPerfilRepository cambiosPerfil, ObjectMapper objectMapper) {
        this.clientes = clientes;
        this.origenes = origenes;
        this.vinculaciones = vinculaciones;
        this.eventos = eventos;
        this.cambiosPerfil = cambiosPerfil;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<Long> vincular(Long idCliente, Origen origen, String idClienteOrigen, String responsable) {
        exigirSistemaExterno(origen);
        String identificador = idClienteOrigen.trim();
        Cliente cliente = clientes.findById(idCliente)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + idCliente));
        if (cliente.fueConsolidado()) {
            throw new ReglaNegocioException("El cliente " + idCliente + " fue unificado en el cliente "
                    + cliente.getIdClienteConsolidado() + "; vincule el identificador a ese perfil");
        }
        ClienteOrigen.Clave clave = new ClienteOrigen.Clave(origen, identificador);
        origenes.findById(clave).ifPresent(existente -> {
            throw new ConflictoException(existente.getIdCliente().equals(idCliente)
                    ? "El identificador " + clave + " ya está vinculado a este cliente"
                    : "El identificador " + clave + " ya está vinculado al cliente " + existente.getIdCliente()
                    + "; para juntarlos, unifique los perfiles");
        });

        origenes.saveAndFlush(ClienteOrigen.vincular(origen, identificador, idCliente, ClienteOrigen.VINCULACION_MANUAL,
                responsable, null));
        vinculaciones.findById(clave).ifPresent(v -> v.resolver(EstadoVinculacion.VINCULADO, responsable));
        registrarCambio(idCliente, responsable, List.of(new CambioCampo("identificadoresOrigen", null, clave.toString())));
        cliente.registrarActualizacion(Origen.CRM, responsable);
        return eventosPendientes(clave);
    }

    @Transactional
    public List<Long> declararNuevoPerfil(Origen origen, String idClienteOrigen, String responsable) {
        exigirSistemaExterno(origen);
        ClienteOrigen.Clave clave = new ClienteOrigen.Clave(origen, idClienteOrigen.trim());
        VinculacionPendiente vinculacion = vinculaciones.findById(clave)
                .orElseThrow(() -> new RecursoNoEncontradoException("No hay una vinculación pendiente para " + clave));
        if (vinculacion.getEstado() != EstadoVinculacion.PENDIENTE) {
            throw new ConflictoException("La vinculación de " + clave + " ya fue resuelta (" + vinculacion.getEstado() + ")");
        }
        vinculacion.resolver(EstadoVinculacion.NUEVO_PERFIL, responsable);
        return eventosPendientes(clave);
    }

    @Transactional(readOnly = true)
    public List<VinculacionResponse> listar(EstadoVinculacion estado) {
        return vinculaciones.findByEstadoOrderByDetectadaEn(estado).stream()
                .map(v -> VinculacionResponse.de(v, eventosPendientes(v.getId()).size()))
                .toList();
    }

    void registrarCambio(Long idCliente, String responsable, List<CambioCampo> cambios) {
        try {
            cambiosPerfil.save(new CambioPerfil(idCliente, TipoCambio.ACTUALIZACION, Origen.CRM, responsable,
                    objectMapper.writeValueAsString(cambios), null));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo registrar el detalle de los cambios", ex);
        }
    }

    private List<Long> eventosPendientes(ClienteOrigen.Clave clave) {
        return eventos.findByOrigenAndIdClienteOrigenAndEstadoOrderByIdAsc(clave.origen().name(),
                        clave.idClienteOrigen(), EstadoEventoCliente.PENDIENTE).stream()
                .map(EventoCliente::getId)
                .toList();
    }

    private static void exigirSistemaExterno(Origen origen) {
        if (!origen.esSistemaExterno()) {
            throw new ReglaNegocioException("Solo se vinculan identificadores de MARKETPLACE o VENTAS");
        }
    }
}
