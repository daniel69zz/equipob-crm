package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.Origen;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Histórico de cambios de un cliente (SCRUM-22). Solo lectura: no hay rutas para modificarlo ni
 * borrarlo. El Gateway exige CLIENTE_CONSULTAR y registra cada consulta en la auditoría de accesos.
 */
@RestController
public class HistorialController {

    private final ConsultaHistorial consulta;

    public HistorialController(ConsultaHistorial consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/api/perfil/clientes/{clienteId}/historial-cambios")
    public ConsultaHistorial.PaginaHistorial historial(
            @PathVariable Long clienteId,
            @RequestParam(required = false) String campo,
            @RequestParam(required = false) Origen origen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanio) {
        return consulta.buscar(clienteId, new ConsultaHistorial.Filtro(campo, origen, desde, hasta), pagina, tamanio);
    }
}
