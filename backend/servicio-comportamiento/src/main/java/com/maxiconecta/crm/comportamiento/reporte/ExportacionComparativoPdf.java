package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.comparacion.TendenciaConsumo;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComparativo.Indicador;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.maxiconecta.crm.comportamiento.reporte.DocumentoPdf.SIN_VALOR;
import static com.maxiconecta.crm.comportamiento.reporte.DocumentoPdf.numero;

/**
 * Exporta el reporte comparativo entre periodos (SCRUM-62) a PDF (SCRUM-60, SCRUM-481,
 * docs/compra/exportacion-reportes.md), a partir del mismo {@link ReporteComparativo} que el JSON y el CSV.
 */
@Component
public class ExportacionComparativoPdf {

    static final String TITULO = "Reporte comparativo de indicadores de clientes";

    /** Indicadores que son conteos: sin decimales, como en pantalla. */
    private static final Set<String> CONTEOS = Set.of("COMPRAS", "CLIENTES_CON_COMPRAS");

    private static final Map<TendenciaConsumo, String> TENDENCIAS = Map.of(TendenciaConsumo.CRECE, "Crece",
            TendenciaConsumo.DECAE, "Decae", TendenciaConsumo.ESTABLE, "Estable", TendenciaConsumo.NUEVO, "Nuevo");

    public byte[] exportar(ReporteComparativo reporte, String usuario) {
        List<List<String>> encabezado = new ArrayList<>();
        encabezado.add(List.of("Alcance", ExportacionComportamientoCsv.alcance(reporte.alcance())));
        encabezado.add(List.of("Periodo A", reporte.periodoA().desde() + " a " + reporte.periodoA().hasta()));
        encabezado.add(List.of("Periodo B", reporte.periodoB().desde() + " a " + reporte.periodoB().hasta()));
        encabezado.add(List.of("Zona horaria", reporte.zonaHoraria()));
        encabezado.add(List.of("Generado", reporte.generadoEn().toString()));
        DocumentoPdf documento = new DocumentoPdf(TITULO, usuario, reporte.generadoEn()).encabezado(encabezado);
        if (reporte.sinDatos()) {
            documento.nota("Sin datos: " + (reporte.mensaje() == null ? "" : reporte.mensaje()));
        }

        List<List<String>> filas = new ArrayList<>();
        for (Indicador i : reporte.indicadores()) {
            boolean conteo = CONTEOS.contains(i.codigo());
            filas.add(List.of(i.nombre(), numero(i.periodoA(), conteo), numero(i.periodoB(), conteo),
                    i.variacionAbsoluta() == null ? SIN_VALOR : signo(i.variacionAbsoluta()) + numero(i.variacionAbsoluta(), conteo),
                    i.variacionPorcentual() == null ? SIN_VALOR
                            : signo(i.variacionPorcentual()) + numero(i.variacionPorcentual(), false) + " %",
                    i.tendencia() == null ? "Sin base" : TENDENCIAS.get(i.tendencia())));
        }
        documento.seccion("Indicadores comparados")
                .tabla(List.of("Indicador", "Periodo A", "Periodo B", "Variación", "Variación %", "Tendencia"),
                        new float[]{2.6f, 1.3f, 1.3f, 1.3f, 1.3f, 1.1f},
                        new boolean[]{false, true, true, true, true, false}, filas);
        return documento.cerrar();
    }

    private static String signo(BigDecimal valor) {
        return valor.signum() > 0 ? "+" : "";
    }
}
