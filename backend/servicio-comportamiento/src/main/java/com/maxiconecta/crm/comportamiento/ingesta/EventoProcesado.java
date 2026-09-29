package com.maxiconecta.crm.comportamiento.ingesta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Transacción ya procesada. Su existencia impide volver a procesar la misma transacción.
 * <p>
 * Implementa {@link Persistable} para que guardar una clave nueva sea siempre un INSERT: si la
 * clave ya existe, la llave primaria lo rechaza en lugar de actualizar la fila existente.
 */
@Entity
@IdClass(EventoProcesado.Id.class)
@Table(schema = "comportamiento", name = "evento_procesado")
public class EventoProcesado implements Persistable<EventoProcesado.Id> {

    @jakarta.persistence.Id
    private String tipoEvento;

    @jakarta.persistence.Id
    private String idTransaccion;

    @Column(nullable = false)
    private Long idEventoRecibido;

    @Column(nullable = false)
    private OffsetDateTime procesadoEn = OffsetDateTime.now();

    @Transient
    private boolean nuevo = true;

    protected EventoProcesado() {
    }

    public EventoProcesado(ClaveIdempotencia clave, Long idEventoRecibido) {
        this.tipoEvento = clave.tipoEvento();
        this.idTransaccion = clave.idTransaccion();
        this.idEventoRecibido = idEventoRecibido;
    }

    @Override
    public Id getId() {
        return new Id(getClave());
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

    public ClaveIdempotencia getClave() {
        return new ClaveIdempotencia(tipoEvento, idTransaccion);
    }

    public Long getIdEventoRecibido() {
        return idEventoRecibido;
    }

    public OffsetDateTime getProcesadoEn() {
        return procesadoEn;
    }

    /** Llave primaria compuesta, con los mismos nombres que los atributos de la entidad. */
    public static class Id implements Serializable {

        private String tipoEvento;
        private String idTransaccion;

        protected Id() {
        }

        public Id(ClaveIdempotencia clave) {
            this.tipoEvento = clave.tipoEvento();
            this.idTransaccion = clave.idTransaccion();
        }

        @Override
        public boolean equals(Object otro) {
            return otro instanceof Id id && Objects.equals(tipoEvento, id.tipoEvento)
                    && Objects.equals(idTransaccion, id.idTransaccion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tipoEvento, idTransaccion);
        }
    }
}
