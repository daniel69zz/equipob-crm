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
 * Identificador del cliente en Marketplace o Ventas, vinculado a un perfil del CRM (RF-62).
 * <p>
 * Implementa {@link Persistable} para que vincular un identificador nuevo sea siempre un INSERT:
 * si otro mensaje ya lo vinculó, la llave primaria lo rechaza.
 */
@Entity
@IdClass(ClienteOrigen.Clave.class)
@Table(schema = "perfil", name = "cliente_origen")
public class ClienteOrigen implements Persistable<ClienteOrigen.Clave> {

    public static final String ALTA_AUTOMATICA = "ALTA_AUTOMATICA";
    public static final String VINCULACION_MANUAL = "VINCULACION_MANUAL";
    public static final String UNIFICACION = "UNIFICACION";
    public static final String SINCRONIZACION_AUTOMATICA = "sincronizacion-automatica";

    @jakarta.persistence.Id
    @Enumerated(EnumType.STRING)
    private Origen origen;

    @jakarta.persistence.Id
    private String idClienteOrigen;

    @Column(nullable = false)
    private Long idCliente;

    @Column(nullable = false)
    private OffsetDateTime fechaVinculacion = OffsetDateTime.now();

    @Column(nullable = false)
    private String motivoVinculacion = ALTA_AUTOMATICA;

    private OffsetDateTime ultimaActualizacionOrigen;

    @Column(nullable = false)
    private String vinculadoPor = SINCRONIZACION_AUTOMATICA;

    @Transient
    private boolean nuevo = true;

    protected ClienteOrigen() {
    }

    public ClienteOrigen(Origen origen, String idClienteOrigen, Long idCliente, OffsetDateTime ultimaActualizacionOrigen) {
        this.origen = origen;
        this.idClienteOrigen = idClienteOrigen;
        this.idCliente = idCliente;
        this.ultimaActualizacionOrigen = ultimaActualizacionOrigen;
    }

    /** Vínculo hecho por una persona, no por la llegada de un evento. */
    public static ClienteOrigen vincular(Origen origen, String idClienteOrigen, Long idCliente, String motivo,
                                         String vinculadoPor, OffsetDateTime ultimaActualizacionOrigen) {
        ClienteOrigen vinculo = new ClienteOrigen(origen, idClienteOrigen, idCliente, ultimaActualizacionOrigen);
        vinculo.motivoVinculacion = motivo;
        vinculo.vinculadoPor = vinculadoPor;
        return vinculo;
    }

    public void registrarActualizacion(OffsetDateTime fechaCambio) {
        this.ultimaActualizacionOrigen = fechaCambio;
    }

    @Override
    public Clave getId() {
        return new Clave(origen, idClienteOrigen);
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

    public Long getIdCliente() {
        return idCliente;
    }

    public OffsetDateTime getFechaVinculacion() {
        return fechaVinculacion;
    }

    public String getMotivoVinculacion() {
        return motivoVinculacion;
    }

    public OffsetDateTime getUltimaActualizacionOrigen() {
        return ultimaActualizacionOrigen;
    }

    public String getVinculadoPor() {
        return vinculadoPor;
    }

    /** Llave primaria: canal e identificador del cliente en ese canal. */
    public static class Clave implements Serializable {

        private Origen origen;
        private String idClienteOrigen;

        protected Clave() {
        }

        public Clave(Origen origen, String idClienteOrigen) {
            this.origen = origen;
            this.idClienteOrigen = idClienteOrigen;
        }

        @Override
        public boolean equals(Object otro) {
            return otro instanceof Clave clave && origen == clave.origen
                    && Objects.equals(idClienteOrigen, clave.idClienteOrigen);
        }

        @Override
        public int hashCode() {
            return Objects.hash(origen, idClienteOrigen);
        }

        @Override
        public String toString() {
            return origen + "/" + idClienteOrigen;
        }
    }
}
