package com.maxiconecta.crm.perfil.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Un campo modificado dentro de una operación del histórico: valor anterior y nuevo.
 * Es inmutable: la base rechaza UPDATE y DELETE.
 */
@Entity
@Immutable
@Table(schema = "perfil", name = "cambio_perfil_detalle")
public class CambioPerfilDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cambio")
    private CambioPerfil cambio;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false)
    private String campo;

    private String valorAnterior;

    private String valorNuevo;

    protected CambioPerfilDetalle() {
    }

    CambioPerfilDetalle(CambioPerfil cambio, int orden, CambioCampo campoCambiado) {
        this.cambio = cambio;
        this.orden = orden;
        this.campo = campoCambiado.campo();
        this.valorAnterior = campoCambiado.anterior();
        this.valorNuevo = campoCambiado.nuevo();
    }

    public Long getId() {
        return id;
    }

    public int getOrden() {
        return orden;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }
}
