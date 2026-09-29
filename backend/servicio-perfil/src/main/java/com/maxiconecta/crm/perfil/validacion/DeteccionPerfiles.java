package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioCampo;
import com.maxiconecta.crm.perfil.cliente.HistorialCambios;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoCambio;
import com.maxiconecta.crm.perfil.comun.ConflictoException;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeteccionPerfiles {

    static final String RESPONSABLE_AUTOMATICO = "deteccion-automatica";

    private final ClienteRepository clientes;
    private final DetectorPerfil detector;
    private final HistorialCambios historial;

    public DeteccionPerfiles(ClienteRepository clientes, DetectorPerfil detector, HistorialCambios historial) {
        this.clientes = clientes;
        this.detector = detector;
        this.historial = historial;
    }

    @Transactional
    public Resultado detectar(Long idCliente) {
        Cliente cliente = clientes.buscarParaValidar(idCliente)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + idCliente));
        if (cliente.fueConsolidado()) {
            throw new ConflictoException("El perfil fue consolidado en el cliente " + cliente.getIdClienteConsolidado());
        }
        DetectorPerfil.Evaluacion evaluacion = detector.evaluar(cliente);
        List<CambioCampo> cambios = cliente.marcarEstado(evaluacion.incompleto(), evaluacion.inconsistencias());
        if (!cambios.isEmpty()) {
            cliente.registrarActualizacion(Origen.CRM, RESPONSABLE_AUTOMATICO);
            historial.registrar(cliente.getId(), TipoCambio.DETECCION, Origen.CRM,
                    RESPONSABLE_AUTOMATICO, cambios, null);
        }
        clientes.save(cliente);
        return new Resultado(cliente.getId(), cliente.getEstado(), evaluacion.incompleto(), evaluacion.inconsistencias());
    }

    public record Resultado(Long idCliente, EstadoPerfil estado, List<String> motivosIncompleto,
                            List<String> motivosInconsistencia) {
    }
}
