import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { NOMBRES_NIVEL } from '../fidelizacion/niveles.service';

/** Saldo de puntos del cliente (GET /api/perfil/clientes/{id}/ficha-integral, bloque `puntos`). */
export interface PuntosFidelizacion {
  saldo: number | null;
  nivel: string | null;
  actualizadoEn: string | null;
  sinDatos: boolean;
  /** SCRUM-49: puntos del periodo de evaluación con los que se asignó el nivel (si el servicio los envía). */
  puntosPeriodo?: number | null;
}

/**
 * Puntos de fidelización del cliente, dentro de la ficha integral (SCRUM-148, SCRUM-10). Es un
 * componente de presentación: recibe el bloque `puntos` ya consultado por
 * FichaIntegralComponent, no hace su propia llamada HTTP. Muestra el nivel actual de fidelización
 * (SCRUM-49, criterio de aceptación 3).
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
          @if (datos.nivel) {
            <span class="nivel" [attr.data-nivel]="datos.nivel">Nivel {{ nombreNivel(datos.nivel) }}</span>
          }
          <small>
            {{ datos.nivel ? '' : 'Sin nivel asignado' }}
            @if (datos.puntosPeriodo !== null && datos.puntosPeriodo !== undefined) {
              {{ datos.puntosPeriodo | number: '1.0-0' }} pts en el periodo de evaluación
            }
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
    .nivel { align-self: flex-start; margin: .2rem 0; padding: .15rem .6rem; border-radius: 999px; font-size: .78rem; font-weight: 700; color: #fff; background: var(--color-primario); }
    .nivel[data-nivel='BRONCE'] { background: #b0703a; }
    .nivel[data-nivel='PLATA'] { background: #8a94a6; }
    .nivel[data-nivel='ORO'] { background: #c9a227; }
  `,
})
export class PuntosFidelizacionComponent {
  nombreNivel(nivel: string): string {
    return NOMBRES_NIVEL[nivel] || nivel;
  }
  readonly puntos = input<PuntosFidelizacion | null>(null);
  readonly cargando = input(false);
  readonly error = input<string | null>(null);
}
