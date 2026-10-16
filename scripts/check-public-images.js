import assert from 'node:assert/strict'

const tag = process.argv[2]
if (!tag || !/^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$/.test(tag)) throw new Error('Pass the release image tag')
for (const name of ['event-registration', 'event-registration-frontend', 'event-registration-minio']) {
  const repository = `mudst-2026-whatsss/${name}`
  const tokenResponse = await fetch(`https://ghcr.io/token?service=ghcr.io&scope=repository:${repository}:pull`)
  assert.ok(tokenResponse.ok, `${name}: anonymous access is blocked. Set this GitHub package to Public, then rerun CI.`)
  const { token } = await tokenResponse.json()
  const manifest = await fetch(`https://ghcr.io/v2/${repository}/manifests/${tag}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.oci.image.index.v1+json,application/vnd.oci.image.manifest.v1+json,application/vnd.docker.distribution.manifest.list.v2+json,application/vnd.docker.distribution.manifest.v2+json',
    },
  })
  assert.ok(manifest.ok, `${name}:${tag}: published manifest is unavailable (${manifest.status})`)
  console.log(`Anonymous pull available: ghcr.io/${repository}:${tag}`)
}
