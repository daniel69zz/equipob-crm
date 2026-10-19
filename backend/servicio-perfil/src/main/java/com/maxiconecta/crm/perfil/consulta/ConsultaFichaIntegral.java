package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigenRepository;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consolida la ficha integral del cliente (SCRUM-10, SCRUM-154): sus datos personales (SCRUM-152),
 * historial de compras (SCRUM-151), segmento (SCRUM-149), puntos de fidelización, nivel de consumo
 * (SCRUM-566) y alertas de cambio de comportamiento (SCRUM-567), en una sola consulta.
 */
@Service
public class ConsultaFichaIntegral {

    private final ClienteRepository clientes;
    private final ClienteOrigenRepository origenes;
    private final HistorialComprasCliente historialCompras;
    private final SegmentoDelCliente segmento;
    private final PuntosDelCliente puntos;
    private final NivelConsumoDelCliente nivelConsumo;
    private final CambiosComportamientoDelCliente cambiosComportamiento;
    private final ClasificacionFrecuenciaDelCliente clasificacionFrecuencia;

    public ConsultaFichaIntegral(ClienteRepository clientes, ClienteOrigenRepository origenes,
                                 HistorialComprasCliente historialCompras, SegmentoDelCliente segmento,
                                 PuntosDelCliente puntos, NivelConsumoDelCliente nivelConsumo,
                                 CambiosComportamientoDelCliente cambiosComportamiento,
                                 ClasificacionFrecuenciaDelCliente clasificacionFrecuencia) {
        this.clientes = clientes;
        this.origenes = origenes;
        this.historialCompras = historialCompras;
        this.segmento = segmento;
        this.puntos = puntos;
        this.nivelConsumo = nivelConsumo;
        this.cambiosComportamiento = cambiosComportamiento;
        this.clasificacionFrecuencia = clasificacionFrecuencia;
    }

    @Transactional(readOnly = true)
    public FichaIntegralResponse obtener(Long clienteId) {
        Cliente cliente = clientes.findById(clienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + clienteId));
        List<ClienteOrigen> identificadoresOrigen = origenes.findByIdClienteOrderByFechaVinculacion(clienteId);
        return new FichaIntegralResponse(
                DatosPersonalesResponse.de(cliente),
                historialCompras.buscar(clienteId, identificadoresOrigen),
                segmento.buscar(clienteId),
                puntos.buscar(clienteId),
                nivelConsumo.buscar(clienteId, identificadoresOrigen),
                cambiosComportamiento.buscar(clienteId, identificadoresOrigen),
                clasificacionFrecuencia.buscar(clienteId, identificadoresOrigen));
    }
}
