package com.maxiconecta.crm.segmentacion.criterio;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/segmentacion/criterios")
public class CriterioController {
    private final CriterioService service;
    public CriterioController(CriterioService service) { this.service = service; }

    @GetMapping
    public List<Criterio> listar(@RequestParam(required = false) Boolean activo,
                                @RequestParam(required = false) Criterio.Indicador indicador, HttpServletRequest req) {
        administrador(req); return service.listar(activo, indicador);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Criterio crear(@Valid @RequestBody SolicitudCriterio s, HttpServletRequest req) {
        return service.crear(s, administrador(req));
    }

    @PutMapping("/{id}")
    public Criterio editar(@PathVariable long id, @Valid @RequestBody SolicitudCriterio s, HttpServletRequest req) {
        return service.editar(id, s, administrador(req));
    }

    @PatchMapping("/{id}/estado")
    public Criterio estado(@PathVariable long id, @Valid @RequestBody Estado s, HttpServletRequest req) {
        return service.cambiarEstado(id, s.activo(), s.version(), administrador(req));
    }

    @GetMapping("/{id}/historial")
    public List<AuditoriaCriterios.Cambio> historial(@PathVariable long id, HttpServletRequest req) {
        administrador(req); return service.historial(id);
    }

    /** Prueba explícita de un caso introducido por el administrador. No asigna segmentos. */
    @PostMapping("/{id}/comprobar")
    public Comprobacion comprobar(@PathVariable long id, @Valid @RequestBody Caso caso, HttpServletRequest req) {
        administrador(req);
        var criterio = service.obtener(id);
        if ((criterio.indicador() == Criterio.Indicador.FRECUENCIA && caso.frecuencia() == null)
                || (criterio.indicador() == Criterio.Indicador.VALOR && caso.valor() == null)
                || (criterio.indicador() == Criterio.Indicador.CATEGORIA && caso.categorias() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el dato del indicador que desea comprobar");
        }
        return new Comprobacion(criterio.id(), criterio.version(), criterio.activo(),
                ReglasCriterio.cumple(criterio, caso.frecuencia(), caso.valor(), caso.categorias()));
    }

    // El Gateway elimina las cabeceras suministradas por el cliente y añade la identidad vigente.
    // El puerto del servicio debe mantenerse en la red interna, como los demás microservicios.
    private String administrador(HttpServletRequest req) {
        String usuario = req.getHeader("X-Usuario"), permisos = req.getHeader("X-Usuario-Permisos");
        if (usuario == null || usuario.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere la identidad del Gateway");
        if (!"ADMINISTRADOR_CRM".equals(req.getHeader("X-Usuario-Rol")) || permisos == null ||
                Arrays.stream(permisos.split(",")).noneMatch("SEGMENTACION_CONFIGURAR"::equals)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el Administrador de CRM puede administrar criterios de segmentación");
        }
        return usuario;
    }

    public record Estado(@NotNull Boolean activo, @NotNull @PositiveOrZero Long version) { }
    public record Caso(@DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal frecuencia,
                       @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal valor,
                       @Size(max = 100) List<@NotBlank @Size(max = 100) String> categorias) { }
    public record Comprobacion(long criterioId, long version, boolean activo, boolean cumple) { }
}
