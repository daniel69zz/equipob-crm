package com.maxiconecta.crm.segmentacion.consulta;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/segmentacion")
public class ClientesPorSegmentoController {
    private final RepositorioConsultaClientes consulta;
    private final UmbralesConsumo umbrales;
    public ClientesPorSegmentoController(RepositorioConsultaClientes consulta, UmbralesConsumo umbrales) {
        this.consulta = consulta;
        this.umbrales = umbrales;
    }

    @GetMapping("/segmentos")
    public List<RepositorioConsultaClientes.SegmentoDisponible> catalogo(HttpServletRequest request) {
        autorizar(request);
        return consulta.catalogo();
    }

    @GetMapping("/clientes")
    public RepositorioConsultaClientes.Pagina buscar(
            @RequestParam(required = false) Long segmentoId,
            @RequestParam(required = false) String segmento,
            @RequestParam(required = false) String nivelConsumo,
            @RequestParam(required = false) String nombre,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio,
            @RequestParam(defaultValue = "nombre") String orden,
            @RequestParam(defaultValue = "asc") String direccion,
            HttpServletRequest request) {
        autorizar(request);
        var filtro = new FiltroConsultaClientes(segmentoId, segmento, nivelConsumo, nombre,
                pagina, tamanio, orden, direccion);
        var valores = umbrales.obtener();
        try {
            return consulta.buscar(filtro, valores);
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "No se pudo consultar los clientes. Intente nuevamente.", ex);
        }
    }

    // Identidad saneada y enviada por el Gateway; puerto del servicio accesible solo en red interna.
    private void autorizar(HttpServletRequest request) {
        String usuario = request.getHeader("X-Usuario");
        String permisos = request.getHeader("X-Usuario-Permisos");
        if (usuario == null || usuario.isBlank()) throw new ResponseStatusException(UNAUTHORIZED, "Se requiere una sesión");
        if (permisos == null || Arrays.stream(permisos.split(",")).noneMatch("SEGMENTOS_CONSULTAR"::equals))
            throw new ResponseStatusException(FORBIDDEN, "No tiene permiso para consultar segmentos");
    }
}
