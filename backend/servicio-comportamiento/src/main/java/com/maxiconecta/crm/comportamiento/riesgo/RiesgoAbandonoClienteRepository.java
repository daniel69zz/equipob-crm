package com.maxiconecta.crm.comportamiento.riesgo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiesgoAbandonoClienteRepository extends JpaRepository<RiesgoAbandonoCliente, Long> {

    /** Clientes de un nivel, del puntaje más alto al más bajo; a igual puntaje, por cliente (SCRUM-326). */
    Page<RiesgoAbandonoCliente> findByNivelOrderByPuntajeDescClienteIdAsc(NivelRiesgo nivel, Pageable pageable);
}
