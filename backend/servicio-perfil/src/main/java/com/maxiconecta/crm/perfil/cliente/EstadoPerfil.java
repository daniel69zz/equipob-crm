package com.maxiconecta.crm.perfil.cliente;

/**
 * Estado de validación del perfil (ver docs/perfil/catalogo-reglas-validacion.md). Es
 * {@code INCOMPLETO} cuando falta o está mal formado un dato obligatorio, e {@code INCONSISTENTE}
 * cuando los datos están presentes pero incumplen una regla de coherencia. En cualquier otro caso
 * el perfil está {@code COMPLETO}.
 */
public enum EstadoPerfil {
    COMPLETO,
    INCOMPLETO,
    INCONSISTENTE
}
