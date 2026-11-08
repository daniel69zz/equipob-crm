package com.maxiconecta.crm.fidelizacion.ajuste;

import java.time.OffsetDateTime;

/**
 * Un ajuste manual aplicado (SCRUM-421): la variación con signo, el saldo antes y después, el motivo, el
 * responsable y la fecha. {@code nivel} es el nivel del cliente después del ajuste (solo en la respuesta del ajuste).
 */
public record Ajuste(long id, String idTransaccion, long clienteId, long puntos, long saldoAnterior,
                     long saldoResultante, String motivo, String referencia, String usuario, String rol,
                     OffsetDateTime registradoEn, String nivel) {

    Ajuste conNivel(String nivelNuevo) {
        return new Ajuste(id, idTransaccion, clienteId, puntos, saldoAnterior, saldoResultante, motivo, referencia,
                usuario, rol, registradoEn, nivelNuevo);
    }
}
