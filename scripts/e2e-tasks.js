import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { join } from 'node:path'

function dockerExecutable() {
  if (process.env.DOCKER_CLI_PATH) return process.env.DOCKER_CLI_PATH
  if (process.platform !== 'win32') return 'docker'
  // Cypress may supply both PATH and Path; Windows only uses one when spawning a child.
  const folders = Object.entries(process.env)
    .filter(([name]) => name.toLowerCase() === 'path')
    .flatMap(([, value]) => value.split(';'))
    .map(folder => folder.trim().replace(/^["']|["']$/g, ''))
  const candidates = folders.map(folder => join(folder, 'docker.exe'))
  if (process.env.ProgramFiles) candidates.push(join(process.env.ProgramFiles, 'Docker', 'Docker', 'resources', 'bin', 'docker.exe'))
  const executable = candidates.find(candidate => existsSync(candidate))
  if (!executable) throw new Error('Docker CLI was not found. Set DOCKER_CLI_PATH to docker.exe.')
  return executable
}

// Pass shell text as one argument to the container, never through the host shell.
export function grantE2eRole({ email, roleCode }, config) {
  if (!/^e2e_[a-z0-9_]+@example\.com$/.test(email) || !['ADMIN', 'SUPER_ADMIN'].includes(roleCode)) {
    throw new Error('Only the fixed E2E accounts may receive test roles.')
  }
  if (!process.env.COMPOSE_PROJECT_NAME) {
    throw new Error('Set COMPOSE_PROJECT_NAME to your disposable E2E Compose project before testing.')
  }
  if (!['localhost', '127.0.0.1', '[::1]'].includes(new URL(config.baseUrl).hostname)) {
    throw new Error('Role seeding requires a local test API.')
  }
  const sql = `insert into auth_user_roles (user_id, role_id) select u.user_id, r.role_id from auth_users u, auth_roles r where u.email = '${email}' and r.role_code = '${roleCode}' on conflict do nothing;`
  const result = spawnSync(dockerExecutable(), ['compose', 'exec', '-T', 'postgres', 'sh', '-c',
    'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], {
    cwd: config.projectRoot, input: `${sql}\n`, encoding: 'utf8', shell: false, windowsHide: true, timeout: 20000,
  })
  if (result.error || result.status !== 0) {
    throw new Error(`E2E role setup failed: ${result.error?.message ?? result.stderr.trim()}`)
  }
  return null
}
