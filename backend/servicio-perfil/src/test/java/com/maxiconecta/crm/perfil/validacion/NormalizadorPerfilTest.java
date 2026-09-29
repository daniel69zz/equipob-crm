package com.maxiconecta.crm.perfil.validacion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-161 · Normalización de los datos del perfil (docs/perfil/mapeo-datos-perfil.md).
 */
class NormalizadorPerfilTest {

    @Test
    void losNombresQuedanConMayusculaInicialYSinEspaciosDeMas() {
        assertThat(NormalizadorPerfil.nombrePropio("  ANA   maría ")).isEqualTo("Ana María");
        assertThat(NormalizadorPerfil.nombrePropio("pérez DE LA cruz")).isEqualTo("Pérez de la Cruz");
        assertThat(NormalizadorPerfil.nombrePropio("de la fuente")).isEqualTo("De la Fuente");
        assertThat(NormalizadorPerfil.nombrePropio("pérez-rojas o'brien")).isEqualTo("Pérez-Rojas O'Brien");
        assertThat(NormalizadorPerfil.nombrePropio("   ")).isEmpty();
        assertThat(NormalizadorPerfil.nombrePropio(null)).isNull();
    }

    @Test
    void elCorreoQuedaEnMinusculasYSinEspacios() {
        assertThat(NormalizadorPerfil.email(" Ana.Perez@Correo.COM ")).isEqualTo("ana.perez@correo.com");
    }

    @Test
    void elTelefonoQuedaSoloConDigitosYCodigoDePais() {
        assertThat(NormalizadorPerfil.telefono("+591 700-12345")).isEqualTo("+59170012345");
        assertThat(NormalizadorPerfil.telefono("700 12345")).isEqualTo("+59170012345");
        assertThat(NormalizadorPerfil.telefono("591 70012345")).isEqualTo("+59170012345");
        assertThat(NormalizadorPerfil.telefono("(2) 2412345")).isEqualTo("+59122412345");
        assertThat(NormalizadorPerfil.telefono("+54 11 4321 0000")).isEqualTo("+541143210000");
    }

    @Test
    void unTelefonoConLetrasSeDejaComoVinoParaQueLaValidacionLoMarque() {
        assertThat(NormalizadorPerfil.telefono("llamar al 7001")).isEqualTo("llamar al 7001");
    }

    @Test
    void elDocumentoQuedaEnMayusculasSinEspaciosNiPuntos() {
        assertThat(NormalizadorPerfil.documento(" 7.788.990-1b ")).isEqualTo("7788990-1B");
    }
}
