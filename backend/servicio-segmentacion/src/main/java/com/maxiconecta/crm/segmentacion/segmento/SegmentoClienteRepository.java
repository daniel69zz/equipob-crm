package com.maxiconecta.crm.segmentacion.segmento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SegmentoClienteRepository extends JpaRepository<SegmentoCliente, Long> {

    /** SCRUM-378: los demás clientes con el mismo segmento vigente, para la sugerencia de categorías (SCRUM-44). */
    @Query("""
            select s.clienteId from SegmentoCliente s
            where s.segmento = :segmento and s.clienteId <> :clienteId
            order by s.clienteId
            """)
    List<Long> companeros(@Param("segmento") String segmento, @Param("clienteId") Long clienteId);
}
