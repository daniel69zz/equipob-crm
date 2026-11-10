package com.maxiconecta.crm.fidelizacion.puntos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SaldoPuntosRepository extends JpaRepository<SaldoPuntos, Long> {

    /**
     * SCRUM-464: cuántos clientes hay en cada nivel de fidelización, para el reporte de distribución
     * (SCRUM-57). Solo cuenta a los clientes con saldo; {@code nivel} es nulo cuando el cliente no
     * alcanza el umbral más bajo ({@code nivel.AsignacionNiveles}).
     */
    @Query("select s.nivel as nivel, count(s) as clientes from SaldoPuntos s group by s.nivel")
    List<ConteoNivel> contarPorNivel();

    /** Lo mismo, restringido a un grupo de clientes: el reporte filtrado por segmento o nivel de consumo. */
    @Query("""
            select s.nivel as nivel, count(s) as clientes from SaldoPuntos s
            where s.clienteId in :clientes group by s.nivel
            """)
    List<ConteoNivel> contarPorNivel(@Param("clientes") Collection<Long> clientes);

    /**
     * SCRUM-476: puntos acumulados de toda la cartera para el panel de indicadores (SCRUM-59): la suma
     * de los saldos vigentes y cuántos clientes tienen saldo. Un saldo en cero no cuenta como cliente con
     * puntos (los canjeó o le caducaron).
     */
    @Query("select coalesce(sum(s.saldo), 0) as puntos, count(s) as clientes from SaldoPuntos s where s.saldo > 0")
    TotalPuntos totalPuntos();

    /** Fila del conteo por nivel; {@code nivel} nulo es el cliente con puntos pero sin nivel asignado. */
    interface TotalPuntos {

        long getPuntos();

        long getClientes();
    }

    interface ConteoNivel {

        String getNivel();

        long getClientes();
    }
}
