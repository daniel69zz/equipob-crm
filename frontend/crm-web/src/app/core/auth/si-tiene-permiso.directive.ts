import { Directive, Input, TemplateRef, ViewContainerRef, inject } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * Muestra el elemento solo si el usuario tiene el permiso indicado.
 * Uso: <button *appSiTienePermiso="'CLIENTE_EDITAR'">Editar</button>
 *
 * Es una ayuda visual: la protección real la aplica el API Gateway.
 */
@Directive({
  selector: '[appSiTienePermiso]',
  standalone: true,
})
export class SiTienePermisoDirective {
  private readonly auth = inject(AuthService);
  private readonly plantilla = inject<TemplateRef<unknown>>(TemplateRef);
  private readonly contenedor = inject(ViewContainerRef);
  private visible = false;

  @Input({ required: true })
  set appSiTienePermiso(permiso: string) {
    const puedeVer = this.auth.tienePermiso(permiso);
    if (puedeVer && !this.visible) {
      this.contenedor.createEmbeddedView(this.plantilla);
      this.visible = true;
    } else if (!puedeVer && this.visible) {
      this.contenedor.clear();
      this.visible = false;
    }
  }
}
