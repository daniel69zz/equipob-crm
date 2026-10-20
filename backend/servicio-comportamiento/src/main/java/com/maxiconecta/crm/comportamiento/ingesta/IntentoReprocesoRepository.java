package com.maxiconecta.crm.comportamiento.ingesta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IntentoReprocesoRepository extends JpaRepository<IntentoReproceso, Long> {

    boolean existsByEvento_IdAndResultado(Long idEvento, ResultadoReproceso resultado);

    List<IntentoReproceso> findByEvento_IdOrderByNumeroDesc(Long idEvento);

    @Query("select distinct i.evento.id from IntentoReproceso i "
            + "where i.evento.id in :eventos and i.resultado <> :enProceso")
    List<Long> eventosConIntentoFinalizado(@Param("eventos") List<Long> eventos,
                                         @Param("enProceso") ResultadoReproceso enProceso);

    @Query("select distinct i.evento.id from IntentoReproceso i "
            + "where i.evento.id in :eventos and i.resultado = :resultado and i.evento.estado = :estado")
    List<Long> eventosConResultado(@Param("eventos") List<Long> eventos,
                                  @Param("resultado") ResultadoReproceso resultado,
                                  @Param("estado") EstadoEvento estado);

    @Query("select coalesce(max(i.numero), 0) from IntentoReproceso i where i.evento.id = :idEvento")
    int ultimoNumero(@Param("idEvento") Long idEvento);
}
