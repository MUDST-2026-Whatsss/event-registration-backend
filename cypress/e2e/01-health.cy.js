import { scenario } from '../support/scenarios.js'
describe('Health Probes', () => {
  scenario('health', function health() {
    cy.request({
      method: 'GET',
      url: '/api/health',
    }).then((response) => {
      expect(response.status).to.eq(200)
      expect(response.body).to.eq('OK')
    })
  })

  scenario('health_actuator', function health_actuator() {
    cy.request({
      method: 'GET',
      url: '/actuator/health',
    }).then((response) => {
      expect(response.status).to.eq(200)
      expect(response.body).to.have.property('status', 'UP')
    })
  })
})
