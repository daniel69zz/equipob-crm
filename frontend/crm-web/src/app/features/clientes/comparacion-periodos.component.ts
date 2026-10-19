import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { Permisos } from '../../core/auth/sesion';
import { IdentificadorOrigen } from './clientes.service';
import {
  ComparacionPeriodosService,
  PeriodosElegidos,
  ResultadoComparacion,
  TendenciaConsumo,
  VariacionConsumo,
} from './comparacion-periodos.service';

const NOMBRES_DE_TENDENCIA: Record<TendenciaConsumo, string> = {
  CRECE: 'Crece',
  DECAE: 'Decae',
  ESTABLE: 'Estable',
  NUEVO: 'Nuevo (sin compras en el periodo base)',
};

/** Fecha local en formato AAAA-MM-DD, el que reciben los campos de fecha y el API. */
function aTexto(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

/**
 * Comparación de la frecuencia y el valor de compra entre dos periodos (SCRUM-40, SCRUM-352 y
 * SCRUM-355) para saber si un cliente o un segmento crece o decae. Por defecto propone el mes
 * anterior como periodo A (base) y el mes en curso como periodo B. Comparar un segmento solo se ofrece
 * con el permiso SEGMENTOS_CONSULTAR (el Gateway lo exige igual). Fechas incompletas, al revés o
 * superpuestas se avisan sin llamar al servidor.
 */
@Component({
  selector: 'app-comparacion-periodos',
  standalone: true,
  imports: [DecimalPipe, FormsModule],
  template: `
    <section class="tarjeta comparacion" aria-label="Comparación entre periodos">
      <h2>Comparar dos periodos</h2>
      <form class="filtro" (ngSubmit)="comparar()">
        @if (puedeCompararSegmentos) {
          <fieldset class="alcance">
            <legend>Alcance</legend>
            <label><input type="radio" name="alcance" value="CLIENTE" [(ngModel)]="alcance" /> Este cliente</label>
            <label><input type="radio" name="alcance" value="SEGMENTO" [(ngModel)]="alcance" /> Un segmento</label>
          </fieldset>
          @if (alcance === 'SEGMENTO') {
            <label>
              Segmento
              <input type="text" name="segmento" [(ngModel)]="segmento" maxlength="60" placeholder="Código del segmento" />
            </label>
          }
        }
        <fieldset class="periodo">
          <legend>Periodo A (base)</legend>
          <label>Desde <input type="date" name="desdeA" [(ngModel)]="desdeA" required /></label>
          <label>Hasta <input type="date" name="hastaA" [(ngModel)]="hastaA" required /></label>
        </fieldset>
        <fieldset class="periodo">
          <legend>Periodo B (comparado)</legend>
          <label>Desde <input type="date" name="desdeB" [(ngModel)]="desdeB" required /></label>
          <label>Hasta <input type="date" name="hastaB" [(ngModel)]="hastaB" required /></label>
        </fieldset>
        <button type="submit" [disabled]="cargando()">Comparar</button>
      </form>

      @if (aviso(); as texto) {
        <p class="error" role="status">{{ texto }}</p>
      }
      @if (!aviso() && resultado(); as comparacion) {
        <p class="alcance-resultado">
          <small class="sin-datos">{{ describirAlcance(comparacion) }}</small>
        </p>
        <table class="resultado" aria-live="polite">
          <thead>
            <tr>
              <th>Métrica</th>
              <th>Periodo A</th>
              <th>Periodo B</th>
              <th>Variación</th>
              <th>Variación %</th>
              <th>Tendencia</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <th scope="row">Frecuencia (compras)</th>
              <td>{{ comparacion.frecuencia.periodoA | number: '1.0-0' }}</td>
              <td>{{ comparacion.frecuencia.periodoB | number: '1.0-0' }}</td>
              <td>{{ comparacion.frecuencia.variacionAbsoluta | number: '1.0-0' }}</td>
              <td>{{ porcentaje(comparacion.frecuencia) }}</td>
              <td><span class="etiqueta" [class]="'tendencia-' + comparacion.frecuencia.tendencia">{{ nombreTendencia(comparacion.frecuencia.tendencia) }}</span></td>
            </tr>
            <tr>
              <th scope="row">Valor de compra</th>
              <td>{{ comparacion.valor.periodoA | number: '1.2-2' }}</td>
              <td>{{ comparacion.valor.periodoB | number: '1.2-2' }}</td>
              <td>{{ comparacion.valor.variacionAbsoluta | number: '1.2-2' }}</td>
              <td>{{ porcentaje(comparacion.valor) }}</td>
              <td><span class="etiqueta" [class]="'tendencia-' + comparacion.valor.tendencia">{{ nombreTendencia(comparacion.valor.tendencia) }}</span></td>
            </tr>
          </tbody>
        </table>
        @if (comparacion.sinDatos && comparacion.mensaje) {
          <p><small class="sin-datos">{{ comparacion.mensaje }}</small></p>
        }
      }
      @if (cargando()) {
        <p><small>Calculando…</small></p>
      }
    </section>
  `,
  styles: `
    .comparacion { margin-bottom: 1rem; padding: 1rem 1.25rem; }
    h2 { margin: 0 0 0.5rem; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--color-texto-suave); }
    .filtro { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 1rem; margin-bottom: 0.75rem; }
    fieldset { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0.75rem; border: 1px solid var(--color-borde); border-radius: 8px; padding: 0.5rem 0.75rem; margin: 0; }
    legend { font-size: 0.8rem; color: var(--color-texto-suave); padding: 0 0.25rem; }
    .resultado { width: 100%; border-collapse: collapse; }
    .resultado th, .resultado td { text-align: left; padding: 0.4rem 0.6rem; border-bottom: 1px solid var(--color-borde); }
    .sin-datos { color: var(--color-texto-suave); }
    .tendencia-CRECE { color: var(--color-exito); background: var(--color-exito-claro); }
    .tendencia-DECAE { color: var(--color-error); background: var(--color-error-claro); }
    .tendencia-NUEVO { color: var(--color-aviso); background: var(--color-aviso-claro); }
  `,
})
export class ComparacionPeriodosComponent {
  private readonly servicio = inject(ComparacionPeriodosService);
  private readonly auth = inject(AuthService);

  readonly idCliente = input.required<string | number>();
  readonly identificadores = input.required<IdentificadorOrigen[]>();

  /** Comparar un segmento exige SEGMENTOS_CONSULTAR además del permiso de indicadores. */
  readonly puedeCompararSegmentos = this.auth.tienePermiso(Permisos.SEGMENTOS_CONSULTAR);

  alcance: 'CLIENTE' | 'SEGMENTO' = 'CLIENTE';
  segmento = '';
  desdeA = aTexto(new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1));
  hastaA = aTexto(new Date(new Date().getFullYear(), new Date().getMonth(), 0));
  desdeB = aTexto(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  hastaB = aTexto(new Date());

  readonly resultado = signal<ResultadoComparacion | null>(null);
  readonly cargando = signal(false);
  readonly aviso = signal<string | null>(null);

  comparar(): void {
    const problema = this.validar();
    if (problema) {
      this.resultado.set(null);
      this.aviso.set(problema);
      return;
    }
    this.aviso.set(null);
    this.cargando.set(true);
    const periodos: PeriodosElegidos = {
      desdeA: this.desdeA,
      hastaA: this.hastaA,
      desdeB: this.desdeB,
      hastaB: this.hastaB,
    };
    const consulta =
      this.alcance === 'SEGMENTO'
        ? this.servicio.compararSegmento(this.segmento.trim(), periodos)
        : this.servicio.compararCliente(Number(this.idCliente()), this.identificadores(), periodos);
    consulta.subscribe({
      next: (resultado) => {
        this.resultado.set(resultado);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.resultado.set(null);
        this.cargando.set(false);
        this.aviso.set(e.error?.mensaje ?? 'No se pudo comparar los periodos.');
      },
    });
  }

  nombreTendencia(tendencia: TendenciaConsumo): string {
    return NOMBRES_DE_TENDENCIA[tendencia] ?? tendencia;
  }

  /** El porcentaje de la variación, o la razón por la que no existe cuando el periodo base está en cero. */
  porcentaje(variacion: VariacionConsumo): string {
    if (variacion.variacionPorcentual === null) {
      return 'Sin base de comparación';
    }
    return `${variacion.variacionPorcentual.toFixed(2)} %`;
  }

  describirAlcance(resultado: ResultadoComparacion): string {
    const periodos = `A: ${resultado.periodoA.desde} a ${resultado.periodoA.hasta} · B: ${resultado.periodoB.desde} a ${resultado.periodoB.hasta}`;
    if (resultado.alcance.tipo === 'SEGMENTO') {
      return `Segmento ${resultado.alcance.segmento} (${resultado.alcance.clientes} clientes) · ${periodos}`;
    }
    return `Este cliente · ${periodos}`;
  }

  private validar(): string | null {
    if (this.alcance === 'SEGMENTO' && !this.segmento.trim()) {
      return 'Indique el segmento a comparar.';
    }
    const fechas: [string, string][] = [
      ['desde del periodo A', this.desdeA],
      ['hasta del periodo A', this.hastaA],
      ['desde del periodo B', this.desdeB],
      ['hasta del periodo B', this.hastaB],
    ];
    const faltante = fechas.find(([, fecha]) => !fecha);
    if (faltante) {
      return `Indique la fecha ${faltante[0]}.`;
    }
    if (this.desdeA > this.hastaA) {
      return "En el periodo A, la fecha 'desde' no puede ser posterior a 'hasta'.";
    }
    if (this.desdeB > this.hastaB) {
      return "En el periodo B, la fecha 'desde' no puede ser posterior a 'hasta'.";
    }
    if (this.desdeA <= this.hastaB && this.desdeB <= this.hastaA) {
      return 'Los periodos a comparar no pueden superponerse.';
    }
    return null;
  }
}
