import { expect, Page, test } from '@playwright/test';

async function preparar(page: Page, fallarCatalogo = false, vacio = false) {
  const sesion = { token: 'prueba-criterios', tipo: 'Bearer', expiraEn: new Date(Date.now() + 3600000).toISOString(),
    usuario: 'admin', nombreCompleto: 'Administrador', rol: 'ADMINISTRADOR_CRM',
    permisos: ['SEGMENTACION_CONFIGURAR', 'INDICADORES_CONSULTAR'] };
  await page.addInitScript(s => sessionStorage.setItem('crm.sesion', JSON.stringify(s)), sesion);
  let criterios: Record<string, any>[] = vacio ? [] : [{
    id: 25, nombre: 'Lectores', descripcion: '', indicador: 'CATEGORIA', condicion: 'CONTIENE',
    minimo: null, maximo: null, categoria: 'Libros', activo: true, version: 0,
    creadoEn: '2026-10-01T12:00:00Z', actualizadoEn: '2026-10-01T12:00:00Z', actualizadoPor: 'admin',
  }];
  let consultaCategorias = 0;
  const guardados: Record<string, any>[] = [];
  const casos: Record<string, any>[] = [];
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/auth/yo') return route.fulfill({ json: sesion });
    if (url.pathname === '/api/comportamiento/categorias') {
      consultaCategorias++;
      if (fallarCatalogo && consultaCategorias === 1) return route.fulfill({ status: 503, json: {} });
      return route.fulfill({ json: vacio ? [] : ['Electronica', 'Electrónica', 'Alimentos', 'Hogar, jardín'] });
    }
    if (url.pathname.endsWith('/historial')) return route.fulfill({ json: [] });
    if (url.pathname.endsWith('/comprobar')) {
      casos.push(route.request().postDataJSON());
      return route.fulfill({ json: { criterioId: 25, version: 0, activo: true, cumple: true } });
    }
    if (url.pathname === '/api/segmentacion/criterios') {
      if (route.request().method() === 'POST') {
        const datos = route.request().postDataJSON(); guardados.push(datos);
        const nuevo = { ...datos, id: 26, activo: true, version: 0, actualizadoPor: 'admin' };
        criterios = [nuevo, ...criterios];
        return route.fulfill({ status: 201, json: nuevo });
      }
      return route.fulfill({ json: criterios });
    }
    return route.fulfill({ status: 404, json: {} });
  });
  await page.goto('/segmentacion/criterios');
  return { guardados, casos };
}

async function abrirCategoria(page: Page) {
  await page.getByRole('button', { name: '+ Nuevo criterio' }).click();
  await page.getByLabel('Nombre del criterio', { exact: true }).fill('Compradores de tecnología');
  await page.getByLabel('Indicador', { exact: true }).selectOption('CATEGORIA');
  return page.getByRole('combobox', { name: 'Categoría consumida', exact: true });
}

test('ayuda: explica indicadores, rangos y aplicación; se cierra con Escape y devuelve el foco', async ({ page }) => {
  await preparar(page);
  const ayuda = page.getByRole('button', { name: 'Ayuda sobre criterios de segmentación' });
  await ayuda.click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo).toContainText('Su frecuencia es 3 compras');
  await expect(dialogo).toContainText('1–5 y 5–10 se cruzan en el 5');
  await expect(dialogo).toContainText('Valor acumulado');
  await page.keyboard.press('Escape');
  await expect(dialogo).not.toBeVisible();
  await expect(ayuda).toBeFocused();
  await abrirCategoria(page);
  await ayuda.click();
  await expect(dialogo).toBeVisible();
  await page.getByRole('button', { name: 'Entendido', exact: true }).click();
  await expect(dialogo).not.toBeVisible();
});

test('categoría: busca sin tildes, unifica variantes y guarda la opción seleccionada', async ({ page }) => {
  const datos = await preparar(page);
  const combo = await abrirCategoria(page);
  await combo.click();
  await expect(page.getByRole('listbox').getByRole('option')).toHaveCount(4); // Incluye Libros de un criterio ya guardado.
  await combo.fill('electronica');
  await expect(page.getByRole('listbox').getByRole('option')).toHaveCount(1);
  await page.getByRole('option', { name: 'Electrónica', exact: true }).click();
  await expect(combo).toHaveValue('Electrónica');
  await page.getByRole('button', { name: 'Guardar criterio', exact: true }).click();
  await expect.poll(() => datos.guardados.length).toBe(1);
  expect(datos.guardados[0]['categoria']).toBe('Electrónica');
});

test('categoría: teclado, Escape y texto sin selección no crean categorías arbitrarias', async ({ page }) => {
  const datos = await preparar(page);
  const combo = await abrirCategoria(page);
  await combo.fill('inexistente');
  await expect(page.getByText('No hay categorías que coincidan con la búsqueda.')).toBeVisible();
  await combo.press('Escape');
  await expect(combo).toHaveValue('');
  await page.getByRole('button', { name: 'Guardar criterio', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Indique la categoría consumida');
  expect(datos.guardados).toHaveLength(0);
  await combo.fill('alimentos');
  await combo.press('ArrowDown');
  await combo.press('Enter');
  await expect(combo).toHaveValue('Alimentos');
  await expect(combo).toHaveAttribute('aria-expanded', 'false');
  await page.getByRole('button', { name: 'Quitar categoría seleccionada' }).click();
  await expect(combo).toHaveValue('');
});

test('prueba de regla: permite varias categorías y conserva nombres con coma', async ({ page }) => {
  const datos = await preparar(page);
  await page.getByRole('button', { name: 'Historial y prueba' }).click();
  const combo = page.getByRole('combobox', { name: 'Categorías consumidas del caso' });
  await combo.fill('hogar');
  await page.getByRole('option', { name: 'Hogar, jardín', exact: true }).click();
  await combo.fill('libros');
  await page.getByRole('option', { name: 'Libros', exact: true }).click();
  await combo.press('Escape');
  await page.getByRole('button', { name: 'Comprobar regla', exact: true }).click();
  await expect.poll(() => datos.casos.length).toBe(1);
  expect(datos.casos[0]['categorias']).toEqual(['Hogar, jardín', 'Libros']);
  await page.getByRole('button', { name: 'Quitar Libros', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Quitar Libros', exact: true })).toHaveCount(0);
});

test('catálogo: permite reintentar un error y diferencia una lista vacía', async ({ page }) => {
  await preparar(page, true);
  const combo = await abrirCategoria(page);
  await expect(page.getByRole('alert')).toContainText('No se pudieron cargar las categorías');
  await page.getByRole('button', { name: 'Reintentar categorías' }).click();
  await combo.fill('electronica');
  await expect(page.getByRole('option', { name: 'Electrónica', exact: true })).toBeVisible();
});

test('móvil: ayuda y selector se ajustan a la pantalla; catálogo vacío muestra mensaje', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await preparar(page, false, true);
  const combo = await abrirCategoria(page);
  await combo.click();
  await expect(page.getByText('Todavía no hay categorías registradas.')).toBeVisible();
  await combo.press('Escape');
  await page.getByRole('button', { name: 'Ayuda sobre criterios de segmentación' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('ayuda-movil.png'), fullPage: true });
});
