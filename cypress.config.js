import { defineConfig } from 'cypress'
import { grantE2eRole } from './scripts/e2e-tasks.js'
import { seatTasks, closeSeatWatches } from './scripts/e2e-sockets.js'

export default defineConfig({
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL || 'http://127.0.0.1:8080',
    viewportWidth: 1280,
    viewportHeight: 720,
    video: false,
    screenshotOnRunFailure: true,
    testIsolation: true,
    setupNodeEvents(on, config) {
      on('task', { grantRole: input => grantE2eRole(input, config), ...seatTasks(config) })
      on('after:spec', closeSeatWatches)
      return config
    },
    supportFile: 'cypress/support/e2e.js',
  },
})
