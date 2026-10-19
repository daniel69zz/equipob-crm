package com.maxiconecta.crm.comportamiento.sugerencia;

import com.maxiconecta.crm.comportamiento.sugerencia.MotorSugerencias.Sugerencia;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Categorías sugeridas a un cliente (SCRUM-381). Sin sugerencias, {@code motivo} dice por qué
 * (criterio de aceptación 3) y {@code mensaje} lo explica en lenguaje del negocio.
 */
public record SugerenciasCliente(long clienteId, String segmento, OffsetDateTime generadoEn, String criterio,
                                 int clientesDelSegmento, List<Sugerencia> sugerencias, boolean sinSugerencias,
                                 Motivo motivo, String mensaje) {
    public SugerenciasCliente { sugerencias = List.copyOf(sugerencias); }

    public enum Motivo {
        SIN_PERFIL("El cliente aún no tiene perfil de preferencias"),
        SIN_PREFERENCIAS("El perfil de preferencias del cliente no tiene categorías"),
        SIN_SEGMENTO("El cliente no tiene un segmento asignado"),
        SIN_DATOS_SEGMENTO("Ningún otro cliente de su segmento tiene preferencias registradas"),
        SIN_CATEGORIAS_NUEVAS("Su segmento no compra categorías nuevas para este cliente");

        private final String mensaje;
        Motivo(String mensaje) { this.mensaje = mensaje; }
        public String mensaje() { return mensaje; }
    }
}
