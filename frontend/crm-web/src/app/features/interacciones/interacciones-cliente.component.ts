import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { finalize, map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { PaginacionComponent } from '../../core/ui/paginacion.component';
import { PestaniasClienteComponent } from '../clientes/pestanias-cliente.component';
import { AsignacionInteraccionComponent } from './asignacion-interaccion.component';
import { CompraInteraccionComponent } from './compra-interaccion.component';
import { FormularioInteraccionComponent } from './formulario-interaccion.component';
import { SeguimientoInteraccionComponent } from './seguimiento-interaccion.component';
import {
  FiltroInteracciones,
  Interaccion,
  InteraccionesService,
  PaginaInteracciones,
  ESTADOS_INTERACCION,
  TIPOS_INTERACCION,
  nombreCanal,
  nombreEstado,
  nombreTipo,
} from './interacciones.service';

const TAMANIO_PAGINA = 20;

/**
 * Historial unificado de interacciones de un cliente (SCRUM-52, SCRUM-430): consultas, reclamos y notas, de la más
 * reciente a la más antigua, con quién las registró y cuándo. Lo ven los tres roles con
 * INTERACCIONES_CONSULTAR (docs/interacciones/registro-interacciones.md). El Agente de Atención y el Administrador
 * registran interacciones nuevas y corrigen las existentes (SCRUM-434): el agente, solo las que registró. El
 * historial se filtra por tipo, por texto en el asunto, la descripción o la compra, y por fechas (SCRUM-431). Cada
 * interacción muestra su estado de seguimiento, y el Agente y el Administrador lo actualizan y la reclasifican
 * (SCRUM-55, SCRUM-450); una cerrada no se edita hasta que el Administrador la reabra. Cada interacción muestra la
 * compra del cliente vinculada, y el Agente y el Administrador la vinculan, la cambian o la quitan (SCRUM-54,
 * SCRUM-444). Cada interacción muestra su responsable; el Administrador la asigna o la reasigna, y el agente filtra
 * las que tiene asignadas (SCRUM-56, SCRUM-455).
 */
@Component({
  selector: 'app-interacciones-cliente',
  standalone: true,
  imports: [AsignacionInteraccionComponent, CompraInteraccionComponent, DatePipe, FormsModule,
    FormularioInteraccionComponent, PaginacionComponent, PestaniasClienteComponent, SeguimientoInteraccionComponent],
  template: `
    <app-pestanias-cliente [id]="clienteId()" />
    <header class="encabezado">
      <div>
        <h1>Interacciones</h1>
        <p class="subtitulo">Cliente {{ clienteId() }} · Consultas, reclamos y notas de atención, de la más reciente a la más antigua.</p>
      </div>
      @if (auth.puedeRegistrarInteracciones() && !registrando() && numeroCliente() !== null) {
        <button type="button" (click)="abrirRegistro()">Registrar interacción</button>
      }
    </header>

    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    @if (registrando()) {
      <app-formulario-interaccion [clienteId]="numeroCliente()!" (guardada)="registrada($event)"
        (cancelada)="registrando.set(false)" />
    }

    <form class="tarjeta filtros" (ngSubmit)="buscar()" novalidate aria-label="Filtros del historial">
      <div>
        <label for="filtro-tipo">Tipo</label>
        <select id="filtro-tipo" name="tipo" [(ngModel)]="borrador.tipo">
          <option value="">Todos</option>
          @for (tipo of tipos; track tipo.valor) { <option [value]="tipo.valor">{{ tipo.nombre }}</option> }
        </select>
      </div>
      <div>
        <label for="filtro-estado">Estado</label>
        <select id="filtro-estado" name="estado" [(ngModel)]="borrador.estado">
          <option value="">Todos</option>
          @for (estado of estados; track estado.valor) { <option [value]="estado.valor">{{ estado.nombre }}</option> }
        </select>
      </div>
      <div class="texto">
        <label for="filtro-texto">Buscar</label>
        <input id="filtro-texto" name="texto" type="search" maxlength="100" [(ngModel)]="borrador.texto"
          placeholder="Asunto, descripción o compra" />
      </div>
      <div>
        <label for="filtro-desde">Desde</label>
        <input id="filtro-desde" name="desde" type="date" [(ngModel)]="borrador.desde"
          [attr.aria-invalid]="!!errorFiltro()" />
      </div>
      <div>
        <label for="filtro-hasta">Hasta</label>
        <input id="filtro-hasta" name="hasta" type="date" [(ngModel)]="borrador.hasta"
          [attr.aria-invalid]="!!errorFiltro()" />
      </div>
      @if (auth.recibeNotificaciones()) {
        <label class="mias">
          <input type="checkbox" name="soloMias" [(ngModel)]="soloMias" /> Solo las asignadas a mí
        </label>
      }
      <div class="acciones">
        <button type="submit" [disabled]="cargando()">Buscar</button>
        <button type="button" class="secundario" [disabled]="cargando() || !hayFiltros()" (click)="limpiar()">Limpiar</button>
      </div>
      @if (errorFiltro()) { <p class="error-filtro" role="alert">{{ errorFiltro() }}</p> }
    </form>

    @if (error()) {
      <div class="error" role="alert">
        <p>{{ error() }}</p>
        <button type="button" class="secundario" (click)="cargar(pagina())">Reintentar</button>
      </div>
    }

    @if (resultado(); as datos) {
      @if (filtrado()) {
        <p class="conteo" role="status">{{ datos.total }} {{ datos.total === 1 ? 'interacción coincide' : 'interacciones coinciden' }} con los filtros.</p>
      }
      <ol class="historial" aria-label="Historial de interacciones">
        @for (interaccion of datos.interacciones; track interaccion.id + '-' + interaccion.version) {
          @if (editando() === interaccion.id) {
            <li>
              <app-formulario-interaccion [clienteId]="interaccion.clienteId" [interaccion]="interaccion"
                (guardada)="modificada($event)" (cancelada)="editando.set(null)" (recargar)="recargar(interaccion.id)" />
            </li>
          } @else {
            <li class="tarjeta interaccion">
              <header>
                <span [class]="'etiqueta tipo-' + interaccion.tipo">{{ nombreTipo(interaccion.tipo) }}</span>
                <span [class]="'etiqueta estado-' + interaccion.estado">{{ nombreEstado(interaccion.estado) }}</span>
                @if (interaccion.canal) { <span class="canal">{{ nombreCanal(interaccion.canal) }}</span> }
                <time [attr.datetime]="interaccion.registradoEn">{{ interaccion.registradoEn | date: 'dd/MM/yyyy HH:mm' }}</time>
                @if (interaccion.estado !== 'CERRADA' && auth.puedeModificarInteraccion(interaccion.registradoPor)) {
                  <button type="button" class="secundario editar" (click)="abrirEdicion(interaccion.id)"
                    [attr.aria-label]="'Editar la interacción n.º ' + interaccion.id">Editar</button>
                }
              </header>
              @if (interaccion.asunto) { <h2>{{ interaccion.asunto }}</h2> }
              <p class="descripcion">{{ interaccion.descripcion }}</p>
              <footer>
                <span>Registrada por {{ interaccion.registradoPor }}</span>
                @if (interaccion.referenciaCompra) { <span>Compra {{ interaccion.referenciaCompra }}</span> }
                @if (interaccion.modificadoEn) {
                  <span>Modificada por {{ interaccion.modificadoPor }} el {{ interaccion.modificadoEn | date: 'dd/MM/yyyy HH:mm' }}</span>
                }
                @if (interaccion.estadoActualizadoEn) {
                  <span>{{ nombreEstado(interaccion.estado) }} desde el {{ interaccion.estadoActualizadoEn | date: 'dd/MM/yyyy HH:mm' }}
                    por {{ interaccion.estadoActualizadoPor }}</span>
                }
              </footer>
              <app-compra-interaccion [interaccion]="interaccion" (actualizada)="compraActualizada($event)"
                (recargar)="recargar(interaccion.id)" />
              <app-asignacion-interaccion [interaccion]="interaccion" (actualizada)="asignada($event)"
                (recargar)="recargar(interaccion.id)" />
              @if (auth.puedeRegistrarInteracciones()) {
                <app-seguimiento-interaccion [interaccion]="interaccion" (actualizada)="seguimientoActualizado($event)"
                  (recargar)="recargar(interaccion.id)" />
              }
            </li>
          }
        } @empty {
          <li class="tarjeta vacio">
            {{ filtrado() ? 'Ninguna interacción coincide con los filtros.' : 'Este cliente no tiene interacciones registradas.' }}
          </li>
        }
      </ol>
      <app-paginacion [pagina]="datos.pagina" [tamanio]="datos.tamanio" [total]="datos.total" [cargando]="cargando()"
        (cambiar)="cargar($event)" />
    } @else if (!error()) {
      <p role="status">Cargando las interacciones…</p>
    }
  `,
  styles: `
    .encabezado { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; flex-wrap: wrap; }
    .filtros {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
      gap: 1rem; align-items: end; margin: 1rem 0;
    }
    .filtros .texto { grid-column: span 2; }
    @media (max-width: 520px) { .filtros .texto { grid-column: auto; } }
    .filtros .acciones { display: flex; gap: .5rem; }
    .filtros .mias { display: flex; gap: .4rem; align-items: center; font-size: .9rem; font-weight: 400; }
    .error-filtro { grid-column: 1 / -1; margin: 0; color: var(--color-error, #b42318); font-size: .85rem; }
    .conteo { color: var(--color-texto-suave); font-size: .9rem; margin: 0 0 .5rem; }
    .historial { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: .75rem; }
    .interaccion { padding: 1rem 1.25rem; }
    .interaccion header { display: flex; align-items: center; gap: .6rem; flex-wrap: wrap; }
    .interaccion time { margin-left: auto; color: var(--color-texto-suave); font-size: .85rem; }
    .canal { color: var(--color-texto-suave); font-size: .85rem; }
    .interaccion h2 { margin: .6rem 0 0; font-size: 1rem; }
    .descripcion { margin: .4rem 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
    .interaccion footer { display: flex; gap: 1rem; flex-wrap: wrap; margin-top: .6rem; color: var(--color-texto-suave); font-size: .82rem; }
    .tipo-RECLAMO { color: #b42318; background: #fdecea; }
    .tipo-CONSULTA { color: #1d4ed8; background: #e8effd; }
    .tipo-NOTA { color: #5b5f6b; background: #eef1f7; }
    .estado-ABIERTA { color: #b54708; background: #fef0c7; }
    .estado-EN_PROCESO { color: #1d4ed8; background: #e8effd; }
    .estado-CERRADA { color: #067647; background: #dcfae6; }
    .vacio { color: var(--color-texto-suave); }
    .editar { padding: .25rem .7rem; font-size: .82rem; }
  `,
})
export class InteraccionesClienteComponent {
  private readonly servicio = inject(InteraccionesService);
  readonly auth = inject(AuthService);

  readonly clienteId = signal('');
  readonly resultado = signal<PaginaInteracciones | null>(null);
  readonly pagina = signal(0);
  readonly cargando = signal(false);
  readonly error = signal('');
  readonly aviso = signal('');
  readonly registrando = signal(false);
  /** Interacción que se está editando. */
  readonly editando = signal<number | null>(null);
  readonly errorFiltro = signal('');
  /** Si la consulta mostrada usa algún filtro. */
  readonly filtrado = signal(false);
  readonly tipos = TIPOS_INTERACCION;

  /** Lo que el usuario escribe en los filtros; se aplica al pulsar Buscar. */
  borrador: FiltroInteracciones = vacio();
  /** SCRUM-56: solo las interacciones de las que el usuario es responsable. */
  soloMias = false;
  readonly nombreTipo = nombreTipo;
  readonly nombreEstado = nombreEstado;
  readonly estados = ESTADOS_INTERACCION;
  readonly nombreCanal = nombreCanal;

  /** Filtros aplicados a la consulta mostrada. */
  private filtro: FiltroInteracciones = vacio();

  constructor() {
    inject(ActivatedRoute).paramMap.pipe(map(p => p.get('id') ?? ''), takeUntilDestroyed()).subscribe(id => {
      this.clienteId.set(id);
      this.resultado.set(null);
      this.borrador = vacio();
      this.soloMias = false;
      this.filtro = vacio();
      this.filtrado.set(false);
      this.cargar(0);
    });
  }

  cargar(pagina: number): void {
    const numero = this.numeroCliente();
    this.error.set('');
    if (numero === null) {
      this.error.set('El identificador del cliente no es válido.');
      return;
    }
    this.cargando.set(true);
    this.servicio.historial(numero, this.filtro, pagina, TAMANIO_PAGINA)
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: datos => {
          this.resultado.set(datos);
          this.pagina.set(datos.pagina);
          this.filtrado.set(tieneFiltros(this.filtro));
        },
        error: (e: HttpErrorResponse) => {
          const campos = (e.error?.campos ?? {}) as Record<string, string>;
          if (e.status === 400 && Object.keys(campos).length) {
            this.errorFiltro.set(e.error?.mensaje ?? 'Revise los filtros.');
            return;
          }
          this.error.set(this.mensaje(e, 'No se pudo cargar el historial de interacciones.'));
        },
      });
  }

  /** Aplica los filtros desde la primera página; las fechas se validan como en el servicio. */
  buscar(): void {
    const { desde, hasta } = this.borrador;
    if (desde && hasta && desde > hasta) {
      this.errorFiltro.set('La fecha desde no puede ser posterior a la fecha hasta.');
      return;
    }
    this.errorFiltro.set('');
    this.filtro = { ...this.borrador, texto: this.borrador.texto?.trim() ?? '',
      responsable: this.soloMias ? this.auth.sesion()?.usuario ?? '' : '' };
    this.cargar(0);
  }

  limpiar(): void {
    this.borrador = vacio();
    this.soloMias = false;
    this.errorFiltro.set('');
    this.buscar();
  }

  hayFiltros(): boolean {
    return this.soloMias || tieneFiltros(this.borrador) || tieneFiltros(this.filtro);
  }

  /** SCRUM-455: la interacción con su responsable nuevo. */
  asignada(interaccion: Interaccion): void {
    this.reemplazar(interaccion);
    this.aviso.set(`La interacción n.º ${interaccion.id} quedó asignada a ${interaccion.responsable}.`);
  }

  abrirRegistro(): void {
    this.aviso.set('');
    this.editando.set(null);
    this.registrando.set(true);
  }

  abrirEdicion(id: number): void {
    this.aviso.set('');
    this.registrando.set(false);
    this.editando.set(id);
  }

  /** La nueva queda primera: se vuelve a la primera página del historial. */
  registrada(interaccion: Interaccion): void {
    this.registrando.set(false);
    this.aviso.set(`Se registró la interacción n.º ${interaccion.id}.`);
    this.cargar(0);
  }

  modificada(interaccion: Interaccion): void {
    this.editando.set(null);
    this.reemplazar(interaccion);
    this.aviso.set(`Se guardaron los cambios de la interacción n.º ${interaccion.id}.`);
  }

  seguimientoActualizado(interaccion: Interaccion): void {
    this.reemplazar(interaccion);
    this.aviso.set(`La interacción n.º ${interaccion.id} quedó ${nombreEstado(interaccion.estado).toLowerCase()}`
      + ` como ${nombreTipo(interaccion.tipo).toLowerCase()}.`);
  }

  /** SCRUM-444: la compra vinculada, cambiada o quitada. */
  compraActualizada(interaccion: Interaccion): void {
    this.reemplazar(interaccion);
    this.aviso.set(interaccion.compra
      ? `La interacción n.º ${interaccion.id} quedó vinculada a la compra ${interaccion.compra.referencia}.`
      : `Se quitó la compra de la interacción n.º ${interaccion.id}.`);
  }

  /** Trae la versión vigente después de un 409 y vuelve a abrir el formulario con ella. */
  recargar(id: number): void {
    const numero = this.numeroCliente();
    if (numero === null) return;
    this.servicio.detalle(numero, id).subscribe({
      next: vigente => this.reemplazar(vigente),
      error: (e: HttpErrorResponse) => this.error.set(this.mensaje(e, 'No se pudo recargar la interacción.')),
    });
  }

  private reemplazar(interaccion: Interaccion): void {
    const datos = this.resultado();
    if (!datos) return;
    this.resultado.set({
      ...datos,
      interacciones: datos.interacciones.map(i => (i.id === interaccion.id ? interaccion : i)),
    });
  }

  protected mensaje(e: HttpErrorResponse, porDefecto: string): string {
    if (e.status === 401) return 'La sesión ha vencido. Inicie sesión nuevamente.';
    if (e.status === 403) return e.error?.mensaje ?? 'No tiene permiso para esta acción.';
    if (e.status === 0) return 'No se pudo conectar con el servidor. Compruebe la conexión e intente de nuevo.';
    return e.error?.mensaje ?? porDefecto;
  }

  numeroCliente(): number | null {
    const id = this.clienteId();
    const numero = Number(id);
    return /^\d+$/.test(id) && Number.isSafeInteger(numero) && numero > 0 ? numero : null;
  }
}

function vacio(): FiltroInteracciones {
  return { tipo: '', estado: '', texto: '', desde: '', hasta: '', responsable: '' };
}

function tieneFiltros(filtro: FiltroInteracciones): boolean {
  return Object.values(filtro).some(valor => typeof valor === 'string' && valor.trim() !== '');
}
