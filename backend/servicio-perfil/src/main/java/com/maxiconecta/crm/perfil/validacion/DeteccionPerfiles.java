package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.CambioCampo;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.HistorialCambios;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoCambio;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ejecuta el motor de detección (SCRUM-166) sobre un perfil ya guardado y audita el resultado:
 * si el estado cambia, {@link HistorialCambios} deja un registro de solo inserción con cuándo se
 * detectó y con qué motivos (SCRUM-170, ver {@code cambio_perfil} en docs/perfil/modelo-datos-perfil.md).
 */
@Service
public class DeteccionPerfiles {

    /** Responsable cuando la detección corre sin que un usuario del CRM la haya iniciado. */
    static final String RESPONSABLE_AUTOMATICO = "deteccion-automatica";

    private final ClienteRepository clientes;
    private final HistorialCambios historial;
    private final DetectorPerfil detector;

    public DeteccionPerfiles(ClienteRepository clientes, HistorialCambios historial, DetectorPerfil detector) {
        this.clientes = clientes;
        this.historial = historial;
        this.detector = detector;
    }

    @Transactional
    public Cliente detectar(Long idCliente) {
        Cliente cliente = clientes.findById(idCliente)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + idCliente));
        List<CambioCampo> cambios = detector.detectar(cliente);
        if (!cambios.isEmpty()) {
            cliente.registrarActualizacion(Origen.CRM, RESPONSABLE_AUTOMATICO);
            historial.registrar(cliente.getId(), TipoCambio.DETECCION, Origen.CRM, RESPONSABLE_AUTOMATICO, cambios,
                    null);
        }
        return cliente;
    }
}
