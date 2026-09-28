package com.maxiconecta.crm.perfil.cliente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClienteOrigenRepository extends JpaRepository<ClienteOrigen, ClienteOrigen.Clave> {

    List<ClienteOrigen> findByIdClienteOrderByFechaVinculacion(Long idCliente);
}
