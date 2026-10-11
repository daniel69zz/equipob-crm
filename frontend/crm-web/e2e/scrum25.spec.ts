import { test, expect, Page, APIRequestContext } from '@playwright/test';

const usuario = process.env['E2E_USUARIO'] ?? 'admin-scrum25';
const password = process.env['E2E_PASSWORD'];
let adminHeaders: Record<string, string>;
test.describe.configure({ mode: 'serial' });

async function ingresar(page: Page, nombre = usuario) {
  await page.goto('/login');
  await page.getByLabel('Usuario', { exact: true }).fill(nombre);
  await page.getByLabel('Contraseña', { exact: true }).fill(password!);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL('/');
}
async function formulario(page: Page, nombre: string, indicador = 'FRECUENCIA') {
  await page.getByRole('button', { name: '+ Nuevo criterio' }).click();
  await page.getByLabel('Nombre del criterio').fill(nombre);
  await page.getByLabel('Indicador', { exact: true }).selectOption(indicador);
}
async function altaApi(request: APIRequestContext, datos: object) {
  const r = await request.post('/api/segmentacion/criterios', { headers: adminHeaders, data: datos });
  expect(r.status(), await r.text()).toBe(201); return r.json();
}

test.beforeAll(async ({ request }) => {
  if (!password) throw new Error('Defina E2E_PASSWORD o use herramientas/pruebas-scrum25/ejecutar.ps1');
  const login = await request.post('/api/auth/login', { data: { usuario, password } });
  expect(login.ok()).toBeTruthy(); adminHeaders = { Authorization: `Bearer ${(await login.json()).token}` };
  for (const rol of ['AGENTE_ATENCION', 'GERENTE_COMERCIAL']) {
    const r = await request.post('/api/admin/usuarios', { headers: adminHeaders, data: {
      nombreUsuario: rol.toLowerCase(), nombreCompleto: `Prueba ${rol}`, password, rol,
    } });
    expect(r.status()).toBe(201);
  }
});

test('CA1 y CA3: alta, persistencia tras recarga, edición, auditoría y desactivación', async ({ page, request }, info) => {
  await ingresar(page);
  await page.getByRole('link', { name: 'Criterios de segmentación', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Criterios de segmentación', exact: true })).toBeVisible();
  await formulario(page, 'Compradores habituales de prueba');
  await page.getByLabel('Mínimo', { exact: true }).fill('5');
  await page.getByLabel('Máximo', { exact: true }).fill('9');
  await page.getByRole('button', { name: 'Guardar criterio' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'creado' })).toBeVisible();
  await page.reload();
  const fila = page.getByRole('row').filter({ hasText: 'Compradores habituales de prueba' });
  await expect(fila).toContainText('5 a 9 compras');
  await fila.getByRole('button', { name: 'Editar', exact: true }).click();
  await page.getByLabel('Mínimo', { exact: true }).fill('4');
  await page.getByLabel('Descripción (opcional)').fill('Incluye clientes con cuatro compras vigentes');
  await page.getByRole('button', { name: 'Guardar criterio' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'actualizado' })).toBeVisible();
  await page.getByLabel('Compras vigentes del caso').fill('4');
  await page.getByRole('button', { name: 'Comprobar regla' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'El caso cumple el criterio.' })).toBeVisible();
  await page.getByLabel('Compras vigentes del caso').fill('3');
  await page.getByRole('button', { name: 'Comprobar regla' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'El caso no cumple el criterio.' })).toBeVisible();
  const historia = page.locator('.cambio').filter({ hasText: 'Edición' });
  await expect(historia).toContainText(usuario);
  await expect(historia).toContainText('5 a 9 compras');
  await expect(historia).toContainText('4 a 9 compras');
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: info.outputPath('historial-edicion.png'), fullPage: true });
  await fila.getByRole('button', { name: 'Desactivar', exact: true }).click();
  await page.getByRole('button', { name: 'Confirmar desactivación' }).click();
  await expect(fila).toContainText('Inactivo');
  await expect(page.locator('.cambio').first()).toContainText('Desactivación');
  const listado = await request.get('/api/segmentacion/criterios?activo=true', { headers: adminHeaders });
  expect((await listado.json()).some((c: any) => c.nombre === 'Compradores habituales de prueba')).toBeFalsy();
});

test('CA2: mensajes de rango inválido, vacíos y superposición visibles', async ({ page, request }, info) => {
  const original = await altaApi(request, { nombre: 'Frecuencia ocupada', indicador: 'FRECUENCIA', condicion: 'ENTRE', minimo: 10, maximo: 20 });
  await ingresar(page); await page.goto('/segmentacion/criterios');
  await formulario(page, 'Criterio inválido');
  await page.getByLabel('Mínimo', { exact: true }).fill('8');
  await page.getByLabel('Máximo', { exact: true }).fill('2');
  await page.getByRole('button', { name: 'Guardar criterio' }).click();
  await expect(page.getByRole('alert')).toContainText('mínimo no puede superar');
  await page.getByLabel('Mínimo', { exact: true }).fill('20');
  await page.getByLabel('Máximo', { exact: true }).fill('30');
  await page.getByRole('button', { name: 'Guardar criterio' }).click();
  await expect(page.getByRole('alert')).toContainText('se superpone');
  await expect(page.getByRole('alert')).toContainText('Frecuencia ocupada');
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: info.outputPath('error-superposicion.png'), fullPage: true });
  await page.getByLabel('Nombre del criterio').fill('   ');
  await page.getByRole('button', { name: 'Guardar criterio' }).click();
  await expect(page.getByRole('alert')).toContainText('Indique el nombre');
  const desactivar = await request.patch(`/api/segmentacion/criterios/${original.id}/estado`,
    { headers: adminHeaders, data: { activo: false, version: original.version } });
  expect(desactivar.ok()).toBeTruthy();
});

test('CA1: valor decimal y categoría se guardan y comprueban con el backend', async ({ page }, info) => {
  await ingresar(page); await page.goto('/segmentacion/criterios');
  await formulario(page, 'Valor medio de prueba', 'VALOR');
  await page.getByLabel('Mínimo', { exact: true }).fill('500.01');
  await page.getByLabel('Máximo', { exact: true }).fill('1000');
  await page.getByRole('button', { name: 'Guardar criterio' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'creado' })).toBeVisible();
  await page.getByLabel('Valor acumulado del caso (Bs)').fill('500.01');
  await page.getByRole('button', { name: 'Comprobar regla' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'El caso cumple el criterio.' })).toBeVisible();
  const filaValor = page.getByRole('row').filter({ hasText: 'Valor medio de prueba' });
  await filaValor.getByRole('button', { name: 'Desactivar', exact: true }).click();
  await page.getByRole('button', { name: 'Confirmar desactivación' }).click();
  await expect(filaValor).toContainText('Inactivo');
  await formulario(page, 'Preferencia por electrónica', 'CATEGORIA');
  await page.getByLabel('Categoría consumida', { exact: true }).fill('Electrónica');
  await page.getByRole('button', { name: 'Guardar criterio' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'creado' })).toBeVisible();
  await page.getByLabel('Categorías consumidas (separadas por coma)').fill('Libros, ELECTRONICA');
  await page.getByRole('button', { name: 'Comprobar regla' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'El caso cumple el criterio.' })).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: info.outputPath('criterios-categorias.png'), fullPage: true });
  const filaCategoria = page.getByRole('row').filter({ hasText: 'Preferencia por electrónica' });
  await filaCategoria.getByRole('button', { name: 'Desactivar', exact: true }).click();
  await page.getByRole('button', { name: 'Confirmar desactivación' }).click();
  await expect(filaCategoria).toContainText('Inactivo');
});

test('CA4: agente y gerente no ven la gestión ni pueden crear, editar o desactivar por API', async ({ page, request }) => {
  for (const nombre of ['agente_atencion', 'gerente_comercial']) {
    await ingresar(page, nombre);
    await expect(page.getByRole('link', { name: 'Criterios de segmentación', exact: true })).toHaveCount(0);
    await page.goto('/segmentacion/criterios');
    await expect(page).toHaveURL('/acceso-denegado');
    const login = await request.post('/api/auth/login', { data: { usuario: nombre, password } });
    const headers = { Authorization: `Bearer ${(await login.json()).token}`, 'X-Usuario-Rol': 'ADMINISTRADOR_CRM', 'X-Usuario-Permisos': 'SEGMENTACION_CONFIGURAR' };
    for (const [method, suffix] of [['POST', ''], ['PUT', '/1'], ['PATCH', '/1/estado']]) {
      const r = await request.fetch(`/api/segmentacion/criterios${suffix}`, { method, headers, data: {} });
      expect(r.status()).toBe(403);
    }
    await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  }
  expect((await request.post('/api/segmentacion/criterios', { data: {} })).status()).toBe(401);
});

test('vista móvil: formulario, filtros y acciones accesibles sin desbordar la página', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await ingresar(page); await page.goto('/segmentacion/criterios');
  await formulario(page, 'Prueba móvil');
  await expect(page.getByLabel('Nombre del criterio')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Guardar criterio' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Historial y prueba', exact: true }).first()).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: info.outputPath('movil.png'), fullPage: true });
});
