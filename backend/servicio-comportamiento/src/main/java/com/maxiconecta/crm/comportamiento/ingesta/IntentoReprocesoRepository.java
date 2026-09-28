package com.maxiconecta.crm.comportamiento.ingesta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IntentoReprocesoRepository extends JpaRepository<IntentoReproceso, Long> {

    boolean existsByEvento_IdAndResultado(Long idEvento, ResultadoReproceso resultado);

    List<IntentoReproceso> findByEvento_IdOrderByNumeroDesc(Long idEvento);

    @Query("select coalesce(max(i.numero), 0) from IntentoReproceso i where i.evento.id = :idEvento")
    int ultimoNumero(@Param("idEvento") Long idEvento);
}
