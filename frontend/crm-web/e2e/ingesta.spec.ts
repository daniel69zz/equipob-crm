import { expect, Page, test } from '@playwright/test';

// Respuestas controladas: estas pruebas no reprocesan eventos de la base de desarrollo.
async function preparar(page: Page, resultado = 'EN_PROCESO', permiso = true) {
  const sesion = {
    token: 'sesion-de-prueba-ingesta', tipo: 'Bearer',
    expiraEn: new Date(Date.now() + 3600000).toISOString(),
    usuario: 'admin', nombreCompleto: 'Administrador de prueba', rol: 'ADMINISTRADOR_CRM',
    permisos: permiso ? ['EVENTOS_REPROCESAR'] : [],
  };
  await page.addInitScript(s => sessionStorage.setItem('crm.sesion', JSON.stringify(s)), sesion);
  const fallido = {
    id: 2, idEventoOrigen: 'evento-fallido', tipoEvento: 'COMPRA_CONFIRMADA', idTransaccion: 'QA-FALLIDA',
    estado: 'FALLIDO', causa: 'Falta compra.idCliente', recibidoEn: '2026-01-15T12:00:00Z',
    procesadoEn: '2026-01-15T12:00:01Z', reprocesado: false, enRevision: false,
  };
  const eventos = [fallido, ...Array.from({ length: 50 }, (_, i) => ({
    ...fallido, id: i + 3, idTransaccion: `QA-${i + 3}`, estado: 'PROCESADO', causa: null,
  }))];
  let solicitudesReproceso = 0;
  let reprocesosReales = 0;
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/auth/yo') return route.fulfill({ json: sesion });
    if (url.pathname === '/api/comportamiento/eventos/2/reprocesar') {
      reprocesosReales++;
      return route.fulfill({ status: 500, json: { mensaje: 'No debe invocarse el reproceso real' } });
    }
    if (url.pathname === '/api/comportamiento/eventos/2/revision') {
      solicitudesReproceso++;
      if (resultado === 'ERROR') return route.fulfill({ status: 503, json: { mensaje: 'Servicio temporalmente no disponible' } });
      fallido.estado = 'EN_ESPERA';
      fallido.reprocesado = true;
      fallido.enRevision = true;
      return route.fulfill({ json: {
        id: 1, idEvento: 2, numero: 1, usuario: 'admin', intentadoEn: new Date().toISOString(),
        resultado: 'EN_PROCESO', causa: null,
      } });
    }
    if (url.pathname.endsWith('/intentos')) return route.fulfill({ json: [] });
    if (url.pathname === '/api/comportamiento/eventos') {
      const desde = url.searchParams.get('desde');
      const hasta = url.searchParams.get('hasta');
      const base = eventos.filter(e => (!desde || e.recibidoEn.slice(0, 10) >= desde)
        && (!hasta || e.recibidoEn.slice(0, 10) <= hasta));
      const estado = url.searchParams.get('estado');
      const filtrados = base.filter(e => !estado || e.estado === estado);
      const pagina = Number(url.searchParams.get('pagina') ?? 0);
      const tamanio = Number(url.searchParams.get('tamanio') ?? 50);
      return route.fulfill({ json: {
        desde, hasta, total: filtrados.length, pagina, tamanio,
        resumen: Object.fromEntries(['RECIBIDO', 'PROCESADO', 'FALLIDO', 'DESCARTADO', 'EN_ESPERA']
          .map(estado => [estado, base.filter(e => e.estado === estado).length])),
        eventos: filtrados.slice(pagina * tamanio, (pagina + 1) * tamanio),
      } });
    }
    return route.fulfill({ status: 404, json: { mensaje: 'Ruta de prueba no configurada' } });
  });
  return { solicitudesReproceso: () => solicitudesReproceso, reprocesosReales: () => reprocesosReales };
}

test('bitácora: abre todo el historial, pagina, filtra y Limpiar restaura todos', async ({ page }) => {
  await preparar(page);
  const consulta = page.waitForRequest(r => new URL(r.url()).pathname === '/api/comportamiento/eventos');
  await page.goto('/eventos');
  const url = new URL((await consulta).url());
  expect(url.searchParams.has('desde')).toBe(false);
  expect(url.searchParams.has('hasta')).toBe(false);
  await expect(page.getByText('Todo el historial.', { exact: false })).toBeVisible();
  await expect(page.locator('tbody > tr')).toHaveCount(50);
  await page.getByRole('button', { name: 'Siguiente', exact: true }).click();
  await expect(page.locator('tbody > tr')).toHaveCount(1);
  await page.getByLabel('Desde', { exact: true }).fill('2026-10-14');
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  await expect(page.getByText('No hay eventos para estos filtros.')).toBeVisible();
  await page.getByRole('button', { name: 'Limpiar', exact: true }).click();
  await expect(page.locator('tbody > tr')).toHaveCount(50);
  await expect(page.getByLabel('Desde', { exact: true })).toHaveValue('');
});

test('inicio: simula el envío y deja en espera, incluso al recargar', async ({ page }) => {
  const estado = await preparar(page);
  await page.goto('/');
  await page.getByRole('button', { name: 'Reprocesar', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Evento enviado a revisión.');
  await expect(page.getByRole('button', { name: 'Reprocesado', exact: true })).toBeDisabled();
  expect(estado.solicitudesReproceso()).toBe(1);
  expect(estado.reprocesosReales()).toBe(0);
  await page.reload();
  await expect(page.getByRole('button', { name: 'Reprocesado', exact: true })).toBeDisabled();
  await expect(page.locator('.evento-fallido').getByText('En espera de revisión', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'Ver eventos en espera de revisión en la bitácora' }).click();
  await expect(page).toHaveURL(/\/eventos\?estado=EN_ESPERA$/);
  await expect(page.getByLabel('Estado', { exact: true })).toHaveValue('EN_ESPERA');
  await expect(page.getByText('Reprocesado · En espera de revisión', { exact: true })).toBeVisible();
});

test('bitácora: simula el envío y conserva el estado de espera', async ({ page }) => {
  const estado = await preparar(page);
  await page.goto('/eventos?estado=FALLIDO');
  await page.getByRole('button', { name: 'Reprocesar', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Evento enviado a revisión.');
  await page.getByLabel('Estado', { exact: true }).selectOption('EN_ESPERA');
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Reprocesado', exact: true })).toBeDisabled();
  await expect(page.locator('tbody')).toContainText('En espera');
  expect(estado.solicitudesReproceso()).toBe(1);
  expect(estado.reprocesosReales()).toBe(0);
});

test('inicio: un error HTTP permite reintentar y no anuncia un reproceso completado', async ({ page }) => {
  await preparar(page, 'ERROR');
  await page.goto('/');
  await page.getByRole('button', { name: 'Reprocesar', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Servicio temporalmente no disponible');
  await expect(page.getByRole('button', { name: 'Reprocesar', exact: true })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Reprocesado', exact: true })).toHaveCount(0);
});

test('inicio: sin permiso no muestra notificaciones ni consulta eventos', async ({ page }) => {
  await preparar(page, 'EN_PROCESO', false);
  let consultas = 0;
  page.on('request', r => { if (r.url().includes('/api/comportamiento/eventos')) consultas++; });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Administrador de prueba' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Notificaciones de eventos fallidos' })).toHaveCount(0);
  expect(consultas).toBe(0);
});
