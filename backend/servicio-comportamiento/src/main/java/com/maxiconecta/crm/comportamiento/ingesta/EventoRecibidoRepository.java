package com.maxiconecta.crm.comportamiento.ingesta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EventoRecibidoRepository
        extends JpaRepository<EventoRecibido, Long>, JpaSpecificationExecutor<EventoRecibido> {
}
