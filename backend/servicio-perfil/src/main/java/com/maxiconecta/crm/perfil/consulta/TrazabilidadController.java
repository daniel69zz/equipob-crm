package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.ConflictoPerfil;
import com.maxiconecta.crm.perfil.cliente.ConflictoPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import com.maxiconecta.crm.perfil.sincronizacion.EstadoEventoCliente;
import com.maxiconecta.crm.perfil.sincronizacion.EventoCliente;
import com.maxiconecta.crm.perfil.sincronizacion.EventoClienteRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Trazabilidad de las actualizaciones automáticas de un perfil (SCRUM-162): qué notificaciones lo
 * afectaron y qué conflictos entre sistemas se resolvieron. El histórico de cambios campo por campo
 * es de SCRUM-22. El Gateway exige CLIENTE_CONSULTAR y audita cada consulta.
 */
@RestController
@RequestMapping("/api/perfil/clientes/{clienteId}")
public class TrazabilidadController {

    private final ClienteRepository clientes;
    private final EventoClienteRepository eventos;
    private final ConflictoPerfilRepository conflictos;

    public TrazabilidadController(ClienteRepository clientes, EventoClienteRepository eventos,
                                  ConflictoPerfilRepository conflictos) {
        this.clientes = clientes;
        this.eventos = eventos;
        this.conflictos = conflictos;
    }

    /** Últimas 100 notificaciones que se aplicaron (o intentaron aplicarse) a este perfil, de la más reciente. */
    @GetMapping("/sincronizaciones")
    @Transactional(readOnly = true)
    public List<SincronizacionResponse> sincronizaciones(@PathVariable Long clienteId) {
        exigirCliente(clienteId);
        return eventos.findTop100ByIdClienteOrderByIdDesc(clienteId).stream().map(SincronizacionResponse::de).toList();
    }

    @GetMapping("/conflictos")
    @Transactional(readOnly = true)
    public List<ConflictoResponse> conflictos(@PathVariable Long clienteId) {
        exigirCliente(clienteId);
        return conflictos.findByIdClienteOrderByFechaDescIdDesc(clienteId).stream().map(ConflictoResponse::de).toList();
    }

    private void exigirCliente(Long clienteId) {
        if (!clientes.existsById(clienteId)) {
            throw new RecursoNoEncontradoException("No existe el cliente " + clienteId);
        }
    }

    public record SincronizacionResponse(Long idEvento, String idEventoOrigen, String tipoEvento, String origen,
                                         String idClienteOrigen, EstadoEventoCliente estado, String causa, int intentos,
                                         OffsetDateTime recibidoEn, OffsetDateTime procesadoEn) {

        static SincronizacionResponse de(EventoCliente evento) {
            return new SincronizacionResponse(evento.getId(), evento.getIdEventoOrigen(), evento.getTipoEvento(),
                    evento.getOrigen(), evento.getIdClienteOrigen(), evento.getEstado(), evento.getCausa(),
                    evento.getIntentos(), evento.getRecibidoEn(), evento.getProcesadoEn());
        }
    }

    public record ConflictoResponse(Long id, String campo, String valorActual, Origen origenActual, String valorRecibido,
                                    Origen origenRecibido, ConflictoPerfil.Decision decision, ConflictoPerfil.Regla regla,
                                    OffsetDateTime fecha, Long idEvento) {

        static ConflictoResponse de(ConflictoPerfil conflicto) {
            return new ConflictoResponse(conflicto.getId(), conflicto.getCampo(), conflicto.getValorActual(),
                    conflicto.getOrigenActual(), conflicto.getValorRecibido(), conflicto.getOrigenRecibido(),
                    conflicto.getDecision(), conflicto.getRegla(), conflicto.getFecha(), conflicto.getIdEvento());
        }
    }
}
