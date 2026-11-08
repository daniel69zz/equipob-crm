package com.maxiconecta.crm.fidelizacion.caducidad;

import com.maxiconecta.crm.fidelizacion.movimiento.RegistroPuntos;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Validación de la caducidad en las consultas de saldo (SCRUM-419). El proceso de caducidad corre una vez al día,
 * así que un saldo puede estar vencido sin que se haya descontado todavía: las consultas lo informan como
 * {@code puntosCaducados} y el saldo disponible en 0. No descuentan nada.
 */
@Component
public class SaldoVigente {

    private final RegistroPuntos registro;
    private final VigenciaPuntos vigencia;

    public SaldoVigente(RegistroPuntos registro, VigenciaPuntos vigencia) {
        this.registro = registro;
        this.vigencia = vigencia;
    }

    /** @param saldoRegistrado el saldo guardado del cliente */
    public Estado evaluar(long clienteId, long saldoRegistrado, OffsetDateTime momento) {
        OffsetDateTime fechaCaducidad = registro.ultimaAcumulacion(clienteId)
                .map(fecha -> ReglaCaducidad.fechaCaducidad(fecha, vigencia.meses()))
                .orElse(null);
        boolean vencido = fechaCaducidad != null && saldoRegistrado > 0 && ReglaCaducidad.vencida(fechaCaducidad, momento);
        return new Estado(vencido ? 0 : saldoRegistrado, fechaCaducidad, vencido ? saldoRegistrado : 0);
    }

    public int vigenciaMeses() {
        return vigencia.meses();
    }

    /**
     * {@code fechaCaducidad} es nula si el cliente nunca acumuló puntos. Tras una caducidad sigue indicando el
     * vencimiento ya aplicado, hasta que una compra nueva lo renueve.
     */
    public record Estado(long saldoDisponible, OffsetDateTime fechaCaducidad, long puntosCaducados) {
    }
}
