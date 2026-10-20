import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';

/** Factor que suma puntos al riesgo de abandono (bloque `riesgoAbandono.factores` de la ficha integral). */
export interface FactorRiesgoAbandono {
  factor: 'RECENCIA' | 'FRECUENCIA' | 'VALOR' | 'CAMBIO_COMPORTAMIENTO';
  puntos: number;
  maximo: number;
  descripcion: string | null;
}

/** Riesgo de abandono del cliente (GET /api/perfil/clientes/{id}/ficha-integral, bloque `riesgoAbandono`). */
export interface RiesgoAbandonoFicha {
  nivel: 'BAJO' | 'MEDIO' | 'ALTO' | null;
  puntaje: number | null;
  inactivo: boolean;
  enSeguimientoDesde: string | null;
  calculadoEn: string | null;
  factores: FactorRiesgoAbandono[];
  sinDatos: boolean;
}

const NOMBRES_DE_NIVEL: Record<string, string> = {
  BAJO: 'Bajo',
  MEDIO: 'Medio',
  ALTO: 'Alto',
};

const NOMBRES_DE_FACTOR: Record<string, string> = {
  RECENCIA: 'Recencia',
  FRECUENCIA: 'Frecuencia',
  VALOR: 'Valor',
  CAMBIO_COMPORTAMIENTO: 'Cambio de comportamiento',
};

/**
 * Riesgo de abandono del cliente dentro de la ficha integral (SCRUM-572, SCRUM-35): el nivel, el
 * puntaje sobre 100, desde cuándo está en seguimiento si el riesgo es alto y los factores que lo
 * explican, cada uno con sus puntos sobre su máximo (criterio de aceptación 4). Es un componente de
 * presentación: recibe el bloque ya consultado por FichaIntegralComponent.
 */
@Component({
  selector: 'app-riesgo-abandono',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  template: `
    <section class="tarjeta riesgo" aria-label="Riesgo de abandono">
      <h2>Riesgo de abandono</h2>
      @if (riesgo(); as datos) {
        @if (datos.sinDatos || !datos.nivel) {
          <strong class="sin-datos">Sin datos</strong>
          <small>Este cliente no tiene compras vigentes con las que calcular su riesgo, o todavía no se calculó.</small>
        } @else {
          <div class="resumen">
            <strong class="nivel" [class]="'nivel-' + datos.nivel">{{ nombreNivel(datos.nivel) }}</strong>
            @if (datos.puntaje !== null) {
              <span class="puntaje">{{ datos.puntaje | number: '1.0-0' }} / 100</span>
            }
            @if (datos.inactivo) {
              <span class="etiqueta inactivo">Inactivo</span>
            }
          </div>
          @if (datos.enSeguimientoDesde) {
            <p class="seguimiento" role="status">
              En seguimiento comercial desde el {{ datos.enSeguimientoDesde | date: 'dd/MM/yyyy' }}.
            </p>
          }
          <ul class="factores" aria-label="Factores del riesgo">
            @for (factor of datos.factores; track factor.factor) {
              <li>
                <div class="encabezado">
                  <span>{{ nombreFactor(factor.factor) }}</span>
                  <span class="puntos">{{ factor.puntos | number: '1.0-2' }} de {{ factor.maximo | number: '1.0-2' }}</span>
                </div>
                <div class="barra" role="presentation">
                  <span [style.width.%]="proporcion(factor)"></span>
                </div>
                @if (factor.descripcion) {
                  <small>{{ factor.descripcion }}</small>
                }
              </li>
            }
          </ul>
          @if (datos.calculadoEn) {
            <small class="calculado">Calculado el {{ datos.calculadoEn | date: 'dd/MM/yyyy HH:mm' }}.</small>
          }
        }
      }
    </section>
  `,
  styles: `
    .riesgo { display: flex; flex-direction: column; gap: 0.35rem; padding: 1rem 1.25rem; }
    .riesgo h2 { margin: 0 0 0.35rem; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--color-texto-suave); }
    .resumen { display: flex; align-items: baseline; flex-wrap: wrap; gap: 0.75rem; }
    .nivel { font-size: 1.6rem; }
    .nivel-ALTO { color: var(--color-error); }
    .nivel-MEDIO { color: var(--color-aviso); }
    .nivel-BAJO { color: var(--color-exito); }
    .puntaje { color: var(--color-texto-suave); font-weight: 600; }
    .etiqueta.inactivo { color: var(--color-error); background: var(--color-error-claro); }
    .seguimiento {
      margin: 0.25rem 0; padding: 0.5rem 0.75rem; border-left: 3px solid var(--color-error);
      background: var(--color-error-claro); border-radius: 4px; font-size: 0.9rem;
    }
    .factores { list-style: none; margin: 0.5rem 0 0; padding: 0; display: flex; flex-direction: column; gap: 0.65rem; }
    .encabezado { display: flex; justify-content: space-between; gap: 0.5rem; font-size: 0.9rem; }
    .puntos { color: var(--color-texto-suave); font-variant-numeric: tabular-nums; }
    .barra { height: 6px; margin: 0.25rem 0; border-radius: var(--radio-pildora); background: var(--color-borde); overflow: hidden; }
    .barra span { display: block; height: 100%; background: var(--color-primario); }
    .factores small, .sin-datos, .calculado, .riesgo > small { color: var(--color-texto-suave); }
  `,
})
export class RiesgoAbandonoComponent {
  readonly riesgo = input<RiesgoAbandonoFicha | null>(null);

  nombreNivel(nivel: string): string {
    return NOMBRES_DE_NIVEL[nivel] ?? nivel;
  }

  nombreFactor(factor: string): string {
    return NOMBRES_DE_FACTOR[factor] ?? factor;
  }

  /** Porcentaje del máximo del factor que alcanzó el cliente, para la barra. */
  proporcion(factor: FactorRiesgoAbandono): number {
    return factor.maximo > 0 ? Math.min(100, (factor.puntos / factor.maximo) * 100) : 0;
  }
}
