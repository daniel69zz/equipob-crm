import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { Subject, catchError, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { PestaniasClienteComponent } from './pestanias-cliente.component';
import { MotivoSinSugerencias, SugerenciasCliente, SugerenciasService } from './sugerencias.service';

type EstadoConsulta =
  | { tipo: 'cargando' }
  | { tipo: 'resultado'; resultado: SugerenciasCliente }
  | { tipo: 'error'; mensaje: string; reintentable: boolean };

const MOTIVOS: Record<MotivoSinSugerencias, { titulo: string; detalle: string }> = {
  SIN_PERFIL: {
    titulo: 'Sin perfil de preferencias',
    detalle: 'Todavía no se construyó el perfil de preferencias de este cliente, así que no hay con qué compararlo.',
  },
  SIN_PREFERENCIAS: {
    titulo: 'Sin preferencias registradas',
    detalle: 'El perfil del cliente no tiene categorías: no tiene compras vigentes.',
  },
  SIN_SEGMENTO: {
    titulo: 'Sin segmento asignado',
    detalle: 'El cliente aún no pertenece a ningún segmento, por eso no hay clientes parecidos con quienes compararlo.',
  },
  SIN_DATOS_SEGMENTO: {
    titulo: 'Segmento sin datos suficientes',
    detalle: 'Ningún otro cliente de su segmento tiene preferencias registradas todavía.',
  },
  SIN_CATEGORIAS_NUEVAS: {
    titulo: 'Sin categorías nuevas',
    detalle: 'Su segmento solo compra categorías que este cliente ya compra o que muy pocos clientes compran.',
  },
};

/**
 * Categorías afines sugeridas a un cliente (SCRUM-44, SCRUM-385): las que compran los clientes de su
 * segmento y él todavía no, ordenadas por afinidad. Si no hay sugerencias, explica el motivo
 * (docs/preferencias/sugerencia-categorias.md).
 */
@Component({
  selector: 'app-sugerencias',
  standalone: true,
  imports: [DatePipe, DecimalPipe, PercentPipe, PestaniasClienteComponent],
  template: `
    <app-pestanias-cliente [id]="clienteId()" />
    <div class="cabecera-sugerencias">
      <div>
        <h1>Categorías sugeridas</h1>
        <p class="subtitulo">Cliente {{ clienteId() }} · Lo que compran los clientes de su segmento y él todavía no.</p>
      </div>
      @if (estado().tipo === 'resultado') {
        <button type="button" class="secundario" (click)="consultar()">Volver a consultar</button>
      }
    </div>
    @if (estado(); as consulta) {
      @switch (consulta.tipo) {
        @case ('cargando') {
          <div class="tarjeta estado-sugerencias" role="status" aria-live="polite">Generando sugerencias…</div>
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
          @if (consulta.resultado.segmento) {
            <p class="segmento">
              Segmento <strong>{{ consulta.resultado.segmento }}</strong>
              @if (consulta.resultado.clientesDelSegmento) {
                · {{ consulta.resultado.clientesDelSegmento | number:'1.0-0' }}
                {{ consulta.resultado.clientesDelSegmento === 1 ? 'cliente analizado' : 'clientes analizados' }}
              }
            </p>
          }
          @if (consulta.resultado.sinSugerencias || !consulta.resultado.sugerencias.length) {
            <section class="tarjeta estado-sugerencias" role="status">
              <h2>{{ titulo(consulta.resultado) }}</h2>
              <p>{{ detalle(consulta.resultado) }}</p>
            </section>
          } @else {
            <ol class="sugerencias">
              @for (sugerencia of consulta.resultado.sugerencias; track sugerencia.categoria) {
                <li class="tarjeta sugerencia">
                  <div class="nombre">
                    <span class="posicion">{{ sugerencia.posicion }}</span>
                    <h2>{{ sugerencia.categoria }}</h2>
                  </div>
                  <div class="afinidad" [attr.aria-label]="'Afinidad ' + (sugerencia.afinidad | percent:'1.0-0')">
                    <div class="barra"><span [style.width.%]="sugerencia.afinidad * 100"></span></div>
                    <strong>{{ sugerencia.afinidad | percent:'1.0-0' }}</strong>
                  </div>
                  <small>
                    La compran {{ sugerencia.clientesQueCompran | number:'1.0-0' }} de
                    {{ consulta.resultado.clientesDelSegmento | number:'1.0-0' }} clientes del segmento
                    · Bs {{ sugerencia.montoSegmento | number:'1.2-2' }} en total
                  </small>
                </li>
              }
            </ol>
          }
          <p class="fecha-sugerencias">Generadas el {{ consulta.resultado.generadoEn | date:'dd/MM/yyyy HH:mm' }}</p>
        }
      }
    }
  `,
  styles: `
    .cabecera-sugerencias { display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; }
    .segmento, .fecha-sugerencias { color: var(--color-texto-suave); }
    .estado-sugerencias { padding: 1.5rem; }
    .error { padding: 1rem; }
    .sugerencias { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.75rem; }
    .sugerencia { display: grid; grid-template-columns: minmax(0, 1fr) minmax(140px, 220px); gap: 0.35rem 1rem; padding: 1rem 1.25rem; }
    .sugerencia small { grid-column: 1 / -1; color: var(--color-texto-suave); }
    .nombre { display: flex; align-items: center; gap: 0.75rem; min-width: 0; }
    .nombre h2 { margin: 0; font-size: 1.1rem; overflow-wrap: anywhere; }
    .posicion {
      flex: none; display: inline-grid; place-items: center; width: 1.8rem; height: 1.8rem; border-radius: 50%;
      background: var(--color-primario); color: #fff; font-weight: 700; font-size: 0.85rem;
    }
    .afinidad { display: flex; align-items: center; gap: 0.6rem; }
    .barra { flex: 1; height: 0.5rem; border-radius: 999px; background: #e9edf6; overflow: hidden; }
    .barra span { display: block; height: 100%; background: var(--color-primario); }
    @media (max-width: 560px) { .sugerencia { grid-template-columns: 1fr; } }
  `,
})
export class SugerenciasComponent {
  private readonly servicio = inject(SugerenciasService);
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
          switchMap(() => this.servicio.consultar(numero).pipe(
            map(resultado => ({ tipo: 'resultado', resultado }) as EstadoConsulta),
            catchError((error: HttpErrorResponse) => of(this.describirError(error))),
            startWith<EstadoConsulta>({ tipo: 'cargando' }),
          )),
        );
      }),
      takeUntilDestroyed(),
    ).subscribe(estado => this.estado.set(estado));
  }

  consultar(): void { this.recargas.next(); }

  titulo(resultado: SugerenciasCliente): string {
    return resultado.motivo ? MOTIVOS[resultado.motivo].titulo : 'Sin sugerencias';
  }

  detalle(resultado: SugerenciasCliente): string {
    return resultado.motivo ? MOTIVOS[resultado.motivo].detalle : resultado.mensaje ?? '';
  }

  private describirError(error: HttpErrorResponse): EstadoConsulta {
    const mensajes: Record<number, string> = {
      400: 'El identificador del cliente no es válido.',
      401: 'La sesión ha vencido. Inicie sesión nuevamente.',
      403: 'No tiene permiso para consultar las sugerencias de este cliente.',
      404: 'No se encontró el cliente solicitado.',
      409: 'El cliente fue consolidado. Consulte su perfil vigente.',
    };
    const detalle = typeof error.error?.mensaje === 'string' ? error.error.mensaje : null;
    return {
      tipo: 'error',
      mensaje: mensajes[error.status] ? (error.status === 409 ? detalle ?? mensajes[409] : mensajes[error.status])
        : 'No se pudieron generar las sugerencias. Intente nuevamente.',
      reintentable: error.status === 0 || error.status >= 500,
    };
  }
}
