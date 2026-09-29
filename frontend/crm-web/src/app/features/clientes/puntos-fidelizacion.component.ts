import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';

/** Saldo de puntos del cliente (GET /api/perfil/clientes/{id}/ficha-integral, bloque `puntos`). */
export interface PuntosFidelizacion {
  saldo: number | null;
  nivel: string | null;
  actualizadoEn: string | null;
  sinDatos: boolean;
}

/**
 * Puntos de fidelización del cliente, dentro de la ficha integral (SCRUM-148, SCRUM-10). Es un
 * componente de presentación: recibe el bloque `puntos` ya consultado por
 * FichaIntegralComponent, no hace su propia llamada HTTP.
 */
@Component({
  selector: 'app-puntos-fidelizacion',
  standalone: true,
  imports: [DecimalPipe, DatePipe],
  template: `
    <section class="tarjeta puntos" aria-label="Puntos de fidelización">
      <h2>Puntos de fidelización</h2>
      @if (puntos(); as datos) {
        @if (datos.sinDatos) {
          <strong class="sin-datos">Sin datos</strong>
          <small>Este cliente aún no tiene puntos asignados.</small>
        } @else {
          <strong>{{ datos.saldo | number: '1.0-0' }} pts</strong>
          <small>
            Nivel {{ datos.nivel }}
            @if (datos.actualizadoEn) {
              · actualizado {{ datos.actualizadoEn | date: 'dd/MM/yyyy HH:mm' }}
            }
          </small>
        }
      } @else if (error()) {
        <small class="error" role="status">{{ error() }}</small>
      } @else if (cargando()) {
        <small>Calculando…</small>
      }
    </section>
  `,
  styles: `
    .puntos { display: flex; flex-direction: column; gap: 0.15rem; padding: 1rem 1.25rem; }
    .puntos h2 { margin: 0 0 0.35rem; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--color-texto-suave); }
    .puntos strong { font-size: 1.6rem; }
    .puntos small { color: var(--color-texto-suave); }
    .sin-datos { color: var(--color-texto-suave); }
  `,
})
export class PuntosFidelizacionComponent {
  readonly puntos = input<PuntosFidelizacion | null>(null);
  readonly cargando = input(false);
  readonly error = input<string | null>(null);
}
