package com.maxiconecta.crm.perfil.cliente;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Único punto de escritura del histórico de cambios del perfil (docs/perfil/historico-cambios.md).
 * Cada operación que modifica el perfil llama a {@link #registrar} dentro de su misma transacción:
 * si el cambio se guarda, su registro histórico también, y viceversa.
 */
@Service
public class HistorialCambios {

    private final CambioPerfilRepository repository;
    private final ObjectMapper objectMapper;

    public HistorialCambios(CambioPerfilRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Registra una operación con sus campos modificados. Si no cambió ningún campo, no registra nada.
     *
     * @return el registro creado, o null si no hubo cambios
     */
    public CambioPerfil registrar(Long idCliente, TipoCambio tipo, Origen origen, String responsable,
                                  List<CambioCampo> cambios, Long idEvento) {
        if (cambios.isEmpty()) {
            return null;
        }
        return repository.save(new CambioPerfil(idCliente, tipo, origen, responsable, cambios, comoJson(cambios),
                idEvento));
    }

    private String comoJson(List<CambioCampo> cambios) {
        try {
            return objectMapper.writeValueAsString(cambios);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo registrar el detalle de los cambios", ex);
        }
    }
}
