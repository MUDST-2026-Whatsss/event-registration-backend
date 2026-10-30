delete process.env.ELECTRON_RUN_AS_NODE
import { readdirSync, readFileSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'node:net'
import { spawn } from 'node:child_process'
import cypress from 'cypress'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const args = process.argv.slice(2)
const fileGroups = { auth: 'login', navigation: 'events', '01-health': 'health', '02-csrf-and-security': 'security', '03-auth-lifecycle': 'login', '04-event-categories': 'events', '05-event-administration': 'event', '06-registration-and-check-in': 'registration', '07-access-control': 'access_control' }
const files = readdirSync(resolve(root, 'cypress/e2e')).filter(name => name.endsWith('.cy.js')).sort().map(file => {
  const source = readFileSync(resolve(root, 'cypress/e2e', file), 'utf8')
  const name = file.replace('.cy.js', '')
  return { group: fileGroups[name] ?? name.replaceAll('-', '_'), spec: `cypress/e2e/${file}`,
    cases: [...source.matchAll(/\bscenario\(\s*['"]([a-z][a-z0-9_]*)['"]/g)].map(match => match[1]) }
})
const cases = files.flatMap(group => group.cases)
if (new Set(cases).size !== cases.length) throw new Error('Duplicate E2E case names.')
const groups = new Map()
function addGroup(name, file, names) {
  const group = groups.get(name) ?? { group: name, specs: new Set(), cases: new Set() }
  group.specs.add(file.spec)
  names.forEach(name => group.cases.add(name))
  groups.set(name, group)
}
const functions = ['offline_check_in', 'live_seats', 'change_password', 'forgot_password', 'reset_password', 'select_role', 'guest_registration', 'registration', 'check_in', 'login', 'logout', 'profile', 'register', 'events', 'event', 'health', 'security', 'session', 'analytics']
for (const file of files) {
  addGroup(file.group, file, file.cases)
  for (const name of file.cases) {
    const feature = functions.find(prefix => name === prefix || name.startsWith(`${prefix}_`))
    if (feature) addGroup(feature, file, [name])
  }
}
const catalog = [...groups.values()].map(group => ({ ...group, specs: [...group.specs], cases: [...group.cases] }))
const specIndex = args.indexOf('--spec')
const spec = specIndex < 0 ? undefined : args[specIndex + 1]
if (specIndex >= 0 && (!spec || spec.startsWith('--'))) throw new Error('--spec needs a value')
const names = args.filter((arg, index) => !arg.startsWith('--') && (specIndex < 0 || index !== specIndex + 1))
if (names.length > 1) throw new Error('Choose one function/case, e.g. npm run e2e -- login_failed')
for (const arg of args.filter(arg => arg.startsWith('--'))) {
  if (!['--open', '--serve', '--list', '--spec'].includes(arg)) throw new Error(`Unknown option: ${arg}`)
}
if (args.includes('--list')) {
  for (const group of catalog) console.log(`${group.group}\n  ${group.cases.join('\n  ')}`)
  process.exit(0)
}
const target = names[0]
const selected = target ? catalog.find(group => group.group === target) ?? (() => {
  const file = files.find(file => file.cases.includes(target))
  return file ? { specs: [file.spec], cases: [target] } : null
})() : null
if (target && !selected) throw new Error(`Unknown E2E function/case: ${target}. Run npm run e2e:list.`)
if (target && spec) throw new Error('Use a function/case name OR --spec, not both.')
// Cypress 16 removed Cypress.env(); the backend still uses Cypress 14.
const major = Number(JSON.parse(readFileSync(resolve(root, 'node_modules/cypress/package.json'), 'utf8')).version.split('.')[0])
const options = major >= 16 ? { expose: { test_cases: selected?.cases ?? [] } } : { env: { test_cases: selected?.cases ?? [] } }
const selectedSpec = selected?.specs ?? spec
if (selectedSpec) options.config = { specPattern: selectedSpec }
let server
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

async function startFrontend() {
  const vite = resolve(root, 'node_modules/vite/bin/vite.js')
  if (!existsSync(vite)) throw new Error('Vite is missing. Run npm ci in the frontend repository.')
  const requested = process.env.E2E_PORT ? Number(process.env.E2E_PORT) : 0
  if (!Number.isInteger(requested) || requested < 0 || requested > 65535) throw new Error('Invalid E2E_PORT.')
  const probe = createServer()
  await new Promise((resolve, reject) => { probe.once('error', reject); probe.listen(requested, '127.0.0.1', resolve) })
  const port = probe.address().port
  await new Promise(resolve => probe.close(resolve))
  const baseUrl = `http://127.0.0.1:${port}`
  let output = ''
  let spawnError
  server = spawn(process.execPath, [vite, '--host', '127.0.0.1', '--port', String(port), '--strictPort'], {
    cwd: root, stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true,
  })
  server.stdout.on('data', chunk => { output = (output + chunk).slice(-4000) })
  server.stderr.on('data', chunk => { output = (output + chunk).slice(-4000) })
  server.on('error', error => { spawnError = error })
  for (let attempt = 0; attempt < 120; attempt++) {
    if (spawnError) throw spawnError
    if (server.exitCode !== null) throw new Error(`Vite stopped before startup: ${output}`)
    try {
      const response = await fetch(baseUrl, { signal: AbortSignal.timeout(1000) })
      if (response.ok && (await response.text()).includes('<div id="app">')) {
        options.config = { ...options.config, baseUrl }
        console.log(`E2E frontend: ${baseUrl} (separate test server)`)
        return
      }
    } catch { /* Our own Vite is still starting. */ }
    await sleep(250)
  }
  throw new Error(`Vite did not become ready: ${output}`)
}

try {
  if (args.includes('--serve')) await startFrontend()
  if (args.includes('--open')) await cypress.open(options)
  else {
    const result = await cypress.run({ ...options, ...(selectedSpec ? { spec: selectedSpec } : {}) })
    for (const run of result.runs ?? []) for (const test of run.tests ?? []) {
      if (test.state === 'failed') console.error(`${test.title.join(' > ')}: ${test.displayError}`)
    }
    if (result.status === 'failed' || result.failures || result.totalFailed > 0 || !(result.totalTests > 0)) process.exitCode = 1
  }
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
} finally {
  if (server && server.exitCode === null) {
    server.kill()
    await Promise.race([new Promise(resolve => server.once('exit', resolve)), sleep(2000)])
  }
}
