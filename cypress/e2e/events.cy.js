import { scenario } from '../support/scenarios.js'
describe('Event Categories', () => {
  
  scenario('events_not_found', function events_not_found() {
    cy.apiRequest('GET', `/events/not-published-${Date.now()}`).then(response => {
      expect(response.status).to.eq(404)
      expect(response.body.code).to.eq('EVENT_NOT_FOUND')
    })
  })
  
})
