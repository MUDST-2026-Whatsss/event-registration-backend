// Run only against a disposable image-delivery project, never the team's database.
import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const project = process.env.DELIVERY_TEST_PROJECT
if (!project?.startsWith('delivery-')) throw new Error('DELIVERY_TEST_PROJECT must start with delivery-')
const directory = resolve(process.env.DELIVERY_DIRECTORY ?? 'target/docker-delivery')
const docker = process.env.DOCKER_CLI ?? (process.platform === 'win32'
  ? resolve(process.env.ProgramFiles ?? 'C:/Program Files', 'Docker/Docker/resources/bin/docker.exe')
  : 'docker')
const compose = [
  'compose', '-p', project, '-f', resolve(directory, 'docker-compose.yml'),
  '--env-file', resolve(directory, '.env'),
]
function projectUrl(service, containerPort, override) {
  const published = execFileSync(docker, [...compose, 'port', service, String(containerPort)], { encoding: 'utf8' })
  const port = published.split(/\r?\n/).find((line) => line.trim())?.match(/:(\d+)\s*$/)?.[1]
  assert.ok(port, `No published ${service} port for the disposable project`)
  const url = new URL(override ?? `http://127.0.0.1:${port}`)
  assert.ok(['localhost', '127.0.0.1'].includes(url.hostname) && url.port === port && url.protocol === 'http:',
    `Refusing ${service} URL outside the named disposable project's published port`)
  return url.origin
}
const web = projectUrl('frontend', 80, process.env.DELIVERY_WEB_URL)
const api = projectUrl('api', 8080, process.env.DELIVERY_API_URL)

for (const [url, expected] of [[`${api}/api/health`, 'OK'], [`${web}/api/health`, 'OK']]) {
  const response = await fetch(url)
  assert.equal(response.status, 200, `Health status: ${url}`)
  assert.equal((await response.text()).trim(), expected, `Health response: ${url}`)
}
const page = await fetch(`${web}/admin/dashboard`)
assert.equal(page.status, 200)
assert.match(await page.text(), /<div id="app">/)

const cookies = new Map()
async function request(path, method = 'GET', body) {
  const headers = { Accept: 'application/json', Origin: new URL(web).origin }
  if (method !== 'GET') {
    const csrf = await request('/auth/csrf')
    headers['X-XSRF-TOKEN'] = csrf.token
    headers['Content-Type'] = 'application/json'
  }
  headers.Cookie = [...cookies].map(([key, value]) => `${key}=${value}`).join('; ')
  const response = await fetch(`${web}/api/v1${path}`, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  })
  for (const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(';', 1)[0]
    const index = pair.indexOf('=')
    cookies.set(pair.slice(0, index), pair.slice(index + 1))
  }
  const payload = await response.json()
  assert.ok(response.ok, `${method} ${path}: ${response.status} ${payload.code ?? ''}`)
  return payload
}

const email = `delivery-${Date.now()}-${randomBytes(4).toString('hex')}@example.test`
const password = `Aa1!${randomBytes(24).toString('base64url')}`
await request('/auth/register', 'POST', {
  email, password, firstName: 'Delivery', lastName: 'Test', phoneNumber: '0812345678',
})

// Execute the actual guarded operator procedure included in the teacher handout.
const setup = readFileSync(resolve(directory, 'README.md'), 'utf8').match(/BEGIN;\r?\nDO \$setup\$[\s\S]*?COMMIT;/)
assert.ok(setup, 'Teacher handout must include the first-administrator SQL procedure')
const sql = setup[0].replace('teacher@example.com', email)
const grant = [...compose, 'exec', '-T', 'postgres',
  'sh', '-c', 'exec psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --set=ON_ERROR_STOP=1',
]
execFileSync(docker, grant, { input: sql, stdio: ['pipe', 'pipe', 'pipe'] })
assert.throws(() => execFileSync(docker, grant, { input: sql, stdio: ['pipe', 'pipe', 'pipe'] }),
  (error) => error.stderr?.toString().includes('An active super administrator already exists'))

await request('/auth/login', 'POST', { email, password })
await request('/auth/select-role', 'POST', { role: 'ADMIN' })
const stats = await request('/admin/events/stats')
assert.equal(typeof stats.total, 'number')
const recent = await request('/admin/registrations/recent?size=5')
assert.ok(Array.isArray(recent.content))
await request('/auth/select-role', 'POST', { role: 'SUPER_ADMIN' })
const users = await request('/admin/users?size=5')
assert.ok(Array.isArray(users.content))
await request('/auth/logout', 'POST')
console.log('Image-only delivery passed: frontend, API proxy, CSRF, registration, login, roles, admin dashboard and users.')
