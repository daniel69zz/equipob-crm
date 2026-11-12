package com.maxiconecta.crm.fidelizacion.parametro;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * Parámetros generales del CRM que usa este servicio (SCRUM-65, docs/seguridad/parametros-generales.md). Se guardan
 * en {@code fidelizacion.parametro_general} y los cálculos los leen de aquí, así que se cambian sin tocar el código
 * ni reiniciar.
 *
 * <p>La lectura usa una copia en memoria que dura {@link #VIGENCIA_COPIA}: un cambio guardado en esta instancia rige
 * de inmediato y, en las demás, a más tardar al vencer su copia.
 */
public class ParametrosGenerales {

    /** Cuánto dura la copia en memoria de los valores antes de volver a leerlos. */
    public static final Duration VIGENCIA_COPIA = Duration.ofSeconds(30);
    static final String USUARIO_INICIAL = "configuracion-inicial";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock reloj;
    private final Map<String, DefinicionParametro> definiciones = new LinkedHashMap<>();
    private final ReglaConjunto reglaConjunto;
    private final ApplicationEventPublisher eventos;
    private volatile Copia copia;

    /**
     * Valida los parámetros en conjunto (por ejemplo, que un umbral sea menor que otro): recibe todos los valores
     * que quedarían y devuelve el motivo por parámetro rechazado.
     */
    @FunctionalInterface
    public interface ReglaConjunto {
        Map<String, String> rechazos(Map<String, BigDecimal> valores);
    }

    private record Copia(Map<String, BigDecimal> valores, Instant leidaEn) {
    }

    public ParametrosGenerales(JdbcTemplate jdbc, PlatformTransactionManager gestor, Clock reloj,
                               List<DefinicionParametro> definiciones, ReglaConjunto reglaConjunto,
                               ApplicationEventPublisher eventos) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(gestor);
        this.reloj = reloj;
        definiciones.forEach(d -> this.definiciones.put(d.codigo(), d));
        this.reglaConjunto = reglaConjunto;
        this.eventos = eventos;
    }

    /** Crea los parámetros que todavía no existen con el valor de la configuración; no toca los que ya existen. */
    public void inicializar() {
        for (DefinicionParametro d : definiciones.values()) {
            jdbc.update("""
                    INSERT INTO fidelizacion.parametro_general(codigo, valor, version, modificado_por, modificado_en)
                    VALUES (?, ?, 1, ?, ?) ON CONFLICT (codigo) DO NOTHING
                    """, d.codigo(), d.valorInicial(), USUARIO_INICIAL, OffsetDateTime.now(reloj));
        }
        copia = null;
    }

    /** El valor vigente de un parámetro, desde la copia en memoria. */
    public BigDecimal valor(String codigo) {
        Copia actual = copia;
        if (actual == null || Duration.between(actual.leidaEn(), Instant.now(reloj)).compareTo(VIGENCIA_COPIA) >= 0) {
            actual = recargar();
        }
        BigDecimal valor = actual.valores().get(codigo);
        if (valor == null) {
            throw new IllegalStateException("El parámetro general " + codigo + " no existe");
        }
        return valor;
    }

    public int entero(String codigo) {
        return valor(codigo).intValueExact();
    }

    /** Todos los parámetros de este servicio, en el orden en que se definieron. */
    public List<Parametro> listar() {
        Map<String, Parametro> porCodigo = new HashMap<>();
        jdbc.query("SELECT * FROM fidelizacion.parametro_general WHERE codigo = ANY (?)",
                (ResultSet rs) -> {
                    Parametro p = fila(rs);
                    porCodigo.put(p.codigo(), p);
                }, (Object) definiciones.keySet().toArray(String[]::new));
        return definiciones.keySet().stream().map(porCodigo::get).filter(p -> p != null).toList();
    }

    /**
     * Aplica varios cambios a la vez: o se guardan todos o ninguno. Rechaza un valor fuera de rango (400), un
     * parámetro que otro usuario cambió desde la versión indicada (409) y una solicitud que no cambia nada (400).
     *
     * <p>Después de guardar publica {@link ParametrosModificados}: lo que dependía del valor anterior se recalcula.
     *
     * @return los cambios efectivamente aplicados, con el valor anterior
     */
    public List<Modificacion> actualizar(List<CambioParametro> cambios, String usuario) {
        Map<String, CambioParametro> porCodigo = validar(cambios);
        List<Modificacion> aplicadas = tx.execute(estado -> aplicar(porCodigo, usuario));
        copia = null;
        eventos.publishEvent(new ParametrosModificados(aplicadas.stream()
                .map(m -> m.definicion().codigo()).collect(Collectors.toUnmodifiableSet())));
        return aplicadas;
    }

    /** Un parámetro que cambió: su valor anterior, el nuevo, quién y cuándo. */
    public record Modificacion(DefinicionParametro definicion, BigDecimal anterior, BigDecimal nuevo, String usuario,
                               OffsetDateTime fecha) {
    }

    private Map<String, CambioParametro> validar(List<CambioParametro> cambios) {
        if (cambios == null || cambios.isEmpty()) {
            throw new ParametroInvalidoException("Indique al menos un parámetro para cambiar", Map.of());
        }
        Map<String, String> rechazos = new TreeMap<>();
        Map<String, CambioParametro> porCodigo = new LinkedHashMap<>();
        Set<String> repetidos = new HashSet<>();
        for (CambioParametro cambio : cambios) {
            String codigo = cambio == null || cambio.codigo() == null ? "" : cambio.codigo().strip();
            DefinicionParametro definicion = definiciones.get(codigo);
            if (definicion == null) {
                rechazos.put(codigo.isEmpty() ? "codigo" : codigo, "no es un parámetro de este servicio");
                continue;
            }
            if (porCodigo.containsKey(codigo)) {
                repetidos.add(codigo);
            }
            String motivo = definicion.rechazo(cambio.valor());
            if (motivo == null && cambio.version() == null) {
                motivo = "falta la versión que se modificó";
            }
            if (motivo != null) {
                rechazos.put(codigo, motivo);
            }
            porCodigo.put(codigo, new CambioParametro(codigo, cambio.valor(), cambio.version()));
        }
        repetidos.forEach(codigo -> rechazos.put(codigo, "se indicó más de una vez"));
        if (!rechazos.isEmpty()) {
            throw invalido(rechazos);
        }
        return porCodigo;
    }

    private List<Modificacion> aplicar(Map<String, CambioParametro> cambios, String usuario) {
        Map<String, Parametro> actuales = new HashMap<>();
        jdbc.query("SELECT * FROM fidelizacion.parametro_general ORDER BY codigo FOR UPDATE", (ResultSet rs) -> {
            Parametro p = fila(rs);
            actuales.put(p.codigo(), p);
        });
        Map<String, String> desactualizados = new TreeMap<>();
        for (CambioParametro cambio : cambios.values()) {
            Parametro actual = actuales.get(cambio.codigo());
            if (actual == null || actual.version() != cambio.version()) {
                desactualizados.put(cambio.codigo(), actual == null ? "no existe"
                        : "su versión actual es " + actual.version() + ", no " + cambio.version());
            }
        }
        if (!desactualizados.isEmpty()) {
            throw new ParametroDesactualizadoException(desactualizados);
        }
        Map<String, BigDecimal> resultantes = new HashMap<>();
        actuales.forEach((codigo, p) -> resultantes.put(codigo, p.valor()));
        cambios.values().forEach(c -> resultantes.put(c.codigo(), c.valor()));
        Map<String, String> rechazos = reglaConjunto.rechazos(Collections.unmodifiableMap(resultantes));
        if (!rechazos.isEmpty()) {
            throw invalido(new TreeMap<>(rechazos));
        }

        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        List<Modificacion> aplicadas = new ArrayList<>();
        for (CambioParametro cambio : cambios.values()) {
            Parametro actual = actuales.get(cambio.codigo());
            if (actual.valor().compareTo(cambio.valor()) == 0) {
                continue;
            }
            jdbc.update("""
                    UPDATE fidelizacion.parametro_general
                    SET valor = ?, version = version + 1, modificado_por = ?, modificado_en = ? WHERE codigo = ?
                    """, cambio.valor(), usuario, ahora, cambio.codigo());
            aplicadas.add(new Modificacion(definiciones.get(cambio.codigo()), actual.valor(), cambio.valor(),
                    usuario, ahora));
        }
        if (aplicadas.isEmpty()) {
            throw new ParametroInvalidoException("Los valores indicados son iguales a los vigentes: no hay cambios",
                    Map.of());
        }
        return aplicadas;
    }

    private Copia recargar() {
        Map<String, BigDecimal> valores = new HashMap<>();
        jdbc.query("SELECT codigo, valor FROM fidelizacion.parametro_general",
                (ResultSet rs) -> {
                    valores.put(rs.getString("codigo"), rs.getBigDecimal("valor"));
                });
        Copia nueva = new Copia(Map.copyOf(valores), Instant.now(reloj));
        copia = nueva;
        return nueva;
    }

    private Parametro fila(ResultSet rs) throws SQLException {
        DefinicionParametro d = definiciones.get(rs.getString("codigo"));
        Function<BigDecimal, BigDecimal> escala = v -> v.setScale(d == null ? 2 : d.decimales());
        return new Parametro(rs.getString("codigo"), d == null ? rs.getString("codigo") : d.nombre(),
                d == null ? null : d.descripcion(), d == null ? null : d.unidad(),
                escala.apply(rs.getBigDecimal("valor")), d == null ? null : d.minimo(),
                d == null ? null : d.maximo(), d == null ? 2 : d.decimales(), rs.getLong("version"),
                rs.getString("modificado_por"), rs.getObject("modificado_en", OffsetDateTime.class));
    }

    private static ParametroInvalidoException invalido(Map<String, String> rechazos) {
        return new ParametroInvalidoException("Parámetros no válidos: " + rechazos.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue()).reduce((a, b) -> a + ". " + b).orElse(""), rechazos);
    }
}
