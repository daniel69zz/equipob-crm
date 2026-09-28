import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { AdminService, Rol, UsuarioInterno } from './admin.service';

@Component({
  selector: 'app-usuarios',
  standalone: true,
  template: `
    <h1>Usuarios y roles</h1>
    <p class="subtitulo">El cambio de rol rige de inmediato: el usuario ve sus nuevas opciones al recargar la aplicación.</p>

    @if (mensaje()) {
      <p [class.error]="mensajeEsError()" role="status">{{ mensaje() }}</p>
    }

    <div class="tarjeta">
      <table>
        <thead>
          <tr>
            <th>Usuario</th>
            <th>Nombre</th>
            <th>Rol</th>
            <th>Estado</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          @for (usuario of usuarios(); track usuario.id) {
            <tr>
              <td>{{ usuario.nombreUsuario }}</td>
              <td>{{ usuario.nombreCompleto }}</td>
              <td>
                <select
                  [value]="usuario.rol"
                  [disabled]="!usuario.activo || esUsuarioActual(usuario)"
                  (change)="cambiarRol(usuario, $any($event.target).value)"
                >
                  @for (rol of rolesActivos(); track rol.codigo) {
                    <option [value]="rol.codigo">{{ rol.nombre }}</option>
                  }
                </select>
              </td>
              <td>{{ usuario.activo ? 'Activo' : 'Desactivado' }}</td>
              <td>
                @if (usuario.activo && !esUsuarioActual(usuario)) {
                  <button class="secundario" type="button" (click)="desactivar(usuario)">Desactivar</button>
                }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="5">No hay usuarios registrados.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .subtitulo { color: var(--color-texto-suave); margin-top: 0; }
    select { min-width: 220px; }
  `,
})
export class UsuariosComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly auth = inject(AuthService);

  readonly usuarios = signal<UsuarioInterno[]>([]);
  readonly roles = signal<Rol[]>([]);
  readonly rolesActivos = computed(() => this.roles().filter((rol) => rol.activo));
  readonly mensaje = signal<string | null>(null);
  readonly mensajeEsError = signal(false);

  ngOnInit(): void {
    this.admin.listarRoles().subscribe({ next: (roles) => this.roles.set(roles), error: (e) => this.mostrarError(e) });
    this.cargarUsuarios();
  }

  esUsuarioActual(usuario: UsuarioInterno): boolean {
    return usuario.nombreUsuario === this.auth.sesion()?.usuario;
  }

  cambiarRol(usuario: UsuarioInterno, rol: string): void {
    this.admin.asignarRol(usuario.id, rol).subscribe({
      next: () => {
        this.mostrarExito(`Rol de ${usuario.nombreUsuario} actualizado.`);
        this.cargarUsuarios();
      },
      error: (e) => this.mostrarError(e),
    });
  }

  desactivar(usuario: UsuarioInterno): void {
    this.admin.desactivarUsuario(usuario.id).subscribe({
      next: () => {
        this.mostrarExito(`Usuario ${usuario.nombreUsuario} desactivado.`);
        this.cargarUsuarios();
      },
      error: (e) => this.mostrarError(e),
    });
  }

  private cargarUsuarios(): void {
    this.admin.listarUsuarios().subscribe({ next: (usuarios) => this.usuarios.set(usuarios), error: (e) => this.mostrarError(e) });
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
