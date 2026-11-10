package com.maxiconecta.crm.comportamiento.reporte;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Documento PDF con el formato estándar de los reportes de clientes (SCRUM-479, docs/compra/exportacion-reportes.md):
 * A4 vertical, Helvetica, título, bloque de encabezado de pares clave-valor, tablas con los números a la derecha y un
 * pie con quién lo exportó, cuándo y el número de página.
 *
 * <p>Se usa una sola vez: se agregan las partes en orden y {@link #cerrar()} devuelve los bytes.
 */
final class DocumentoPdf {

    /** Lo que se muestra en lugar de un valor nulo, igual que en pantalla. */
    static final String SIN_VALOR = "—";

    private static final float MARGEN = 36f;
    private static final Color TITULOS = new Color(0x1F, 0x3A, 0x5F);
    private static final Color FONDO_TITULOS = new Color(0xE8, 0xEE, 0xF5);
    private static final Color BORDE = new Color(0xD0, 0xD7, 0xDE);
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, TITULOS);
    private static final Font SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TITULOS);
    private static final Font TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font PIE = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm xxx");

    private final ByteArrayOutputStream salida = new ByteArrayOutputStream();
    private final Document documento = new Document(PageSize.A4, MARGEN, MARGEN, MARGEN, MARGEN + 14);

    DocumentoPdf(String titulo, String usuario, OffsetDateTime generadoEn) {
        PdfWriter escritor = PdfWriter.getInstance(documento, salida);
        String pie = "Generado por " + (usuario == null ? "desconocido" : usuario) + " · "
                + generadoEn.format(FECHA_HORA) + " · CRM MaxiConecta";
        escritor.setPageEvent(new Pie(pie));
        documento.addTitle(titulo);
        documento.addAuthor(usuario == null ? "" : usuario);
        documento.addCreator("CRM MaxiConecta");
        documento.open();
        Paragraph encabezado = new Paragraph(titulo, TITULO);
        encabezado.setSpacingAfter(8f);
        documento.add(encabezado);
    }

    /** Pares clave-valor sin bordes, como el bloque de encabezado del CSV. */
    DocumentoPdf encabezado(List<List<String>> pares) {
        PdfPTable tabla = new PdfPTable(new float[]{1.2f, 4f});
        tabla.setWidthPercentage(100);
        tabla.setSpacingAfter(10f);
        for (List<String> par : pares) {
            tabla.addCell(celda(par.get(0), NEGRITA, false, false));
            tabla.addCell(celda(par.get(1), TEXTO, false, false));
        }
        documento.add(tabla);
        return this;
    }

    DocumentoPdf seccion(String titulo) {
        Paragraph parrafo = new Paragraph(titulo, SECCION);
        parrafo.setSpacingBefore(6f);
        parrafo.setSpacingAfter(4f);
        documento.add(parrafo);
        return this;
    }

    DocumentoPdf nota(String texto) {
        Paragraph parrafo = new Paragraph(texto, TEXTO);
        parrafo.setSpacingAfter(6f);
        documento.add(parrafo);
        return this;
    }

    /**
     * Una tabla con fila de títulos que se repite en cada página. {@code numericas} dice qué columnas se alinean a
     * la derecha.
     */
    DocumentoPdf tabla(List<String> titulos, float[] anchos, boolean[] numericas, List<List<String>> filas) {
        PdfPTable tabla = new PdfPTable(anchos);
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        tabla.setSpacingAfter(10f);
        for (int i = 0; i < titulos.size(); i++) {
            PdfPCell celda = celda(titulos.get(i), NEGRITA, numericas[i], true);
            celda.setBackgroundColor(FONDO_TITULOS);
            tabla.addCell(celda);
        }
        for (List<String> fila : filas) {
            for (int i = 0; i < fila.size(); i++) {
                tabla.addCell(celda(fila.get(i), TEXTO, numericas[i], true));
            }
        }
        documento.add(tabla);
        return this;
    }

    byte[] cerrar() {
        documento.close();
        return salida.toByteArray();
    }

    /** Un número como en pantalla: separador de miles y dos decimales, los conteos sin decimales; nulo es «—». */
    static String numero(BigDecimal valor, boolean conteo) {
        if (valor == null) {
            return SIN_VALOR;
        }
        DecimalFormat formato = new DecimalFormat(conteo ? "#,##0" : "#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor);
    }

    private static PdfPCell celda(String texto, Font fuente, boolean derecha, boolean conBorde) {
        PdfPCell celda = new PdfPCell(new Phrase(texto == null ? "" : texto, fuente));
        celda.setHorizontalAlignment(derecha ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        celda.setPadding(4f);
        if (conBorde) {
            celda.setBorder(Rectangle.BOTTOM);
            celda.setBorderColor(BORDE);
        } else {
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setPadding(2f);
        }
        return celda;
    }

    /** Pie de cada página: quién y cuándo a la izquierda, número de página a la derecha. */
    private static final class Pie extends PdfPageEventHelper {

        private final String texto;

        Pie(String texto) {
            this.texto = texto;
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            try {
                float y = documento.bottom() - 18;
                ColumnText.showTextAligned(escritor.getDirectContent(), Element.ALIGN_LEFT,
                        new Phrase(texto, PIE), documento.left(), y, 0);
                ColumnText.showTextAligned(escritor.getDirectContent(), Element.ALIGN_RIGHT,
                        new Phrase("Página " + escritor.getPageNumber(), PIE), documento.right(), y, 0);
            } catch (DocumentException e) {
                throw new IllegalStateException("No se pudo escribir el pie del PDF", e);
            }
        }
    }
}
