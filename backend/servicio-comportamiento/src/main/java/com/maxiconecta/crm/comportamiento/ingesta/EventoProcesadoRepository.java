package com.maxiconecta.crm.comportamiento.ingesta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, EventoProcesado.Id> {

    default Optional<EventoProcesado> buscar(ClaveIdempotencia clave) {
        return findById(new EventoProcesado.Id(clave));
    }
}
