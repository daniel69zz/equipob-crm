package com.maxiconecta.crm.perfil.consentimiento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Gestión del consentimiento de tratamiento de datos (docs/perfil/consentimiento-datos.md). El
 * Gateway exige CLIENTE_CONSULTAR para las consultas y CLIENTE_EDITAR para los cambios, y audita
 * cada acceso. El responsable de un cambio es el usuario que envía el Gateway.
 */
@RestController
@RequestMapping("/api/perfil/clientes/{clienteId}/consentimiento")
public class ConsentimientoController {

    static final String CABECERA_USUARIO = "X-Usuario";
    static final String USUARIO_DESCONOCIDO = "desconocido";

    private final GestionConsentimiento gestion;

    public ConsentimientoController(GestionConsentimiento gestion) {
        this.gestion = gestion;
    }

    @GetMapping
    public ConsentimientoResponse consultar(@PathVariable Long clienteId) {
        return ConsentimientoResponse.de(gestion.consultar(clienteId));
    }

    @PutMapping
    public ConsentimientoResponse registrar(@PathVariable Long clienteId,
                                            @Valid @RequestBody SolicitudConsentimiento solicitud,
                                            @RequestHeader(value = CABECERA_USUARIO, required = false) String usuario) {
        return ConsentimientoResponse.de(gestion.registrar(clienteId, solicitud, responsable(usuario)));
    }

    @PostMapping("/revocacion")
    public ConsentimientoResponse revocar(@PathVariable Long clienteId,
                                          @Valid @RequestBody(required = false) RevocacionRequest solicitud,
                                          @RequestHeader(value = CABECERA_USUARIO, required = false) String usuario) {
        String motivo = solicitud != null ? solicitud.motivo() : null;
        return ConsentimientoResponse.de(gestion.revocar(clienteId, motivo, responsable(usuario)));
    }

    @GetMapping("/historial")
    public List<RegistroResponse> historial(@PathVariable Long clienteId) {
        return gestion.historial(clienteId).stream().map(RegistroResponse::de).toList();
    }

    private static String responsable(String usuario) {
        return usuario != null && !usuario.isBlank() ? usuario.trim() : USUARIO_DESCONOCIDO;
    }

    public record RevocacionRequest(@Size(max = 300) String motivo) {
    }

    public record ConsentimientoResponse(Long idCliente, EstadoConsentimiento estado, boolean vigente,
                                         CanalConsentimiento canal, Set<AlcanceConsentimiento> alcances,
                                         OffsetDateTime fechaOtorgamiento, LocalDate vigenciaDesde,
                                         LocalDate vigenciaHasta, OffsetDateTime fechaRevocacion,
                                         String motivoRevocacion, OffsetDateTime actualizadoEn,
                                         String actualizadoPor) {

        static ConsentimientoResponse de(Consentimiento c) {
            return new ConsentimientoResponse(c.getIdCliente(), c.getEstado(), c.estaVigente(LocalDate.now()),
                    c.getCanal(), c.getAlcances(), c.getFechaOtorgamiento(), c.getVigenciaDesde(),
                    c.getVigenciaHasta(), c.getFechaRevocacion(), c.getMotivoRevocacion(), c.getActualizadoEn(),
                    c.getActualizadoPor());
        }
    }

    public record RegistroResponse(Long id, OffsetDateTime fecha, OperacionConsentimiento operacion,
                                   EstadoConsentimiento estado, CanalConsentimiento canal,
                                   Set<AlcanceConsentimiento> alcances, OffsetDateTime fechaOtorgamiento,
                                   LocalDate vigenciaDesde, LocalDate vigenciaHasta, OffsetDateTime fechaRevocacion,
                                   String motivoRevocacion, String responsable) {

        static RegistroResponse de(RegistroConsentimiento r) {
            return new RegistroResponse(r.getId(), r.getFecha(), r.getOperacion(), r.getEstado(), r.getCanal(),
                    r.getAlcances(), r.getFechaOtorgamiento(), r.getVigenciaDesde(), r.getVigenciaHasta(),
                    r.getFechaRevocacion(), r.getMotivoRevocacion(), r.getResponsable());
        }
    }
}
