import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EstadoPerfil, FiltroPerfiles, PaginaPerfiles, PerfilesService, ResumenPerfil } from './perfiles.service';

const TAMANIO_PAGINA = 50;

/** Solo los estados con incidencia: SCRUM-12 no revisa perfiles ya COMPLETO. */
const ESTADOS_CON_INCIDENCIA: { codigo: EstadoPerfil; nombre: string }[] = [
  { codigo: 'INCOMPLETO', nombre: 'Incompletos' },
  { codigo: 'INCONSISTENTE', nombre: 'Inconsistentes' },
];

const TIPOS_DOCUMENTO = ['CI', 'NIT', 'PASAPORTE', 'CE'];

/**
 * Vista de revisión para personal autorizado (SCRUM-169): perfiles que el motor de detección
 * (SCRUM-166) marcó incompletos o inconsistentes, con el motivo de cada uno.
 */
@Component({
  selector: 'app-revision-perfiles',
  standalone: true,
  imports: [DatePipe, FormsModule],
  template: `
    <h1>Revisión de perfiles</h1>
    <p class="subtitulo">
      Perfiles de cliente que quedaron incompletos o inconsistentes al detectarlos (SCRUM-12).
    </p>

    <div class="pestanias" role="tablist">
      @for (opcion of estados; track opcion.codigo) {
        <button
          type="button"
          class="secundario"
          role="tab"
          [class.activa]="estado() === opcion.codigo"
          [attr.aria-selected]="estado() === opcion.codigo"
          [disabled]="cargando()"
          (click)="cambiarEstado(opcion.codigo)"
        >
          {{ opcion.nombre }}
        </button>
      }
    </div>

    <form class="tarjeta filtros" (ngSubmit)="buscar(0)">
      <div>
        <label for="tipoDocumento">Tipo de documento</label>
        <select id="tipoDocumento" name="tipoDocumento" [(ngModel)]="filtro.tipoDocumento">
          <option value="">Todos</option>
          @for (tipo of tiposDocumento; track tipo) {
            <option [value]="tipo">{{ tipo }}</option>
          }
        </select>
      </div>
      <div>
        <label for="numeroDocumento">Número de documento</label>
        <input id="numeroDocumento" name="numeroDocumento" [(ngModel)]="filtro.numeroDocumento" placeholder="4455667" />
      </div>
      <div>
        <label for="motivo">Motivo</label>
        <input id="motivo" name="motivo" [(ngModel)]="filtro.motivo" placeholder="dirección principal" />
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
            <th>Cliente</th>
            <th>Documento</th>
            <th>Motivo</th>
            <th>Última actualización</th>
          </tr>
        </thead>
        <tbody>
          @for (perfil of perfiles(); track perfil.id) {
            <tr>
              <td>{{ nombreCompleto(perfil) }}</td>
              <td class="id">{{ perfil.tipoDocumento ?? '—' }} {{ perfil.numeroDocumento ?? '' }}</td>
              <td class="motivo">{{ perfil.motivosIncidencia ?? '—' }}</td>
              <td class="fecha">{{ perfil.actualizadoEn | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
            </tr>
          } @empty {
            <tr><td colspan="4">No hay perfiles pendientes de revisión en esta categoría.</td></tr>
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
    .pestanias { display: flex; gap: 0.5rem; margin-bottom: 1rem; }
    .pestanias button.activa { background: var(--color-primario); color: #fff; }
    .filtros {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
      gap: 1rem; align-items: end; margin-bottom: 1rem;
    }
    .acciones { display: flex; gap: 0.5rem; }
    .id { font-family: monospace; font-size: 0.85rem; white-space: nowrap; }
    .motivo { color: var(--color-texto-suave); font-size: 0.9rem; }
    .fecha { white-space: nowrap; }
    .paginacion { display: flex; align-items: center; justify-content: flex-end; gap: 0.75rem; margin-top: 1rem; }
  `,
})
export class RevisionPerfilesComponent implements OnInit {
  private readonly perfilesService = inject(PerfilesService);

  readonly estados = ESTADOS_CON_INCIDENCIA;
  readonly tiposDocumento = TIPOS_DOCUMENTO;
  readonly estado = signal<EstadoPerfil>('INCOMPLETO');
  filtro: FiltroPerfiles = RevisionPerfilesComponent.filtroVacio();

  readonly perfiles = signal<ResumenPerfil[]>([]);
  readonly pagina = signal(0);
  readonly total = signal(0);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  readonly desdeRegistro = computed(() => this.pagina() * TAMANIO_PAGINA + 1);
  readonly hastaRegistro = computed(() => this.pagina() * TAMANIO_PAGINA + this.perfiles().length);
  readonly esUltimaPagina = computed(() => this.hastaRegistro() >= this.total());

  ngOnInit(): void {
    this.buscar(0);
  }

  cambiarEstado(estado: EstadoPerfil): void {
    this.estado.set(estado);
    this.buscar(0);
  }

  buscar(pagina: number): void {
    this.cargando.set(true);
    this.error.set(null);
    this.perfilesService.buscar(this.estado(), this.filtro, pagina, TAMANIO_PAGINA).subscribe({
      next: (resultado: PaginaPerfiles) => {
        this.perfiles.set(resultado.clientes);
        this.pagina.set(resultado.pagina);
        this.total.set(resultado.total);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(e.error?.mensaje ?? 'No se pudo consultar los perfiles con incidencias.');
      },
    });
  }

  limpiar(): void {
    this.filtro = RevisionPerfilesComponent.filtroVacio();
    this.buscar(0);
  }

  nombreCompleto(perfil: ResumenPerfil): string {
    return [perfil.nombres, perfil.apellidos].filter(Boolean).join(' ') || '(sin nombre)';
  }

  private static filtroVacio(): FiltroPerfiles {
    return { tipoDocumento: '', numeroDocumento: '', motivo: '', desde: '', hasta: '' };
  }
}
