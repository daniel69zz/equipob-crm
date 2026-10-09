package com.maxiconecta.crm.comportamiento.evolucion;

import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.CompraEnRango;
import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.ConsumoEnRango;
import com.maxiconecta.crm.comportamiento.compra.LineaConsumoCategoria;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Valida la consistencia y la calidad del consumo antes de calcular la evolución (SCRUM-306,
 * docs/compra/evolucion-consumo-categoria.md#calidad-de-los-datos-scrum-306). Corrige lo que el
 * cálculo no podría usar —una categoría sin asignar se agrupa como {@link #SIN_CATEGORIA}, un monto
 * faltante cuenta como cero— y verifica que lo devuelto y el desglose de cada compra sean
 * coherentes, informando cada caso en {@link CalidadDatos} sin interrumpir el cálculo.
 */
public final class DepuracionConsumo {

    public static final String SIN_CATEGORIA = "Sin categoría";

    private DepuracionConsumo() {
    }

    public static Resultado depurar(ConsumoEnRango consumo) {
        Contadores contadores = new Contadores();
        List<LineaConsumoCategoria> compradas = consumo.compradas().stream()
                .map(linea -> normalizar(linea, contadores)).toList();
        List<LineaConsumoCategoria> devueltas = consumo.devueltas().stream()
                .map(linea -> normalizar(linea, contadores)).toList();

        Map<Clave, Cantidades> comprado = porCompraYCategoria(compradas);
        Map<Clave, Cantidades> devuelto = porCompraYCategoria(devueltas);
        long devolucionesInconsistentes = devuelto.entrySet().stream()
                .filter(e -> !e.getValue().cabeEn(comprado.get(e.getKey())))
                .count();

        Map<Long, BigDecimal> desglosePorCompra = new HashMap<>();
        comprado.forEach((clave, cantidades) -> {
            Cantidades restante = cantidades.menos(devuelto.get(clave));
            desglosePorCompra.merge(clave.idCompra(), restante.monto().max(BigDecimal.ZERO), BigDecimal::add);
        });
        long desgloseInconsistente = consumo.compras().stream()
                .filter(compra -> !desgloseCoincide(compra, desglosePorCompra))
                .count();

        CalidadDatos calidad = CalidadDatos.de(consumo.compras().size(), consumo.comprasAnuladas(),
                contadores.sinCategoria, contadores.sinMonto, devolucionesInconsistentes, desgloseInconsistente);
        return new Resultado(new ConsumoEnRango(consumo.compras(), compradas, devueltas, consumo.comprasAnuladas()),
                calidad);
    }

    private static LineaConsumoCategoria normalizar(LineaConsumoCategoria linea, Contadores contadores) {
        String categoria = linea.categoria() == null ? "" : linea.categoria().strip();
        if (categoria.isEmpty()) {
            contadores.sinCategoria++;
            categoria = SIN_CATEGORIA;
        }
        BigDecimal monto = linea.monto();
        if (monto == null) {
            contadores.sinMonto++;
            monto = BigDecimal.ZERO;
        }
        return new LineaConsumoCategoria(linea.idCompra(), categoria, linea.unidades(), monto);
    }

    /** Suma en un solo registro las líneas que, una vez normalizadas, son de la misma compra y categoría. */
    private static Map<Clave, Cantidades> porCompraYCategoria(List<LineaConsumoCategoria> lineas) {
        Map<Clave, Cantidades> acumulado = new LinkedHashMap<>();
        lineas.forEach(l -> acumulado.merge(new Clave(l.idCompra(), l.categoria()),
                new Cantidades(l.unidades(), l.monto()), Cantidades::mas));
        return acumulado;
    }

    private static boolean desgloseCoincide(CompraEnRango compra, Map<Long, BigDecimal> desglosePorCompra) {
        BigDecimal desglose = desglosePorCompra.getOrDefault(compra.idCompra(), BigDecimal.ZERO);
        return compra.montoVigente() != null && desglose.compareTo(compra.montoVigente()) == 0;
    }

    /** Consumo listo para el cálculo y lo que se encontró al revisarlo. */
    public record Resultado(ConsumoEnRango consumo, CalidadDatos calidad) {
    }

    private record Clave(long idCompra, String categoria) {
    }

    private record Cantidades(long unidades, BigDecimal monto) {

        Cantidades mas(Cantidades otra) {
            return new Cantidades(unidades + otra.unidades, monto.add(otra.monto));
        }

        Cantidades menos(Cantidades otra) {
            return otra == null ? this : new Cantidades(unidades - otra.unidades, monto.subtract(otra.monto));
        }

        /** Lo devuelto es coherente si la compra tenía esa categoría y no se devolvió más de lo comprado. */
        boolean cabeEn(Cantidades comprado) {
            return comprado != null && unidades <= comprado.unidades && monto.compareTo(comprado.monto) <= 0;
        }
    }

    private static final class Contadores {
        private long sinCategoria;
        private long sinMonto;
    }
}
