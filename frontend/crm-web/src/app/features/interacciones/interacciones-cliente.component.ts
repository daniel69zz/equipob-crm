import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { finalize, map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { PaginacionComponent } from '../../core/ui/paginacion.component';
import { PestaniasClienteComponent } from '../clientes/pestanias-cliente.component';
import { FormularioInteraccionComponent } from './formulario-interaccion.component';
import {
  FiltroInteracciones,
  Interaccion,
  InteraccionesService,
  PaginaInteracciones,
  nombreCanal,
  nombreTipo,
} from './interacciones.service';

const TAMANIO_PAGINA = 20;

/**
 * Historial unificado de interacciones de un cliente (SCRUM-52, SCRUM-430): consultas, reclamos y notas, de la más
 * reciente a la más antigua, con quién las registró y cuándo. Lo ven los tres roles con
 * INTERACCIONES_CONSULTAR (docs/interacciones/registro-interacciones.md). El Agente de Atención y el Administrador
 * registran interacciones nuevas y corrigen las existentes (SCRUM-434): el agente, solo las que registró.
 */
@Component({
  selector: 'app-interacciones-cliente',
  standalone: true,
  imports: [DatePipe, FormularioInteraccionComponent, PaginacionComponent, PestaniasClienteComponent],
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

    @if (error()) {
      <div class="error" role="alert">
        <p>{{ error() }}</p>
        <button type="button" class="secundario" (click)="cargar(pagina())">Reintentar</button>
      </div>
    }

    @if (resultado(); as datos) {
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
                @if (interaccion.canal) { <span class="canal">{{ nombreCanal(interaccion.canal) }}</span> }
                <time [attr.datetime]="interaccion.registradoEn">{{ interaccion.registradoEn | date: 'dd/MM/yyyy HH:mm' }}</time>
                @if (auth.puedeModificarInteraccion(interaccion.registradoPor)) {
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
              </footer>
            </li>
          }
        } @empty {
          <li class="tarjeta vacio">Este cliente no tiene interacciones registradas.</li>
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
  readonly nombreTipo = nombreTipo;
  readonly nombreCanal = nombreCanal;

  /** Filtros aplicados a la consulta. */
  protected filtro: FiltroInteracciones = {};

  constructor() {
    inject(ActivatedRoute).paramMap.pipe(map(p => p.get('id') ?? ''), takeUntilDestroyed()).subscribe(id => {
      this.clienteId.set(id);
      this.resultado.set(null);
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
        },
        error: (e: HttpErrorResponse) => this.error.set(this.mensaje(e, 'No se pudo cargar el historial de interacciones.')),
      });
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

  /** Trae la versión vigente después de un 409 y vuelve a abrir el formulario con ella. */
  recargar(id: number): void {
    this.servicio.detalle(id).subscribe({
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
