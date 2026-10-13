import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PestaniasClienteComponent } from './pestanias-cliente.component';
import { ClientesService, IdentificadorOrigen, PerfilCliente } from './clientes.service';
import {
  CalidadDatos,
  EvolucionConsumo,
  EvolucionConsumoService,
  PuntoEvolucion,
  Tendencia,
  TipoPeriodo,
  Variacion,
} from './evolucion-consumo.service';

/** Medidas del gráfico en unidades del viewBox; el SVG escala con el ancho de la tarjeta. */
const ANCHO = 760;
const ALTO = 300;
const MARGEN = { arriba: 16, abajo: 34, izquierda: 64, derecha: 24, derechaConEtiquetas: 120 };

/** Categorías del gráfico: más que esto se agrupa en «Otras categorías». La tabla muestra todas. */
const MAXIMO_SERIES = 8;
const OTRAS = 'Otras categorías';

/**
 * Paleta categórica en orden fijo, validada para daltonismo sobre la superficie blanca de las
 * tarjetas. Cada categoría conserva su color mientras siga en pantalla, aunque cambie el filtro.
 */
const COLORES = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7', '#e34948'];
const COLOR_OTRAS = '#898781';

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

const FORMATO_EJE = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
const FORMATO_PORCENTAJE = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 });

const TENDENCIAS: Record<Tendencia, { texto: string; icono: string; clase: string }> = {
  CRECE: { texto: 'Crece', icono: '↗', clase: 'crece' },
  ESTABLE: { texto: 'Estable', icono: '→', clase: 'estable' },
  DECRECE: { texto: 'Decrece', icono: '↘', clase: 'decrece' },
  DEJO_DE_COMPRAR: { texto: 'Dejó de comprar', icono: '✕', clase: 'dejo' },
  SIN_CONSUMO: { texto: 'Sin consumo', icono: '·', clase: 'neutra' },
  SIN_COMPARACION: { texto: 'Sin comparación', icono: '·', clase: 'neutra' },
};

type Rango = 'ultimos-6' | 'ultimos-12' | 'anio-actual' | 'anio-anterior' | 'personalizado';
type Comparacion = 'ANTERIOR' | 'BASE';

interface SerieGrafico {
  categoria: string;
  color: string;
  valores: number[];
  compras: number[];
}

/**
 * Evolución del consumo de un cliente por categoría, periodo a periodo (SCRUM-32, SCRUM-301):
 * un gráfico de líneas con el monto de cada categoría y una tabla con el monto, las compras y la
 * variación de cada periodo, para identificar en qué categorías crece, se mantiene o deja de
 * comprar. Combina los identificadores del perfil con la consulta de servicio-comportamiento, como
 * el historial de compras (docs/compra/evolucion-consumo-categoria.md).
 */
@Component({
  selector: 'app-evolucion-consumo',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule, PestaniasClienteComponent],
  template: `
    <app-pestanias-cliente [id]="id()" />
    <h1>Evolución del consumo por categoría</h1>
    @if (perfil(); as p) {
      <p class="subtitulo">
        {{ p.nombres }} {{ p.apellidos }} · {{ p.tipoDocumento }} {{ p.numeroDocumento }} · cliente {{ p.id }}
      </p>
    }

    <form class="tarjeta filtros" (ngSubmit)="consultar()">
      <div>
        <label for="rango">Rango</label>
        <select id="rango" name="rango" [(ngModel)]="rango" (ngModelChange)="elegirRango($event)">
          <option value="ultimos-6">Últimos 6 meses</option>
          <option value="ultimos-12">Últimos 12 meses</option>
          <option value="anio-actual">Este año</option>
          <option value="anio-anterior">Año anterior</option>
          <option value="personalizado">Personalizado</option>
        </select>
      </div>
      <div>
        <label for="desde">Desde</label>
        <input id="desde" name="desde" type="date" [(ngModel)]="desde" (ngModelChange)="rango = 'personalizado'" />
      </div>
      <div>
        <label for="hasta">Hasta</label>
        <input id="hasta" name="hasta" type="date" [(ngModel)]="hasta" (ngModelChange)="rango = 'personalizado'" />
      </div>
      <div>
        <label for="periodo">Periodo</label>
        <select id="periodo" name="periodo" [(ngModel)]="periodo">
          <option value="MENSUAL">Mensual</option>
          <option value="TRIMESTRAL">Trimestral</option>
          <option value="ANUAL">Anual</option>
        </select>
      </div>
      <div class="acciones">
        <button type="submit" [disabled]="cargando()">Consultar</button>
      </div>

      @if (opcionesCategoria().length > 0) {
        <fieldset class="categorias">
          <legend>Categorías</legend>
          <button type="button" class="chip" [class.elegida]="categoriasElegidas().length === 0"
                  [attr.aria-pressed]="categoriasElegidas().length === 0" (click)="mostrarTodas()">
            Todas
          </button>
          @for (categoria of opcionesCategoria(); track categoria) {
            <button type="button" class="chip" [class.elegida]="estaElegida(categoria)"
                    [attr.aria-pressed]="estaElegida(categoria)" (click)="alternarCategoria(categoria)">
              {{ categoria }}
            </button>
          }
        </fieldset>
      }
    </form>

    @if (error()) {
      <p class="error" role="status">{{ error() }}</p>
    }

    @if (datos(); as d) {
      <div class="resultado" [class.recargando]="cargando()">
        <p class="rango">
          Del {{ d.desde | date: 'dd/MM/yyyy' }} al {{ d.hasta | date: 'dd/MM/yyyy' }} ·
          {{ d.calidadDatos.comprasAnalizadas }} {{ d.calidadDatos.comprasAnalizadas === 1 ? 'compra vigente' : 'compras vigentes' }}
          @if (d.calidadDatos.comprasAnuladasExcluidas > 0) {
            · {{ d.calidadDatos.comprasAnuladasExcluidas }}
            {{ d.calidadDatos.comprasAnuladasExcluidas === 1 ? 'compra anulada no se incluye' : 'compras anuladas no se incluyen' }}
          }
        </p>

        @if (!d.calidadDatos.consistente) {
          <p class="aviso" role="status">
            <strong>Algunos datos se corrigieron al calcular:</strong> {{ describirCalidad(d.calidadDatos) }}.
            Téngalo en cuenta antes de sacar conclusiones.
          </p>
        }

        @if (d.sinDatos) {
          <p class="tarjeta vacio">
            El cliente no tiene compras vigentes
            {{ categoriasElegidas().length > 0 ? 'de las categorías elegidas' : '' }}
            en este rango.
          </p>
        }

        @if (grafico(); as g) {
          <section class="tarjeta grafico" aria-labelledby="titulo-grafico">
            <header>
              <h2 id="titulo-grafico">
                Monto por periodo
                @if (g.series.length === 1) {
                  <span class="serie-unica">· {{ g.series[0].categoria }}</span>
                }
              </h2>
              @if (g.series.length > 1) {
                <ul class="leyenda" aria-label="Categorías del gráfico">
                  @for (serie of g.series; track serie.categoria) {
                    <li><span class="clave-linea" [style.background]="serie.color"></span>{{ serie.categoria }}</li>
                  }
                </ul>
              }
            </header>

            <div class="lienzo-desplazable">
              <div class="lienzo">
                <svg [attr.viewBox]="'0 0 ' + ancho + ' ' + alto" role="img" tabindex="0"
                     [attr.aria-label]="'Evolución del monto por categoría. Use las flechas para recorrer los periodos.'"
                     (pointermove)="seguirPuntero($event)" (pointerleave)="indiceActivo.set(null)"
                     (keydown)="moverConTeclado($event)" (blur)="indiceActivo.set(null)">
                  @for (tick of g.ticks; track tick.valor) {
                    <line class="grilla" [attr.x1]="g.izquierda" [attr.x2]="g.derecha" [attr.y1]="tick.y" [attr.y2]="tick.y" />
                    <text class="eje" [attr.x]="g.izquierda - 8" [attr.y]="tick.y + 4" text-anchor="end">{{ tick.texto }}</text>
                  }
                  @for (etiqueta of g.etiquetasX; track etiqueta.indice) {
                    @if (etiqueta.visible) {
                      <text class="eje" [attr.x]="etiqueta.x" [attr.y]="alto - 12" text-anchor="middle">{{ etiqueta.texto }}</text>
                    }
                  }

                  @if (indiceActivo() !== null) {
                    <line class="cruz" [attr.x1]="g.etiquetasX[indiceActivo()!].x" [attr.x2]="g.etiquetasX[indiceActivo()!].x"
                          [attr.y1]="g.arriba" [attr.y2]="g.base" />
                  }

                  @for (serie of g.series; track serie.categoria) {
                    <path class="linea" [attr.d]="serie.ruta" [attr.stroke]="serie.color" />
                    <path class="linea parcial" [attr.d]="serie.rutaParcial" [attr.stroke]="serie.color" />
                    @if (indiceActivo() !== null) {
                      <circle class="punto" [attr.cx]="serie.puntos[indiceActivo()!].x" [attr.cy]="serie.puntos[indiceActivo()!].y"
                              r="4.5" [attr.fill]="serie.color" />
                    } @else {
                      <circle class="punto" [attr.cx]="serie.puntos[serie.puntos.length - 1].x"
                              [attr.cy]="serie.puntos[serie.puntos.length - 1].y" r="4" [attr.fill]="serie.color" />
                    }
                  }

                  @for (etiqueta of g.etiquetasDirectas; track etiqueta.texto) {
                    <text class="etiqueta-directa" [attr.x]="etiqueta.x" [attr.y]="etiqueta.y">{{ etiqueta.texto }}</text>
                  }
                </svg>

                @if (detalleActivo(); as detalle) {
                  <div class="tooltip" role="status" [style.left.%]="detalle.posicion"
                       [class.a-la-izquierda]="detalle.aLaIzquierda">
                    <p class="tooltip-periodo">{{ detalle.periodo }}</p>
                    @for (fila of detalle.filas; track fila.categoria) {
                      <p class="tooltip-fila">
                        <span class="clave-linea" [style.background]="fila.color"></span>
                        <strong>{{ fila.monto | number: '1.2-2' }}</strong>
                        <span>{{ fila.categoria }} · {{ fila.compras }} {{ fila.compras === 1 ? 'compra' : 'compras' }}</span>
                      </p>
                    }
                    <p class="tooltip-total">
                      Total <strong>{{ detalle.total | number: '1.2-2' }}</strong> · {{ detalle.comprasTotal }}
                      {{ detalle.comprasTotal === 1 ? 'compra' : 'compras' }}
                    </p>
                  </div>
                }
              </div>
            </div>
            @if (hayPeriodosParciales()) {
              <p class="nota">* Periodo parcial: el rango no lo cubre completo, por eso su tramo va punteado.</p>
            }
          </section>
        }

        @if (d.categorias.length > 0) {
          <section class="tarjeta detalle" aria-labelledby="titulo-detalle">
            <header>
              <h2 id="titulo-detalle">Detalle por categoría y periodo</h2>
              <div class="comparacion">
                <label for="comparacion">Comparar con</label>
                <select id="comparacion" name="comparacion" [ngModel]="comparacion()" (ngModelChange)="comparacion.set($event)">
                  <option value="ANTERIOR">Periodo anterior</option>
                  <option value="BASE">Periodo base</option>
                </select>
                @if (comparacion() === 'BASE') {
                  <select aria-label="Periodo base" name="periodoBase" [ngModel]="d.periodoBase" (ngModelChange)="cambiarPeriodoBase($event)">
                    @for (p of d.periodos; track p.clave) {
                      <option [value]="p.clave">{{ etiquetaLarga(p.clave) }}</option>
                    }
                  </select>
                }
              </div>
            </header>

            <div class="tabla-desplazable">
              <table>
                <thead>
                  <tr>
                    <th scope="col" class="fija">Categoría</th>
                    @for (p of d.periodos; track p.clave) {
                      <th scope="col" class="numero" [title]="p.parcial ? 'Periodo parcial: del ' + (p.inicio | date: 'dd/MM/yyyy') + ' al ' + (p.fin | date: 'dd/MM/yyyy') : ''">
                        {{ etiquetaLarga(p.clave) }}{{ p.parcial ? '*' : '' }}
                        @if (comparacion() === 'BASE' && p.clave === d.periodoBase) {
                          <span class="marca-base">base</span>
                        }
                      </th>
                    }
                    <th scope="col" class="numero">Total</th>
                    <th scope="col">Tendencia</th>
                  </tr>
                </thead>
                <tbody>
                  @for (serie of d.categorias; track serie.categoria) {
                    <tr>
                      <th scope="row" class="fija">
                        <span class="clave-linea" [style.background]="colorDe(serie.categoria)"></span>{{ serie.categoria }}
                      </th>
                      @for (punto of serie.evolucion; track punto.periodo) {
                        <td class="numero celda" [class.cero]="punto.compras === 0">
                          <strong>{{ punto.monto | number: '1.2-2' }}</strong>
                          <small>{{ punto.compras }} {{ punto.compras === 1 ? 'compra' : 'compras' }}</small>
                          @if (variacionVisible(punto, d.periodoBase); as v) {
                            <small class="variacion" [class]="'variacion ' + v.clase">{{ v.texto }}</small>
                          }
                        </td>
                      }
                      <td class="numero celda">
                        <strong>{{ serie.monto | number: '1.2-2' }}</strong>
                        <small>{{ serie.compras }} {{ serie.compras === 1 ? 'compra' : 'compras' }}</small>
                      </td>
                      <td>
                        <span class="etiqueta tendencia" [class]="'etiqueta tendencia ' + tendencia(serie.tendencia).clase">
                          <span aria-hidden="true">{{ tendencia(serie.tendencia).icono }}</span>
                          {{ tendencia(serie.tendencia).texto }}
                        </span>
                      </td>
                    </tr>
                  }
                </tbody>
                <tfoot>
                  <tr>
                    <th scope="row" class="fija">Total</th>
                    @for (total of d.totales; track total.periodo) {
                      <td class="numero celda" [class.cero]="total.compras === 0">
                        <strong>{{ total.monto | number: '1.2-2' }}</strong>
                        <small>{{ total.compras }} {{ total.compras === 1 ? 'compra' : 'compras' }}</small>
                      </td>
                    }
                    <td class="numero celda"><strong>{{ montoTotal() | number: '1.2-2' }}</strong></td>
                    <td></td>
                  </tr>
                </tfoot>
              </table>
            </div>
            <p class="nota">
              Montos vigentes: sin compras anuladas y descontando las devoluciones en el periodo de la compra.
              Un periodo sin compras se muestra en cero.
              @if (hayPeriodosParciales()) {
                * Periodo parcial: el rango no lo cubre completo.
              }
              La tendencia compara la primera mitad de los periodos completos con la segunda.
            </p>
          </section>
        }
      </div>
    }
  `,
  styles: `
    .subtitulo { color: var(--color-texto-suave); margin-top: 0; }
    .filtros {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
      gap: 1rem; align-items: end; margin-bottom: 1rem;
    }
    .acciones { display: flex; gap: 0.5rem; }
    .categorias { grid-column: 1 / -1; display: flex; flex-wrap: wrap; gap: 0.4rem; margin: 0; }
    .chip {
      padding: 0.3rem 0.75rem; border-radius: var(--radio-pildora); font-size: 0.84rem; font-weight: 600;
      background: var(--color-superficie); color: var(--color-texto-suave); border-color: var(--color-borde-fuerte);
    }
    .chip:hover:not(:disabled) { background: var(--color-primario-claro); color: var(--color-primario-oscuro); border-color: var(--color-primario); }
    .chip.elegida { background: var(--color-primario-claro); color: var(--color-primario-oscuro); border-color: var(--color-primario); }

    .resultado { transition: opacity 0.15s ease; }
    .resultado.recargando { opacity: 0.55; }
    .rango { color: var(--color-texto-suave); font-size: 0.9rem; margin: 0 0 0.75rem; }
    .aviso {
      padding: 0.7rem 0.9rem; border-radius: var(--radio-chico); border: 1px solid #f2d39b;
      color: var(--color-aviso); background: var(--color-aviso-claro); font-size: 0.9rem;
    }
    .vacio { color: var(--color-texto-suave); }

    section.tarjeta { margin-bottom: 1rem; padding: 1rem 1.25rem; }
    section header { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.75rem; margin-bottom: 0.75rem; }
    h2 { margin: 0; font-size: 1rem; }
    .serie-unica { color: var(--color-texto-suave); font-weight: 500; }

    .leyenda { display: flex; flex-wrap: wrap; gap: 0.35rem 1rem; margin: 0; padding: 0; list-style: none; font-size: 0.84rem; color: var(--color-texto-suave); }
    .leyenda li { display: inline-flex; align-items: center; gap: 0.4rem; }
    .clave-linea { display: inline-block; width: 14px; height: 3px; border-radius: 2px; margin-right: 0.45rem; vertical-align: middle; flex: none; }
    .leyenda .clave-linea { margin-right: 0; }

    /* En pantallas chicas el gráfico conserva un ancho legible y se desplaza dentro de la tarjeta */
    .lienzo-desplazable { overflow-x: auto; }
    .lienzo { position: relative; min-width: 600px; }
    svg { display: block; width: 100%; height: auto; overflow: visible; }
    svg:focus-visible { outline: none; box-shadow: var(--foco); border-radius: var(--radio-chico); }
    .grilla { stroke: #e9ecf3; stroke-width: 1; }
    .eje { fill: #8a93a8; font-size: 11px; font-variant-numeric: tabular-nums; }
    .cruz { stroke: var(--color-borde-fuerte); stroke-width: 1; }
    .linea { fill: none; stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
    .linea.parcial { stroke-dasharray: 2 5; }
    .punto { stroke: var(--color-superficie); stroke-width: 2; }
    .etiqueta-directa { fill: var(--color-texto-suave); font-size: 11.5px; font-weight: 600; }

    .tooltip {
      position: absolute; top: 0.5rem; transform: translateX(12px); min-width: 190px; max-width: 280px;
      padding: 0.55rem 0.7rem; border-radius: var(--radio-chico); background: var(--color-superficie);
      border: 1px solid var(--color-borde); box-shadow: var(--sombra-2); pointer-events: none; font-size: 0.82rem;
    }
    .tooltip.a-la-izquierda { transform: translateX(calc(-100% - 12px)); }
    .tooltip p { margin: 0; }
    .tooltip-periodo { color: var(--color-texto-suave); font-weight: 600; margin-bottom: 0.3rem !important; }
    .tooltip-fila { display: flex; align-items: center; gap: 0.45rem; white-space: nowrap; }
    .tooltip-fila .clave-linea { margin-right: 0; }
    .tooltip-fila strong { font-variant-numeric: tabular-nums; }
    .tooltip-fila span:last-child { color: var(--color-texto-suave); overflow: hidden; text-overflow: ellipsis; }
    .tooltip-total { margin-top: 0.35rem !important; padding-top: 0.35rem; border-top: 1px solid var(--color-borde); color: var(--color-texto-suave); }

    .comparacion { display: flex; align-items: center; gap: 0.5rem; }
    .comparacion label { margin: 0; white-space: nowrap; }
    .comparacion select { width: auto; height: 34px; padding: 0.3rem 0.6rem; }
    .tabla-desplazable { overflow-x: auto; }
    table { min-width: max-content; }
    th.fija { position: sticky; left: 0; z-index: 1; background: var(--color-superficie); white-space: nowrap; }
    thead th.fija { background: var(--color-superficie-suave); }
    .numero { text-align: right; white-space: nowrap; }
    .celda strong, .celda small { display: block; font-variant-numeric: tabular-nums; }
    .celda small { color: var(--color-texto-suave); font-size: 0.78rem; }
    .celda.cero strong { color: #9aa3b8; font-weight: 500; }
    .variacion.sube { color: var(--color-exito); }
    .variacion.baja { color: var(--color-error); }
    .marca-base {
      display: inline-block; margin-left: 0.3rem; padding: 0 0.35rem; border-radius: var(--radio-pildora);
      background: var(--color-primario-claro); color: var(--color-primario); font-size: 0.68rem;
    }
    tfoot th, tfoot td { border-top: 1px solid var(--color-borde-fuerte); background: var(--color-superficie-suave); }
    .tendencia.crece { color: var(--color-exito); background: var(--color-exito-claro); }
    .tendencia.estable { color: var(--color-info); background: var(--color-info-claro); }
    .tendencia.decrece { color: var(--color-aviso); background: var(--color-aviso-claro); }
    .tendencia.dejo { color: var(--color-error); background: var(--color-error-claro); }
    .nota { margin: 0.75rem 0 0; color: var(--color-texto-suave); font-size: 0.82rem; }
  `,
})
export class EvolucionConsumoComponent implements OnInit {
  private readonly clientes = inject(ClientesService);
  private readonly evolucion = inject(EvolucionConsumoService);

  /** Identificador del cliente, tomado de la ruta /clientes/:id/evolucion-consumo. */
  readonly id = input.required<string>();

  readonly ancho = ANCHO;
  readonly alto = ALTO;

  readonly perfil = signal<PerfilCliente | null>(null);
  readonly datos = signal<EvolucionConsumo | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly categoriasElegidas = signal<string[]>([]);
  readonly comparacion = signal<Comparacion>('ANTERIOR');
  readonly indiceActivo = signal<number | null>(null);

  rango: Rango = 'ultimos-12';
  desde = '';
  hasta = '';
  periodo: TipoPeriodo = 'MENSUAL';
  private periodoBase = '';

  private identificadores: IdentificadorOrigen[] = [];
  /** Color asignado a cada categoría: se conserva mientras la categoría siga en pantalla. */
  private readonly colores = signal<Map<string, string>>(new Map());

  /** Las categorías del rango y las elegidas, aunque ya no tengan consumo en el rango. */
  readonly opcionesCategoria = computed(() => {
    const disponibles = this.datos()?.categoriasDisponibles ?? [];
    const todas = new Set([...disponibles, ...this.categoriasElegidas()]);
    return [...todas].sort((a, b) => a.localeCompare(b, 'es', { sensitivity: 'base' }));
  });

  readonly montoTotal = computed(() => (this.datos()?.totales ?? []).reduce((suma, t) => suma + t.monto, 0));

  readonly hayPeriodosParciales = computed(() => (this.datos()?.periodos ?? []).some((p) => p.parcial));

  /** Series del gráfico: las categorías de la respuesta, con «Otras categorías» si no caben. */
  private readonly seriesGrafico = computed<SerieGrafico[]>(() => {
    const d = this.datos();
    if (!d) {
      return [];
    }
    const colores = this.colores();
    const graficadas = d.categorias.length > MAXIMO_SERIES ? d.categorias.slice(0, MAXIMO_SERIES - 1) : d.categorias;
    const series: SerieGrafico[] = graficadas.map((serie) => ({
      categoria: serie.categoria,
      color: colores.get(serie.categoria) ?? COLOR_OTRAS,
      valores: serie.evolucion.map((p) => p.monto),
      compras: serie.evolucion.map((p) => p.compras),
    }));
    if (d.categorias.length > MAXIMO_SERIES) {
      const resto = d.categorias.slice(MAXIMO_SERIES - 1);
      series.push({
        categoria: OTRAS,
        color: COLOR_OTRAS,
        valores: d.periodos.map((_, i) => resto.reduce((suma, s) => suma + s.evolucion[i].monto, 0)),
        compras: d.periodos.map((_, i) => resto.reduce((suma, s) => suma + s.evolucion[i].compras, 0)),
      });
    }
    return series;
  });

  /** Sin consumo en el rango no hay gráfico: solo el aviso y, si se filtró, la tabla en cero. */
  readonly grafico = computed(() => {
    const d = this.datos();
    const series = this.seriesGrafico();
    if (!d || d.sinDatos || series.length === 0) {
      return null;
    }
    const conEtiquetas = series.length >= 2 && series.length <= 4;
    const izquierda = MARGEN.izquierda;
    const derecha = ANCHO - (conEtiquetas ? MARGEN.derechaConEtiquetas : MARGEN.derecha);
    const arriba = MARGEN.arriba;
    const base = ALTO - MARGEN.abajo;
    const n = d.periodos.length;
    const x = (i: number) => (n === 1 ? (izquierda + derecha) / 2 : izquierda + (i * (derecha - izquierda)) / (n - 1));
    const escala = escalaY(Math.max(0, ...series.flatMap((s) => s.valores)));
    const y = (valor: number) => base - (valor / escala.maximo) * (base - arriba);

    // Un tramo que llega a un periodo parcial va punteado: su caída puede deberse solo a que el
    // periodo (por ejemplo, el mes en curso) todavía no terminó.
    const parcial = (i: number) => d.periodos[i].parcial || d.periodos[i + 1].parcial;
    const conPuntos = series.map((s) => {
      const puntos = s.valores.map((valor, i) => ({ x: x(i), y: y(valor) }));
      return {
        ...s,
        puntos,
        ruta: ruta(puntos, (i) => !parcial(i)),
        rutaParcial: ruta(puntos, parcial),
      };
    });
    const pasoEtiquetas = Math.ceil(n / 12);
    return {
      izquierda,
      derecha,
      arriba,
      base,
      series: conPuntos,
      ticks: escala.ticks.map((valor) => ({ valor, y: y(valor), texto: FORMATO_EJE.format(valor) })),
      etiquetasX: d.periodos.map((p, i) => ({
        indice: i,
        x: x(i),
        texto: etiquetaCorta(p.clave) + (p.parcial ? '*' : ''),
        visible: i % pasoEtiquetas === 0 || i === n - 1,
      })),
      etiquetasDirectas: conEtiquetas ? etiquetasDirectas(conPuntos, derecha) : [],
    };
  });

  /** Lo que muestra el tooltip del periodo bajo el puntero: todas las series, de mayor a menor. */
  readonly detalleActivo = computed(() => {
    const indice = this.indiceActivo();
    const d = this.datos();
    const g = this.grafico();
    if (indice === null || !d || !g) {
      return null;
    }
    const filas = g.series
      .map((s) => ({ categoria: s.categoria, color: s.color, monto: s.valores[indice], compras: s.compras[indice] }))
      .sort((a, b) => b.monto - a.monto);
    const posicion = (g.etiquetasX[indice].x / ANCHO) * 100;
    return {
      periodo: etiquetaLarga(d.periodos[indice].clave),
      filas,
      total: d.totales[indice].monto,
      comprasTotal: d.totales[indice].compras,
      posicion,
      aLaIzquierda: posicion > 55,
    };
  });

  ngOnInit(): void {
    this.elegirRango(this.rango, false);
    this.clientes.obtener(this.idCliente()).subscribe({
      next: (perfil) => {
        this.perfil.set(perfil);
        this.identificadores = perfil.identificadoresOrigen;
        this.consultar();
      },
      error: (e: HttpErrorResponse) => this.mostrarError(e),
    });
  }

  consultar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.evolucion
      .consultar(this.idCliente(), this.identificadores, {
        desde: this.desde,
        hasta: this.hasta,
        periodo: this.periodo,
        categorias: this.categoriasElegidas(),
        periodoBase: this.periodoBase,
      })
      .subscribe({
        next: (datos) => {
          this.asignarColores(datos);
          this.datos.set(datos);
          this.desde = datos.desde;
          this.hasta = datos.hasta;
          this.periodoBase = datos.periodoBase;
          this.indiceActivo.set(null);
          this.cargando.set(false);
        },
        error: (e: HttpErrorResponse) => {
          this.cargando.set(false);
          this.mostrarError(e);
        },
      });
  }

  /** Los rangos predefinidos se aplican al elegirlos; el personalizado, con «Consultar». */
  elegirRango(rango: Rango, aplicar = true): void {
    const hoy = new Date();
    const anio = hoy.getFullYear();
    const fechas: Partial<Record<Rango, [Date, Date]>> = {
      'ultimos-6': [new Date(anio, hoy.getMonth() - 5, 1), hoy],
      'ultimos-12': [new Date(anio, hoy.getMonth() - 11, 1), hoy],
      'anio-actual': [new Date(anio, 0, 1), hoy],
      'anio-anterior': [new Date(anio - 1, 0, 1), new Date(anio - 1, 11, 31)],
    };
    const elegido = fechas[rango];
    if (!elegido) {
      return;
    }
    this.desde = fechaIso(elegido[0]);
    this.hasta = fechaIso(elegido[1]);
    this.periodoBase = '';
    if (aplicar) {
      this.consultar();
    }
  }

  alternarCategoria(categoria: string): void {
    const elegidas = this.categoriasElegidas();
    this.categoriasElegidas.set(
      elegidas.includes(categoria) ? elegidas.filter((c) => c !== categoria) : [...elegidas, categoria],
    );
    this.consultar();
  }

  mostrarTodas(): void {
    if (this.categoriasElegidas().length > 0) {
      this.categoriasElegidas.set([]);
      this.consultar();
    }
  }

  estaElegida(categoria: string): boolean {
    return this.categoriasElegidas().includes(categoria);
  }

  cambiarPeriodoBase(clave: string): void {
    this.periodoBase = clave;
    this.consultar();
  }

  /** La crucecita sigue al puntero y se ajusta al periodo más cercano. */
  seguirPuntero(evento: PointerEvent): void {
    const g = this.grafico();
    const n = this.datos()?.periodos.length ?? 0;
    if (!g || n === 0) {
      return;
    }
    const caja = (evento.currentTarget as SVGSVGElement).getBoundingClientRect();
    const x = ((evento.clientX - caja.left) * ANCHO) / caja.width;
    const indice = n === 1 ? 0 : Math.round(((x - g.izquierda) * (n - 1)) / (g.derecha - g.izquierda));
    this.indiceActivo.set(Math.min(Math.max(indice, 0), n - 1));
  }

  moverConTeclado(evento: KeyboardEvent): void {
    const n = this.datos()?.periodos.length ?? 0;
    if (n === 0) {
      return;
    }
    const actual = this.indiceActivo();
    const siguiente: Record<string, number> = {
      ArrowRight: actual === null ? 0 : Math.min(actual + 1, n - 1),
      ArrowLeft: actual === null ? n - 1 : Math.max(actual - 1, 0),
      Home: 0,
      End: n - 1,
    };
    if (evento.key in siguiente) {
      evento.preventDefault();
      this.indiceActivo.set(siguiente[evento.key]);
    } else if (evento.key === 'Escape') {
      this.indiceActivo.set(null);
    }
  }

  colorDe(categoria: string): string {
    return this.colores().get(categoria) ?? COLOR_OTRAS;
  }

  tendencia(tendencia: Tendencia) {
    return TENDENCIAS[tendencia];
  }

  etiquetaLarga(clave: string): string {
    return etiquetaLarga(clave);
  }

  /** Variación del periodo según la comparación elegida; el periodo base no se compara consigo mismo. */
  variacionVisible(punto: PuntoEvolucion, periodoBase: string): { texto: string; clase: string } | null {
    if (this.comparacion() === 'BASE') {
      return punto.periodo === periodoBase ? null : describirVariacion(punto.variacionBase);
    }
    return describirVariacion(punto.variacionAnterior);
  }

  describirCalidad(calidad: CalidadDatos): string {
    return [
      [calidad.registrosSinCategoria, 'registro sin categoría', 'registros sin categoría (agrupados como «Sin categoría»)'],
      [calidad.registrosSinMonto, 'registro sin monto', 'registros sin monto (contados como cero)'],
      [calidad.devolucionesInconsistentes, 'devolución mayor a lo comprado', 'devoluciones mayores a lo comprado'],
      [calidad.comprasConDesgloseInconsistente, 'compra cuyo desglose no suma su monto', 'compras cuyo desglose no suma su monto'],
    ]
      .filter(([cantidad]) => (cantidad as number) > 0)
      .map(([cantidad, singular, plural]) => `${cantidad} ${cantidad === 1 ? singular : plural}`)
      .join(', ');
  }

  /**
   * Las categorías que siguen en pantalla conservan su color; las nuevas toman el primer color
   * libre, en el orden fijo de la paleta. «Otras categorías» va siempre en gris.
   */
  private asignarColores(datos: EvolucionConsumo): void {
    const graficadas = (datos.categorias.length > MAXIMO_SERIES
      ? datos.categorias.slice(0, MAXIMO_SERIES - 1)
      : datos.categorias
    ).map((s) => s.categoria);
    const anteriores = this.colores();
    const asignados = new Map<string, string>();
    for (const categoria of graficadas) {
      const color = anteriores.get(categoria);
      if (color && ![...asignados.values()].includes(color)) {
        asignados.set(categoria, color);
      }
    }
    for (const categoria of graficadas) {
      if (!asignados.has(categoria)) {
        const libre = COLORES.find((color) => ![...asignados.values()].includes(color)) ?? COLOR_OTRAS;
        asignados.set(categoria, libre);
      }
    }
    this.colores.set(asignados);
  }

  private idCliente(): number {
    return Number(this.id());
  }

  private mostrarError(error: HttpErrorResponse): void {
    this.error.set(error.error?.mensaje ?? 'No se pudo consultar la evolución del consumo.');
  }
}

/** Escala del eje Y que empieza en cero y termina en un número redondo, con 4 o 5 marcas. */
function escalaY(maximoDatos: number): { maximo: number; ticks: number[] } {
  if (maximoDatos <= 0) {
    return { maximo: 1, ticks: [0] };
  }
  const crudo = maximoDatos / 4;
  const potencia = 10 ** Math.floor(Math.log10(crudo));
  const paso = [1, 2, 2.5, 5, 10].map((m) => m * potencia).find((p) => p >= crudo) ?? 10 * potencia;
  const maximo = paso * Math.ceil(maximoDatos / paso);
  const ticks: number[] = [];
  for (let valor = 0; valor <= maximo + paso / 2; valor += paso) {
    ticks.push(valor);
  }
  return { maximo, ticks };
}

/** Ruta SVG con los tramos {@code i → i+1} que cumplen la condición; cada tramo suelto empieza con M. */
function ruta(puntos: { x: number; y: number }[], incluir: (i: number) => boolean): string {
  const tramos: string[] = [];
  for (let i = 0; i < puntos.length - 1; i++) {
    if (incluir(i)) {
      const desde = puntos[i];
      const hasta = puntos[i + 1];
      tramos.push(`M${desde.x.toFixed(1)},${desde.y.toFixed(1)} L${hasta.x.toFixed(1)},${hasta.y.toFixed(1)}`);
    }
  }
  return tramos.join(' ');
}

/** Etiquetas al final de cada línea; si se pisarían, se omiten y quedan la leyenda y el tooltip. */
function etiquetasDirectas(
  series: { categoria: string; puntos: { x: number; y: number }[] }[],
  derecha: number,
): { texto: string; x: number; y: number }[] {
  const etiquetas = series.map((s) => ({
    texto: s.categoria.length > 16 ? `${s.categoria.slice(0, 15)}…` : s.categoria,
    x: derecha + 10,
    y: s.puntos[s.puntos.length - 1].y + 4,
  }));
  const ordenadas = [...etiquetas].sort((a, b) => a.y - b.y);
  const sePisan = ordenadas.some((e, i) => i > 0 && e.y - ordenadas[i - 1].y < 14);
  return sePisan ? [] : etiquetas;
}

function describirVariacion(variacion: Variacion | null): { texto: string; clase: string } {
  if (!variacion) {
    return { texto: '—', clase: '' };
  }
  if (variacion.porcentaje === null) {
    return variacion.monto > 0 ? { texto: '▲ nuevo', clase: 'sube' } : { texto: '—', clase: '' };
  }
  if (variacion.porcentaje > 0) {
    return { texto: `▲ +${FORMATO_PORCENTAJE.format(variacion.porcentaje)} %`, clase: 'sube' };
  }
  if (variacion.porcentaje < 0) {
    return { texto: `▼ ${FORMATO_PORCENTAJE.format(variacion.porcentaje)} %`, clase: 'baja' };
  }
  return { texto: '= 0 %', clase: '' };
}

/** {@code 2026-09} → «sep 26», {@code 2026-T3} → «T3 26», {@code 2026} → «2026». */
function etiquetaCorta(clave: string): string {
  const mes = /^(\d{4})-(\d{2})$/.exec(clave);
  if (mes) {
    return `${MESES[Number(mes[2]) - 1]} ${mes[1].slice(2)}`;
  }
  const trimestre = /^(\d{4})-(T\d)$/.exec(clave);
  return trimestre ? `${trimestre[2]} ${trimestre[1].slice(2)}` : clave;
}

/** {@code 2026-09} → «sep 2026», {@code 2026-T3} → «T3 2026», {@code 2026} → «2026». */
function etiquetaLarga(clave: string): string {
  const mes = /^(\d{4})-(\d{2})$/.exec(clave);
  if (mes) {
    return `${MESES[Number(mes[2]) - 1]} ${mes[1]}`;
  }
  const trimestre = /^(\d{4})-(T\d)$/.exec(clave);
  return trimestre ? `${trimestre[2]} ${trimestre[1]}` : clave;
}

function fechaIso(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}
