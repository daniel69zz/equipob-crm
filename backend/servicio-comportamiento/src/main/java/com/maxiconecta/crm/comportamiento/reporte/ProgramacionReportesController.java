package com.maxiconecta.crm.comportamiento.reporte;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reportes programados del usuario autenticado (SCRUM-61). Toda operación usa la identidad del Gateway
 * ({@code X-Usuario}): cada usuario ve y gestiona solo sus programaciones, archivos y avisos. Los permisos los
 * controla {@link PermisoReportes}.
 */
@RestController
@RequestMapping("/api/comportamiento/reportes/programaciones")
public class ProgramacionReportesController {

    private final ProgramacionReportes servicio;

    public ProgramacionReportesController(ProgramacionReportes servicio) {
        this.servicio = servicio;
    }

    public record CambioEstado(@NotNull EstadoProgramacion estado) {
    }

    @GetMapping
    public List<ProgramacionReportes.Programacion> listar(@RequestHeader("X-Usuario") String usuario) {
        return servicio.listar(usuario);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> crear(@RequestHeader("X-Usuario") String usuario,
                                   @Valid @RequestBody SolicitudProgramacion solicitud) {
        return Map.of("id", servicio.guardar(usuario, null, solicitud));
    }

    @PutMapping("/{id}")
    public void editar(@RequestHeader("X-Usuario") String usuario, @PathVariable UUID id,
                       @Valid @RequestBody SolicitudProgramacion solicitud) {
        servicio.guardar(usuario, id, solicitud);
    }

    @PutMapping("/{id}/estado")
    public void estado(@RequestHeader("X-Usuario") String usuario, @PathVariable UUID id,
                       @Valid @RequestBody CambioEstado cambio) {
        servicio.estado(usuario, id, cambio.estado());
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
    public void leer(@RequestHeader("X-Usuario") String usuario, @PathVariable UUID id) {
        servicio.leer(usuario, id);
    }
}
