import { test, expect, Page, APIRequestContext } from '@playwright/test';

/**
 * SCRUM-478 · Panel de indicadores en el navegador con datos reales, contra el entorno de
 * herramientas/pruebas-reportes/ejecutar.sh: tres clientes con compras de septiembre de 2026 (activos en
 * los últimos 90 días), «Alto valor» con Ana y Bruno, y 110 puntos (40 + 20 + 50, a 1 punto por Bs 10)
 * sembrados por eventos.
 */
const admin = process.env['E2E_USUARIO'] ?? 'admin-reportes';
const password = process.env['E2E_PASSWORD'];
const mq = { url: process.env['E2E_MQ_URL'], usuario: process.env['E2E_MQ_USUARIO'], password: process.env['E2E_MQ_PASSWORD'] };
const RUTA_API = '/api/segmentacion/reportes/panel';

async function ingresar(page: Page, usuario = admin) {
  await page.goto('/login');
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(password!);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL('/');
}

async function token(page: Page): Promise<string> {
  return page.evaluate(() => JSON.parse(sessionStorage.getItem('crm.sesion')!).token);
}

async function puntosDelPanel(request: APIRequestContext, headers: Record<string, string>): Promise<number> {
  return (await (await request.get(RUTA_API, { headers })).json()).puntos.puntos;
}

/** Publica un evento como Marketplace y Ventas, igual que herramientas/simulador-eventos/publicar.py. */
async function publicar(request: APIRequestContext, ruta: string, evento: object) {
  const respuesta = await request.post(`${mq.url}/api/exchanges/%2F/ventas.eventos/publish`, {
    headers: { Authorization: `Basic ${Buffer.from(`${mq.usuario}:${mq.password}`).toString('base64')}` },
    data: { properties: { content_type: 'application/json', delivery_mode: 2 }, routing_key: ruta,
            payload: JSON.stringify(evento), payload_encoding: 'string' },
  });
  expect(respuesta.ok()).toBeTruthy();
  expect((await respuesta.json()).routed).toBe(true);
}

test.beforeAll(async ({ request }) => {
  if (!password || !mq.url) throw new Error('Ejecute herramientas/pruebas-reportes/ejecutar.sh para preparar el entorno aislado.');
  // Los movimientos de puntos se procesan en paralelo a las compras: se espera a que estén los sembrados.
  // "Al menos": si una prueba falla, Playwright repite este paso en otro worker después de que la del
  // criterio 2 ya sumó puntos.
  const login = await request.post('/api/auth/login', { data: { usuario: admin, password } });
  const headers = { Authorization: `Bearer ${(await login.json()).token}` };
  await expect.poll(() => puntosDelPanel(request, headers), { timeout: 30_000 }).toBeGreaterThanOrEqual(110);
});

// --- Criterio 1: clientes activos, distribución por segmento y puntos acumulados ---

test('Gerente: el panel muestra clientes activos, distribución por segmento y puntos', async ({ page }, info) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await page.getByRole('link', { name: 'Panel de indicadores' }).click();
  await expect(page).toHaveURL('/analisis/panel');

  const activos = page.getByRole('region', { name: 'Clientes activos' });
  await expect(activos).toContainText('3');
  await expect(activos).toContainText('de 3 clientes con compras (100.00 %)');
  await expect(activos).toContainText('últimos 90 días');

  const segmentos = page.getByRole('region', { name: 'Distribución por segmento' });
  await expect(segmentos.locator('.nombre')).toHaveText(['Alto valor', 'Sin segmento']);
  await expect(segmentos.locator('li').first()).toContainText('2 · 66.67 %');
  await expect(segmentos.locator('li').last()).toContainText('1 · 33.33 %');

  const puntos = page.getByRole('region', { name: 'Puntos acumulados' });
  await expect(puntos).toContainText('110');
  await expect(puntos).toContainText('2 clientes con puntos');
  await page.screenshot({ path: info.outputPath('panel-indicadores.png'), fullPage: true });
});

test('El panel coincide con el reporte de distribución por segmento', async ({ page, request }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  const headers = { Authorization: `Bearer ${await token(page)}` };
  const panel = await (await request.get(RUTA_API, { headers })).json();
  const reporte = await (await request.get('/api/segmentacion/reportes/distribucion', { headers })).json();
  expect(panel.segmentos).toEqual(reporte.segmentos);
  expect(panel.clientes).toBe(reporte.total);
});

// --- Criterio 2: con cambios en los datos, al recargar los indicadores se actualizan ---

test('Al llegar puntos nuevos, Actualizar muestra el total nuevo', async ({ page, request }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  const headers = { Authorization: `Bearer ${await token(page)}` };
  const antes = await puntosDelPanel(request, headers);
  await page.goto('/analisis/panel');
  const cifra = page.getByRole('region', { name: 'Puntos acumulados' }).locator('.cifra');
  await expect(cifra).toHaveText(antes.toLocaleString('en-US'));

  // Carla compra por Bs 80: 8 puntos más. La transacción es nueva en cada corrida, porque un
  // movimiento repetido se descarta (idempotencia) y no sumaría nada.
  await publicar(request, 'puntos.movimiento', {
    idEvento: crypto.randomUUID(), tipoEvento: 'MOVIMIENTO_PUNTOS', fechaEmision: '2026-09-12T10:05:00-04:00',
    movimiento: { idTransaccion: `V-R3-E2E-${crypto.randomUUID().slice(0, 8)}`, idCliente: 'CLI-R3',
                  tipoMovimiento: 'ACUMULACION', valorConsumido: 80.0, fecha: '2026-09-12T10:05:00-04:00' },
  });
  await expect.poll(() => puntosDelPanel(request, headers), { timeout: 30_000 }).toBe(antes + 8);

  // Sin recargar, la pantalla sigue mostrando lo anterior; al pulsar Actualizar cambia.
  await expect(cifra).toHaveText(antes.toLocaleString('en-US'));
  await page.getByRole('button', { name: 'Actualizar' }).click();
  await expect(cifra).toHaveText((antes + 8).toLocaleString('en-US'));
  await expect(page.getByRole('region', { name: 'Puntos acumulados' })).toContainText('3 clientes con puntos');

  // Recargar la página también lo refleja.
  await page.reload();
  await expect(page.getByRole('region', { name: 'Puntos acumulados' }).locator('.cifra'))
    .toHaveText((antes + 8).toLocaleString('en-US'));
});

// --- Criterio 3: un rol sin permiso no abre el panel ---

test('Agente: sin enlace, URL directa denegada y API 403', async ({ page, request }) => {
  await ingresar(page, 'reportes-agente-atencion');
  await expect(page.getByRole('link', { name: 'Panel de indicadores' })).toHaveCount(0);
  await page.goto('/analisis/panel');
  await expect(page).toHaveURL('/acceso-denegado');
  const headers = { Authorization: `Bearer ${await token(page)}` };
  expect((await request.get(RUTA_API, { headers })).status()).toBe(403);
  // Las consultas internas del panel tampoco se alcanzan por el Gateway.
  expect((await request.get('/api/comportamiento/reportes/clientes-activos', { headers })).status()).toBe(403);
  expect((await request.get('/api/fidelizacion/reportes/puntos', { headers })).status()).toBe(403);
});

test('Administrador: puede abrir el panel', async ({ page }) => {
  await ingresar(page);
  await page.getByRole('link', { name: 'Panel de indicadores' }).click();
  await expect(page.getByRole('region', { name: 'Clientes activos' })).toBeVisible();
});

test('Sin sesión: va al login y la API responde 401', async ({ page, request }) => {
  await page.goto('/analisis/panel');
  await expect(page).toHaveURL('/login');
  expect((await request.get(RUTA_API)).status()).toBe(401);
});
