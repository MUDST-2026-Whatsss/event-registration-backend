import { scenario } from '../support/scenarios.js'

describe('change_password', () => {
  scenario('change_password', function change_password() {
    cy.createAccount().then(account => {
      cy.apiLogin(account.email, account.password)
      cy.apiRequest('POST', '/auth/change-password', { currentPassword: account.password, newPassword: 'NewPassword123!' }).its('status').should('eq', 200)
      cy.apiRequest('GET', '/auth/me').its('status').should('eq', 401)
      cy.apiRequest('POST', '/auth/login', { email: account.email, password: account.password }).its('status').should('eq', 401)
      cy.apiLogin(account.email, 'NewPassword123!').its('status').should('eq', 200)
    })
  })
  scenario('change_password_failed', function change_password_failed() {
    cy.createAccount().then(account => {
      cy.apiLogin(account.email, account.password)
      cy.apiRequest('POST', '/auth/change-password', { currentPassword: 'WrongPassword123!', newPassword: 'NewPassword123!' }).then(response => {
        expect(response.status).to.eq(400)
        expect(response.body.code).to.eq('INVALID_CURRENT_PASSWORD')
      })
      cy.apiRequest('GET', '/auth/me').its('status').should('eq', 200)
      cy.apiLogin(account.email, account.password).its('status').should('eq', 200)
    })
  })
  scenario('change_password_weak', function change_password_weak() {
    cy.createAccount().then(account => {
      cy.apiLogin(account.email, account.password)
      cy.apiRequest('POST', '/auth/change-password', { currentPassword: account.password, newPassword: 'weakpass' }).its('status').should('eq', 400)
    })
  })
})
