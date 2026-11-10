package com.maxiconecta.crm.comportamiento.reporte;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;

@RestController
@RequestMapping("/api/comportamiento/reportes/programaciones")
public class ProgramacionReportesController {
    private final ProgramacionReportes servicio;
    public ProgramacionReportesController(ProgramacionReportes servicio) { this.servicio = servicio; }

    @GetMapping
    public List<ProgramacionReportes.Programacion> listar(@RequestHeader("X-Usuario") String usuario) {
        return servicio.listar(usuario);
    }
    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String, UUID> crear(@RequestHeader("X-Usuario") String usuario, @Valid @RequestBody SolicitudProgramacion s) {
        return Map.of("id", servicio.guardar(usuario, null, s));
    }
    @PutMapping("/{id}")
    public void editar(@RequestHeader("X-Usuario") String usuario, @PathVariable UUID id, @Valid @RequestBody SolicitudProgramacion s) {
        servicio.guardar(usuario, id, s);
    }
    public record CambioEstado(String estado) {}
    @PutMapping("/{id}/estado")
    public void estado(@RequestHeader("X-Usuario") String usuario, @PathVariable UUID id, @RequestBody CambioEstado s) {
        servicio.estado(usuario, id, s.estado() == null ? "" : s.estado());
    }
    @GetMapping("/ejecuciones")
    public List<ProgramacionReportes.Ejecucion> historial(@RequestHeader("X-Usuario") String usuario,
                                                         @RequestParam(defaultValue = "0") int pagina) {
        return servicio.historial(usuario, pagina);
    }
    @GetMapping("/avisos")
    public Map<String, Long> avisos(@RequestHeader("X-Usuario") String usuario) {
        return Map.of("noLeidas", servicio.noLeidas(usuario));
    }
    @GetMapping("/ejecuciones/{id}/archivo")
    public ResponseEntity<byte[]> archivo(@RequestHeader("X-Usuario") String usuario, @PathVariable UUID id) {
        return servicio.descargar(usuario, id).respuesta();
    }
    @PutMapping("/ejecuciones/{id}/leida")
    public void leer(@RequestHeader("X-Usuario") String usuario, @PathVariable UUID id) { servicio.leer(usuario, id); }
}
