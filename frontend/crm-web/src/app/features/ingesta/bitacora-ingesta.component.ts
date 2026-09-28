import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  Bitacora,
  EstadoEvento,
  FiltroBitacora,
  IngestaService,
  IntentoReproceso,
  ResultadoReproceso,
} from './ingesta.service';

const TAMANIO_PAGINA = 50;
const DURACION_ALERTA_MS = 8_000;

/** Estados en el orden en que se muestran, con su nombre visible. */
const ESTADOS: { codigo: EstadoEvento; nombre: string }[] = [
  { codigo: 'RECIBIDO', nombre: 'Recibidos' },
  { codigo: 'PROCESADO', nombre: 'Procesados' },
  { codigo: 'FALLIDO', nombre: 'Fallidos' },
  { codigo: 'DESCARTADO', nombre: 'Duplicados' },
];

@Component({
  selector: 'app-bitacora-ingesta',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <h1>Bitácora de ingesta</h1>
    <p class="subtitulo">Eventos de compra recibidos de Marketplace y Ventas, y el resultado de su procesamiento.</p>

    <form class="tarjeta filtros" (ngSubmit)="buscar(0)">
      <div>
        <label for="desde">Desde</label>
        <input id="desde" name="desde" type="date" [(ngModel)]="filtro.desde" />
      </div>
      <div>
        <label for="hasta">Hasta</label>
        <input id="hasta" name="hasta" type="date" [(ngModel)]="filtro.hasta" />
      </div>
      <div>
        <label for="estado">Estado</label>
        <select id="estado" name="estado" [(ngModel)]="filtro.estado">
          <option value="">Todos</option>
          @for (estado of estados; track estado.codigo) {
            <option [value]="estado.codigo">{{ estado.nombre }}</option>
          }
        </select>
      </div>
      <div>
        <label for="origen">Origen</label>
        <select id="origen" name="origen" [(ngModel)]="filtro.origen">
          <option value="">Todos</option>
          <option value="MARKETPLACE">Marketplace</option>
          <option value="VENTAS">Ventas</option>
        </select>
      </div>
      <div>
        <label for="transaccion">Transacción</label>
        <input id="transaccion" name="transaccion" [(ngModel)]="filtro.transaccion" placeholder="V-100234" />
      </div>
      <div class="acciones">
        <button type="submit" [disabled]="cargando()">Buscar</button>
        <button type="button" class="secundario" (click)="limpiar()">Limpiar</button>
      </div>
    </form>

    @if (error()) {
      <p class="error" role="status">{{ error() }}</p>
    }

    @if (alertaEventosInvalidos(); as alerta) {
      <div class="alerta-invalidos" role="alert">
        <span>{{ alerta }}</span>
        <button type="button" class="cerrar-alerta" aria-label="Cerrar alerta" (click)="cerrarAlerta()">×</button>
      </div>
    }

    @if (mensajeOperacion(); as mensaje) {
      <div class="mensaje-operacion" [class.error-operacion]="mensaje.tipo === 'error'" role="status">
        <span>{{ mensaje.texto }}</span>
        <button type="button" class="cerrar-mensaje" aria-label="Cerrar mensaje" (click)="mensajeOperacion.set(null)">×</button>
      </div>
    }

    @if (bitacora(); as datos) {
      <p class="periodo">Periodo: {{ datos.desde | date: 'dd/MM/yyyy' }} al {{ datos.hasta | date: 'dd/MM/yyyy' }}</p>
      <section class="resumen">
        @for (estado of estados; track estado.codigo) {
          <article class="tarjeta" [class]="'estado-' + estado.codigo">
            <span class="cantidad">{{ datos.resumen[estado.codigo] }}</span>
            <span>{{ estado.nombre }}</span>
          </article>
        }
      </section>

      <div class="tarjeta">
        <table>
          <thead>
            <tr>
              <th>Recibido</th>
              <th>Origen</th>
              <th>Transacción</th>
              <th>Evento</th>
              <th>Estado</th>
              <th>Causa</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (evento of datos.eventos; track evento.id) {
              <tr>
                <td class="fecha">{{ evento.recibidoEn | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                <td>{{ evento.origen ?? '—' }}</td>
                <td class="id">{{ evento.idTransaccion ?? '—' }}</td>
                <td class="id">{{ evento.idEventoOrigen ?? 'sin identificador' }}</td>
                <td><span class="etiqueta" [class]="'estado-' + evento.estado">{{ nombreEstado(evento.estado) }}</span></td>
                <td class="causa">{{ evento.causa }}</td>
                <td class="acciones-evento">
                  @if (evento.estado === 'FALLIDO') {
                    <button type="button" [disabled]="procesandoId() !== null" (click)="reprocesar(evento.id)">
                      {{ procesandoId() === evento.id ? 'Reprocesando…' : 'Reprocesar' }}
                    </button>
                  }
                  <button type="button" class="secundario" [disabled]="cargandoHistorialId() === evento.id"
                          [attr.aria-expanded]="historialVisibleId() === evento.id"
                          (click)="mostrarOcultarHistorial(evento.id)">
                    {{ historialVisibleId() === evento.id ? 'Ocultar historial' : 'Ver historial' }}
                  </button>
                </td>
              </tr>
              @if (historialVisibleId() === evento.id) {
                <tr class="fila-historial">
                  <td colspan="7">
                    @if (cargandoHistorialId() === evento.id) {
                      <p>Consultando intentos…</p>
                    } @else {
                      @if ((historialPorEvento()[evento.id] ?? []).length === 0) {
                        <p>Este evento todavía no tiene intentos de reproceso.</p>
                      } @else {
                        <table class="tabla-historial">
                          <thead>
                            <tr>
                              <th>Intento</th>
                              <th>Fecha</th>
                              <th>Resultado</th>
                              <th>Causa</th>
                              <th>Usuario</th>
                            </tr>
                          </thead>
                          <tbody>
                            @for (intento of historialPorEvento()[evento.id] ?? []; track intento.id) {
                              <tr>
                                <td>{{ intento.numero }}</td>
                                <td class="fecha">{{ intento.intentadoEn | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                                <td><span class="etiqueta" [class]="'estado-' + intento.resultado">{{ nombreResultado(intento.resultado) }}</span></td>
                                <td class="causa">{{ intento.causa ?? '—' }}</td>
                                <td>{{ intento.usuario ?? 'No informado' }}</td>
                              </tr>
                            }
                          </tbody>
                        </table>
                      }
                    }
                  </td>
                </tr>
              }
            } @empty {
              <tr><td colspan="7">No hay eventos para estos filtros.</td></tr>
            }
          </tbody>
        </table>

        @if (datos.total > 0) {
          <div class="paginacion">
            <span>{{ desdeRegistro() }}–{{ hastaRegistro() }} de {{ datos.total }}</span>
            <button type="button" class="secundario" [disabled]="datos.pagina === 0 || cargando()" (click)="buscar(datos.pagina - 1)">
              Anterior
            </button>
            <button type="button" class="secundario" [disabled]="esUltimaPagina() || cargando()" (click)="buscar(datos.pagina + 1)">
              Siguiente
            </button>
          </div>
        }
      </div>
    }
  `,
  styles: `
    .subtitulo { color: var(--color-texto-suave); margin-top: 0; }
    .filtros {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
      gap: 1rem; align-items: end; margin-bottom: 1rem;
    }
    .acciones { display: flex; gap: 0.5rem; }
    .alerta-invalidos {
      display: flex; align-items: center; justify-content: space-between; gap: 1rem;
      margin-bottom: 1rem; padding: 0.8rem 1rem;
      color: var(--color-error); background: #fdecea;
      border: 1px solid var(--color-error); border-radius: var(--radio);
    }
    .cerrar-alerta { padding: 0.1rem 0.4rem; color: var(--color-error); background: transparent; font-size: 1.25rem; }
    .mensaje-operacion {
      display: flex; align-items: center; justify-content: space-between; gap: 1rem;
      margin-bottom: 1rem; padding: 0.8rem 1rem;
      color: #1a7f37; background: #e6f4ea; border: 1px solid #1a7f37; border-radius: var(--radio);
    }
    .mensaje-operacion.error-operacion { color: var(--color-error); background: #fdecea; border-color: var(--color-error); }
    .cerrar-mensaje { padding: 0.1rem 0.4rem; color: inherit; background: transparent; font-size: 1.25rem; }
    .periodo { color: var(--color-texto-suave); }
    .resumen { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 1rem; margin-bottom: 1rem; }
    .resumen .tarjeta { display: flex; flex-direction: column; gap: 0.2rem; padding: 1rem; border-left-width: 4px; }
    .cantidad { font-size: 1.6rem; font-weight: 700; }
    .fecha { white-space: nowrap; }
    .id { font-family: monospace; font-size: 0.85rem; }
    .causa { color: var(--color-texto-suave); font-size: 0.9rem; }
    .acciones-evento { display: flex; flex-direction: column; align-items: stretch; gap: 0.4rem; min-width: 145px; }
    .fila-historial > td { padding: 1rem; background: #f8fafc; }
    .fila-historial p { margin: 0; color: var(--color-texto-suave); }
    .tabla-historial { background: #fff; }
    .tabla-historial th, .tabla-historial td { font-size: 0.85rem; }
    .etiqueta { padding: 0.15rem 0.5rem; border-radius: 999px; font-size: 0.8rem; font-weight: 600; background: #eef2f7; }
    .estado-PROCESADO { border-left-color: #1a7f37; }
    .estado-FALLIDO { border-left-color: var(--color-error); }
    .estado-DESCARTADO { border-left-color: #9a6700; }
    .etiqueta.estado-PROCESADO { color: #1a7f37; background: #e6f4ea; }
    .etiqueta.estado-FALLIDO { color: var(--color-error); background: #fdecea; }
    .etiqueta.estado-DESCARTADO { color: #9a6700; background: #fff4e0; }
    .etiqueta.estado-EN_PROCESO { color: var(--color-primario); background: var(--color-primario-claro); }
    .paginacion { display: flex; align-items: center; justify-content: flex-end; gap: 0.75rem; margin-top: 1rem; }
  `,
})
export class BitacoraIngestaComponent implements OnInit, OnDestroy {
  private readonly ingesta = inject(IngestaService);
  private temporizadorAlerta?: ReturnType<typeof setTimeout>;

  readonly estados = ESTADOS;
  filtro: FiltroBitacora = BitacoraIngestaComponent.filtroVacio();

  readonly bitacora = signal<Bitacora | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly alertaEventosInvalidos = signal<string | null>(null);
  readonly mensajeOperacion = signal<{ tipo: 'exito' | 'error'; texto: string } | null>(null);
  readonly procesandoId = signal<number | null>(null);
  readonly historialVisibleId = signal<number | null>(null);
  readonly cargandoHistorialId = signal<number | null>(null);
  readonly historialPorEvento = signal<Partial<Record<number, IntentoReproceso[]>>>({});

  readonly desdeRegistro = computed(() => (this.bitacora()?.pagina ?? 0) * TAMANIO_PAGINA + 1);
  readonly hastaRegistro = computed(
    () => (this.bitacora()?.pagina ?? 0) * TAMANIO_PAGINA + (this.bitacora()?.eventos.length ?? 0),
  );
  readonly esUltimaPagina = computed(() => this.hastaRegistro() >= (this.bitacora()?.total ?? 0));

  ngOnInit(): void {
    this.buscar(0);
  }

  ngOnDestroy(): void {
    this.cancelarTemporizadorAlerta();
  }

  buscar(pagina: number): void {
    this.cargando.set(true);
    this.error.set(null);
    this.cerrarAlerta();
    this.ingesta.buscar(this.filtro, pagina, TAMANIO_PAGINA).subscribe({
      next: (resultado) => {
        this.bitacora.set(resultado);
        this.mostrarAlertaDeEventosInvalidos(resultado);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(e.error?.mensaje ?? 'No se pudo consultar la bitácora de ingesta.');
      },
    });
  }

  limpiar(): void {
    this.filtro = BitacoraIngestaComponent.filtroVacio();
    this.buscar(0);
  }

  nombreEstado(estado: EstadoEvento): string {
    return ESTADOS.find((e) => e.codigo === estado)?.nombre.replace(/s$/, '') ?? estado;
  }

  nombreResultado(resultado: ResultadoReproceso): string {
    return {
      EN_PROCESO: 'En proceso',
      PROCESADO: 'Procesado',
      FALLIDO: 'Fallido',
      DESCARTADO: 'Duplicado',
    }[resultado];
  }

  reprocesar(idEvento: number): void {
    if (!window.confirm(`¿Desea reprocesar el evento ${idEvento}?`)) {
      return;
    }
    this.procesandoId.set(idEvento);
    this.mensajeOperacion.set(null);
    this.ingesta.reprocesar(idEvento).subscribe({
      next: (intento) => {
        this.procesandoId.set(null);
        this.mensajeOperacion.set(this.mensajePara(intento));
        this.cargarHistorial(idEvento);
        this.buscar(this.bitacora()?.pagina ?? 0);
      },
      error: (e: HttpErrorResponse) => {
        this.procesandoId.set(null);
        this.mensajeOperacion.set({
          tipo: 'error',
          texto: e.error?.mensaje ?? 'No se pudo reprocesar el evento.',
        });
      },
    });
  }

  mostrarOcultarHistorial(idEvento: number): void {
    if (this.historialVisibleId() === idEvento) {
      this.historialVisibleId.set(null);
      return;
    }
    this.historialVisibleId.set(idEvento);
    this.cargarHistorial(idEvento);
  }

  cerrarAlerta(): void {
    this.cancelarTemporizadorAlerta();
    this.alertaEventosInvalidos.set(null);
  }

  private mostrarAlertaDeEventosInvalidos(resultado: Bitacora): void {
    const cantidad = resultado.eventos.filter((evento) => evento.estado === 'FALLIDO').length;
    if (cantidad === 0) {
      return;
    }
    this.alertaEventosInvalidos.set(
      cantidad === 1
        ? 'Se detectó 1 evento inválido. Revisa la causa en la bitácora.'
        : `Se detectaron ${cantidad} eventos inválidos. Revisa las causas en la bitácora.`,
    );
    this.temporizadorAlerta = setTimeout(() => this.alertaEventosInvalidos.set(null), DURACION_ALERTA_MS);
  }

  private cancelarTemporizadorAlerta(): void {
    if (this.temporizadorAlerta !== undefined) {
      clearTimeout(this.temporizadorAlerta);
      this.temporizadorAlerta = undefined;
    }
  }

  private cargarHistorial(idEvento: number): void {
    this.cargandoHistorialId.set(idEvento);
    this.ingesta.consultarIntentos(idEvento).subscribe({
      next: (intentos) => {
        this.historialPorEvento.update((actual) => ({ ...actual, [idEvento]: intentos }));
        this.cargandoHistorialId.set(null);
      },
      error: (e: HttpErrorResponse) => {
        this.cargandoHistorialId.set(null);
        this.mensajeOperacion.set({
          tipo: 'error',
          texto: e.error?.mensaje ?? 'No se pudo consultar el historial de reprocesos.',
        });
      },
    });
  }

  private mensajePara(intento: IntentoReproceso): { tipo: 'exito' | 'error'; texto: string } {
    if (intento.resultado === 'PROCESADO') {
      return { tipo: 'exito', texto: `El evento ${intento.idEvento} se reprocesó correctamente.` };
    }
    if (intento.resultado === 'DESCARTADO') {
      return {
        tipo: 'exito',
        texto: `El evento ${intento.idEvento} no se duplicó: ${intento.causa ?? 'la compra ya estaba registrada'}.`,
      };
    }
    return {
      tipo: 'error',
      texto: `El evento ${intento.idEvento} volvió a fallar: ${intento.causa ?? 'causa no informada'}.`,
    };
  }

  private static filtroVacio(): FiltroBitacora {
    return { desde: '', hasta: '', estado: '', origen: '', transaccion: '' };
  }
}
