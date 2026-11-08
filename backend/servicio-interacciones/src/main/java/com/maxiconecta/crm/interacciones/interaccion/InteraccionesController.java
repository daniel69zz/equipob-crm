package com.maxiconecta.crm.interacciones.interaccion;

import com.maxiconecta.crm.interacciones.comun.IdentidadGateway;
import com.maxiconecta.crm.interacciones.comun.IdentidadGateway.Identidad;
import com.maxiconecta.crm.interacciones.comun.Validaciones;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * API de interacciones con el cliente (SCRUM-52). El acceso se comprueba antes de validar el cuerpo (SCRUM-435).
 */
@RestController
@RequestMapping("/api/interacciones")
public class InteraccionesController {

    private final ServicioInteracciones servicio;
    private final Validaciones validaciones;

    public InteraccionesController(ServicioInteracciones servicio, Validaciones validaciones) {
        this.servicio = servicio;
        this.validaciones = validaciones;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Interaccion registrar(@RequestBody(required = false) SolicitudInteraccion solicitud,
                                 HttpServletRequest http) {
        Identidad identidad = IdentidadGateway.registro(http);
        validaciones.validar(solicitud, "La interacción no es válida");
        return servicio.registrar(solicitud, identidad);
    }

    @PutMapping("/{id}")
    public Interaccion modificar(@PathVariable long id, @RequestBody(required = false) SolicitudCambio solicitud,
                                 HttpServletRequest http) {
        Identidad identidad = IdentidadGateway.registro(http);
        validaciones.validar(solicitud, "El cambio no es válido");
        return servicio.modificar(id, solicitud, identidad);
    }

    @GetMapping("/{id}")
    public Interaccion detalle(@PathVariable long id, HttpServletRequest http) {
        IdentidadGateway.consulta(http);
        return servicio.detalle(id);
    }

    /** Historial de un cliente, de la más reciente a la más antigua, con los filtros de SCRUM-431. */
    @GetMapping("/clientes/{clienteId}")
    public PaginaInteracciones historial(@PathVariable long clienteId,
                                         @RequestParam(required = false) String tipo,
                                         @RequestParam(required = false) String texto,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                         @RequestParam(required = false) Integer pagina,
                                         @RequestParam(required = false) Integer tamanio,
                                         HttpServletRequest http) {
        IdentidadGateway.consulta(http);
        return servicio.historial(clienteId, tipo, texto, desde, hasta, pagina, tamanio);
    }
}
