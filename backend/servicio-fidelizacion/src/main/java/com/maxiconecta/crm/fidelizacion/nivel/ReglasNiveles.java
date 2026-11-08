package com.maxiconecta.crm.fidelizacion.nivel;

import com.maxiconecta.crm.fidelizacion.comun.ReglaInvalidaException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reglas de los umbrales de niveles (SCRUM-410, criterio de aceptación 2). Una configuración es válida si:
 * <ul>
 *   <li>define una sola vez cada nivel (bronce, plata y oro);</li>
 *   <li>cada rango tiene mínimo cero o mayor y máximo mayor o igual que el mínimo;</li>
 *   <li>solo el oro, el nivel más alto, queda sin máximo;</li>
 *   <li>los rangos van en orden bronce, plata, oro, <b>no se superponen</b> (los límites están incluidos:
 *       0–500 y 500–1999 chocan en 500) y no dejan huecos entre un nivel y el siguiente.</li>
 * </ul>
 * El bronce puede empezar por encima de cero: los clientes con menos puntos quedan sin nivel.
 */
public final class ReglasNiveles {

    private ReglasNiveles() {
    }

    /** @return los umbrales ordenados del nivel más bajo al más alto */
    public static List<UmbralNivel> validar(List<UmbralNivel> umbrales) {
        Map<String, String> campos = new LinkedHashMap<>();
        Map<Nivel, UmbralNivel> porNivel = new EnumMap<>(Nivel.class);
        for (UmbralNivel umbral : umbrales == null ? List.<UmbralNivel>of() : umbrales) {
            if (umbral == null || umbral.nivel() == null) {
                campos.put("niveles", "cada umbral debe indicar su nivel (BRONCE, PLATA u ORO)");
                continue;
            }
            if (porNivel.put(umbral.nivel(), umbral) != null) {
                campos.put(umbral.nivel().name(), "el nivel " + umbral.nivel().nombre() + " está repetido");
            }
        }
        for (Nivel nivel : Nivel.values()) {
            if (!porNivel.containsKey(nivel)) {
                campos.putIfAbsent(nivel.name(), "falta el umbral del nivel " + nivel.nombre());
            }
        }
        rechazarSiHay(campos, "Los niveles no son válidos");

        for (UmbralNivel umbral : porNivel.values()) {
            String nivel = umbral.nivel().name();
            if (umbral.puntosMinimos() == null) {
                campos.put(nivel + ".puntosMinimos", "es obligatorio");
            } else if (umbral.puntosMinimos() < 0) {
                campos.put(nivel + ".puntosMinimos", "no puede ser negativo");
            } else if (umbral.puntosMaximos() != null && umbral.puntosMaximos() < umbral.puntosMinimos()) {
                campos.put(nivel + ".puntosMaximos", "debe ser mayor o igual que el mínimo (" + umbral.puntosMinimos() + ")");
            }
            if (umbral.nivel() != Nivel.ORO && umbral.puntosMaximos() == null) {
                campos.put(nivel + ".puntosMaximos", "es obligatorio: solo el nivel Oro queda sin máximo");
            }
            if (umbral.nivel() == Nivel.ORO && umbral.puntosMaximos() != null) {
                campos.put(nivel + ".puntosMaximos", "debe quedar vacío: el nivel Oro no tiene máximo");
            }
        }
        rechazarSiHay(campos, "Los umbrales no son válidos");

        List<UmbralNivel> ordenados = new ArrayList<>(porNivel.values());
        ordenados.sort(Comparator.comparing(UmbralNivel::nivel));
        List<String> conflictos = new ArrayList<>();
        for (int i = 1; i < ordenados.size(); i++) {
            UmbralNivel anterior = ordenados.get(i - 1);
            UmbralNivel actual = ordenados.get(i);
            String clave = actual.nivel().name() + ".puntosMinimos";
            long siguiente = anterior.puntosMaximos() + 1;
            if (actual.puntosMinimos() <= anterior.puntosMaximos()) {
                String detalle = "los umbrales de " + anterior.nivel().nombre() + " (" + anterior.rango() + ") y "
                        + actual.nivel().nombre() + " (" + actual.rango() + ") se superponen; el mínimo de "
                        + actual.nivel().nombre() + " debe ser " + siguiente;
                campos.put(clave, detalle);
                conflictos.add(detalle);
            } else if (actual.puntosMinimos() > siguiente) {
                String detalle = "entre " + anterior.nivel().nombre() + " y " + actual.nivel().nombre()
                        + " quedarían sin nivel los clientes con " + siguiente + "–" + (actual.puntosMinimos() - 1)
                        + " puntos; el mínimo de " + actual.nivel().nombre() + " debe ser " + siguiente;
                campos.put(clave, detalle);
                conflictos.add(detalle);
            }
        }
        if (!conflictos.isEmpty()) {
            throw new ReglaInvalidaException("Conflicto en los umbrales de niveles: " + String.join("; ", conflictos),
                    campos);
        }
        return List.copyOf(ordenados);
    }

    private static void rechazarSiHay(Map<String, String> campos, String titulo) {
        if (!campos.isEmpty()) {
            StringBuilder mensaje = new StringBuilder(titulo).append(": ");
            campos.forEach((campo, motivo) -> mensaje.append(campo).append(' ').append(motivo).append(", "));
            throw new ReglaInvalidaException(mensaje.substring(0, mensaje.length() - 2), campos);
        }
    }
}
