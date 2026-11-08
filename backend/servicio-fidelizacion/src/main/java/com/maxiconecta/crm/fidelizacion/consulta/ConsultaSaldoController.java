package com.maxiconecta.crm.fidelizacion.consulta;

import com.maxiconecta.crm.fidelizacion.comun.IdentidadGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Consulta del saldo, el nivel y los últimos movimientos de puntos de un cliente (SCRUM-47,
 * docs/fidelizacion/consulta-saldo-puntos.md). Exige la identidad del API Gateway y el permiso
 * {@code PUNTOS_CONSULTAR} (SCRUM-396), y está habilitada para el Administrador de CRM y el Agente de
 * Atención al Cliente (SCRUM-397): el rol se exige aunque otro rol reciba por error el permiso. Cada
 * consulta respondida queda en la auditoría (SCRUM-401).
 */
@RestController
@RequestMapping("/api/fidelizacion/clientes")
public class ConsultaSaldoController {

    /** SCRUM-397: roles que consultan el saldo y los movimientos de un cliente. */
    static final Set<String> ROLES_CONSULTA = Set.of(IdentidadGateway.ADMINISTRADOR_CRM, IdentidadGateway.AGENTE_ATENCION);

    private final ConsultaSaldoPuntos consulta;
    private final AuditoriaConsultaSaldo auditoria;

    public ConsultaSaldoController(ConsultaSaldoPuntos consulta, AuditoriaConsultaSaldo auditoria) {
        this.consulta = consulta;
        this.auditoria = auditoria;
    }

    @GetMapping("/{clienteId}/saldo")
    public ConsultaSaldoPuntos.SaldoCliente saldo(@PathVariable long clienteId,
                                                  @RequestParam(defaultValue = "" + ConsultaSaldoPuntos.MOVIMIENTOS_POR_DEFECTO)
                                                  int movimientos, HttpServletRequest solicitud) {
        IdentidadGateway.Identidad identidad = IdentidadGateway.consultaPuntos(solicitud);
        if (identidad.rol() == null || !ROLES_CONSULTA.contains(identidad.rol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el Administrador de CRM y el Agente de "
                    + "Atención al Cliente pueden consultar el saldo y los movimientos de puntos de un cliente");
        }
        ConsultaSaldoPuntos.SaldoCliente saldo;
        try {
            saldo = consulta.consultar(clienteId, movimientos);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        auditoria.registrar(identidad.usuario(), identidad.rol(), saldo);
        return saldo;
    }
}
