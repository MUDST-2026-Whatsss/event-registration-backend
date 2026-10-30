import { spawnSync } from 'node:child_process'

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
  const result = spawnSync('docker', ['compose', 'exec', '-T', 'postgres', 'sh', '-c',
    'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], {
    cwd: config.projectRoot, input: `${sql}\n`, encoding: 'utf8', shell: false, windowsHide: true, timeout: 20000,
  })
  if (result.error || result.status !== 0) {
    throw new Error(`E2E role setup failed: ${result.error?.message ?? result.stderr.trim()}`)
  }
  return null
}
