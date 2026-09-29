package com.maxiconecta.crm.comportamiento.compra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface FrecuenciaCompraRepository extends JpaRepository<FrecuenciaCompra, String> {

    /** Incremento atómico dentro de la misma transacción que guarda la compra. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO comportamiento.frecuencia_compra AS frecuencia
                (id_cliente_origen, cantidad, actualizada_en)
            VALUES (:idClienteOrigen, 1, now())
            ON CONFLICT (id_cliente_origen) DO UPDATE
            SET cantidad = frecuencia.cantidad + 1,
                actualizada_en = now()
            """, nativeQuery = true)
    void incrementar(@Param("idClienteOrigen") String idClienteOrigen);

    @Query("SELECT COALESCE(SUM(f.cantidad), 0) FROM FrecuenciaCompra f "
            + "WHERE f.idClienteOrigen IN :identificadores")
    long sumar(@Param("identificadores") Collection<String> identificadores);
}
