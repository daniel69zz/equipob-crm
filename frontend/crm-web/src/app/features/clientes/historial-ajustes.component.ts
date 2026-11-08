import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { AjustePuntos, AjustesPuntosService } from '../fidelizacion/ajustes-puntos.service';

/**
 * Historial de ajustes manuales del saldo de puntos, dentro de la ficha integral del cliente (SCRUM-51, criterio de
 * aceptación 4, SCRUM-427): cada ajuste con su variación, el saldo antes y después, el motivo, el responsable y la
 * fecha. Consulta su propio servicio, así que un error aquí no impide ver el resto de la ficha.
 */
@Component({
  selector: 'app-historial-ajustes',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  template: `
    <section class="tarjeta ajustes" aria-labelledby="titulo-ajustes">
      <h2 id="titulo-ajustes">Ajustes de puntos</h2>
      @if (error()) {
        <p class="error" role="status">{{ error() }}</p>
      } @else {
        @if (ajustes(); as lista) {
          @if (lista.length) {
            <div class="tabla">
              <table>
                <thead>
                  <tr><th>Fecha</th><th class="numero">Ajuste</th><th class="numero">Saldo</th><th>Motivo</th><th>Responsable</th></tr>
                </thead>
                <tbody>
                  @for (ajuste of lista; track ajuste.id) {
                    <tr>
                      <td>{{ ajuste.registradoEn | date: 'dd/MM/yyyy HH:mm' }}</td>
                      <td class="numero" [class.resta]="ajuste.puntos < 0">{{ ajuste.puntos > 0 ? '+' : '' }}{{ ajuste.puntos | number: '1.0-0' }}</td>
                      <td class="numero">{{ ajuste.saldoAnterior | number: '1.0-0' }} → {{ ajuste.saldoResultante | number: '1.0-0' }}</td>
                      <td>
                        {{ ajuste.motivo }}
                        @if (ajuste.referencia) { <small>Ref. {{ ajuste.referencia }}</small> }
                      </td>
                      <td>{{ ajuste.usuario }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            <small class="sin-datos">Este cliente no tiene ajustes manuales de puntos.</small>
          }
        } @else {
          <small>Cargando…</small>
        }
      }
    </section>
  `,
  styles: `
    .ajustes { margin-bottom: 1rem; padding: 1rem 1.25rem; }
    h2 { margin: 0 0 0.5rem; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--color-texto-suave); }
    .tabla { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
    th, td { padding: 0.45rem 0.5rem; text-align: left; border-bottom: 1px solid var(--color-borde); vertical-align: top; }
    td small { display: block; color: var(--color-texto-suave); }
    .numero { text-align: right; white-space: nowrap; }
    .resta { color: #b42318; }
    .sin-datos { color: var(--color-texto-suave); }
  `,
})
export class HistorialAjustesComponent implements OnInit {
  private readonly servicio = inject(AjustesPuntosService);

  /** Identificador del cliente en el CRM. */
  readonly clienteId = input.required<number>();
  readonly ajustes = signal<AjustePuntos[] | null>(null);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.servicio.historial(this.clienteId()).subscribe({
      next: ajustes => this.ajustes.set(ajustes),
      error: (e: HttpErrorResponse) => this.error.set(e.status === 403
        ? 'No tiene permiso para ver los ajustes de puntos.'
        : 'No se pudo cargar el historial de ajustes.'),
    });
  }
}
