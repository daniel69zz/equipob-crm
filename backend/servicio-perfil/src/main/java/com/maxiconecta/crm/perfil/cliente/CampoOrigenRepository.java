package com.maxiconecta.crm.perfil.cliente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampoOrigenRepository extends JpaRepository<CampoOrigen, CampoOrigen.Clave> {

    List<CampoOrigen> findByIdCliente(Long idCliente);
}
