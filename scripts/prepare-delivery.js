import { randomBytes } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const args = process.argv.slice(2)
const options = {}
for (let index = 0; index < args.length; index += 2) {
  const key = args[index]
  if (!['--output', '--tag', '--frontend-sha'].includes(key) || !args[index + 1]) {
    throw new Error('Usage: node scripts/prepare-delivery.js --output DIRECTORY --tag IMAGE_TAG --frontend-sha SHA')
  }
  options[key] = args[index + 1]
}
if (!options['--output'] || !options['--tag'] || !options['--frontend-sha']) {
  throw new Error('--output, --tag, and --frontend-sha are required')
}
const tag = options['--tag']
if (!/^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$/.test(tag)) throw new Error('Invalid image tag')
const output = resolve(options['--output'])
if (existsSync(join(output, '.env'))) {
  throw new Error('Refusing to overwrite an existing .env: keep secrets stable for an existing database')
}
let environment = readFileSync(join(root, '.env.example'), 'utf8')
const values = {
  IMAGE_TAG: tag,
  POSTGRES_PASSWORD: randomBytes(32).toString('hex'),
  JWT_SECRET: randomBytes(48).toString('base64'),
  STORAGE_ACCESS_KEY: 'event_minio_admin',
  STORAGE_SECRET_KEY: randomBytes(32).toString('hex'),
  MAIL_USER: '',
  MAIL_PASSWORD: '',
  MAIL_FROM_EMAIL: 'events@example.test',
}
for (const [key, value] of Object.entries(values)) {
  const expression = new RegExp(`^${key}=.*$`, 'm')
  if (!expression.test(environment)) throw new Error(`Missing template variable: ${key}`)
  environment = environment.replace(expression, `${key}=${value}`)
}
mkdirSync(output, { recursive: true })
writeFileSync(join(output, 'docker-compose.yml'), readFileSync(join(root, 'docker-compose.yml')))
writeFileSync(join(output, '.env'), environment, { mode: 0o600 })
writeFileSync(join(output, 'README.md'), readFileSync(join(root, 'docs/docker-delivery.md')))
writeFileSync(join(output, 'versions.txt'), `Image tag: ${tag}\nBackend source: ${process.env.GITHUB_SHA ?? 'working-tree (uncommitted preparation)'}\nFrontend source: ${options['--frontend-sha']}\nMinIO source: RELEASE.2025-10-15T17-29-55Z\n`)
console.log(`Prepared Docker delivery in ${output}; generated secrets were not printed.`)
