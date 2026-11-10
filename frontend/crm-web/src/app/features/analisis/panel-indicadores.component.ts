import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { PanelIndicadores, PanelIndicadoresService } from './panel-indicadores.service';

/**
 * Panel con los principales indicadores de clientes (SCRUM-59, RF-51): clientes activos, distribución por
 * segmento y puntos acumulados, para conocer el estado de la cartera de un vistazo (criterio 1). Se calcula
 * al pedirlo: «Actualizar» vuelve a consultarlo y muestra los cambios (criterio 2). Si un indicador no está
 * disponible porque su servicio no respondió, los demás se muestran igual.
 */
@Component({
  selector: 'app-panel-indicadores',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  template: `
    <div class="encabezado">
      <div>
        <h1>Panel de indicadores</h1>
        <p class="subtitulo">Estado de la cartera de clientes de un vistazo.</p>
      </div>
      <div class="acciones">
        @if (panel(); as p) {
          <small>Actualizado: {{ p.generadoEn | date: 'dd/MM/yyyy HH:mm:ss' }}</small>
        }
        <button type="button" (click)="cargar()" [disabled]="cargando()">Actualizar</button>
      </div>
    </div>

    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }

    @if (panel(); as p) {
      @if (p.sinDatos) {
        <section class="tarjeta" aria-label="Sin datos">
          <h2>Todavía no hay clientes con compras</h2>
          <p>Los indicadores aparecerán cuando se registren compras.</p>
        </section>
      }
      <div class="indicadores">
        <section class="tarjeta" aria-label="Clientes activos">
          <h2>Clientes activos</h2>
          @if (p.activos.disponible) {
            <p class="cifra">{{ p.activos.activos }}</p>
            <p>de {{ p.clientes }} clientes con compras ({{ p.activos.porcentaje | number: '1.2-2' }} %)</p>
            <p class="nota">Con una compra en los últimos {{ p.activos.umbralDias }} días.</p>
          } @else {
            <p class="no-disponible">No disponible en este momento.</p>
          }
        </section>
        <section class="tarjeta" aria-label="Puntos acumulados">
          <h2>Puntos acumulados</h2>
          @if (p.puntos.disponible) {
            <p class="cifra">{{ p.puntos.puntos | number }}</p>
            <p>{{ p.puntos.clientesConPuntos }} clientes con puntos</p>
          } @else {
            <p class="no-disponible">No disponible en este momento.</p>
          }
        </section>
      </div>
      <section class="tarjeta" aria-label="Distribución por segmento">
        <h2>Distribución por segmento</h2>
        @if (p.segmentos.length === 0) {
          <p class="nota">Sin clientes que distribuir.</p>
        } @else {
          <ul class="barras">
            @for (g of p.segmentos; track g.valor) {
              <li>
                <span class="nombre" [class.sin-asignar]="g.valor === null">{{ g.valor ?? 'Sin segmento' }}</span>
                <span class="pista"><span class="barra" [style.width.%]="g.porcentaje"></span></span>
                <span class="valor">{{ g.clientes }} · {{ g.porcentaje | number: '1.2-2' }} %</span>
              </li>
            }
          </ul>
        }
      </section>
    } @else if (cargando()) {
      <p><small>Cargando…</small></p>
    }
  `,
  styles: `
    .encabezado { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: flex-start; gap: 1rem; }
    .acciones { display: flex; align-items: center; gap: 0.75rem; color: var(--color-texto-suave); }
    .indicadores { display: grid; grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr)); gap: 1rem; margin: 1rem 0; }
    h2 { font-size: 1rem; margin: 0 0 0.5rem; }
    p { margin: 0.25rem 0; }
    .cifra { font-size: 2rem; font-weight: 700; }
    .nota, .no-disponible { font-size: 0.85rem; color: var(--color-texto-suave); }
    .no-disponible { font-style: italic; }
    .barras { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.5rem; }
    .barras li { display: grid; grid-template-columns: minmax(8rem, 12rem) 1fr auto; align-items: center; gap: 0.75rem; }
    .sin-asignar { font-style: italic; color: var(--color-texto-suave); }
    .pista { height: 0.75rem; border-radius: 4px; background: var(--color-superficie-suave); overflow: hidden; }
    .barra { display: block; height: 100%; background: var(--color-primario); }
    .valor { white-space: nowrap; font-variant-numeric: tabular-nums; }
  `,
})
export class PanelIndicadoresComponent implements OnInit {
  private readonly servicio = inject(PanelIndicadoresService);

  readonly panel = signal<PanelIndicadores | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  /** Criterio 2: cada carga vuelve a calcular el panel en el servidor. */
  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.servicio.consultar().subscribe({
      next: (panel) => {
        this.panel.set(panel);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(e.error?.mensaje ?? 'No se pudo cargar el panel de indicadores.');
      },
    });
  }
}
