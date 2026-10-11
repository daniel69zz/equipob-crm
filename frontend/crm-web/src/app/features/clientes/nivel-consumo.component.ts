import { DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';

/** Nivel de consumo del cliente (GET /api/perfil/clientes/{id}/ficha-integral, bloque `nivelConsumo`). */
export interface NivelConsumoFicha {
  nivel: 'BAJO' | 'MEDIO' | 'ALTO' | null;
  valorAcumulado: number | null;
  sinDatos: boolean;
}

const NOMBRES_DE_NIVEL: Record<string, string> = {
  BAJO: 'Bajo',
  MEDIO: 'Medio',
  ALTO: 'Alto',
};

/**
 * Nivel de consumo del cliente (bajo, medio o alto), dentro de la ficha integral (SCRUM-566,
 * SCRUM-27). Es un componente de presentación: recibe el bloque `nivelConsumo` ya consultado por
 * FichaIntegralComponent, no hace su propia llamada HTTP.
 */
@Component({
  selector: 'app-nivel-consumo',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <section class="tarjeta nivel-consumo" aria-label="Nivel de consumo">
      <h2>Nivel de consumo</h2>
      @if (nivel(); as datos) {
        @if (datos.sinDatos || !datos.nivel) {
          <strong class="sin-datos">Sin nivel</strong>
          <small>Este cliente no tiene compras vigentes que clasificar.</small>
        } @else {
          <strong [class]="'nivel-' + datos.nivel">{{ nombreNivel(datos.nivel) }}</strong>
          @if (datos.valorAcumulado !== null) {
            <small>Valor acumulado: {{ datos.valorAcumulado | number: '1.2-2' }}</small>
          }
        }
      }
    </section>
  `,
  styles: `
    .nivel-consumo { display: flex; flex-direction: column; gap: 0.15rem; padding: 1rem 1.25rem; }
    .nivel-consumo h2 { margin: 0 0 0.35rem; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--color-texto-suave); }
    .nivel-consumo strong { font-size: 1.6rem; }
    .nivel-consumo small { color: var(--color-texto-suave); }
    .sin-datos { color: var(--color-texto-suave); }
    .nivel-ALTO { color: var(--color-exito); }
    .nivel-MEDIO { color: var(--color-aviso); }
  `,
})
export class NivelConsumoComponent {
  readonly nivel = input<NivelConsumoFicha | null>(null);

  nombreNivel(nivel: string | null): string {
    return nivel ? (NOMBRES_DE_NIVEL[nivel] ?? nivel) : '';
  }
}
