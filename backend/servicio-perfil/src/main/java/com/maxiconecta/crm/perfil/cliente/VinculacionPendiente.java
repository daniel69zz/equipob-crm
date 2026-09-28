package com.maxiconecta.crm.perfil.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

/**
 * Identificador de origen desconocido que coincide con un perfil existente. No se vincula solo:
 * un administrador confirma el vínculo o decide que es otra persona (docs/perfil/identificadores-origen.md).
 */
@Entity
@IdClass(ClienteOrigen.Clave.class)
@Table(schema = "perfil", name = "vinculacion_pendiente")
public class VinculacionPendiente implements Persistable<ClienteOrigen.Clave> {

    @jakarta.persistence.Id
    @Enumerated(EnumType.STRING)
    private Origen origen;

    @jakarta.persistence.Id
    private String idClienteOrigen;

    private Long idClienteSugerido;

    @Column(nullable = false)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoVinculacion estado = EstadoVinculacion.PENDIENTE;

    @Column(nullable = false)
    private OffsetDateTime detectadaEn = OffsetDateTime.now();

    private String resueltaPor;

    private OffsetDateTime resueltaEn;

    @Transient
    private boolean nuevo = true;

    protected VinculacionPendiente() {
    }

    public VinculacionPendiente(Origen origen, String idClienteOrigen, Long idClienteSugerido, String motivo) {
        this.origen = origen;
        this.idClienteOrigen = idClienteOrigen;
        this.idClienteSugerido = idClienteSugerido;
        this.motivo = motivo;
    }

    public void resolver(EstadoVinculacion decision, String responsable) {
        this.estado = decision;
        this.resueltaPor = responsable;
        this.resueltaEn = OffsetDateTime.now();
    }

    @Override
    public ClienteOrigen.Clave getId() {
        return new ClienteOrigen.Clave(origen, idClienteOrigen);
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }

    @PostLoad
    @PostPersist
    void marcarExistente() {
        nuevo = false;
    }

    public Origen getOrigen() {
        return origen;
    }

    public String getIdClienteOrigen() {
        return idClienteOrigen;
    }

    public Long getIdClienteSugerido() {
        return idClienteSugerido;
    }

    public String getMotivo() {
        return motivo;
    }

    public EstadoVinculacion getEstado() {
        return estado;
    }

    public OffsetDateTime getDetectadaEn() {
        return detectadaEn;
    }

    public String getResueltaPor() {
        return resueltaPor;
    }

    public OffsetDateTime getResueltaEn() {
        return resueltaEn;
    }
}
