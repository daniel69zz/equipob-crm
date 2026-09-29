package com.maxiconecta.crm.segmentacion.segmento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Segmento vigente de un cliente (SCRUM-149). La asignación en sí (criterios, umbrales) no es
 * parte de esta historia: este servicio solo responde la consulta síncrona del segmento ya
 * asignado, que consolida la ficha integral (SCRUM-10).
 */
@Entity
@Table(schema = "segmentacion", name = "segmento_cliente")
public class SegmentoCliente {

    @Id
    private Long clienteId;

    @Column(nullable = false)
    private String segmento;

    @Column(nullable = false)
    private OffsetDateTime asignadoEn;

    protected SegmentoCliente() {
    }

    public SegmentoCliente(Long clienteId, String segmento, OffsetDateTime asignadoEn) {
        this.clienteId = clienteId;
        this.segmento = segmento;
        this.asignadoEn = asignadoEn;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public String getSegmento() {
        return segmento;
    }

    public OffsetDateTime getAsignadoEn() {
        return asignadoEn;
    }
}
