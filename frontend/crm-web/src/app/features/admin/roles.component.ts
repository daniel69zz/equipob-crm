import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService, PermisoDisponible, Rol } from './admin.service';

/** Rol que se está creando (id null) o editando. */
interface RolEnEdicion {
  id: number | null;
  codigo: string;
  nombre: string;
  descripcion: string;
  permisos: Set<string>;
}

@Component({
  selector: 'app-roles',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="encabezado">
      <div>
        <h1>Roles y permisos</h1>
        <p class="subtitulo">Los cambios de permisos rigen para los usuarios del rol en su siguiente acción.</p>
      </div>
      @if (!edicion()) {
        <button type="button" (click)="nuevoRol()">Nuevo rol</button>
      }
    </div>

    @if (mensaje()) {
      <p [class.error]="mensajeEsError()" role="status">{{ mensaje() }}</p>
    }

    @if (edicion(); as rol) {
      <form class="tarjeta editor" (ngSubmit)="guardar(rol)">
        <h2>{{ rol.id === null ? 'Nuevo rol' : 'Editar rol ' + rol.codigo }}</h2>

        @if (rol.id === null) {
          <div>
            <label for="codigo">Código</label>
            <input id="codigo" name="codigo" [(ngModel)]="rol.codigo" placeholder="SUPERVISOR_ATENCION" required />
          </div>
        }
        <div>
          <label for="nombre">Nombre</label>
          <input id="nombre" name="nombre" [(ngModel)]="rol.nombre" required />
        </div>
        <div>
          <label for="descripcion">Descripción</label>
          <input id="descripcion" name="descripcion" [(ngModel)]="rol.descripcion" />
        </div>

        <fieldset>
          <legend>Permisos</legend>
          @for (permiso of permisos(); track permiso.codigo) {
            <label class="permiso">
              <input
                type="checkbox"
                [checked]="rol.permisos.has(permiso.codigo)"
                (change)="alternarPermiso(rol, permiso.codigo)"
              />
              <span><strong>{{ permiso.codigo }}</strong> · {{ permiso.descripcion }}</span>
            </label>
          }
        </fieldset>

        <div class="acciones">
          <button type="submit" [disabled]="guardando()">Guardar</button>
          <button type="button" class="secundario" (click)="cancelar()">Cancelar</button>
        </div>
      </form>
    }

    <div class="tarjeta">
      <table>
        <thead>
          <tr>
            <th>Código</th>
            <th>Nombre</th>
            <th>Permisos</th>
            <th>Estado</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          @for (rol of roles(); track rol.id) {
            <tr>
              <td>{{ rol.codigo }}</td>
              <td>{{ rol.nombre }}</td>
              <td>{{ rol.permisos.length }}</td>
              <td>{{ rol.activo ? 'Activo' : 'Desactivado' }}</td>
              <td class="acciones">
                @if (rol.activo) {
                  <button type="button" class="secundario" (click)="editar(rol)">Editar</button>
                  <button type="button" class="secundario" (click)="desactivar(rol)">Desactivar</button>
                }
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .encabezado { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
    .subtitulo { color: var(--color-texto-suave); margin-top: 0; }
    .editor { display: grid; gap: 1rem; margin-bottom: 1.5rem; }
    .editor h2 { margin: 0; font-size: 1.1rem; color: var(--color-primario); }
    fieldset { border: 1px solid var(--color-borde); border-radius: var(--radio); padding: 0.75rem 1rem; }
    .permiso { display: flex; gap: 0.6rem; align-items: flex-start; font-weight: normal; margin: 0.4rem 0; }
    .permiso input { width: auto; margin-top: 0.2rem; }
    .acciones { display: flex; gap: 0.5rem; }
  `,
})
export class RolesComponent implements OnInit {
  private readonly admin = inject(AdminService);

  readonly roles = signal<Rol[]>([]);
  readonly permisos = signal<PermisoDisponible[]>([]);
  readonly edicion = signal<RolEnEdicion | null>(null);
  readonly guardando = signal(false);
  readonly mensaje = signal<string | null>(null);
  readonly mensajeEsError = signal(false);

  ngOnInit(): void {
    this.admin.listarPermisos().subscribe({ next: (p) => this.permisos.set(p), error: (e) => this.mostrarError(e) });
    this.cargarRoles();
  }

  nuevoRol(): void {
    this.edicion.set({ id: null, codigo: '', nombre: '', descripcion: '', permisos: new Set() });
  }

  editar(rol: Rol): void {
    this.edicion.set({
      id: rol.id,
      codigo: rol.codigo,
      nombre: rol.nombre,
      descripcion: rol.descripcion ?? '',
      permisos: new Set(rol.permisos),
    });
  }

  cancelar(): void {
    this.edicion.set(null);
  }

  alternarPermiso(rol: RolEnEdicion, codigo: string): void {
    if (rol.permisos.has(codigo)) {
      rol.permisos.delete(codigo);
    } else {
      rol.permisos.add(codigo);
    }
  }

  guardar(rol: RolEnEdicion): void {
    if (rol.permisos.size === 0) {
      this.mensajeEsError.set(true);
      this.mensaje.set('Seleccione al menos un permiso.');
      return;
    }
    const datos = { nombre: rol.nombre, descripcion: rol.descripcion || null, permisos: [...rol.permisos] };
    const operacion =
      rol.id === null ? this.admin.crearRol(rol.codigo.trim().toUpperCase(), datos) : this.admin.actualizarRol(rol.id, datos);

    this.guardando.set(true);
    operacion.subscribe({
      next: (guardado) => {
        this.guardando.set(false);
        this.edicion.set(null);
        this.mostrarExito(`Rol ${guardado.codigo} guardado.`);
        this.cargarRoles();
      },
      error: (e) => {
        this.guardando.set(false);
        this.mostrarError(e);
      },
    });
  }

  desactivar(rol: Rol): void {
    this.admin.desactivarRol(rol.id).subscribe({
      next: () => {
        this.mostrarExito(`Rol ${rol.codigo} desactivado.`);
        this.cargarRoles();
      },
      error: (e) => this.mostrarError(e),
    });
  }

  private cargarRoles(): void {
    this.admin.listarRoles().subscribe({ next: (roles) => this.roles.set(roles), error: (e) => this.mostrarError(e) });
  }

  private mostrarExito(texto: string): void {
    this.mensajeEsError.set(false);
    this.mensaje.set(texto);
  }

  private mostrarError(error: HttpErrorResponse): void {
    this.mensajeEsError.set(true);
    this.mensaje.set(error.error?.mensaje ?? 'No se pudo completar la operación.');
  }
}
