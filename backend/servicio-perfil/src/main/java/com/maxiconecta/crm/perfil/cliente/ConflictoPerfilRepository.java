package com.maxiconecta.crm.perfil.cliente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConflictoPerfilRepository extends JpaRepository<ConflictoPerfil, Long> {

    List<ConflictoPerfil> findByIdClienteOrderByFechaDescIdDesc(Long idCliente);
}
