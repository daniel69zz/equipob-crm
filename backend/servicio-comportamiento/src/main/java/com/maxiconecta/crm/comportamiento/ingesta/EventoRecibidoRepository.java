package com.maxiconecta.crm.comportamiento.ingesta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface EventoRecibidoRepository
        extends JpaRepository<EventoRecibido, Long>, JpaSpecificationExecutor<EventoRecibido> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventoRecibido e where e.id = :id")
    Optional<EventoRecibido> buscarParaActualizar(@Param("id") Long id);
}
