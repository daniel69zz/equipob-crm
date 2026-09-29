package com.maxiconecta.crm.comportamiento.compra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Ítem devuelto en una devolución parcial, por categoría de producto.
 */
@Entity
@Table(schema = "comportamiento", name = "anulacion_item")
public class AnulacionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_anulacion")
    private Anulacion anulacion;

    @Column(nullable = false)
    private String categoria;

    @Column(nullable = false)
    private int cantidad;

    @Column(nullable = false)
    private BigDecimal monto;

    protected AnulacionItem() {
    }

    AnulacionItem(Anulacion anulacion, String categoria, int cantidad, BigDecimal monto) {
        this.anulacion = anulacion;
        this.categoria = categoria;
        this.cantidad = cantidad;
        this.monto = monto;
    }

    public Long getId() {
        return id;
    }

    public String getCategoria() {
        return categoria;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BigDecimal getMonto() {
        return monto;
    }
}
