import { scenario } from '../support/scenarios.js'
describe('Event Categories', () => {
  scenario('events_categories', function events_categories() {
    cy.request({
      method: 'GET',
      url: '/api/v1/event-categories',
    }).then((response) => {
      expect(response.status).to.eq(200)
      expect(response.body).to.be.an('array')
      expect(response.body.length).to.be.greaterThan(0)
      expect(response.body[0]).to.have.property('eventCategoryId')
      expect(response.body[0]).to.have.property('code')
    })
  })
})
