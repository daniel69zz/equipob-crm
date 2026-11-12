package com.maxiconecta.crm.fidelizacion.parametro;

import java.util.List;

/** Los cambios que el Administrador de CRM guarda juntos: se aplican todos o ninguno (SCRUM-65). */
public record SolicitudParametros(List<CambioParametro> cambios) {
}
