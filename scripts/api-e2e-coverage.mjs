// Reports which REST endpoints the Cypress API suite calls.
// Endpoints come from the Spring controllers; calls come from cypress/e2e and cypress/support.
// Usage: node scripts/api-e2e-coverage.mjs [--markdown]
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const markdown = process.argv.includes('--markdown')

function files(dir, ext) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return files(path, ext)
    return path.endsWith(ext) ? [path] : []
  })
}

// "/events/{eventId}/x", "/events/${event.eventId}/x" and "/events/0000.../x" all become "/events/:/x"
function normalize(path) {
  return path
    .split('?')[0]
    .replace(/\$\{[^}]*\}/g, ':')
    .replace(/\{[^}]*\}/g, ':')
    .replace(/\/[0-9a-f-]{36}(?=\/|$)/gi, '/:')
    .replace(/\/+$/, '') || '/'
}

const mappingRe = /@(Get|Post|Put|Patch|Delete|Request)Mapping(?:\(([^)]*)\))?/g
const endpoints = []
for (const file of files('src/main/java', 'Controller.java')) {
  const source = readFileSync(file, 'utf8')
  const classStart = source.search(/\bclass\s+\w+/)
  let base = ''
  for (const match of source.matchAll(mappingRe)) {
    const [, verb, args = ''] = match
    const path = (args.match(/"([^"]*)"/) || [, ''])[1]
    if (verb === 'Request' && match.index < classStart) {
      base = path
      continue
    }
    if (verb === 'Request') continue
    endpoints.push({
      method: verb.toUpperCase(),
      path: base + path,
      controller: file.split('/').pop().replace('.java', ''),
    })
  }
}

const calls = new Set()
const add = (method, url) => calls.add(`${method.toUpperCase()} ${normalize(url)}`)
const url = '[\'"`](/api[^\'"`]*)[\'"`]'
for (const file of [...files('cypress/e2e', '.js'), ...files('cypress/support', '.js')]) {
  const source = readFileSync(file, 'utf8')
  for (const m of source.matchAll(new RegExp(`cy\\.apiSend\\(\\s*'(\\w+)'\\s*,\\s*${url}`, 'g'))) add(m[1], m[2])
  for (const m of source.matchAll(new RegExp(`cy\\.request\\(\\s*${url}`, 'g'))) add('GET', m[1])
  // cy.request({ method, url }) and fetch(url, { method }) in either property order
  for (const m of source.matchAll(/(?:cy\.request\(\s*\{|fetch\()([\s\S]{0,300}?)\}\s*\)/g)) {
    const u = m[1].match(new RegExp(url))
    if (!u) continue
    const method = (m[1].match(/method:\s*'(\w+)'/) || [, 'GET'])[1]
    add(method, u[1])
  }
  for (const m of source.matchAll(new RegExp(`(?:cy\\.apiRegister|cy\\.apiLogin|getCsrfToken)`, 'g'))) {
    if (m[0] === 'cy.apiRegister') add('POST', '/api/v1/auth/register')
    if (m[0] === 'cy.apiLogin') add('POST', '/api/v1/auth/login')
    if (m[0] === 'getCsrfToken') add('GET', '/api/v1/auth/csrf')
  }
  // A forEach over a list of export URLs
  for (const m of source.matchAll(/\[((?:\s*'\/api[^']*'\s*,?)+)\]\s*\.forEach/g)) {
    for (const u of m[1].matchAll(/'(\/api[^']*)'/g)) add('GET', u[1])
  }
}

const rows = endpoints.map((e) => ({ ...e, covered: calls.has(`${e.method} ${normalize(e.path)}`) }))
const covered = rows.filter((r) => r.covered).length
const percent = ((100 * covered) / rows.length).toFixed(1)

if (markdown) {
  console.log('## API E2E endpoint coverage (Cypress)\n')
  console.log(`**${covered} / ${rows.length} endpoints (${percent}%)** are called by the Cypress API suite.\n`)
  const missing = rows.filter((r) => !r.covered)
  if (missing.length) {
    console.log('<details><summary>Endpoints without an E2E call</summary>\n')
    console.log('| Method | Path | Controller |\n|---|---|---|')
    for (const r of missing) console.log(`| ${r.method} | \`${r.path}\` | ${r.controller} |`)
    console.log('\n</details>')
  }
} else {
  for (const r of rows) console.log(`${r.covered ? '✔' : '✘'} ${r.method.padEnd(6)} ${r.path}`)
  console.log(`\n${covered}/${rows.length} endpoints covered (${percent}%)`)
}
