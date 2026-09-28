package com.maxiconecta.crm.perfil.cliente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CambioPerfilRepository extends JpaRepository<CambioPerfil, Long> {

    List<CambioPerfil> findByIdClienteOrderByFechaAscIdAsc(Long idCliente);
}
