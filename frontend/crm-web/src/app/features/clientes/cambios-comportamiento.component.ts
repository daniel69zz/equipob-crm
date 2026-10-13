import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';

/** Alerta de cambio de comportamiento (bloque `cambiosComportamiento.alertas` de la ficha integral). */
export interface AlertaCambioComportamiento {
  tipo: 'FRECUENCIA' | 'MONTO';
  direccion: 'AUMENTO' | 'DISMINUCION';
  valorActual: number;
  valorHabitual: number;
  variacion: number;
  periodoDesde: string | null;
  periodoHasta: string | null;
  detectadoEn: string | null;
  mensaje: string | null;
}

/** Cambios de comportamiento del cliente (GET /api/perfil/clientes/{id}/ficha-integral, bloque `cambiosComportamiento`). */
export interface CambiosComportamientoFicha {
  estado: 'CON_VARIACION' | 'ESTABLE' | 'SIN_LINEA_BASE' | 'SIN_EVALUAR' | null;
  evaluadoEn: string | null;
  alertas: AlertaCambioComportamiento[];
  sinDatos: boolean;
}

const NOMBRES_DE_TIPO: Record<string, string> = {
  FRECUENCIA: 'Frecuencia',
  MONTO: 'Monto',
};

const DESCRIPCION_DE_ESTADO: Record<string, string> = {
  CON_VARIACION: 'Cambió su hábito de compra respecto a lo habitual.',
  ESTABLE: 'Compra de forma parecida a lo habitual.',
  SIN_LINEA_BASE: 'Sin línea base: aún no tiene historial suficiente para comparar.',
  SIN_EVALUAR: 'Todavía no se evaluó su comportamiento de compra.',
};

/**
 * Alertas de cambio de comportamiento del cliente dentro de la ficha integral (SCRUM-567, SCRUM-34):
 * cada detección con su fecha, el tipo de variación (frecuencia o monto), la dirección y los valores
 * comparados. Es un componente de presentación: recibe el bloque ya consultado por FichaIntegralComponent.
 */
@Component({
  selector: 'app-cambios-comportamiento',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  template: `
    <section class="tarjeta cambios" aria-label="Cambios de comportamiento">
      <h2>Cambios de comportamiento</h2>
      @if (cambios(); as datos) {
        @if (datos.sinDatos) {
          <small class="sin-datos">No se pudo consultar el comportamiento de compra de este cliente.</small>
        } @else {
          <small class="estado">
            {{ descripcionEstado(datos.estado) }}
            @if (datos.evaluadoEn) {
              Última evaluación: {{ datos.evaluadoEn | date: 'dd/MM/yyyy HH:mm' }}.
            }
          </small>
          @for (alerta of datos.alertas; track $index) {
            <article class="alerta" [class.aumento]="alerta.direccion === 'AUMENTO'">
              <header>
                <span class="fecha">{{ alerta.detectadoEn | date: 'dd/MM/yyyy' }}</span>
                <span class="etiqueta">{{ nombreTipo(alerta.tipo) }}</span>
                <strong class="direccion">
                  {{ alerta.direccion === 'AUMENTO' ? '▲ Aumento' : '▼ Disminución' }}
                  {{ porcentaje(alerta.variacion) | number: '1.0-0' }} %
                </strong>
              </header>
              <p class="valores">
                Actual: <strong>{{ alerta.valorActual | number: formato(alerta.tipo) }}</strong>
                · Habitual: <strong>{{ alerta.valorHabitual | number: formato(alerta.tipo) }}</strong>
                @if (alerta.periodoDesde && alerta.periodoHasta) {
                  <span class="periodo">
                    (del {{ alerta.periodoDesde | date: 'dd/MM/yyyy' }} al {{ alerta.periodoHasta | date: 'dd/MM/yyyy' }})
                  </span>
                }
              </p>
              @if (alerta.mensaje) {
                <small>{{ alerta.mensaje }}</small>
              }
            </article>
          } @empty {
            <p><small class="sin-datos">Sin alertas registradas.</small></p>
          }
        }
      }
    </section>
  `,
  styles: `
    .cambios { padding: 1rem 1.25rem; }
    .cambios h2 { margin: 0 0 0.35rem; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--color-texto-suave); }
    .estado, .sin-datos, .periodo, .alerta small { color: var(--color-texto-suave); }
    .alerta { margin-top: 0.75rem; padding: 0.6rem 0.75rem; border-left: 3px solid var(--color-error); background: var(--color-error-claro); border-radius: 4px; }
    .alerta.aumento { border-left-color: var(--color-exito); background: var(--color-exito-claro); }
    .alerta header { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; }
    .alerta .direccion { color: var(--color-error); }
    .alerta.aumento .direccion { color: var(--color-exito); }
    .valores { margin: 0.35rem 0 0.15rem; }
  `,
})
export class CambiosComportamientoComponent {
  readonly cambios = input<CambiosComportamientoFicha | null>(null);

  descripcionEstado(estado: string | null): string {
    return estado ? (DESCRIPCION_DE_ESTADO[estado] ?? estado) : '';
  }

  nombreTipo(tipo: string): string {
    return NOMBRES_DE_TIPO[tipo] ?? tipo;
  }

  porcentaje(variacion: number): number {
    return Math.abs(variacion) * 100;
  }

  /** La frecuencia se muestra como cantidad de compras; el monto, con dos decimales. */
  formato(tipo: string): string {
    return tipo === 'MONTO' ? '1.2-2' : '1.0-2';
  }
}
