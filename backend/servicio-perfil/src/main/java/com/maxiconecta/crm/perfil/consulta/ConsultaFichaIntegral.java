package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigenRepository;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consolida la ficha integral del cliente (SCRUM-10, SCRUM-154): sus datos personales (SCRUM-152),
 * historial de compras (SCRUM-151), segmento (SCRUM-149) y puntos de fidelización, en una sola
 * consulta.
 */
@Service
public class ConsultaFichaIntegral {

    private final ClienteRepository clientes;
    private final ClienteOrigenRepository origenes;
    private final HistorialComprasCliente historialCompras;
    private final SegmentoDelCliente segmento;
    private final PuntosDelCliente puntos;

    public ConsultaFichaIntegral(ClienteRepository clientes, ClienteOrigenRepository origenes,
                                 HistorialComprasCliente historialCompras, SegmentoDelCliente segmento,
                                 PuntosDelCliente puntos) {
        this.clientes = clientes;
        this.origenes = origenes;
        this.historialCompras = historialCompras;
        this.segmento = segmento;
        this.puntos = puntos;
    }

    @Transactional(readOnly = true)
    public FichaIntegralResponse obtener(Long clienteId) {
        Cliente cliente = clientes.findById(clienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + clienteId));
        return new FichaIntegralResponse(
                DatosPersonalesResponse.de(cliente),
                historialCompras.buscar(clienteId, origenes.findByIdClienteOrderByFechaVinculacion(clienteId)),
                segmento.buscar(clienteId),
                puntos.buscar(clienteId));
    }
}
