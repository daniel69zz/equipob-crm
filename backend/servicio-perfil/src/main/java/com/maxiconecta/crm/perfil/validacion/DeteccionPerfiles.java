package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.comun.ConflictoException;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeteccionPerfiles {

    private final ClienteRepository clientes;
    private final DetectorIncidenciasPerfil detector;

    public DeteccionPerfiles(ClienteRepository clientes, DetectorIncidenciasPerfil detector) {
        this.clientes = clientes;
        this.detector = detector;
    }

    @Transactional
    public Resultado detectar(Long idCliente) {
        Cliente cliente = clientes.buscarParaValidar(idCliente)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + idCliente));
        if (cliente.fueConsolidado()) {
            throw new ConflictoException("El perfil fue consolidado en el cliente " + cliente.getIdClienteConsolidado());
        }
        DetectorIncidenciasPerfil.Evaluacion evaluacion = detector.evaluar(cliente);
        cliente.marcarEstado(evaluacion.incompleto(), evaluacion.inconsistencias());
        clientes.save(cliente);
        return new Resultado(cliente.getId(), cliente.getEstado(), evaluacion.incompleto(), evaluacion.inconsistencias());
    }

    public record Resultado(Long idCliente, EstadoPerfil estado, List<String> motivosIncompleto,
                            List<String> motivosInconsistencia) {
    }
}
