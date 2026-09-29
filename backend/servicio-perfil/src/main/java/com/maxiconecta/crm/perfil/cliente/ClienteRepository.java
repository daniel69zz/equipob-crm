package com.maxiconecta.crm.perfil.cliente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cliente c where c.id = :id")
    Optional<Cliente> buscarParaValidar(@Param("id") Long id);

    /** Perfiles vigentes (no absorbidos) con ese documento, del más antiguo al más reciente. */
    List<Cliente> findByTipoDocumentoAndNumeroDocumentoAndIdClienteConsolidadoIsNullOrderById(String tipoDocumento,
                                                                                             String numeroDocumento);

    /** Perfiles absorbidos antes por este perfil. */
    List<Cliente> findByIdClienteConsolidado(Long idCliente);
}
