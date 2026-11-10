package com.maxiconecta.crm.comportamiento.reporte;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComparativo.Indicador;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComparativo.Periodo;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Alcance;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Categoria;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Frecuencia;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Valor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** SCRUM-481 · PDF de los reportes de comportamiento de compra (SCRUM-58) y comparativo (SCRUM-62). */
class ExportacionReportesPdfTest {

    private static final OffsetDateTime GENERADO = OffsetDateTime.parse("2026-11-10T09:15:00-04:00");

    @Test
    void elPdfDeComportamientoTieneElEncabezadoLosIndicadoresYLasCategoriasComoEnPantalla() throws IOException {
        byte[] pdf = new ExportacionComportamientoPdf().exportar(comportamiento(false), "luis");

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String texto = texto(pdf);
        contiene(texto, "Reporte de comportamiento de compra");
        contiene(texto, "Segmento Alto valor (2 clientes)");
        contiene(texto, "2026-09-01 a 2026-09-30");
        contiene(texto, "America/La_Paz");
        // Conteos sin decimales y montos con separador de miles y dos decimales, como en pantalla.
        contiene(texto, "Compras COMPRAS 3");
        contiene(texto, "Valor total (Bs) VALOR_TOTAL 1,180.00");
        contiene(texto, "Ticket promedio (Bs) TICKET_PROMEDIO 393.33");
        contiene(texto, "Electrodomésticos 2 5 900.00 76.27 %");
        // El pie dice quién lo exportó y cuándo, y el número de página.
        contiene(texto, "Generado por luis · 2026-11-10 09:15 -04:00");
        contiene(texto, "Página 1");
    }

    @Test
    void unReporteSinDatosLoDiceYMuestraLosPromediosNulosComoRaya() throws IOException {
        String texto = texto(new ExportacionComportamientoPdf().exportar(comportamiento(true), "luis"));

        contiene(texto, "Sin datos: No hay compras vigentes en el periodo.");
        contiene(texto, "Ticket promedio (Bs) TICKET_PROMEDIO —");
        contiene(texto, "No hay compras por categoría en el periodo.");
    }

    @Test
    void elPdfComparativoTieneLosDosPeriodosLaVariacionYLaTendencia() throws IOException {
        ReporteComparativo reporte = new ReporteComparativo(GENERADO,
                new Periodo(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
                new Periodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), "America/La_Paz",
                new Alcance(null, null), false, null, List.of(
                Indicador.de("COMPRAS", "Compras", new BigDecimal("2"), new BigDecimal("4")),
                Indicador.de("VALOR_TOTAL", "Valor total (Bs)", new BigDecimal("250.00"), new BigDecimal("1180.00")),
                Indicador.de("TICKET_PROMEDIO", "Ticket promedio (Bs)", null, new BigDecimal("125.00"))));

        String texto = texto(new ExportacionComparativoPdf().exportar(reporte, "admin"));

        contiene(texto, "Reporte comparativo de indicadores de clientes");
        contiene(texto, "Toda la cartera");
        contiene(texto, "2026-08-01 a 2026-08-31");
        contiene(texto, "Compras 2 4 +2 +100.00 % Crece");
        contiene(texto, "Valor total (Bs) 250.00 1,180.00 +930.00 +372.00 % Crece");
        contiene(texto, "Ticket promedio (Bs) — 125.00 — — Sin base");
        contiene(texto, "Generado por admin");
    }

    @Test
    void losNumerosSeFormateanComoEnPantalla() {
        assertThat(DocumentoPdf.numero(new BigDecimal("1234567.5"), false)).isEqualTo("1,234,567.50");
        assertThat(DocumentoPdf.numero(new BigDecimal("12"), true)).isEqualTo("12");
        assertThat(DocumentoPdf.numero(new BigDecimal("-3.456"), false)).isEqualTo("-3.46");
        assertThat(DocumentoPdf.numero(null, false)).isEqualTo("—");
    }

    private static ReporteComportamiento comportamiento(boolean sinDatos) {
        if (sinDatos) {
            return new ReporteComportamiento(GENERADO, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    "America/La_Paz", new Alcance(null, null), true, "No hay compras vigentes en el periodo.",
                    new Frecuencia(0, 0, null), new Valor(new BigDecimal("0.00"), null, null), List.of());
        }
        return new ReporteComportamiento(GENERADO, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                "America/La_Paz", new Alcance("Alto valor", 2L), false, null,
                new Frecuencia(3, 2, new BigDecimal("1.50")),
                new Valor(new BigDecimal("1180.00"), new BigDecimal("393.33"), new BigDecimal("590.00")),
                List.of(new Categoria("Electrodomésticos", 2, 5, new BigDecimal("900.00"), new BigDecimal("76.27"), false),
                        new Categoria("Sin categoría", 1, 1, new BigDecimal("280.00"), new BigDecimal("23.73"), true)));
    }

    /**
     * El extractor junta las celdas de una fila sin separarlas, así que se compara sin espacios ni saltos de línea.
     */
    private static void contiene(String texto, String esperado) {
        assertThat(texto.replaceAll("\\s+", "")).contains(esperado.replaceAll("\\s+", ""));
    }

    /** El texto de todas las páginas. */
    static String texto(byte[] pdf) throws IOException {
        PdfReader lector = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            List<String> paginas = new ArrayList<>();
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                paginas.add(extractor.getTextFromPage(pagina));
            }
            return String.join(" ", paginas).replaceAll("\\s+", " ");
        } finally {
            lector.close();
        }
    }
}
