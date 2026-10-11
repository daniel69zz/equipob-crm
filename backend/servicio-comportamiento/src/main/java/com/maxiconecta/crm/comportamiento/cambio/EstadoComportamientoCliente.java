package com.maxiconecta.crm.comportamiento.cambio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Resultado de la última evaluación de un cliente con compras vigentes
 * (docs/compra/cambios-comportamiento.md). Marca a los clientes "sin línea base"; se sobrescribe en
 * cada evaluación y se elimina cuando el cliente ya no tiene compras vigentes.
 */
@Entity
@Table(schema = "comportamiento", name = "estado_comportamiento_cliente")
public class EstadoComportamientoCliente {

    @Id
    @Column(length = 64)
    private String idClienteOrigen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoComportamiento estado;

    @Column(nullable = false)
    private int comprasLineaBase;

    @Column(nullable = false)
    private OffsetDateTime evaluadoEn;

    protected EstadoComportamientoCliente() {
    }

    public EstadoComportamientoCliente(String idClienteOrigen, EstadoComportamiento estado, int comprasLineaBase,
                                       OffsetDateTime evaluadoEn) {
        this.idClienteOrigen = idClienteOrigen;
        this.estado = estado;
        this.comprasLineaBase = comprasLineaBase;
        this.evaluadoEn = evaluadoEn;
    }

    public void actualizar(EstadoComportamiento estado, int comprasLineaBase, OffsetDateTime evaluadoEn) {
        this.estado = estado;
        this.comprasLineaBase = comprasLineaBase;
        this.evaluadoEn = evaluadoEn;
    }

    public String getIdClienteOrigen() {
        return idClienteOrigen;
    }

    public EstadoComportamiento getEstado() {
        return estado;
    }

    public int getComprasLineaBase() {
        return comprasLineaBase;
    }

    public OffsetDateTime getEvaluadoEn() {
        return evaluadoEn;
    }
}
