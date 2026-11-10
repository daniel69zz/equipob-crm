package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.reporte.ExportacionComportamientoCsv.IndicadorExportado;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Categoria;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.maxiconecta.crm.comportamiento.reporte.DocumentoPdf.numero;

/**
 * Exporta el reporte de comportamiento de compra (SCRUM-58) a PDF (SCRUM-60, SCRUM-481,
 * docs/compra/exportacion-reportes.md). Usa el mismo {@link ReporteComportamiento} que el JSON de la pantalla y
 * las mismas partes que el CSV ({@link ExportacionComportamientoCsv}), así que los tres muestran los mismos datos.
 */
@Component
public class ExportacionComportamientoPdf {

    static final String TITULO = "Reporte de comportamiento de compra";

    public byte[] exportar(ReporteComportamiento reporte, String usuario) {
        List<List<String>> encabezado = new ArrayList<>();
        encabezado.add(List.of("Alcance", ExportacionComportamientoCsv.alcance(reporte.alcance())));
        encabezado.add(List.of("Periodo", reporte.desde() + " a " + reporte.hasta()));
        encabezado.add(List.of("Zona horaria", reporte.zonaHoraria()));
        encabezado.add(List.of("Generado", reporte.generadoEn().toString()));
        DocumentoPdf documento = new DocumentoPdf(TITULO, usuario, reporte.generadoEn()).encabezado(encabezado);
        if (reporte.sinDatos()) {
            documento.nota("Sin datos: " + (reporte.mensaje() == null ? "" : reporte.mensaje()));
        }

        List<List<String>> indicadores = new ArrayList<>();
        for (IndicadorExportado i : ExportacionComportamientoCsv.indicadores(reporte)) {
            indicadores.add(List.of(i.nombre(), i.codigo(), numero(i.valor(), i.conteo())));
        }
        documento.seccion("Frecuencia y valor")
                .tabla(List.of("Indicador", "Código", "Valor"), new float[]{3f, 2.5f, 1.5f},
                        new boolean[]{false, false, true}, indicadores);

        List<List<String>> categorias = new ArrayList<>();
        for (Categoria c : reporte.categorias()) {
            categorias.add(List.of(c.categoria(), numero(BigDecimal.valueOf(c.compras()), true),
                    numero(BigDecimal.valueOf(c.unidades()), true), numero(c.monto(), false),
                    numero(c.porcentaje(), false) + " %"));
        }
        documento.seccion("Categorías");
        if (categorias.isEmpty()) {
            documento.nota("No hay compras por categoría en el periodo.");
        } else {
            documento.tabla(List.of("Categoría", "Compras", "Unidades", "Monto (Bs)", "% del monto"),
                    new float[]{3f, 1.2f, 1.2f, 1.6f, 1.4f}, new boolean[]{false, true, true, true, true}, categorias);
        }
        return documento.cerrar();
    }
}
