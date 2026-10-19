package com.maxiconecta.crm.comportamiento.sugerencia;

import com.maxiconecta.crm.comportamiento.sugerencia.AgregadoSegmento.CategoriaSegmento;
import com.maxiconecta.crm.comportamiento.sugerencia.AgregadoSegmento.ConsumoSegmento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRUM-382: combina el perfil del cliente con el consumo de su segmento. Excluye lo que el cliente ya
 * compra, calcula la afinidad (proporción de compañeros que compran la categoría), aplica los umbrales
 * y ordena por afinidad, monto del segmento y nombre (docs/preferencias/sugerencia-categorias.md).
 */
public final class MotorSugerencias {
    public static final String CRITERIO = "AFINIDAD_SEGMENTO_V1";

    private MotorSugerencias() { }

    public static List<Sugerencia> sugerir(Collection<String> categoriasDelCliente, ConsumoSegmento segmento,
                                           ReglaSugerencias regla) {
        if (segmento.clientesConPreferencias() == 0) { return List.of(); }
        Set<String> propias = categoriasDelCliente.stream().map(MotorSugerencias::clave).collect(Collectors.toSet());
        BigDecimal base = BigDecimal.valueOf(segmento.clientesConPreferencias());
        List<Candidata> candidatas = new ArrayList<>();
        for (CategoriaSegmento c : segmento.categorias()) {
            if (propias.contains(clave(c.categoria())) || c.clientes() < regla.minimoClientes()) { continue; }
            BigDecimal afinidad = BigDecimal.valueOf(c.clientes()).divide(base, 4, RoundingMode.HALF_UP);
            if (afinidad.compareTo(regla.afinidadMinima()) >= 0) { candidatas.add(new Candidata(c, afinidad)); }
        }
        candidatas.sort(Comparator.comparing(Candidata::afinidad).reversed()
                .thenComparing(c -> c.categoria().monto(), Comparator.reverseOrder())
                .thenComparing(c -> c.categoria().categoria(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(c -> c.categoria().categoria()));
        List<Sugerencia> sugerencias = new ArrayList<>();
        for (Candidata c : candidatas.subList(0, Math.min(regla.maximo(), candidatas.size()))) {
            sugerencias.add(new Sugerencia(sugerencias.size() + 1, c.categoria().categoria(), c.afinidad(),
                    c.categoria().clientes(), c.categoria().monto()));
        }
        return List.copyOf(sugerencias);
    }

    /** La misma categoría con otras mayúsculas o espacios no es una categoría nueva para el cliente. */
    private static String clave(String categoria) { return categoria.strip().toLowerCase(Locale.ROOT); }

    private record Candidata(CategoriaSegmento categoria, BigDecimal afinidad) { }

    public record Sugerencia(int posicion, String categoria, BigDecimal afinidad, int clientesQueCompran,
                             BigDecimal montoSegmento) { }
}
