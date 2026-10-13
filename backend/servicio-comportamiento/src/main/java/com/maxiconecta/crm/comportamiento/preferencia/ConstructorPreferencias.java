package com.maxiconecta.crm.comportamiento.preferencia;

import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.ConsumoEnRango;
import com.maxiconecta.crm.comportamiento.compra.LineaConsumoCategoria;
import com.maxiconecta.crm.comportamiento.indicador.RankingCategorias;
import com.maxiconecta.crm.comportamiento.preferencia.PerfilPreferencias.CategoriaPreferida;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** SCRUM-360, 362 y 365: valida primero, reutiliza el ranking y elimina solo lo no asignado. */
public final class ConstructorPreferencias {
    private ConstructorPreferencias() { }

    public static List<CategoriaPreferida> construir(ConsumoEnRango consumo) {
        validar(consumo);
        List<CategoriaPreferida> categorias = new ArrayList<>();
        RankingCategorias.calcular(consumo.compradas(), consumo.devueltas()).stream()
                .filter(c -> !c.sinCategoria())
                .forEach(c -> categorias.add(new CategoriaPreferida(categorias.size() + 1, c.categoria(),
                        c.compras(), c.unidades(), c.monto())));
        return List.copyOf(categorias);
    }

    private static void validar(ConsumoEnRango consumo) {
        Set<Long> compras = consumo.compras().stream().map(c -> c.idCompra()).collect(Collectors.toSet());
        if (compras.size() != consumo.compras().size()
                || consumo.compras().stream().anyMatch(c -> c.montoVigente() == null || c.montoVigente().signum() <= 0)
                || Stream.concat(consumo.compradas().stream(), consumo.devueltas().stream())
                    .anyMatch(l -> !compras.contains(l.idCompra()) || invalida(l))) {
            throw inconsistentes();
        }
        Map<Clave, Cantidades> comprado = agrupar(consumo.compradas());
        Map<Clave, Cantidades> devuelto = agrupar(consumo.devueltas());
        devuelto.forEach((clave, cantidad) -> {
            Cantidades original = comprado.get(clave);
            if (original == null || cantidad.unidades() > original.unidades()
                    || cantidad.monto().compareTo(original.monto()) > 0) {
                throw inconsistentes();
            }
        });
        Map<Long, BigDecimal> desglose = new HashMap<>();
        comprado.forEach((clave, cantidad) -> {
            BigDecimal devolucion = devuelto.containsKey(clave) ? devuelto.get(clave).monto() : BigDecimal.ZERO;
            desglose.merge(clave.compra(), cantidad.monto().subtract(devolucion), BigDecimal::add);
        });
        if (consumo.compras().stream().anyMatch(c -> c.montoVigente()
                .compareTo(desglose.getOrDefault(c.idCompra(), BigDecimal.ZERO)) != 0)) { throw inconsistentes(); }
    }

    private static Map<Clave, Cantidades> agrupar(List<LineaConsumoCategoria> lineas) {
        Map<Clave, Cantidades> resultado = new HashMap<>();
        lineas.forEach(l -> {
            // null es ausencia de categoría, distinta de cualquier nombre de presentación.
            String categoria = l.categoria() == null || l.categoria().isBlank() ? null : l.categoria().strip();
            resultado.merge(new Clave(l.idCompra(), categoria), new Cantidades(l.unidades(), l.monto()), Cantidades::mas);
        });
        return resultado;
    }

    private record Clave(long compra, String categoria) { }
    private record Cantidades(long unidades, BigDecimal monto) {
        Cantidades mas(Cantidades otra) { return new Cantidades(unidades + otra.unidades, monto.add(otra.monto)); }
    }

    private static boolean invalida(LineaConsumoCategoria l) {
        return l.unidades() < 0 || l.monto() == null || l.monto().signum() < 0
                || (l.categoria() != null && l.categoria().strip().length() > 100);
    }

    private static PreferenciasException inconsistentes() {
        return new PreferenciasException(HttpStatus.CONFLICT,
                "El consumo contiene montos, devoluciones o desgloses inconsistentes; se conserva el perfil anterior");
    }
}
