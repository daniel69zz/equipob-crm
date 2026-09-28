package com.maxiconecta.crm.perfil.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Perfil único del cliente en el CRM.
 */
@Entity
@Table(schema = "perfil", name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombres;

    private String apellidos;

    private String tipoDocumento;

    private String numeroDocumento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPerfil estado = EstadoPerfil.COMPLETO;

    private String motivosIncompleto;

    @Column(nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    private Origen actualizadoPorOrigen;

    private String actualizadoPor;

    public Cliente() {
    }

    public void identificar(String nombres, String apellidos, String tipoDocumento, String numeroDocumento) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
    }

    public void registrarActualizacion(Origen origen, String responsable) {
        this.actualizadoEn = OffsetDateTime.now();
        this.actualizadoPorOrigen = origen;
        this.actualizadoPor = responsable;
    }

    public Long getId() {
        return id;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public EstadoPerfil getEstado() {
        return estado;
    }

    public String getMotivosIncompleto() {
        return motivosIncompleto;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public Origen getActualizadoPorOrigen() {
        return actualizadoPorOrigen;
    }

    public String getActualizadoPor() {
        return actualizadoPor;
    }
}
