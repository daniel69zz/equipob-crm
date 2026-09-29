package com.maxiconecta.crm.comportamiento.compra;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Anulación total o devolución parcial de una compra, registrada a partir de un evento RIO-CRM-05.
 */
@Entity
@Table(schema = "comportamiento", name = "anulacion")
public class Anulacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String idAnulacionOrigen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_compra")
    private Compra compra;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoAnulacion tipo;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    @Column(nullable = false)
    private BigDecimal montoRevertido;

    @Column(nullable = false)
    private String motivo;

    @Column(name = "id_evento", nullable = false)
    private Long idEvento;

    @Column(nullable = false)
    private OffsetDateTime registradaEn = OffsetDateTime.now();

    @OneToMany(mappedBy = "anulacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<AnulacionItem> items = new ArrayList<>();

    protected Anulacion() {
    }

    public Anulacion(String idAnulacionOrigen, Compra compra, TipoAnulacion tipo, OffsetDateTime fecha,
                     BigDecimal montoRevertido, String motivo, Long idEvento) {
        this.idAnulacionOrigen = idAnulacionOrigen;
        this.compra = compra;
        this.tipo = tipo;
        this.fecha = fecha;
        this.montoRevertido = montoRevertido;
        this.motivo = motivo;
        this.idEvento = idEvento;
    }

    public void agregarItem(String categoria, int cantidad, BigDecimal monto) {
        items.add(new AnulacionItem(this, categoria, cantidad, monto));
    }

    public Long getId() {
        return id;
    }

    public String getIdAnulacionOrigen() {
        return idAnulacionOrigen;
    }

    public Compra getCompra() {
        return compra;
    }

    public TipoAnulacion getTipo() {
        return tipo;
    }

    public OffsetDateTime getFecha() {
        return fecha;
    }

    public BigDecimal getMontoRevertido() {
        return montoRevertido;
    }

    public String getMotivo() {
        return motivo;
    }

    public Long getIdEvento() {
        return idEvento;
    }

    public OffsetDateTime getRegistradaEn() {
        return registradaEn;
    }

    public List<AnulacionItem> getItems() {
        return items;
    }
}
