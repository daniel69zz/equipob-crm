package com.maxiconecta.crm.comportamiento.compra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Indicador persistido de compras confirmadas vigentes para un identificador de cliente. */
@Entity
@Table(schema = "comportamiento", name = "frecuencia_compra")
public class FrecuenciaCompra {

    @Id
    @Column(length = 64)
    private String idClienteOrigen;

    @Column(nullable = false)
    private long cantidad;

    @Column(nullable = false)
    private OffsetDateTime actualizadaEn;

    protected FrecuenciaCompra() {
    }

    public String getIdClienteOrigen() {
        return idClienteOrigen;
    }

    public long getCantidad() {
        return cantidad;
    }

    public OffsetDateTime getActualizadaEn() {
        return actualizadaEn;
    }
}
