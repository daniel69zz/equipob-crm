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
import com.maxiconecta.crm.perfil.cliente.VinculacionPendienteRepository;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import com.maxiconecta.crm.perfil.comun.ReglaNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Parte de la unificación de duplicados que corresponde a los identificadores de origen (SCRUM-552):
 * todos los identificadores del perfil absorbido pasan al perfil que se conserva, para que un
 * evento posterior con cualquiera de ellos no vuelva a crear el duplicado.
 * <p>
 * La fusión de los datos del perfil (qué nombre, contacto y direcciones se conservan) es de
 * SCRUM-13, que debe llamar a este servicio dentro de la misma transacción.
 */
@Service
public class ConsolidacionIdentificadores {

    private final ClienteRepository clientes;
    private final ClienteOrigenRepository origenes;
    private final VinculacionPendienteRepository vinculaciones;
    private final HistorialCambios historial;

    public ConsolidacionIdentificadores(ClienteRepository clientes, ClienteOrigenRepository origenes,
                                        VinculacionPendienteRepository vinculaciones, HistorialCambios historial) {
        this.clientes = clientes;
        this.origenes = origenes;
        this.vinculaciones = vinculaciones;
        this.historial = historial;
    }

    /**
     * @return los identificadores que pasaron al perfil conservado
     */
    @Transactional
    public List<String> consolidar(Long idAbsorbido, Long idConservado, String responsable) {
        if (idAbsorbido.equals(idConservado)) {
            throw new ReglaNegocioException("No se puede unificar un perfil consigo mismo");
        }
        Cliente absorbido = vigente(idAbsorbido);
        Cliente conservado = vigente(idConservado);

        List<String> movidos = new ArrayList<>();
        List<CambioCampo> cambiosConservado = new ArrayList<>();
        List<CambioCampo> cambiosAbsorbido = new ArrayList<>();
        for (ClienteOrigen vinculo : origenes.findByIdClienteOrderByFechaVinculacion(idAbsorbido)) {
            vinculo.reasignar(idConservado, ClienteOrigen.UNIFICACION, responsable);
            movidos.add(vinculo.getId());
            cambiosConservado.add(new CambioCampo("identificadoresOrigen", null, vinculo.getId()));
            cambiosAbsorbido.add(new CambioCampo("identificadoresOrigen", vinculo.getId(), null));
        }

        absorbido.consolidarEn(idConservado);
        cambiosAbsorbido.add(new CambioCampo("idClienteConsolidado", null, idConservado.toString()));
        // Los perfiles que el absorbido había absorbido antes pasan a apuntar directamente al conservado.
        clientes.findByIdClienteConsolidado(idAbsorbido).forEach(anterior -> anterior.consolidarEn(idConservado));
        vinculaciones.findByIdClienteSugeridoAndEstado(idAbsorbido, EstadoVinculacion.PENDIENTE)
                .forEach(pendiente -> pendiente.sugerir(idConservado));

        absorbido.registrarActualizacion(Origen.CRM, responsable);
        conservado.registrarActualizacion(Origen.CRM, responsable);
        historial.registrar(idAbsorbido, TipoCambio.UNIFICACION, Origen.CRM, responsable, cambiosAbsorbido, null);
        historial.registrar(idConservado, TipoCambio.UNIFICACION, Origen.CRM, responsable, cambiosConservado, null);
        return movidos;
    }

    private Cliente vigente(Long idCliente) {
        Cliente cliente = clientes.findById(idCliente)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + idCliente));
        if (cliente.fueConsolidado()) {
            throw new ReglaNegocioException("El cliente " + idCliente + " ya fue unificado en el cliente "
                    + cliente.getIdClienteConsolidado());
        }
        return cliente;
    }
}
