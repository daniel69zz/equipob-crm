package com.maxiconecta.crm.perfil.validacion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.perfil.cliente.CambioCampo;
import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoCambio;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ejecuta el motor de detección (SCRUM-166) sobre un perfil ya guardado y audita el resultado:
 * si el estado cambia, queda un registro de solo inserción con cuándo se detectó y con qué
 * motivos (SCRUM-170, ver {@code cambio_perfil} en docs/perfil/modelo-datos-perfil.md).
 */
@Service
public class DeteccionPerfiles {

    /** Responsable cuando la detección corre sin que un usuario del CRM la haya iniciado. */
    static final String RESPONSABLE_AUTOMATICO = "deteccion-automatica";

    private final ClienteRepository clientes;
    private final CambioPerfilRepository cambiosPerfil;
    private final DetectorPerfil detector;
    private final ObjectMapper objectMapper;

    public DeteccionPerfiles(ClienteRepository clientes, CambioPerfilRepository cambiosPerfil,
                             DetectorPerfil detector, ObjectMapper objectMapper) {
        this.clientes = clientes;
        this.cambiosPerfil = cambiosPerfil;
        this.detector = detector;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Cliente detectar(Long idCliente) {
        Cliente cliente = clientes.findById(idCliente)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + idCliente));
        List<CambioCampo> cambios = detector.detectar(cliente);
        if (!cambios.isEmpty()) {
            cliente.registrarActualizacion(Origen.SISTEMA, RESPONSABLE_AUTOMATICO);
            cambiosPerfil.save(new CambioPerfil(cliente.getId(), TipoCambio.ACTUALIZACION, Origen.SISTEMA,
                    RESPONSABLE_AUTOMATICO, comoJson(cambios), null));
        }
        return cliente;
    }

    private String comoJson(List<CambioCampo> cambios) {
        try {
            return objectMapper.writeValueAsString(cambios);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo registrar el detalle de la detección", ex);
        }
    }
}
