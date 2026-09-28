package com.maxiconecta.crm.perfil.cliente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    /** Perfiles vigentes (no absorbidos) con ese documento, del más antiguo al más reciente. */
    List<Cliente> findByTipoDocumentoAndNumeroDocumentoAndIdClienteConsolidadoIsNullOrderById(String tipoDocumento,
                                                                                             String numeroDocumento);

    /** Perfiles absorbidos antes por este perfil. */
    List<Cliente> findByIdClienteConsolidado(Long idCliente);
}
