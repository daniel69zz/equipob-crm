import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatrizRetencion, RetencionCohortesService, RetencionPeriodo } from './retencion-cohortes.service';

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/**
 * Retención de clientes por cohorte de primera compra (SCRUM-331, SCRUM-36, RF-28): una matriz con una
 * fila por cohorte mensual y una columna por periodo posterior, cada celda con el porcentaje y la
 * cantidad de clientes que volvió a comprar, coloreada según la retención.
 */
@Component({
  selector: 'app-retencion-cohortes',
  standalone: true,
  imports: [DecimalPipe, FormsModule],
  template: `
    <h1>Retención por cohortes</h1>
    <p class="subtitulo">
      Clientes agrupados por el mes de su primera compra y porcentaje que vuelve a comprar en cada mes
      posterior. Las compras anuladas no cuentan.
    </p>

    <form class="tarjeta filtros" (ngSubmit)="buscar()">
      <label>
        Desde
        <input type="month" name="desde" [(ngModel)]="desde" />
      </label>
      <label>
        Hasta
        <input type="month" name="hasta" [(ngModel)]="hasta" [max]="datos()?.mesActual ?? ''" />
      </label>
      <button type="submit" [disabled]="cargando()">Ver</button>
    </form>

    @if (error()) {
      <p class="error" role="status">{{ error() }}</p>
    }

    @if (datos(); as d) {
      <div class="tarjeta matriz">
        <table>
          <thead>
            <tr>
              <th>Cohorte</th>
              <th class="numero">Clientes</th>
              @for (periodo of columnas(); track periodo) {
                <th class="numero">Mes {{ periodo }}</th>
              }
            </tr>
          </thead>
          <tbody>
            @for (fila of d.cohortes; track fila.cohorte) {
              <tr>
                <td class="cohorte">{{ nombreMes(fila.cohorte) }}</td>
                <td class="numero">{{ fila.clientes }}</td>
                @if (fila.clientes === 0) {
                  <td [attr.colspan]="columnas().length" class="vacio">Sin clientes nuevos</td>
                } @else {
                  @for (periodo of columnas(); track periodo) {
                    @if (fila.periodos[periodo]; as celda) {
                      <td class="celda" [style.background]="fondo(celda)" [class.alta]="celda.porcentaje >= 60"
                          [title]="celda.clientes + ' de ' + fila.clientes + ' clientes compraron en ' + nombreMes(celda.mes)">
                        <strong>{{ celda.porcentaje | number: '1.0-1' }} %</strong>
                        <small>{{ celda.clientes }}</small>
                      </td>
                    } @else {
                      <td></td>
                    }
                  }
                }
              </tr>
            }
          </tbody>
        </table>
        <p class="nota">
          Meses en hora de Bolivia ({{ d.zonaHoraria }}). El mes en curso ({{ nombreMes(d.mesActual) }}) aún
          está incompleto.
        </p>
      </div>
    } @else if (cargando()) {
      <p><small>Cargando…</small></p>
    }
  `,
  styles: `
    .filtros { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 1rem; margin-bottom: 1rem; padding: 1rem 1.25rem; }
    .filtros label { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.85rem; color: var(--color-texto-suave); }
    .matriz { overflow-x: auto; }
    table { border-collapse: separate; border-spacing: 2px; }
    th, td { white-space: nowrap; }
    .numero { text-align: right; }
    .cohorte { font-weight: 600; text-transform: capitalize; }
    .celda { min-width: 4.5rem; text-align: center; border-radius: 4px; padding: 0.35rem 0.5rem; }
    .celda strong { display: block; }
    .celda small { color: var(--color-texto-suave); }
    .celda.alta, .celda.alta small { color: #fff; }
    .vacio { color: var(--color-texto-suave); font-style: italic; }
    .nota { margin: 0.75rem 0 0; font-size: 0.8rem; color: var(--color-texto-suave); }
  `,
})
export class RetencionCohortesComponent implements OnInit {
  private readonly servicio = inject(RetencionCohortesService);

  readonly datos = signal<MatrizRetencion | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  desde: string | null = null;
  hasta: string | null = null;

  /** Periodos que tiene la cohorte más antigua: cada columna de la matriz. */
  readonly columnas = computed(() => {
    const largo = Math.max(0, ...(this.datos()?.cohortes.map((c) => c.periodos.length) ?? []));
    return Array.from({ length: largo }, (_, i) => i);
  });

  ngOnInit(): void {
    this.buscar();
  }

  buscar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.servicio.matriz(this.desde, this.hasta).subscribe({
      next: (matriz) => {
        this.datos.set(matriz);
        this.desde = matriz.desde;
        this.hasta = matriz.hasta;
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(e.error?.mensaje ?? 'No se pudo calcular la retención por cohortes.');
      },
    });
  }

  /** "2026-07" → "jul 2026". */
  nombreMes(mes: string): string {
    const [anio, numero] = mes.split('-');
    return `${MESES[Number(numero) - 1] ?? numero} ${anio}`;
  }

  /** Más intenso cuanto mayor es la retención. */
  fondo(celda: RetencionPeriodo): string {
    const intensidad = Math.round(8 + (celda.porcentaje / 100) * 82);
    return `color-mix(in srgb, var(--color-primario) ${intensidad}%, var(--color-superficie))`;
  }
}
