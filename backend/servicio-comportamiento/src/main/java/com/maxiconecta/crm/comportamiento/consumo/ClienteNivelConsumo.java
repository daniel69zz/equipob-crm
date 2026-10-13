package com.maxiconecta.crm.comportamiento.consumo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Nivel de consumo vigente de un cliente (docs/compra/nivel-consumo.md, SCRUM-267). Se sobrescribe
 * cada vez que {@link ClasificadorConsumo} reclasifica al cliente y se elimina cuando se queda sin
 * compras vigentes: "sin nivel" no se guarda.
 */
@Entity
@Table(schema = "comportamiento", name = "cliente_nivel_consumo")
public class ClienteNivelConsumo {

    @Id
    @Column(length = 64)
    private String idClienteOrigen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NivelConsumo nivel;

    @Column(nullable = false)
    private BigDecimal valorAcumulado;

    @Column(nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    protected ClienteNivelConsumo() {
    }

    public ClienteNivelConsumo(String idClienteOrigen, NivelConsumo nivel, BigDecimal valorAcumulado) {
        this.idClienteOrigen = idClienteOrigen;
        this.nivel = nivel;
        this.valorAcumulado = valorAcumulado;
    }

    /** Aplica una nueva clasificación; {@code actualizadoEn} solo cambia si el nivel o el valor cambiaron. */
    public void actualizar(NivelConsumo nivel, BigDecimal valorAcumulado) {
        if (this.nivel == nivel && this.valorAcumulado.compareTo(valorAcumulado) == 0) {
            return;
        }
        this.nivel = nivel;
        this.valorAcumulado = valorAcumulado;
        this.actualizadoEn = OffsetDateTime.now();
    }

    public String getIdClienteOrigen() {
        return idClienteOrigen;
    }

    public NivelConsumo getNivel() {
        return nivel;
    }

    public BigDecimal getValorAcumulado() {
        return valorAcumulado;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }
}
