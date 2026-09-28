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

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Qué sistema puso el valor actual de un campo del perfil, y la fecha de ese cambio en su sistema.
 */
@Entity
@IdClass(CampoOrigen.Clave.class)
@Table(schema = "perfil", name = "campo_origen")
public class CampoOrigen implements Persistable<CampoOrigen.Clave> {

    @jakarta.persistence.Id
    private Long idCliente;

    @jakarta.persistence.Id
    private String campo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origen origen;

    @Column(nullable = false)
    private OffsetDateTime actualizadoEn;

    @Transient
    private boolean nuevo = true;

    protected CampoOrigen() {
    }

    public CampoOrigen(Long idCliente, String campo, Origen origen, OffsetDateTime actualizadoEn) {
        this.idCliente = idCliente;
        this.campo = campo;
        this.origen = origen;
        this.actualizadoEn = actualizadoEn;
    }

    public void registrar(Origen nuevoOrigen, OffsetDateTime fecha) {
        this.origen = nuevoOrigen;
        this.actualizadoEn = fecha;
    }

    @Override
    public Clave getId() {
        return new Clave(idCliente, campo);
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

    public String getCampo() {
        return campo;
    }

    public Origen getOrigen() {
        return origen;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public static class Clave implements Serializable {

        private Long idCliente;
        private String campo;

        protected Clave() {
        }

        public Clave(Long idCliente, String campo) {
            this.idCliente = idCliente;
            this.campo = campo;
        }

        @Override
        public boolean equals(Object otro) {
            return otro instanceof Clave clave && Objects.equals(idCliente, clave.idCliente)
                    && Objects.equals(campo, clave.campo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idCliente, campo);
        }
    }
}
