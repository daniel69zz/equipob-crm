import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IdentificadorOrigen } from './clientes.service';
import { ConsumoPeriodoService, ValorPorPeriodo } from './consumo-periodo.service';

/** Fecha local en formato AAAA-MM-DD, el que reciben los campos de fecha y el API. */
function aTexto(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

/**
 * Valor total y promedio por compra que consumió un cliente en un periodo (SCRUM-39, SCRUM-585),
 * para medir su aporte en distintos momentos y no solo en todo su historial. Por defecto propone el
 * mes en curso. Un rango incompleto o al revés se avisa sin llamar al servidor; un periodo sin
 * compras se muestra en cero con su mensaje, no como un error.
 */
@Component({
  selector: 'app-consumo-periodo',
  standalone: true,
  imports: [DecimalPipe, FormsModule],
  template: `
    <section class="tarjeta consumo-periodo" aria-label="Consumo por periodo">
      <h2>Consumo por periodo</h2>
      <form class="filtro" (ngSubmit)="consultar()">
        <label>
          Desde
          <input type="date" name="desde" [(ngModel)]="desde" required />
        </label>
        <label>
          Hasta
          <input type="date" name="hasta" [(ngModel)]="hasta" required />
        </label>
        <button type="submit" [disabled]="cargando()">Consultar</button>
      </form>

      @if (aviso(); as texto) {
        <p class="error" role="status">{{ texto }}</p>
      }
      @if (!aviso() && resultado(); as valor) {
        <div class="resultado" aria-live="polite">
          <div class="indicador">
            <span class="nombre-indicador">Valor total</span>
            <strong [class.sin-datos]="valor.sinDatos">{{ valor.total | number: '1.2-2' }}</strong>
          </div>
          <div class="indicador">
            <span class="nombre-indicador">Promedio por compra</span>
            <strong [class.sin-datos]="valor.sinDatos">{{ valor.promedio | number: '1.2-2' }}</strong>
          </div>
          <div class="indicador">
            <span class="nombre-indicador">Compras</span>
            <strong [class.sin-datos]="valor.sinDatos">{{ valor.compras }}</strong>
          </div>
        </div>
        <p><small class="sin-datos">{{ nota(valor) }}</small></p>
      }
      @if (cargando()) {
        <p><small>Calculando…</small></p>
      }
    </section>
  `,
  styles: `
    .consumo-periodo { margin-bottom: 1rem; padding: 1rem 1.25rem; }
    h2 { margin: 0 0 0.5rem; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--color-texto-suave); }
    .filtro { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 1rem; margin-bottom: 0.75rem; }
    .resultado { display: flex; flex-wrap: wrap; gap: 2rem; }
    .indicador { display: flex; flex-direction: column; gap: 0.15rem; }
    .nombre-indicador { color: var(--color-texto-suave); font-size: 0.8rem; }
    .indicador strong { font-size: 1.6rem; }
    .sin-datos { color: var(--color-texto-suave); }
  `,
})
export class ConsumoPeriodoComponent {
  private readonly servicio = inject(ConsumoPeriodoService);

  readonly idCliente = input.required<string | number>();
  readonly identificadores = input.required<IdentificadorOrigen[]>();

  desde = aTexto(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  hasta = aTexto(new Date());

  readonly resultado = signal<ValorPorPeriodo | null>(null);
  readonly cargando = signal(false);
  readonly aviso = signal<string | null>(null);

  consultar(): void {
    const faltante = !this.desde ? 'desde' : !this.hasta ? 'hasta' : null;
    if (faltante) {
      this.avisar(`Indique la fecha '${faltante}' del periodo.`);
      return;
    }
    if (this.desde > this.hasta) {
      this.avisar("La fecha 'desde' no puede ser posterior a 'hasta'.");
      return;
    }
    this.aviso.set(null);
    this.cargando.set(true);
    this.servicio.consultar(Number(this.idCliente()), this.identificadores(), this.desde, this.hasta).subscribe({
      next: (valor) => {
        this.resultado.set(valor);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.resultado.set(null);
        this.cargando.set(false);
        this.aviso.set(e.error?.mensaje ?? 'No se pudo consultar el consumo del periodo.');
      },
    });
  }

  /** El mensaje del servidor si el periodo no tiene compras; si las tiene, qué periodo y zona se calcularon. */
  nota(valor: ValorPorPeriodo): string {
    if (valor.sinDatos && valor.mensaje) {
      return valor.mensaje;
    }
    return `Del ${valor.desde} al ${valor.hasta}, zona horaria ${valor.zonaHoraria}. No incluye compras anuladas.`;
  }

  private avisar(texto: string): void {
    this.resultado.set(null);
    this.aviso.set(texto);
  }
}
