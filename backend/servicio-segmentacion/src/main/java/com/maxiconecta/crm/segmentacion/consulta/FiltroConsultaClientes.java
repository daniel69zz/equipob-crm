package com.maxiconecta.crm.segmentacion.consulta;

import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import java.util.Set;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** SCRUM-276: filtros globales; tamaño limitado y orden SQL cerrado. */
public record FiltroConsultaClientes(Long segmentoId, String segmento, String nivelConsumo, String nombre,
                                    int pagina, int tamanio, String orden, String direccion) {
    public FiltroConsultaClientes {
        segmento = texto(segmento);
        nombre = texto(nombre);
        nivelConsumo = texto(nivelConsumo).toUpperCase(Locale.ROOT);
        orden = texto(orden);
        direccion = texto(direccion).toLowerCase(Locale.ROOT);
        if (segmentoId != null && segmentoId <= 0) error("El segmento debe ser positivo");
        if (segmentoId != null && !segmento.isEmpty()) error("Seleccione un solo segmento");
        if (segmentoId == null && segmento.isEmpty() && nivelConsumo.isEmpty())
            error("Seleccione un segmento o un nivel de consumo");
        if (segmento.length() > 100 || nombre.length() > 200) error("El filtro supera el largo permitido");
        if (!nivelConsumo.isEmpty() && !Set.of("BAJO", "MEDIO", "ALTO", "SIN_NIVEL").contains(nivelConsumo))
            error("Nivel de consumo inválido");
        if (pagina < 0 || tamanio < 1 || tamanio > 100) error("Página desde 0 y tamaño entre 1 y 100");
        if (!Set.of("nombre", "segmento", "nivelConsumo", "ultimaCompra").contains(orden))
            error("Columna de ordenamiento inválida");
        if (!Set.of("asc", "desc").contains(direccion)) error("Dirección de ordenamiento inválida");
    }
    private static String texto(String valor) { return valor == null ? "" : valor.trim(); }
    private static void error(String mensaje) { throw new ResponseStatusException(BAD_REQUEST, mensaje); }
}
