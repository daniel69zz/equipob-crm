import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuditoriaService, EventoAuditoria, FiltroAuditoria } from './auditoria.service';

const TAMANIO_PAGINA = 50;

/** Nombre visible de cada operación de auditoría. */
const NOMBRES_DE_OPERACION: Record<string, string> = {
  CLIENTE_CONSULTADO: 'Consulta de cliente',
  CLIENTE_MODIFICADO: 'Modificación de cliente',
  ACCESO_DENEGADO: 'Acceso denegado',
  ROL_CREADO: 'Rol creado',
  ROL_ACTUALIZADO: 'Rol actualizado',
  ROL_DESACTIVADO: 'Rol desactivado',
  USUARIO_CREADO: 'Usuario creado',
  ROL_ASIGNADO: 'Rol asignado',
  USUARIO_DESACTIVADO: 'Usuario desactivado',
};

@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <h1>Auditoría</h1>
    <p class="subtitulo">Quién consultó o modificó datos de clientes, los accesos denegados y los cambios de seguridad.</p>

    <form class="tarjeta filtros" (ngSubmit)="buscar(0)">
      <div>
        <label for="usuario">Usuario</label>
        <input id="usuario" name="usuario" [(ngModel)]="filtro.usuario" placeholder="ana" />
      </div>
      <div>
        <label for="operacion">Operación</label>
        <select id="operacion" name="operacion" [(ngModel)]="filtro.operacion">
          <option value="">Todas</option>
          @for (operacion of operaciones(); track operacion) {
            <option [value]="operacion">{{ nombreOperacion(operacion) }}</option>
          }
        </select>
      </div>
      <div>
        <label for="clienteId">Cliente</label>
        <input id="clienteId" name="clienteId" [(ngModel)]="filtro.clienteId" placeholder="Id del cliente" />
      </div>
      <div>
        <label for="desde">Desde</label>
        <input id="desde" name="desde" type="date" [(ngModel)]="filtro.desde" />
      </div>
      <div>
        <label for="hasta">Hasta</label>
        <input id="hasta" name="hasta" type="date" [(ngModel)]="filtro.hasta" />
      </div>
      <div class="acciones">
        <button type="submit" [disabled]="cargando()">Buscar</button>
        <button type="button" class="secundario" (click)="limpiar()">Limpiar</button>
      </div>
    </form>

    @if (error()) {
      <p class="error" role="status">{{ error() }}</p>
    }

    <div class="tarjeta">
      <table>
        <thead>
          <tr>
            <th>Fecha y hora</th>
            <th>Usuario</th>
            <th>Operación</th>
            <th>Afectado</th>
            <th>Detalle</th>
          </tr>
        </thead>
        <tbody>
          @for (evento of eventos(); track evento.id) {
            <tr [class.denegado]="evento.operacion === 'ACCESO_DENEGADO'">
              <td class="fecha">{{ evento.ocurridoEn | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
              <td>{{ evento.usuario }}</td>
              <td>{{ nombreOperacion(evento.operacion) }}</td>
              <td>{{ evento.entidad }}{{ evento.entidadId ? ' ' + evento.entidadId : '' }}</td>
              <td class="detalle">{{ evento.detalle }}</td>
            </tr>
          } @empty {
            <tr><td colspan="5">No hay registros para estos filtros.</td></tr>
          }
        </tbody>
      </table>

      @if (total() > 0) {
        <div class="paginacion">
          <span>{{ desdeRegistro() }}–{{ hastaRegistro() }} de {{ total() }}</span>
          <button type="button" class="secundario" [disabled]="pagina() === 0 || cargando()" (click)="buscar(pagina() - 1)">
            Anterior
          </button>
          <button type="button" class="secundario" [disabled]="esUltimaPagina() || cargando()" (click)="buscar(pagina() + 1)">
            Siguiente
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .subtitulo { color: var(--color-texto-suave); margin-top: 0; }
    .filtros {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
      gap: 1rem; align-items: end; margin-bottom: 1rem;
    }
    .acciones { display: flex; gap: 0.5rem; }
    .fecha { white-space: nowrap; }
    .detalle { color: var(--color-texto-suave); font-size: 0.9rem; }
    tr.denegado td { color: var(--color-error); }
    .paginacion { display: flex; align-items: center; justify-content: flex-end; gap: 0.75rem; margin-top: 1rem; }
  `,
})
export class AuditoriaComponent implements OnInit {
  private readonly auditoria = inject(AuditoriaService);

  filtro: FiltroAuditoria = AuditoriaComponent.filtroVacio();

  readonly operaciones = signal<string[]>([]);
  readonly eventos = signal<EventoAuditoria[]>([]);
  readonly pagina = signal(0);
  readonly total = signal(0);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  readonly desdeRegistro = computed(() => this.pagina() * TAMANIO_PAGINA + 1);
  readonly hastaRegistro = computed(() => this.pagina() * TAMANIO_PAGINA + this.eventos().length);
  readonly esUltimaPagina = computed(() => this.hastaRegistro() >= this.total());

  ngOnInit(): void {
    this.auditoria.listarOperaciones().subscribe({
      next: (operaciones) => this.operaciones.set(operaciones),
      error: (e) => this.mostrarError(e),
    });
    this.buscar(0);
  }

  buscar(pagina: number): void {
    this.cargando.set(true);
    this.error.set(null);
    this.auditoria.buscar(this.filtro, pagina, TAMANIO_PAGINA).subscribe({
      next: (resultado) => {
        this.eventos.set(resultado.eventos);
        this.pagina.set(resultado.pagina);
        this.total.set(resultado.total);
        this.cargando.set(false);
      },
      error: (e) => {
        this.cargando.set(false);
        this.mostrarError(e);
      },
    });
  }

  limpiar(): void {
    this.filtro = AuditoriaComponent.filtroVacio();
    this.buscar(0);
  }

  nombreOperacion(operacion: string): string {
    return NOMBRES_DE_OPERACION[operacion] ?? operacion;
  }

  private mostrarError(error: HttpErrorResponse): void {
    this.error.set(error.error?.mensaje ?? 'No se pudo consultar la auditoría.');
  }

  private static filtroVacio(): FiltroAuditoria {
    return { usuario: '', operacion: '', clienteId: '', desde: '', hasta: '' };
  }
}
