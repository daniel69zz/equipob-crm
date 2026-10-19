import { test, expect, Page } from '@playwright/test';

const admin = process.env['E2E_USUARIO'] ?? 'admin-scrum43';
const password = process.env['E2E_PASSWORD'];
const ruta = '/api/comportamiento/clientes/4301/preferencias';
let headers: Record<string, string>;

async function ingresar(page: Page, usuario = admin) {
  await page.goto('/login');
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(password!);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL('/');
}

test.beforeAll(async ({ request }) => {
  if (!password) throw new Error('Ejecute herramientas/pruebas-scrum43/ejecutar.ps1 para preparar el entorno aislado.');
  const login = await request.post('/api/auth/login', { data: { usuario: admin, password } });
  expect(login.ok()).toBeTruthy();
  headers = { Authorization: `Bearer ${(await login.json()).token}` };
  for (const rol of ['AGENTE_ATENCION', 'GERENTE_COMERCIAL']) {
    const creado = await request.post('/api/admin/usuarios', { headers, data: {
      nombreUsuario: `scrum43-${rol.toLowerCase()}`, nombreCompleto: `Prueba ${rol}`, password, rol,
    } });
    expect(creado.status()).toBe(201);
  }
});

test('Administrador: buscar, consultar, conservar orden y recargar sin escritura', async ({ page, request }, info) => {
  const antes = await (await request.get(ruta, { headers })).json();
  const historialAntes = await (await request.get(`${ruta}/actualizaciones`, { headers })).json();
  await ingresar(page);
  await page.goto('/clientes');
  await page.getByLabel('Tipo de documento').selectOption('CI');
  await page.getByLabel('Número de documento').fill('4301');
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  await page.getByRole('row').filter({ hasText: 'Ana Preferencias' }).getByRole('link', { name: 'Preferencias', exact: true }).click();
  await expect(page).toHaveURL('/clientes/4301/preferencias');
  await expect(page.locator('tbody th')).toHaveText(['Electrónica', 'Hogar', 'Deportes']);
  await expect(page.getByRole('region', { name: 'Preferencia principal' })).toContainText('Electrónica');
  await expect(page.getByText('Última actualización:')).toBeVisible();
  await page.getByRole('button', { name: 'Volver a consultar' }).click();
  await expect(page.locator('tbody th')).toHaveText(['Electrónica', 'Hogar', 'Deportes']);
  await page.reload();
  await expect(page.locator('tbody th')).toHaveCount(3);
  expect(await (await request.get(ruta, { headers })).json()).toEqual(antes);
  expect(await (await request.get(`${ruta}/actualizaciones`, { headers })).json()).toEqual(historialAntes);
  await page.screenshot({ path: info.outputPath('preferencias-desktop.png'), fullPage: true });
});

test('Agente: consulta permitida y una sola categoría', async ({ page }) => {
  await ingresar(page, 'scrum43-agente_atencion');
  await page.goto('/clientes/4304/preferencias');
  await expect(page.locator('tbody th')).toHaveText(['Alimentos']);
  await expect(page.getByRole('tab', { name: 'Preferencias', exact: true })).toBeVisible();
});

for (const [id, mensaje] of [[4302, 'Sin preferencias registradas'], [4303, 'Preferencias aún no disponibles']] as const) {
  test(`Perfil ${id}: estado vacío sin error`, async ({ page }) => {
    await ingresar(page);
    await page.goto(`/clientes/${id}/preferencias`);
    await expect(page.getByRole('heading', { name: mensaje })).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
    await expect(page.getByRole('table')).toHaveCount(0);
  });
}

test('Gerente: enlace oculto, URL directa denegada y API 403', async ({ page, request }) => {
  await ingresar(page, 'scrum43-gerente_comercial');
  await page.goto('/clientes');
  await expect(page.getByRole('row').filter({ hasText: 'Ana Preferencias' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Preferencias', exact: true })).toHaveCount(0);
  await page.goto('/clientes/4301/preferencias');
  await expect(page).toHaveURL('/acceso-denegado');
  const sesion = await page.evaluate(() => JSON.parse(sessionStorage.getItem('crm.sesion')!));
  expect((await request.get(ruta, { headers: { Authorization: `Bearer ${sesion.token}` } })).status()).toBe(403);
});

test('Sin sesión: login y API 401', async ({ page, request }) => {
  await page.goto('/clientes/4301/preferencias');
  await expect(page).toHaveURL('/login');
  expect((await request.get(ruta)).status()).toBe(401);
});

for (const [rol, permisos] of [
  ['ADMINISTRADOR_CRM', []], ['AGENTE_ATENCION', []],
  ['GERENTE_COMERCIAL', ['FICHA_INTEGRAL_CONSULTAR']],
  ['ANALISTA_DATOS', ['FICHA_INTEGRAL_CONSULTAR']],
] as [string, string[]][]) {
  test(`Guard exige rol y permiso: ${rol} con ${permisos.length} permisos`, async ({ page }) => {
    await ingresar(page);
    // Simula una sesión local con permisos distintos; el Gateway tiene pruebas independientes.
    await page.evaluate(({ rol, permisos }) => {
      const sesion = JSON.parse(sessionStorage.getItem('crm.sesion')!);
      sessionStorage.setItem('crm.sesion', JSON.stringify({ ...sesion, rol, permisos }));
    }, { rol, permisos });
    await page.route('**/api/auth/yo', route => route.fulfill({ json: { usuario: admin, rol, permisos } }));
    let consultas = 0;
    page.on('request', req => { if (req.url().endsWith(ruta)) consultas++; });
    await page.goto('/clientes/4301/preferencias');
    await expect(page).toHaveURL('/acceso-denegado');
    expect(consultas).toBe(0);
  });
}

test('Cambiar de cliente en la misma vista cancela la consulta anterior', async ({ page }) => {
  await ingresar(page);
  await page.goto('/clientes/4301/preferencias');
  await expect(page.locator('tbody th')).toHaveCount(3);
  let liberar!: () => void;
  const espera = new Promise<void>(resolve => liberar = resolve);
  let solicitada!: () => void;
  const solicitud = new Promise<void>(resolve => solicitada = resolve);
  await page.route('**/api/comportamiento/clientes/4304/preferencias', async route => {
    solicitada();
    await espera;
    await route.fulfill({ json: { clienteId: 4304, construido: true, sinPreferencias: false,
      categorias: [{ posicion: 1, categoria: 'Alimentos', compras: 1, unidades: 2, monto: 50 }] } });
  });
  const navegar = async (id: number) => page.evaluate(id => {
    history.pushState({}, '', `/clientes/${id}/preferencias`);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, id);
  await navegar(4304);
  await solicitud;
  await expect(page.getByRole('status')).toContainText('Consultando preferencias');
  await expect(page.getByRole('table')).toHaveCount(0);
  await navegar(4301);
  await expect(page.locator('tbody th')).toHaveText(['Electrónica', 'Hogar', 'Deportes']);
  liberar();
  await expect(page.getByRole('heading', { name: 'Electrónica', exact: true })).toBeVisible();
  await expect(page.getByText('Alimentos', { exact: true })).toHaveCount(0);
});

for (const [id, mensaje] of [['0', 'El identificador del cliente no es válido.'], ['abc', 'El identificador del cliente no es válido.'],
  ['999999', 'No se encontró el cliente solicitado.'], ['4305', 'El cliente fue consolidado; utilice el cliente 4301']] as const) {
  test(`Identidad ${id}: error visible`, async ({ page }) => {
    await ingresar(page);
    await page.goto(`/clientes/${id}/preferencias`);
    await expect(page.getByRole('alert')).toContainText(mensaje);
    await expect(page.getByRole('table')).toHaveCount(0);
  });
}

test('Fallo 503: limpia resultado anterior y permite reintentar', async ({ page }) => {
  await ingresar(page);
  await page.goto('/clientes/4301/preferencias');
  await expect(page.locator('tbody th')).toHaveCount(3);
  await page.route(`**${ruta}`, route => route.fulfill({ status: 503, json: { mensaje: 'No disponible' } }));
  await page.getByRole('button', { name: 'Volver a consultar' }).click();
  await expect(page.getByRole('alert')).toContainText('No se pudieron consultar');
  await expect(page.getByRole('table')).toHaveCount(0);
  await page.unroute(`**${ruta}`);
  await page.getByRole('button', { name: 'Reintentar consulta' }).click();
  await expect(page.locator('tbody th')).toHaveCount(3);
});

test('Permiso revocado: 403 al recargar elimina los resultados anteriores', async ({ page }) => {
  await ingresar(page);
  await page.goto('/clientes/4301/preferencias');
  await expect(page.locator('tbody th')).toHaveCount(3);
  await page.route(`**${ruta}`, route => route.fulfill({ status: 403, json: {} }));
  await page.getByRole('button', { name: 'Volver a consultar' }).click();
  await expect(page.getByRole('alert')).toContainText('No tiene permiso');
  await expect(page.getByRole('table')).toHaveCount(0);
});

test('Vista móvil conserva navegación y resultados', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await ingresar(page);
  await page.goto('/clientes/4301/preferencias');
  await expect(page.locator('tbody th')).toHaveCount(3);
  await expect(page.getByRole('tab', { name: 'Preferencias', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: info.outputPath('preferencias-mobile.png'), fullPage: true });
});
