import { test, expect, Page } from '@playwright/test';

const API = '/api/comportamiento/reportes/programaciones';
const permisos = ['REPORTES_CONSULTAR', 'REPORTES_EXPORTAR', 'REPORTES_PROGRAMAR'];

async function preparar(page: Page, autorizado = true) {
  const sesion = { token: 'prueba', usuario: 'ana', nombreCompleto: 'Ana', rol: autorizado ? 'GERENTE_COMERCIAL' : 'AGENTE_ATENCION',
    expiraEn: '2099-01-01T00:00:00Z', tipo: 'Bearer', permisos: autorizado ? permisos : [] };
  await page.addInitScript(s => sessionStorage.setItem('crm.sesion', JSON.stringify(s)), sesion);
  const programaciones: Record<string, unknown>[] = [];
  let leida = false;
  await page.route('**/api/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname;
    if (path === '/api/auth/yo') return route.fulfill({ json: sesion });
    if (path === API && req.method() === 'POST') {
      programaciones.push({ ...req.postDataJSON(), id: 'p1', estado: 'ACTIVA', proxima: '2030-01-01T08:00:00-04:00' });
      return route.fulfill({ status: 201, json: { id: 'p1' } });
    }
    if (path === API) return route.fulfill({ json: programaciones });
    if (path === API + '/p1/estado') {
      programaciones[0]['estado'] = req.postDataJSON().estado;
      return route.fulfill({ status: 200 });
    }
    if (path === API + '/p1' && req.method() === 'PUT') {
      Object.assign(programaciones[0], req.postDataJSON()); return route.fulfill({ status: 200 });
    }
    if (path.endsWith('/leida')) { leida = true; return route.fulfill({ status: 200 }); }
    if (path.endsWith('/avisos')) return route.fulfill({ json: { noLeidas: leida ? 1 : 2 } });
    if (path.endsWith('/archivo')) return route.fulfill({ contentType: 'text/csv',
      headers: { 'Content-Disposition': 'attachment; filename="semanal.csv"' }, body: 'reporte,total\nsemanal,100' });
    if (path.endsWith('/ejecuciones')) return route.fulfill({ json: [
      { id: 'e1', programacion: 'p1', reporte: 'Semanal', prevista: '2026-11-10T08:00:00-04:00', ejecutada: '2026-11-10T08:00:03-04:00', estado: 'COMPLETADA', causa: null, nombre: 'semanal.csv', leida },
      { id: 'e2', programacion: 'p1', reporte: 'Segmento', prevista: '2026-11-10T08:00:00-04:00', ejecutada: '2026-11-10T08:00:03-04:00', estado: 'FALLIDA', causa: 'Segmentación no disponible', nombre: null, leida: false },
    ] });
    return route.fulfill({ json: [] });
  });
}

test('crea, edita, pausa, activa y cancela una programación', async ({ page }) => {
  await preparar(page);
  await page.goto('/analisis/programaciones');
  await page.getByLabel('Nombre', { exact: true }).fill('Reporte semanal');
  await page.getByLabel('Inicio', { exact: true }).fill('2030-01-01T08:00');
  const guardado = page.waitForRequest(r => r.url().endsWith(API) && r.method() === 'POST');
  await page.getByRole('button', { name: 'Programar reporte', exact: true }).click();
  expect((await guardado).postDataJSON()).toMatchObject({ frecuencia: 'SEMANAL', formato: 'CSV', dias: 7, inicio: '2030-01-01T08:00:00-04:00' });
  const tarjeta = page.locator('article').filter({ has: page.getByRole('heading', { name: 'Reporte semanal', exact: true }) });
  await expect(tarjeta).toContainText('ACTIVA');
  await tarjeta.getByRole('button', { name: 'Editar', exact: true }).click();
  await page.getByLabel('Nombre', { exact: true }).fill('Reporte semanal editado');
  await page.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
  const editada = page.locator('article').filter({ has: page.getByRole('heading', { name: 'Reporte semanal editado', exact: true }) });
  await expect(editada).toBeVisible();
  await editada.getByRole('button', { name: 'Pausar', exact: true }).click();
  await expect(editada).toContainText('PAUSADA');
  await editada.getByRole('button', { name: 'Activar', exact: true }).click();
  await expect(editada).toContainText('ACTIVA');
  await editada.getByRole('button', { name: 'Cancelar programación', exact: true }).click();
  await expect(editada).toContainText('CANCELADA');
  await expect(editada.getByRole('button')).toHaveCount(0);
});

test('recibe avisos, descarga el archivo y consulta la causa de fallo', async ({ page }) => {
  await preparar(page);
  await page.goto('/analisis/programaciones');
  await expect(page.getByRole('link', { name: 'Reportes: 2 avisos sin leer' })).toBeVisible();
  await expect(page.getByText('Segmentación no disponible', { exact: true })).toBeVisible();
  const disponible = page.locator('article').filter({ hasText: 'Reporte disponible' });
  const descarga = page.waitForEvent('download');
  await disponible.getByRole('button', { name: 'Descargar semanal.csv' }).click();
  expect((await descarga).suggestedFilename()).toBe('semanal.csv');
  await disponible.getByRole('button', { name: 'Marcar aviso leído' }).click();
  await expect(disponible.getByRole('button', { name: 'Marcar aviso leído' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Reportes: 1 avisos sin leer' })).toBeVisible();
});

test('agente sin permiso no accede a las programaciones', async ({ page }) => {
  await preparar(page, false);
  await page.goto('/analisis/programaciones');
  await expect(page).toHaveURL(/acceso-denegado/);
  await expect(page.getByRole('link', { name: 'Reportes programados', exact: true })).toHaveCount(0);
});
