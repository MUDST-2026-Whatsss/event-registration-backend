import { scenario } from '../support/scenarios.js'
describe('Health Probes', () => {
  scenario('health_wrong_method', function health_wrong_method() {
    cy.request({ method: 'POST', url: '/api/health', failOnStatusCode: false }).its('status').should('eq', 405)
  })
  

  
})
