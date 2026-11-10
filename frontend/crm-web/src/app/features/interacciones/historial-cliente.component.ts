import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, map } from 'rxjs';
import { PestaniasClienteComponent } from '../clientes/pestanias-cliente.component';
import {
  EventoHistorial,
  HistorialCliente,
  InteraccionesService,
  nombreCanal,
  nombreEstado,
  nombreTipo,
} from './interacciones.service';

type Vista = 'TODO' | 'INTERACCION' | 'COMPRA';

const LIMITE = 50;

const NOMBRES_ESTADO_COMPRA: Record<string, string> = {
  CONFIRMADA: 'Confirmada',
  DEVOLUCION_PARCIAL: 'Devolución parcial',
  ANULADA: 'Anulada',
};

/**
 * Historial del cliente para atenderlo (SCRUM-53, SCRUM-439): sus interacciones y sus compras en una sola línea de
 * tiempo, de la más reciente a la más antigua (docs/interacciones/historial-cliente.md). Lo ven el Administrador
 * de CRM y el Agente de Atención.
 */
@Component({
  selector: 'app-historial-cliente',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, PestaniasClienteComponent],
  template: `
    <app-pestanias-cliente [id]="clienteId()" />
    <header>
      <h1>Historial de atención</h1>
      <p class="subtitulo">Cliente {{ clienteId() }} · Interacciones y compras, de la más reciente a la más antigua.</p>
    </header>

    @if (error()) {
      <div class="error" role="alert">
        <p>{{ error() }}</p>
        <button type="button" class="secundario" (click)="cargar()">Reintentar</button>
      </div>
    }

    @if (historial(); as datos) {
      <section class="tarjeta resumen" aria-label="Resumen del cliente">
        <div><strong>{{ datos.totalInteracciones }}</strong><small>{{ datos.totalInteracciones === 1 ? 'interacción' : 'interacciones' }}</small></div>
        <div><strong>{{ datos.comprasDisponibles ? datos.totalCompras : '—' }}</strong><small>{{ datos.totalCompras === 1 ? 'compra' : 'compras' }}</small></div>
        <div><strong>{{ abiertas() }}</strong><small>{{ abiertas() === 1 ? 'caso sin cerrar' : 'casos sin cerrar' }} entre los mostrados</small></div>
        <div class="vista" role="group" aria-label="Mostrar">
          <button type="button" class="secundario" [attr.aria-pressed]="vista() === 'TODO'" (click)="vista.set('TODO')">Todo</button>
          <button type="button" class="secundario" [attr.aria-pressed]="vista() === 'INTERACCION'" (click)="vista.set('INTERACCION')">Interacciones</button>
          <button type="button" class="secundario" [attr.aria-pressed]="vista() === 'COMPRA'" (click)="vista.set('COMPRA')">Compras</button>
        </div>
      </section>

      @for (aviso of datos.avisos; track aviso) {
        <p class="aviso" role="status">{{ aviso }}.</p>
      }

      <ol class="linea" aria-label="Línea de tiempo del cliente">
        @for (evento of visibles(); track $index) {
          <li [class]="'evento ' + evento.tipo">
            <time [attr.datetime]="evento.fecha">{{ evento.fecha | date: 'dd/MM/yyyy HH:mm' }}</time>
            @if (evento.interaccion; as i) {
              <article class="tarjeta">
                <header>
                  <span class="marca">Interacción</span>
                  <span [class]="'etiqueta tipo-' + i.tipo">{{ nombreTipo(i.tipo) }}</span>
                  <span [class]="'etiqueta estado-' + i.estado">{{ nombreEstado(i.estado) }}</span>
                  @if (i.canal) { <span class="suave">{{ nombreCanal(i.canal) }}</span> }
                </header>
                @if (i.asunto) { <h2>{{ i.asunto }}</h2> }
                <p class="descripcion">{{ i.descripcion }}</p>
                <footer>
                  <span>Registrada por {{ i.registradoPor }}</span>
                  @if (i.compra) {
                    <span>Vinculada a la compra {{ i.compra.referencia }} por {{ i.compra.vinculadaPor }}</span>
                  } @else if (i.referenciaCompra) { <span>Compra {{ i.referenciaCompra }}</span> }
                </footer>
              </article>
            }
            @if (evento.compra; as c) {
              <article class="tarjeta">
                <header>
                  <span class="marca">Compra</span>
                  <span class="referencia">{{ c.referencia }}</span>
                  <span [class]="'etiqueta compra-' + c.estado">{{ nombreEstadoCompra(c.estado) }}</span>
                  <strong class="monto">Bs {{ c.montoTotal | number: '1.2-2' }}</strong>
                </header>
                @if (c.items.length) {
                  <p class="suave">
                    @for (item of c.items; track $index) {
                      {{ item.cantidad }} × {{ item.categoria }}@if (!$last) {, }
                    }
                  </p>
                }
              </article>
            }
          </li>
        } @empty {
          <li class="tarjeta vacio">
            @switch (vista()) {
              @case ('INTERACCION') { El cliente no tiene interacciones registradas. }
              @case ('COMPRA') { {{ datos.comprasDisponibles ? 'El cliente no tiene compras registradas.' : 'Las compras no están disponibles en este momento.' }} }
              @default { El cliente no tiene interacciones ni compras registradas. }
            }
          </li>
        }
      </ol>

      @if (datos.hayMas) {
        <p class="suave mas">
          Se muestran los {{ datos.eventos.length }} eventos más recientes. Vea todo en
          <a [routerLink]="['/clientes', clienteId(), 'interacciones']">Interacciones</a> y en
          <a [routerLink]="['/clientes', clienteId(), 'compras']">Compras e indicadores</a>.
        </p>
      }
    } @else if (!error()) {
      <p role="status">Cargando el historial del cliente…</p>
    }
  `,
  styles: `
    .resumen { display: flex; gap: 1.5rem; flex-wrap: wrap; align-items: center; padding: 1rem 1.25rem; margin-bottom: 1rem; }
    .resumen > div:not(.vista) { display: flex; flex-direction: column; }
    .resumen strong { font-size: 1.4rem; }
    .resumen small, .suave { color: var(--color-texto-suave); font-size: .85rem; }
    .vista { margin-left: auto; display: inline-flex; gap: .35rem; flex-wrap: wrap; }
    .vista button { padding: .3rem .75rem; font-size: .85rem; }
    .vista button[aria-pressed='true'] { background: var(--color-primario); color: #fff; border-color: var(--color-primario); }
    .aviso { color: var(--color-texto-suave); background: var(--color-superficie-suave); border: 1px solid var(--color-borde);
      border-radius: var(--radio-chico); padding: .6rem .9rem; font-size: .9rem; }
    .linea { list-style: none; margin: 0; padding: 0 0 0 1.1rem; border-left: 2px solid var(--color-borde); display: flex; flex-direction: column; gap: .9rem; }
    .evento { position: relative; }
    .evento::before { content: ''; position: absolute; left: calc(-1.1rem - 6px); top: .35rem; width: 10px; height: 10px;
      border-radius: 50%; background: var(--color-primario); }
    .evento.COMPRA::before { background: #067647; }
    .evento time { display: block; color: var(--color-texto-suave); font-size: .82rem; margin-bottom: .3rem; }
    article { padding: .85rem 1.1rem; }
    article header { display: flex; gap: .55rem; align-items: center; flex-wrap: wrap; }
    .marca { font-size: .72rem; font-weight: 700; text-transform: uppercase; letter-spacing: .05em; color: var(--color-texto-suave); }
    .referencia { font-weight: 600; }
    .monto { margin-left: auto; }
    article h2 { margin: .5rem 0 0; font-size: 1rem; }
    .descripcion { margin: .35rem 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
    article footer { display: flex; gap: 1rem; flex-wrap: wrap; margin-top: .5rem; color: var(--color-texto-suave); font-size: .82rem; }
    article p.suave { margin: .4rem 0 0; }
    .tipo-RECLAMO { color: #b42318; background: #fdecea; }
    .tipo-CONSULTA { color: #1d4ed8; background: #e8effd; }
    .estado-ABIERTA { color: #b54708; background: #fef0c7; }
    .estado-EN_PROCESO { color: #1d4ed8; background: #e8effd; }
    .estado-CERRADA, .compra-CONFIRMADA { color: #067647; background: #dcfae6; }
    .compra-ANULADA { color: #b42318; background: #fdecea; }
    .compra-DEVOLUCION_PARCIAL { color: #b54708; background: #fef0c7; }
    .vacio { color: var(--color-texto-suave); }
    .mas { margin-top: 1rem; }
  `,
})
export class HistorialClienteComponent {
  private readonly servicio = inject(InteraccionesService);

  readonly clienteId = signal('');
  readonly historial = signal<HistorialCliente | null>(null);
  readonly cargando = signal(false);
  readonly error = signal('');
  readonly vista = signal<Vista>('TODO');
  readonly nombreTipo = nombreTipo;
  readonly nombreEstado = nombreEstado;
  readonly nombreCanal = nombreCanal;

  readonly visibles = computed<EventoHistorial[]>(() => {
    const eventos = this.historial()?.eventos ?? [];
    const vista = this.vista();
    return vista === 'TODO' ? eventos : eventos.filter(e => e.tipo === vista);
  });
  readonly abiertas = computed(() => (this.historial()?.eventos ?? [])
    .filter(e => e.interaccion && e.interaccion.estado !== 'CERRADA').length);

  constructor() {
    inject(ActivatedRoute).paramMap.pipe(map(p => p.get('id') ?? ''), takeUntilDestroyed()).subscribe(id => {
      this.clienteId.set(id);
      this.historial.set(null);
      this.vista.set('TODO');
      this.cargar();
    });
  }

  cargar(): void {
    const id = this.clienteId();
    const numero = Number(id);
    this.error.set('');
    if (!/^\d+$/.test(id) || !Number.isSafeInteger(numero) || numero <= 0) {
      this.error.set('El identificador del cliente no es válido.');
      return;
    }
    this.cargando.set(true);
    this.servicio.historialCliente(numero, LIMITE).pipe(finalize(() => this.cargando.set(false))).subscribe({
      next: datos => this.historial.set(datos),
      error: (e: HttpErrorResponse) => this.error.set(this.mensaje(e)),
    });
  }

  nombreEstadoCompra(estado: string): string {
    return NOMBRES_ESTADO_COMPRA[estado] ?? estado;
  }

  private mensaje(e: HttpErrorResponse): string {
    if (e.status === 401) return 'La sesión ha vencido. Inicie sesión nuevamente.';
    if (e.status === 403) return 'No tiene permiso para consultar el historial de atención de este cliente.';
    if (e.status === 404) return e.error?.mensaje ?? 'El cliente no existe en el CRM.';
    if (e.status === 0) return 'No se pudo conectar con el servidor. Compruebe la conexión e intente de nuevo.';
    return e.error?.mensaje ?? 'No se pudo cargar el historial del cliente.';
  }
}
