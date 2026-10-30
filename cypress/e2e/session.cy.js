import { scenario } from '../support/scenarios.js'

describe('session', () => {
  scenario('session_refresh', function session_refresh() {
    cy.createAccount().then(account => cy.apiLogin(account.email, account.password))
    cy.getCookie('EVENT_REFRESH_TOKEN').then(previous => {
      cy.apiRequest('POST', '/auth/refresh', { role: 'USER' }).its('status').should('eq', 200)
      cy.getCookie('EVENT_REFRESH_TOKEN').its('value').should('not.eq', previous.value)
      cy.apiRequest('GET', '/auth/me').its('status').should('eq', 200)
    })
  })
  scenario('session_refresh_missing', function session_refresh_missing() {
    cy.apiRequest('POST', '/auth/refresh').then(response => {
      expect(response.status).to.eq(401)
      expect(response.body.code).to.eq('INVALID_REFRESH_TOKEN')
    })
  })
  scenario('session_role_denied', function session_role_denied() {
    cy.createAccount().then(account => cy.apiLogin(account.email, account.password))
    cy.apiRequest('POST', '/auth/select-role', { role: 'SUPER_ADMIN' }).its('status').should('eq', 403)
    cy.apiRequest('GET', '/auth/me').its('body.role').should('eq', 'USER')
  })
  scenario('session_select_role', function session_select_role() {
    cy.createAccount().then(account => cy.apiLogin(account.email, account.password))
    cy.apiRequest('POST', '/auth/select-role', { role: 'USER' }).then(response => {
      expect(response.status).to.eq(200)
      expect(response.body.user.role).to.eq('USER')
    })
  })
})
