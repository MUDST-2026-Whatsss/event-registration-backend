delete process.env.ELECTRON_RUN_AS_NODE

import cypress from 'cypress'

const isInteractive = process.argv.includes('--open')

if (isInteractive) {
  cypress.open().catch((err) => {
    console.error(err)
    process.exit(1)
  })
} else {
  cypress.run().then((results) => {
    if (results.totalFailed > 0 || results.status === 'failed') {
      process.exit(1)
    }
  }).catch((err) => {
    console.error(err)
    process.exit(1)
  })
}
