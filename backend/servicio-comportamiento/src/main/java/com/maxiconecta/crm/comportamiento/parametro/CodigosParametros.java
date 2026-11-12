package com.maxiconecta.crm.comportamiento.parametro;

/** Códigos de los parámetros generales del CRM que son de servicio-comportamiento (SCRUM-65). */
public final class CodigosParametros {

    /** SCRUM-515: días sin compras vigentes para considerar inactivo a un cliente. */
    public static final String INACTIVIDAD_DIAS = "INACTIVIDAD_DIAS";
    /** SCRUM-518: valor acumulado (Bs) desde el que un cliente es de consumo MEDIO. */
    public static final String NIVEL_CONSUMO_UMBRAL_MEDIO = "NIVEL_CONSUMO_UMBRAL_MEDIO";
    /** SCRUM-518: valor acumulado (Bs) desde el que un cliente es de consumo ALTO. */
    public static final String NIVEL_CONSUMO_UMBRAL_ALTO = "NIVEL_CONSUMO_UMBRAL_ALTO";

    private CodigosParametros() {
    }
}
