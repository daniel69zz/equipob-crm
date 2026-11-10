package com.maxiconecta.crm.interacciones.interaccion;

import com.maxiconecta.crm.interacciones.comun.IdentidadGateway;
import com.maxiconecta.crm.interacciones.comun.IdentidadGateway.Identidad;
import com.maxiconecta.crm.interacciones.comun.Validaciones;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * API de interacciones con el cliente (SCRUM-52). Todas las rutas van bajo el cliente, como pide
 * docs/seguridad/convencion-rutas-clientes.md: así el Gateway audita cada consulta y cada cambio con el cliente
 * afectado. El acceso se comprueba antes de validar el cuerpo (SCRUM-435).
 */
@RestController
@RequestMapping("/api/interacciones/clientes/{clienteId}/interacciones")
public class InteraccionesController {

    private final ServicioInteracciones servicio;
    private final Validaciones validaciones;

    public InteraccionesController(ServicioInteracciones servicio, Validaciones validaciones) {
        this.servicio = servicio;
        this.validaciones = validaciones;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Interaccion registrar(@PathVariable long clienteId,
                                 @RequestBody(required = false) SolicitudInteraccion solicitud,
                                 HttpServletRequest http) {
        Identidad identidad = IdentidadGateway.registro(http);
        validaciones.validar(solicitud, "La interacción no es válida");
        return servicio.registrar(clienteId, solicitud, identidad);
    }

    @PutMapping("/{id}")
    public Interaccion modificar(@PathVariable long clienteId, @PathVariable long id,
                                 @RequestBody(required = false) SolicitudCambio solicitud, HttpServletRequest http) {
        Identidad identidad = IdentidadGateway.registro(http);
        validaciones.validar(solicitud, "El cambio no es válido");
        return servicio.modificar(clienteId, id, solicitud, identidad);
    }

    /** Estado de seguimiento o tipo nuevos (SCRUM-55). */
    @PatchMapping("/{id}")
    public Interaccion seguimiento(@PathVariable long clienteId, @PathVariable long id,
                                   @RequestBody(required = false) SolicitudSeguimiento solicitud,
                                   HttpServletRequest http) {
        Identidad identidad = IdentidadGateway.registro(http);
        validaciones.validar(solicitud, "El cambio de seguimiento no es válido");
        return servicio.seguimiento(clienteId, id, solicitud, identidad);
    }

    /** Vincula una compra del historial del cliente, o reemplaza la vinculada (SCRUM-54). */
    @PutMapping("/{id}/compra")
    public Interaccion vincularCompra(@PathVariable long clienteId, @PathVariable long id,
                                      @RequestBody(required = false) SolicitudVinculacion solicitud,
                                      HttpServletRequest http) {
        Identidad identidad = IdentidadGateway.vinculacionCompra(http);
        validaciones.validar(solicitud, "La vinculación de la compra no es válida");
        return servicio.vincularCompra(clienteId, id, solicitud, identidad);
    }

    /** Quita la compra vinculada (SCRUM-54); la versión va como parámetro porque DELETE no lleva cuerpo. */
    @DeleteMapping("/{id}/compra")
    public Interaccion desvincularCompra(@PathVariable long clienteId, @PathVariable long id,
                                         @RequestParam long version, HttpServletRequest http) {
        Identidad identidad = IdentidadGateway.vinculacionCompra(http);
        return servicio.desvincularCompra(clienteId, id, version, identidad);
    }

    /** Asigna la interacción a un agente responsable, o la reasigna (SCRUM-56). */
    @PutMapping("/{id}/responsable")
    public Interaccion asignar(@PathVariable long clienteId, @PathVariable long id,
                               @RequestBody(required = false) SolicitudAsignacion solicitud, HttpServletRequest http) {
        Identidad identidad = IdentidadGateway.registro(http);
        validaciones.validar(solicitud, "La asignación no es válida");
        return servicio.asignar(clienteId, id, solicitud, identidad);
    }

    @GetMapping("/{id}")
    public Interaccion detalle(@PathVariable long clienteId, @PathVariable long id, HttpServletRequest http) {
        IdentidadGateway.consulta(http);
        return servicio.detalle(clienteId, id);
    }

    /** Quién creó y quién cambió la interacción, con los valores anteriores y nuevos (SCRUM-436). */
    @GetMapping("/{id}/auditoria")
    public List<AuditoriaInteracciones.RegistroAuditoria> auditoria(@PathVariable long clienteId, @PathVariable long id,
                                                                    HttpServletRequest http) {
        IdentidadGateway.auditoria(http);
        return servicio.auditoria(clienteId, id);
    }

    /** Historial del cliente, de la más reciente a la más antigua, con los filtros de SCRUM-431. */
    @GetMapping
    public PaginaInteracciones historial(@PathVariable long clienteId,
                                         @RequestParam(required = false) String tipo,
                                         @RequestParam(required = false) String estado,
                                         @RequestParam(required = false) String texto,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                         @RequestParam(required = false) Integer pagina,
                                         @RequestParam(required = false) Integer tamanio,
                                         @RequestParam(required = false) String responsable,
                                         HttpServletRequest http) {
        IdentidadGateway.consulta(http);
        return servicio.historial(clienteId, tipo, estado, texto, desde, hasta, pagina, tamanio, responsable);
    }
}
