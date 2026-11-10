import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ClientesSegmentoService, SegmentoDisponible } from '../segmentacion/clientes-segmento.service';
import { ExportarReporteComponent, FormatoExportacion, descargarArchivo, motivoFalloExportacion } from './exportar-reporte.component';
import { validarPeriodo } from './reporte-comportamiento.component';
import { IndicadorComparado, PeriodosComparados, ReporteComparativo, ReporteComparativoService, Tendencia } from './reporte-comparativo.service';

/** Indicadores que son conteos: se muestran sin decimales. */
const CONTEOS = new Set(['COMPRAS', 'CLIENTES_CON_COMPRAS']);

const TENDENCIAS: Record<Tendencia, string> = { CRECE: '▲ Crece', DECAE: '▼ Decae', ESTABLE: '= Estable', NUEVO: '★ Nuevo' };

/**
 * Comparación de los indicadores de clientes entre dos periodos (SCRUM-62, RF-54): los de cada periodo y su
 * variación (criterio 1). Los periodos se validan aquí antes de consultar, así que unos superpuestos se avisan
 * sin llamar al servidor (criterio 2). «Exportar» descarga en CSV o PDF el archivo que genera el servidor con el
 * mismo reporte (criterio 3 y SCRUM-60), solo para quien tiene REPORTES_EXPORTAR.
 */
@Component({
  selector: 'app-reporte-comparativo',
  standalone: true,
  imports: [DecimalPipe, FormsModule, ExportarReporteComponent],
  template: `
    <h1>Comparativo entre periodos</h1>
    <p class="subtitulo">
      Indicadores de compra de la cartera o de un segmento en dos periodos, y cuánto variaron del periodo A al B.
      Los periodos no pueden compartir días.
    </p>

    <form class="tarjeta filtros" (ngSubmit)="comparar()" novalidate>
      <fieldset>
        <legend>Periodo A (base)</legend>
        <label>Desde <input type="date" name="desdeA" [(ngModel)]="desdeA" aria-label="Desde del periodo A" /></label>
        <label>Hasta <input type="date" name="hastaA" [(ngModel)]="hastaA" aria-label="Hasta del periodo A" /></label>
      </fieldset>
      <fieldset>
        <legend>Periodo B (comparado)</legend>
        <label>Desde <input type="date" name="desdeB" [(ngModel)]="desdeB" aria-label="Desde del periodo B" /></label>
        <label>Hasta <input type="date" name="hastaB" [(ngModel)]="hastaB" aria-label="Hasta del periodo B" /></label>
      </fieldset>
      <label>
        Segmento
        <select name="segmento" [(ngModel)]="segmento">
          <option [ngValue]="null">Toda la cartera</option>
          @for (s of segmentos(); track s.segmento) {
            <option [ngValue]="s.segmento">{{ s.segmento }} ({{ s.clientes }})</option>
          }
        </select>
      </label>
      <div class="botones">
        <button type="submit" [disabled]="cargando()">Comparar</button>
        <app-exportar-reporte [deshabilitado]="cargando()" [exportando]="exportando()" (exportar)="exportar($event)" />
      </div>
    </form>

    @if (aviso()) {
      <p class="error" role="alert">{{ aviso() }}</p>
    }

    @if (reporte(); as r) {
      <p class="alcance">
        {{ r.alcance.segmento ? 'Segmento «' + r.alcance.segmento + '» (' + r.alcance.clientesDelSegmento + ' clientes)' : 'Toda la cartera' }}
        · A: {{ r.periodoA.desde }} a {{ r.periodoA.hasta }} · B: {{ r.periodoB.desde }} a {{ r.periodoB.hasta }}
      </p>
      @if (r.sinDatos) {
        <section class="tarjeta" aria-label="Sin datos">
          <h2>Sin datos para comparar</h2>
          <p>{{ r.mensaje }}</p>
        </section>
      } @else {
        <section class="tarjeta" aria-label="Indicadores comparados">
          <table>
            <thead>
              <tr>
                <th scope="col">Indicador</th>
                <th scope="col" class="numero">Periodo A</th>
                <th scope="col" class="numero">Periodo B</th>
                <th scope="col" class="numero">Variación</th>
                <th scope="col" class="numero">Variación %</th>
                <th scope="col">Tendencia</th>
              </tr>
            </thead>
            <tbody>
              @for (i of r.indicadores; track i.codigo) {
                <tr [attr.data-codigo]="i.codigo">
                  <th scope="row">{{ i.nombre }}</th>
                  <td class="numero">{{ valor(i, i.periodoA) }}</td>
                  <td class="numero">{{ valor(i, i.periodoB) }}</td>
                  <td class="numero">{{ i.variacionAbsoluta === null ? '—' : signo(i.variacionAbsoluta) + valor(i, i.variacionAbsoluta) }}</td>
                  <td class="numero">{{ i.variacionPorcentual === null ? '—' : signo(i.variacionPorcentual) + (i.variacionPorcentual | number: '1.2-2') + ' %' }}</td>
                  <td>
                    @if (i.tendencia) {
                      <span class="tendencia" [class]="'tendencia ' + i.tendencia.toLowerCase()">{{ nombreTendencia(i.tendencia) }}</span>
                    } @else {
                      <span class="sin-base" title="Uno de los periodos no tiene compras: no hay base para comparar">Sin base</span>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </section>
      }
    } @else if (cargando()) {
      <p><small>Comparando…</small></p>
    }
  `,
  styles: `
    .filtros { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 1rem; margin-bottom: 1rem; padding: 1rem 1.25rem; }
    fieldset { display: flex; gap: 0.75rem; border: 1px solid var(--color-borde); border-radius: 6px; padding: 0.5rem 0.75rem 0.75rem; margin: 0; }
    legend { font-size: 0.8rem; color: var(--color-texto-suave); padding: 0 0.25rem; }
    .filtros label { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.85rem; color: var(--color-texto-suave); }
    .botones { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0.75rem; }
    .alcance { color: var(--color-texto-suave); margin: 0 0 0.75rem; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 0.45rem 0.5rem; border-bottom: 1px solid var(--color-borde); text-align: left; }
    tbody tr:last-child th { border-bottom: none; }
    .numero { text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
    .tendencia { display: inline-block; padding: 0.1rem 0.5rem; border-radius: 999px; font-size: 0.8rem; font-weight: 600; }
    .crece { background: var(--color-exito-claro); color: var(--color-exito); }
    .decae { background: var(--color-error-claro); color: var(--color-error); }
    .estable { background: var(--color-superficie-suave); color: var(--color-texto-suave); }
    .nuevo { background: var(--color-info-claro); color: var(--color-info); }
    .sin-base { font-size: 0.8rem; font-style: italic; color: var(--color-texto-suave); }
  `,
})
export class ReporteComparativoComponent implements OnInit {
  private readonly servicio = inject(ReporteComparativoService);
  private readonly catalogo = inject(ClientesSegmentoService);
  private readonly decimales = new DecimalPipe('en-US');

  readonly reporte = signal<ReporteComparativo | null>(null);
  readonly segmentos = signal<SegmentoDisponible[]>([]);
  readonly cargando = signal(false);
  readonly exportando = signal(false);
  readonly aviso = signal<string | null>(null);

  // Por defecto: el mes anterior contra el mes en curso hasta hoy.
  desdeA = primerDia(-1);
  hastaA = ultimoDia(-1);
  desdeB = primerDia(0);
  hastaB = hoy();
  segmento: string | null = null;

  ngOnInit(): void {
    this.catalogo.segmentos().subscribe({ next: (lista) => this.segmentos.set(lista), error: () => this.segmentos.set([]) });
    this.comparar();
  }

  comparar(): void {
    const periodos = this.validar();
    if (!periodos) {
      this.reporte.set(null);
      return;
    }
    this.cargando.set(true);
    this.servicio.comparar(periodos).subscribe({
      next: (reporte) => {
        this.reporte.set(reporte);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.reporte.set(null);
        this.aviso.set(e.error?.mensaje ?? 'No se pudo generar el reporte comparativo.');
      },
    });
  }

  exportar(formato: FormatoExportacion): void {
    const periodos = this.validar();
    if (!periodos) {
      return;
    }
    this.exportando.set(true);
    this.servicio.exportar(periodos, formato).subscribe({
      next: (respuesta) => {
        this.exportando.set(false);
        descargarArchivo(respuesta, `reporte-comparativo.${formato}`);
      },
      error: (e: HttpErrorResponse) => {
        this.exportando.set(false);
        this.aviso.set(motivoFalloExportacion(e, 'reporte comparativo'));
      },
    });
  }

  /** Criterio 2: los periodos se revisan antes de consultar al servidor. */
  private validar(): PeriodosComparados | null {
    const problema = validarPeriodos(this.desdeA, this.hastaA, this.desdeB, this.hastaB);
    this.aviso.set(problema);
    return problema ? null : { desdeA: this.desdeA, hastaA: this.hastaA, desdeB: this.desdeB, hastaB: this.hastaB, segmento: this.segmento };
  }

  valor(indicador: IndicadorComparado, numero: number | null): string {
    if (numero === null) {
      return '—';
    }
    return this.decimales.transform(numero, CONTEOS.has(indicador.codigo) ? '1.0-0' : '1.2-2')!;
  }

  signo(numero: number): string {
    return numero > 0 ? '+' : '';
  }

  nombreTendencia(tendencia: Tendencia): string {
    return TENDENCIAS[tendencia];
  }
}

/** Mensaje si los periodos no se pueden comparar; null si están bien. Las fechas llegan como AAAA-MM-DD. */
export function validarPeriodos(desdeA: string, hastaA: string, desdeB: string, hastaB: string): string | null {
  const a = validarPeriodo(desdeA, hastaA);
  if (a) {
    return `Periodo A: ${a}`;
  }
  const b = validarPeriodo(desdeB, hastaB);
  if (b) {
    return `Periodo B: ${b}`;
  }
  if (desdeA <= hastaB && desdeB <= hastaA) {
    return `Los periodos se superponen (${desdeA} a ${hastaA} y ${desdeB} a ${hastaB}). Elija periodos que no compartan días.`;
  }
  return null;
}

function hoy(): string {
  return fecha(new Date());
}

function primerDia(mesesAtras: number): string {
  const d = new Date();
  return fecha(new Date(d.getFullYear(), d.getMonth() + mesesAtras, 1));
}

function ultimoDia(mesesAtras: number): string {
  const d = new Date();
  return fecha(new Date(d.getFullYear(), d.getMonth() + mesesAtras + 1, 0));
}

function fecha(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
