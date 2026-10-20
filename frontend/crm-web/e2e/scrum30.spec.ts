import { test, expect, Page } from '@playwright/test';

const usuario = process.env['E2E_USUARIO'] ?? 'admin-scrum30';
const password = process.env['E2E_PASSWORD'];
let headers: Record<string, string>;
const ruta = (id = 30001) => `/api/segmentacion/clientes/${id}/historial-segmentos`;
const vista = (id = 30001) => `/clientes/${id}/historial-segmentos`;

async function ingresar(page: Page, nombre = usuario) {
  await page.goto('/login');
  await page.getByLabel('Usuario', { exact: true }).fill(nombre);
  await page.getByLabel('Contraseña', { exact: true }).fill(password!);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL('/');
}

test.beforeAll(async ({ request }) => {
  test.setTimeout(90000);
  if (!password || !process.env['E2E_RABBIT_URL']) throw new Error('Ejecute herramientas/pruebas-scrum30/ejecutar.ps1');
  const login = await request.post('/api/auth/login', { data: { usuario, password } });
  expect(login.status()).toBe(200);
  headers = { Authorization: `Bearer ${(await login.json()).token}` };
  for (const rol of ['AGENTE_ATENCION', 'GERENTE_COMERCIAL']) {
    const alta = await request.post('/api/admin/usuarios', { headers, data: {
      nombreUsuario: rol.toLowerCase(), nombreCompleto: `Prueba ${rol}`, password, rol,
    } });
    expect(alta.status()).toBe(201);
  }
  const habitual = await request.post('/api/segmentacion/criterios', { headers, data: {
    nombre: 'Habituales', indicador: 'FRECUENCIA', condicion: 'ENTRE', minimo: 1, maximo: 4,
  } });
  expect(habitual.status()).toBe(201);
  const criterio = await habitual.json();
  expect((await request.post('/api/segmentacion/criterios', { headers, data: {
    nombre: 'Frecuentes', indicador: 'FRECUENCIA', condicion: 'ENTRE', minimo: 5, maximo: 20,
  } })).status()).toBe(201);
  const rabbitHeaders = { Authorization: `Basic ${Buffer.from(`${process.env['RABBIT_USUARIO']}:${process.env['RABBIT_PASSWORD']}`).toString('base64')}` };
  async function publicar(idCliente: number, frecuencia: number, segundo: number, idEvento = crypto.randomUUID()) {
    const respuesta = await request.post(`${process.env['E2E_RABBIT_URL']}/api/exchanges/%2F/crm.comportamiento.eventos/publish`, {
      headers: rabbitHeaders, data: { properties: { content_type: 'application/json' }, routing_key: 'metricas.actualizadas',
        payload_encoding: 'string', payload: JSON.stringify({ idEvento, idCliente, tipoEventoOrigen: 'COMPRA_CONFIRMADA',
          frecuencia, valor: 100, categorias: [], calculadasEn: new Date(Date.UTC(2026, 9, 19, 12, 0, segundo)).toISOString() }) },
    });
    expect(respuesta.ok(), await respuesta.text()).toBeTruthy();
    expect((await respuesta.json()).routed).toBeTruthy();
    return idEvento;
  }
  for (let i = 0; i < 23; i++) await publicar(30001, i % 2 === 0 ? 2 : 8, i);
  // Reentrega y recálculo sin cambio no deben sumar filas.
  const evento = await publicar(30001, 2, 24);
  await publicar(30001, 2, 24, evento);
  await publicar(30004, 2, 0);
  await publicar(30004, 0, 1);
  await expect.poll(async () => (await (await request.get(ruta(), { headers })).json()).totalElementos).toBe(23);
  await expect.poll(async () => (await (await request.get(ruta(30004), { headers })).json()).totalElementos).toBe(2);
  // Cambiar el criterio vigente no debe cambiar la explicación de las asignaciones previas.
  expect((await request.put(`/api/segmentacion/criterios/${criterio.id}`, { headers, data: {
    nombre: 'Habituales', indicador: 'FRECUENCIA', condicion: 'ENTRE', minimo: 0, maximo: 4, version: criterio.version,
  } })).status()).toBe(200);
});

test('historial real generado por RabbitMQ: paginación, orden y regla original', async ({ page }, info) => {
  await ingresar(page);
  await page.goto(vista());
  await expect(page.getByRole('heading', { name: 'Historial de segmentos', exact: true })).toBeVisible();
  const cambios = page.getByRole('article', { name: 'Cambio de segmento' });
  await expect(cambios).toHaveCount(20);
  await expect(page.getByText('Página 1 de 2 · 23 cambios')).toBeVisible();
  await cambios.first().getByText('Ver regla y métricas del cambio').click();
  await expect(cambios.first()).toContainText('entre 1 y 4');
  await expect(cambios.first()).toContainText('Versión 0');
  await expect(cambios.first()).toContainText('Frecuencia de compra: 2');
  await expect(cambios.first()).toContainText('Frecuentes');
  await expect(cambios.first()).toContainText('Habituales');
  await expect(page.getByRole('button', { name: 'Más recientes', exact: true })).toBeDisabled();
  await page.screenshot({ path: info.outputPath('scrum30-escritorio.png'), fullPage: true });
  await page.getByRole('button', { name: 'Más antiguos', exact: true }).click();
  await expect(cambios).toHaveCount(3);
  await expect(page.getByText('Página 2 de 2 · 23 cambios')).toBeVisible();
  await expect(cambios.last()).toContainText('Primera asignación');
  await expect(page.getByRole('button', { name: 'Más antiguos', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Más recientes', exact: true }).click();
  await expect(cambios).toHaveCount(20);
});

test('retiro de segmento conserva los criterios evaluados', async ({ page }) => {
  await ingresar(page); await page.goto(vista(30004));
  const retiro = page.getByRole('article').first();
  await expect(retiro).toContainText('Retiro de segmento');
  await expect(retiro).toContainText('Ningún criterio vigente coincide');
  await retiro.getByText('Ver regla y métricas del cambio').click();
  await expect(retiro).toContainText('Criterios evaluados al retirar el segmento');
  await expect(retiro).toContainText('Habituales');
});

test('consulta un historial vacío y un registro migrado con regla incompleta', async ({ page }) => {
  await ingresar(page); await page.goto(vista(30002));
  await expect(page.getByRole('status')).toContainText('todavía no tiene cambios de segmento');
  await page.goto(vista(30003));
  await expect(page.getByRole('article')).toHaveCount(1);
  await expect(page.getByText('Registro anterior:', { exact: false })).toBeVisible();
  await expect(page.getByRole('article')).toContainText('Legado');
});

test('gerente consulta; agente no accede; ni el administrador puede escribir', async ({ page, request }) => {
  await ingresar(page, 'gerente_comercial'); await page.goto(vista());
  await expect(page.getByRole('article')).toHaveCount(20);
  for (const method of ['POST', 'PUT', 'PATCH', 'DELETE']) {
    expect((await request.fetch(ruta(), { method, headers })).status()).toBe(403);
  }
  expect((await request.get(ruta())).status()).toBe(401);
  const login = await request.post('/api/auth/login', { data: { usuario: 'agente_atencion', password } });
  const agenteHeaders = { Authorization: `Bearer ${(await login.json()).token}`,
    'X-Usuario': 'admin', 'X-Usuario-Permisos': 'SEGMENTOS_CONSULTAR' };
  expect((await request.get(ruta(), { headers: agenteHeaders })).status()).toBe(403);
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  await ingresar(page, 'agente_atencion'); await page.goto(vista());
  await expect(page).toHaveURL('/acceso-denegado');
  await expect(page.getByRole('tab', { name: 'Historial de segmentos' })).toHaveCount(0);
});

test('muestra error recuperable y permite reintentar la consulta', async ({ page }) => {
  await ingresar(page);
  await page.route('**/api/segmentacion/clientes/30001/historial-segmentos?*', route => route.fulfill({
    status: 503, contentType: 'application/json', body: JSON.stringify({ mensaje: 'Servicio temporalmente no disponible' }),
  }));
  await page.goto(vista());
  await expect(page.getByRole('alert')).toContainText('Servicio temporalmente no disponible');
  await page.unroute('**/api/segmentacion/clientes/30001/historial-segmentos?*');
  await page.getByRole('button', { name: 'Reintentar' }).click();
  await expect(page.getByRole('article')).toHaveCount(20);
});

test('rechaza un identificador inválido sin mostrar datos de otro cliente', async ({ page }) => {
  await ingresar(page); await page.goto('/clientes/invalido/historial-segmentos');
  await expect(page.getByRole('alert')).toContainText('identificador del cliente no es válido');
  await expect(page.getByRole('article')).toHaveCount(0);
});

test('vista móvil sin desbordamiento y navegación entre pestañas', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await ingresar(page); await page.goto(vista(30004));
  await expect(page.getByRole('article')).toHaveCount(2);
  await expect(page.getByRole('tab', { name: 'Historial de segmentos', exact: true })).toHaveClass(/activa/);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: info.outputPath('scrum30-movil.png'), fullPage: true });
});
