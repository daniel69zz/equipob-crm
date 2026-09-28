package com.maxiconecta.crm.comportamiento.compra;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Compra confirmada de un cliente, registrada a partir de un evento de Marketplace y Ventas.
 */
@Entity
@Table(schema = "comportamiento", name = "compra")
public class Compra {

    public static final String CONFIRMADA = "CONFIRMADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origen origen;

    @Column(nullable = false)
    private String idCompraOrigen;

    @Column(nullable = false)
    private String idClienteOrigen;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    @Column(nullable = false)
    private BigDecimal montoTotal;

    @Column(nullable = false)
    private String estado = CONFIRMADA;

    @Column(name = "id_evento", nullable = false)
    private Long idEvento;

    @Column(nullable = false)
    private OffsetDateTime registradaEn = OffsetDateTime.now();

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CompraItem> items = new ArrayList<>();

    protected Compra() {
    }

    public Compra(Origen origen, String idCompraOrigen, String idClienteOrigen, OffsetDateTime fecha,
                  BigDecimal montoTotal, Long idEvento) {
        this.origen = origen;
        this.idCompraOrigen = idCompraOrigen;
        this.idClienteOrigen = idClienteOrigen;
        this.fecha = fecha;
        this.montoTotal = montoTotal;
        this.idEvento = idEvento;
    }

    public void agregarItem(String categoria, int cantidad, BigDecimal monto) {
        items.add(new CompraItem(this, categoria, cantidad, monto));
    }

    public Long getId() {
        return id;
    }

    public Origen getOrigen() {
        return origen;
    }

    public String getIdCompraOrigen() {
        return idCompraOrigen;
    }

    public String getIdClienteOrigen() {
        return idClienteOrigen;
    }

    public OffsetDateTime getFecha() {
        return fecha;
    }

    public BigDecimal getMontoTotal() {
        return montoTotal;
    }

    public String getEstado() {
        return estado;
    }

    public Long getIdEvento() {
        return idEvento;
    }

    public OffsetDateTime getRegistradaEn() {
        return registradaEn;
    }

    public List<CompraItem> getItems() {
        return items;
    }
}
