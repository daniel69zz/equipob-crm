import { Component, input } from '@angular/core';

/** Clasificación por frecuencia (GET /api/perfil/clientes/{id}/ficha-integral, bloque `clasificacionFrecuencia`). */
export interface ClasificacionFrecuenciaFicha {
  clasificacion: 'OCASIONAL' | 'HABITUAL' | 'FRECUENTE' | null;
  frecuencia: number | null;
  sinDatos: boolean;
}

const NOMBRES_DE_CLASIFICACION: Record<string, string> = {
  OCASIONAL: 'Ocasional',
  HABITUAL: 'Habitual',
  FRECUENTE: 'Frecuente',
};

/**
 * Clasificación del cliente por frecuencia de compra (ocasional, habitual o frecuente) con la
 * frecuencia que la determina, dentro de la ficha integral (SCRUM-583, SCRUM-38). Es un componente
 * de presentación: recibe el bloque `clasificacionFrecuencia` ya consultado por FichaIntegralComponent.
 */
@Component({
  selector: 'app-clasificacion-frecuencia',
  standalone: true,
  template: `
    <section class="tarjeta clasificacion-frecuencia" aria-label="Clasificación por frecuencia">
      <h2>Frecuencia de compra</h2>
      @if (clasificacion(); as datos) {
        @if (datos.sinDatos || !datos.clasificacion) {
          <strong class="sin-datos">Sin clasificación</strong>
          <small>Este cliente no tiene compras vigentes que clasificar.</small>
        } @else {
          <strong [class]="'clasificacion-' + datos.clasificacion">{{ nombre(datos.clasificacion) }}</strong>
          @if (datos.frecuencia !== null) {
            <small>{{ datos.frecuencia }} {{ datos.frecuencia === 1 ? 'compra vigente' : 'compras vigentes' }}</small>
          }
        }
      } @else {
        <strong class="sin-datos">Sin clasificación</strong>
      }
    </section>
  `,
  styles: `
    .clasificacion-frecuencia { display: flex; flex-direction: column; gap: 0.15rem; padding: 1rem 1.25rem; }
    .clasificacion-frecuencia h2 { margin: 0 0 0.35rem; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--color-texto-suave); }
    .clasificacion-frecuencia strong { font-size: 1.6rem; }
    .clasificacion-frecuencia small { color: var(--color-texto-suave); }
    .sin-datos { color: var(--color-texto-suave); }
    .clasificacion-FRECUENTE { color: var(--color-exito); }
    .clasificacion-HABITUAL { color: var(--color-aviso); }
  `,
})
export class ClasificacionFrecuenciaComponent {
  readonly clasificacion = input<ClasificacionFrecuenciaFicha | null>(null);

  nombre(clasificacion: string | null): string {
    return clasificacion ? (NOMBRES_DE_CLASIFICACION[clasificacion] ?? clasificacion) : '';
  }
}
