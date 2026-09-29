package com.maxiconecta.crm.perfil.vinculacion;

import com.maxiconecta.crm.perfil.cliente.CambioCampo;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigenRepository;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.EstadoVinculacion;
import com.maxiconecta.crm.perfil.cliente.HistorialCambios;
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
    private final HistorialCambios historial;

    public VinculacionIdentificadores(ClienteRepository clientes, ClienteOrigenRepository origenes,
                                      VinculacionPendienteRepository vinculaciones, EventoClienteRepository eventos,
                                      HistorialCambios historial) {
        this.clientes = clientes;
        this.origenes = origenes;
        this.vinculaciones = vinculaciones;
        this.eventos = eventos;
        this.historial = historial;
    }

    @Transactional
    public List<Long> vincular(Long idCliente, String idClienteOrigen, String responsable) {
        String identificador = idClienteOrigen.trim();
        Cliente cliente = clientes.findById(idCliente)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + idCliente));
        if (cliente.fueConsolidado()) {
            throw new ReglaNegocioException("El cliente " + idCliente + " fue unificado en el cliente "
                    + cliente.getIdClienteConsolidado() + "; vincule el identificador a ese perfil");
        }
        origenes.findById(identificador).ifPresent(existente -> {
            throw new ConflictoException(existente.getIdCliente().equals(idCliente)
                    ? "El identificador " + identificador + " ya está vinculado a este cliente"
                    : "El identificador " + identificador + " ya está vinculado al cliente " + existente.getIdCliente()
                    + "; para juntarlos, unifique los perfiles");
        });

        origenes.saveAndFlush(ClienteOrigen.vincular(identificador, idCliente, ClienteOrigen.VINCULACION_MANUAL,
                responsable, null));
        vinculaciones.findById(identificador).ifPresent(v -> v.resolver(EstadoVinculacion.VINCULADO, responsable));
        historial.registrar(idCliente, TipoCambio.VINCULACION, Origen.CRM, responsable,
                List.of(new CambioCampo("identificadoresOrigen", null, identificador)), null);
        cliente.registrarActualizacion(Origen.CRM, responsable);
        return eventosPendientes(identificador);
    }

    @Transactional
    public List<Long> declararNuevoPerfil(String idClienteOrigen, String responsable) {
        String identificador = idClienteOrigen.trim();
        VinculacionPendiente vinculacion = vinculaciones.findById(identificador)
                .orElseThrow(() -> new RecursoNoEncontradoException("No hay una vinculación pendiente para " + identificador));
        if (vinculacion.getEstado() != EstadoVinculacion.PENDIENTE) {
            throw new ConflictoException("La vinculación de " + identificador + " ya fue resuelta ("
                    + vinculacion.getEstado() + ")");
        }
        vinculacion.resolver(EstadoVinculacion.NUEVO_PERFIL, responsable);
        return eventosPendientes(identificador);
    }

    @Transactional(readOnly = true)
    public List<VinculacionResponse> listar(EstadoVinculacion estado) {
        return vinculaciones.findByEstadoOrderByDetectadaEn(estado).stream()
                .map(v -> VinculacionResponse.de(v, eventosPendientes(v.getId()).size()))
                .toList();
    }

    private List<Long> eventosPendientes(String identificador) {
        return eventos.findByIdClienteOrigenAndEstadoOrderByIdAsc(identificador, EstadoEventoCliente.PENDIENTE).stream()
                .map(EventoCliente::getId)
                .toList();
    }
}
