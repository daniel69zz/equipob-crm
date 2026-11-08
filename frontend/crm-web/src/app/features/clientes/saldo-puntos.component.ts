import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { Subject, catchError, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { NOMBRES_NIVEL } from '../fidelizacion/niveles.service';
import { PestaniasClienteComponent } from './pestanias-cliente.component';
import { MovimientoPuntos, SaldoPuntosCliente, SaldoPuntosService, TipoMovimientoPuntos } from './saldo-puntos.service';

type EstadoConsulta =
  | { tipo: 'cargando' }
  | { tipo: 'resultado'; saldo: SaldoPuntosCliente }
  | { tipo: 'error'; mensaje: string; reintentable: boolean };

const TIPOS: Record<TipoMovimientoPuntos, string> = {
  ACUMULACION: 'Acumulación',
  REDENCION: 'Redención',
  REVERSA: 'Reversa',
};

/** Últimos movimientos que se muestran; el resto queda en el historial completo de la API. */
const MOVIMIENTOS = 10;

/**
 * Saldo, nivel y últimos movimientos de puntos de un cliente, para que el Agente de Atención responda sus
 * consultas sobre el programa de fidelización (SCRUM-47, SCRUM-399, docs/fidelizacion/consulta-saldo-puntos.md).
 * Un cliente sin puntos se muestra con un aviso, no como un error.
 */
@Component({
  selector: 'app-saldo-puntos',
  standalone: true,
  imports: [DatePipe, DecimalPipe, PestaniasClienteComponent],
  template: `
    <app-pestanias-cliente [id]="clienteId()" />
    <div class="cabecera-puntos">
      <div>
        <h1>Puntos de fidelización</h1>
        <p class="subtitulo">Cliente {{ clienteId() }} · Saldo, nivel y últimos movimientos.</p>
      </div>
      @if (estado().tipo === 'resultado') {
        <button type="button" class="secundario" (click)="consultar()">Volver a consultar</button>
      }
    </div>
    @if (estado(); as consulta) {
      @switch (consulta.tipo) {
        @case ('cargando') {
          <div class="tarjeta estado-puntos" role="status" aria-live="polite">Consultando los puntos…</div>
        }
        @case ('error') {
          <div class="error" role="alert">
            <p>{{ consulta.mensaje }}</p>
            @if (consulta.reintentable) {
              <button type="button" class="secundario" (click)="consultar()">Reintentar consulta</button>
            }
          </div>
        }
        @case ('resultado') {
          @if (!consulta.saldo.tienePuntos) {
            <section class="tarjeta estado-puntos" role="status">
              <h2>Sin puntos</h2>
              <p>{{ consulta.saldo.mensaje || 'Este cliente aún no tiene puntos' }}.</p>
            </section>
          } @else {
            <section class="resumen">
              <div class="tarjeta dato">
                <h2>Saldo</h2>
                <strong>{{ consulta.saldo.saldo | number: '1.0-0' }} pts</strong>
                @if (consulta.saldo.actualizadoEn) {
                  <small>Actualizado {{ consulta.saldo.actualizadoEn | date: 'dd/MM/yyyy HH:mm' }}</small>
                }
              </div>
              <div class="tarjeta dato">
                <h2>Nivel</h2>
                @if (consulta.saldo.nivel) {
                  <span class="nivel" [attr.data-nivel]="consulta.saldo.nivel">{{ nombreNivel(consulta.saldo.nivel) }}</span>
                } @else {
                  <strong class="sin-nivel">Sin nivel</strong>
                }
                <small>{{ consulta.saldo.puntosPeriodo | number: '1.0-0' }} pts en el periodo de evaluación</small>
              </div>
            </section>

            <section class="tarjeta movimientos" aria-label="Últimos movimientos de puntos">
              <h2>Últimos movimientos</h2>
              @if (consulta.saldo.ultimosMovimientos.length) {
                <div class="tabla">
                  <table>
                    <thead>
                      <tr><th>Fecha</th><th>Tipo</th><th>Transacción</th><th class="numero">Puntos</th><th class="numero">Saldo</th></tr>
                    </thead>
                    <tbody>
                      @for (movimiento of consulta.saldo.ultimosMovimientos; track movimiento.idTransaccion + movimiento.tipoMovimiento) {
                        <tr>
                          <td>{{ movimiento.fechaTransaccion | date: 'dd/MM/yyyy HH:mm' }}</td>
                          <td><span class="tipo" [attr.data-tipo]="movimiento.tipoMovimiento">{{ tipo(movimiento) }}</span></td>
                          <td>
                            {{ movimiento.idTransaccion }}
                            @if (movimiento.idTransaccionRevertida) {
                              <small>revierte {{ movimiento.idTransaccionRevertida }}</small>
                            }
                          </td>
                          <td class="numero" [class.resta]="movimiento.variacionSaldo < 0">
                            {{ movimiento.variacionSaldo > 0 ? '+' : '' }}{{ movimiento.variacionSaldo | number: '1.0-0' }}
                          </td>
                          <td class="numero">{{ movimiento.saldoResultante | number: '1.0-0' }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
                @if (consulta.saldo.totalMovimientos > consulta.saldo.ultimosMovimientos.length) {
                  <small>Se muestran los {{ consulta.saldo.ultimosMovimientos.length }} más recientes de
                    {{ consulta.saldo.totalMovimientos | number: '1.0-0' }} movimientos.</small>
                }
              } @else {
                <p>El cliente no tiene movimientos registrados.</p>
              }
            </section>
          }
        }
      }
    }
  `,
  styles: `
    .cabecera-puntos { display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; }
    .estado-puntos { padding: 1.5rem; }
    .estado-puntos h2 { margin-top: 0; }
    .error { padding: 1rem; }
    .resumen { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 0.75rem; margin-bottom: 0.75rem; }
    .dato { display: flex; flex-direction: column; gap: 0.2rem; padding: 1rem 1.25rem; }
    .dato h2, .movimientos h2 { margin: 0 0 0.35rem; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--color-texto-suave); }
    .dato strong { font-size: 1.6rem; }
    .dato small, .movimientos small { color: var(--color-texto-suave); }
    .sin-nivel { color: var(--color-texto-suave); }
    .nivel { align-self: flex-start; padding: 0.2rem 0.75rem; border-radius: 999px; font-weight: 700; color: #fff; background: var(--color-primario); }
    .nivel[data-nivel='BRONCE'] { background: #b0703a; }
    .nivel[data-nivel='PLATA'] { background: #8a94a6; }
    .nivel[data-nivel='ORO'] { background: #c9a227; }
    .movimientos { padding: 1rem 1.25rem; }
    .tabla { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
    th, td { padding: 0.45rem 0.5rem; text-align: left; border-bottom: 1px solid var(--color-borde); vertical-align: top; }
    td small { display: block; }
    .numero { text-align: right; white-space: nowrap; }
    .resta { color: #b42318; }
    .tipo { font-size: 0.8rem; font-weight: 600; }
    .tipo[data-tipo='REDENCION'], .tipo[data-tipo='REVERSA'] { color: #b42318; }
  `,
})
export class SaldoPuntosComponent {
  private readonly servicio = inject(SaldoPuntosService);
  private readonly recargas = new Subject<void>();
  readonly clienteId = signal('');
  readonly estado = signal<EstadoConsulta>({ tipo: 'cargando' });

  constructor() {
    inject(ActivatedRoute).paramMap.pipe(
      map(params => params.get('id') ?? ''),
      distinctUntilChanged(),
      switchMap(id => {
        this.clienteId.set(id);
        const numero = Number(id);
        if (!/^\d+$/.test(id) || !Number.isSafeInteger(numero) || numero <= 0) {
          return of<EstadoConsulta>({ tipo: 'error', mensaje: 'El identificador del cliente no es válido.', reintentable: false });
        }
        return this.recargas.pipe(
          startWith(undefined),
          switchMap(() => this.servicio.consultar(numero, MOVIMIENTOS).pipe(
            map(saldo => ({ tipo: 'resultado', saldo }) as EstadoConsulta),
            catchError((error: HttpErrorResponse) => of(this.describirError(error))),
            startWith<EstadoConsulta>({ tipo: 'cargando' }),
          )),
        );
      }),
      takeUntilDestroyed(),
    ).subscribe(estado => this.estado.set(estado));
  }

  consultar(): void { this.recargas.next(); }

  nombreNivel(nivel: string): string {
    return NOMBRES_NIVEL[nivel] || nivel;
  }

  tipo(movimiento: MovimientoPuntos): string {
    return TIPOS[movimiento.tipoMovimiento] ?? movimiento.tipoMovimiento;
  }

  private describirError(error: HttpErrorResponse): EstadoConsulta {
    const mensajes: Record<number, string> = {
      400: 'La consulta no es válida.',
      401: 'La sesión ha vencido. Inicie sesión nuevamente.',
      403: 'No tiene permiso para consultar los puntos de este cliente.',
    };
    return {
      tipo: 'error',
      mensaje: mensajes[error.status] ?? 'No se pudieron consultar los puntos. Intente nuevamente.',
      reintentable: error.status === 0 || error.status >= 500,
    };
  }
}
