package com.maxiconecta.crm.perfil.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

/**
 * Registro de una creación o modificación del perfil: fecha, sistema de origen, responsable y
 * campos cambiados (JSON con {campo, anterior, nuevo}). Es inmutable: la base rechaza UPDATE y DELETE.
 */
@Entity
@Immutable
@Table(schema = "perfil", name = "cambio_perfil")
public class CambioPerfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long idCliente;

    @Column(nullable = false)
    private OffsetDateTime fecha = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCambio tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origen origen;

    @Column(nullable = false)
    private String responsable;

    @Column(nullable = false)
    private String cambios;

    private Long idEvento;

    protected CambioPerfil() {
    }

    public CambioPerfil(Long idCliente, TipoCambio tipo, Origen origen, String responsable, String cambios,
                        Long idEvento) {
        this.idCliente = idCliente;
        this.tipo = tipo;
        this.origen = origen;
        this.responsable = responsable;
        this.cambios = cambios;
        this.idEvento = idEvento;
    }

    public Long getId() {
        return id;
    }

    public Long getIdCliente() {
        return idCliente;
    }

    public OffsetDateTime getFecha() {
        return fecha;
    }

    public TipoCambio getTipo() {
        return tipo;
    }

    public Origen getOrigen() {
        return origen;
    }

    public String getResponsable() {
        return responsable;
    }

    public String getCambios() {
        return cambios;
    }

    public Long getIdEvento() {
        return idEvento;
    }
}
