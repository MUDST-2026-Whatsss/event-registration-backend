import { scenario } from '../support/scenarios.js'

describe('logout', () => {
  
  scenario('logout_without_session', function logout_without_session() {
    cy.apiRequest('POST', '/auth/logout').its('status').should('eq', 200)
    cy.apiRequest('POST', '/auth/logout').its('status').should('eq', 200)
  })
})
