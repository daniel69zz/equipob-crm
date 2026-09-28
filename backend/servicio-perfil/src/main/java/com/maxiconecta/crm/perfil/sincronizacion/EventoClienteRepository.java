package com.maxiconecta.crm.perfil.sincronizacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoClienteRepository extends JpaRepository<EventoCliente, Long> {

    List<EventoCliente> findByOrigenAndIdClienteOrigenAndEstadoOrderByIdAsc(String origen, String idClienteOrigen,
                                                                            EstadoEventoCliente estado);
}
