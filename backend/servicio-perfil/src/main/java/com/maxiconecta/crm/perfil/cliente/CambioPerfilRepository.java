package com.maxiconecta.crm.perfil.cliente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface CambioPerfilRepository extends JpaRepository<CambioPerfil, Long>, JpaSpecificationExecutor<CambioPerfil> {

    List<CambioPerfil> findByIdClienteOrderByFechaAscIdAsc(Long idCliente);
}
