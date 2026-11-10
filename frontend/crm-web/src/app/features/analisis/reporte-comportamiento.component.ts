import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ClientesSegmentoService, SegmentoDisponible } from '../segmentacion/clientes-segmento.service';
import { ReporteComportamiento, ReporteComportamientoService } from './reporte-comportamiento.service';

/** Mismo tope que el servidor (RangoPeriodo.DIAS_MAXIMOS): cinco años. */
const DIAS_MAXIMOS = 5 * 366;

/**
 * Reporte de comportamiento de compra de la cartera o de un segmento en un periodo (SCRUM-58, RF-50):
 * frecuencia, valor y categorías. El periodo se valida aquí antes de pedir el reporte, así que uno
 * invertido o incompleto se avisa sin consultar al servidor (criterio de aceptación 2).
 */
@Component({
  selector: 'app-reporte-comportamiento',
  standalone: true,
  imports: [DecimalPipe, FormsModule],
  template: `
    <h1>Comportamiento de compra</h1>
    <p class="subtitulo">
      Frecuencia, valor y categorías de compra de la cartera o de un segmento en un periodo. Solo cuentan las
      compras vigentes vinculadas a un cliente; las anuladas no cuentan y las devoluciones se descuentan.
    </p>

    <form class="tarjeta filtros" (ngSubmit)="generar()" novalidate>
      <label>
        Desde
        <input type="date" name="desde" [(ngModel)]="desde" />
      </label>
      <label>
        Hasta
        <input type="date" name="hasta" [(ngModel)]="hasta" />
      </label>
      <label>
        Segmento
        <select name="segmento" [(ngModel)]="segmento">
          <option [ngValue]="null">Toda la cartera</option>
          @for (s of segmentos(); track s.segmento) {
            <option [ngValue]="s.segmento">{{ s.segmento }} ({{ s.clientes }})</option>
          }
        </select>
      </label>
      <button type="submit" [disabled]="cargando()">Generar</button>
    </form>

    @if (aviso()) {
      <p class="error" role="alert">{{ aviso() }}</p>
    }

    @if (reporte(); as r) {
      <p class="alcance">
        {{ r.alcance.segmento ? 'Segmento «' + r.alcance.segmento + '» (' + r.alcance.clientesDelSegmento + ' clientes)' : 'Toda la cartera' }}
        · del {{ r.desde }} al {{ r.hasta }}
      </p>
      @if (r.sinDatos) {
        <section class="tarjeta vacio" aria-label="Sin datos">
          <h2>Sin datos para este filtro</h2>
          <p>{{ r.mensaje }}</p>
        </section>
      } @else {
        <div class="indicadores">
          <section class="tarjeta" aria-label="Frecuencia de compra">
            <h2>Frecuencia</h2>
            <p class="cifra">{{ r.frecuencia.compras }} <small>compras</small></p>
            <p>{{ r.frecuencia.clientesConCompras }} clientes compraron</p>
            <p>{{ r.frecuencia.comprasPorCliente | number: '1.2-2' }} compras por cliente</p>
          </section>
          <section class="tarjeta" aria-label="Valor de compra">
            <h2>Valor</h2>
            <p class="cifra">Bs {{ r.valor.total | number: '1.2-2' }}</p>
            <p>Ticket promedio: Bs {{ r.valor.ticketPromedio | number: '1.2-2' }}</p>
            <p>Por cliente: Bs {{ r.valor.valorPorCliente | number: '1.2-2' }}</p>
          </section>
        </div>
        <section class="tarjeta" aria-label="Categorías de compra">
          <h2>Categorías</h2>
          <table>
            <thead>
              <tr>
                <th scope="col">Categoría</th>
                <th scope="col" class="numero">Compras</th>
                <th scope="col" class="numero">Unidades</th>
                <th scope="col" class="numero">Monto (Bs)</th>
                <th scope="col" class="numero">% del monto</th>
              </tr>
            </thead>
            <tbody>
              @for (c of r.categorias; track c.categoria + c.sinCategoria) {
                <tr>
                  <th scope="row" [class.sin-categoria]="c.sinCategoria">{{ c.categoria }}</th>
                  <td class="numero">{{ c.compras }}</td>
                  <td class="numero">{{ c.unidades }}</td>
                  <td class="numero">{{ c.monto | number: '1.2-2' }}</td>
                  <td class="numero">
                    <span class="barra" [style.width.%]="c.porcentaje"></span>{{ c.porcentaje | number: '1.2-2' }} %
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </section>
      }
    } @else if (cargando()) {
      <p><small>Generando…</small></p>
    }
  `,
  styles: `
    .filtros { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 1rem; margin-bottom: 1rem; padding: 1rem 1.25rem; }
    .filtros label { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.85rem; color: var(--color-texto-suave); }
    .alcance { color: var(--color-texto-suave); margin: 0 0 0.75rem; }
    .indicadores { display: grid; grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr)); gap: 1rem; margin-bottom: 1rem; }
    .indicadores p { margin: 0.25rem 0; }
    .cifra { font-size: 1.8rem; font-weight: 700; }
    .cifra small { font-size: 0.9rem; font-weight: 400; color: var(--color-texto-suave); }
    h2 { font-size: 1rem; margin: 0 0 0.5rem; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 0.4rem 0.5rem; border-bottom: 1px solid var(--color-borde); text-align: left; }
    tbody tr:last-child th { border-bottom: none; }
    .numero { text-align: right; white-space: nowrap; }
    .sin-categoria { font-style: italic; color: var(--color-texto-suave); }
    .barra { display: inline-block; height: 0.5rem; max-width: 4rem; margin-right: 0.4rem; border-radius: 2px;
             background: var(--color-primario); vertical-align: middle; }
    .vacio h2 { margin-bottom: 0.25rem; }
  `,
})
export class ReporteComportamientoComponent implements OnInit {
  private readonly servicio = inject(ReporteComportamientoService);
  private readonly catalogo = inject(ClientesSegmentoService);

  readonly reporte = signal<ReporteComportamiento | null>(null);
  readonly segmentos = signal<SegmentoDisponible[]>([]);
  readonly cargando = signal(false);
  readonly aviso = signal<string | null>(null);

  desde = primerDiaDelMes();
  hasta = hoy();
  segmento: string | null = null;

  ngOnInit(): void {
    // Sin el catálogo se puede reportar igual la cartera completa.
    this.catalogo.segmentos().subscribe({ next: (lista) => this.segmentos.set(lista), error: () => this.segmentos.set([]) });
    this.generar();
  }

  generar(): void {
    const problema = validarPeriodo(this.desde, this.hasta);
    if (problema) {
      // Criterio 2: se avisa sin consultar al servidor.
      this.aviso.set(problema);
      this.reporte.set(null);
      return;
    }
    this.aviso.set(null);
    this.cargando.set(true);
    this.servicio.generar(this.desde, this.hasta, this.segmento).subscribe({
      next: (reporte) => {
        this.reporte.set(reporte);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.reporte.set(null);
        this.aviso.set(e.error?.mensaje ?? 'No se pudo generar el reporte de comportamiento de compra.');
      },
    });
  }
}

/** Mensaje si el periodo no se puede pedir; null si está bien. Las fechas llegan como AAAA-MM-DD. */
export function validarPeriodo(desde: string, hasta: string): string | null {
  if (!desde || !hasta) {
    return 'Indique las fechas desde y hasta del periodo.';
  }
  if (desde > hasta) {
    return 'La fecha «desde» no puede ser posterior a «hasta». Corrija el periodo.';
  }
  const dias = (Date.parse(hasta) - Date.parse(desde)) / 86_400_000 + 1;
  if (dias > DIAS_MAXIMOS) {
    return `El periodo abarca ${dias} días; el máximo es ${DIAS_MAXIMOS}. Acorte el rango.`;
  }
  return null;
}

function hoy(): string {
  return fecha(new Date());
}

function primerDiaDelMes(): string {
  const d = new Date();
  return fecha(new Date(d.getFullYear(), d.getMonth(), 1));
}

/** Fecha local en AAAA-MM-DD, sin pasar por UTC. */
function fecha(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
