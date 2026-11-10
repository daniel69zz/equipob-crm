package com.maxiconecta.crm.comportamiento.reporte;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** Un reporte exportado, listo para descargar como adjunto (SCRUM-60). */
public record ArchivoExportado(String nombre, FormatoExportacion formato, byte[] contenido) {

    public ResponseEntity<byte[]> respuesta() {
        return ResponseEntity.ok()
                .contentType(formato.tipo())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(contenido);
    }
}
