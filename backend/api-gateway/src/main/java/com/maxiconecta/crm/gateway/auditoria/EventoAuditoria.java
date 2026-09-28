package com.maxiconecta.crm.gateway.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

/**
 * Registro de auditoría. Es inmutable: la base de datos rechaza cualquier UPDATE o DELETE.
 */
@Entity
@Immutable
@Table(schema = "auditoria", name = "evento")
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private OffsetDateTime ocurridoEn = OffsetDateTime.now();

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private String operacion;

    @Column(nullable = false)
    private String entidad;

    private String entidadId;

    private String detalle;

    protected EventoAuditoria() {
    }

    public EventoAuditoria(String usuario, String operacion, String entidad, String entidadId, String detalle) {
        this.usuario = usuario;
        this.operacion = operacion;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.detalle = detalle;
    }

    public Long getId() {
        return id;
    }

    public OffsetDateTime getOcurridoEn() {
        return ocurridoEn;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getOperacion() {
        return operacion;
    }

    public String getEntidad() {
        return entidad;
    }

    public String getEntidadId() {
        return entidadId;
    }

    public String getDetalle() {
        return detalle;
    }
}
