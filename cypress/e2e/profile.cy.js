import { scenario } from '../support/scenarios.js'

describe('profile', () => {
  beforeEach(() => cy.createAccount().then(account => cy.apiLogin(account.email, account.password)))
  
  scenario('profile_update', function profile_update() {
    cy.apiRequest('PATCH', '/auth/me', { firstName: 'Updated', lastName: 'Participant', phoneNumber: '081-111-2222' }).its('status').should('eq', 200)
    cy.apiRequest('GET', '/auth/me').its('body').should('include', { firstName: 'Updated', lastName: 'Participant', phoneNumber: '0811112222' })
  })
  scenario('profile_partial_update', function profile_partial_update() {
    cy.apiRequest('PATCH', '/auth/me', { firstName: 'Updated' }).its('status').should('eq', 200)
    cy.apiRequest('GET', '/auth/me').its('body').should('include', { firstName: 'Updated', lastName: 'Student', phoneNumber: '0812345678' })
  })
  scenario('profile_invalid_phone', function profile_invalid_phone() {
    cy.apiRequest('PATCH', '/auth/me', { phoneNumber: 'not-a-phone' }).its('status').should('eq', 400)
    cy.apiRequest('GET', '/auth/me').its('body.phoneNumber').should('eq', '0812345678')
  })
  scenario('profile_name_too_long', function profile_name_too_long() {
    cy.apiRequest('PATCH', '/auth/me', { firstName: 'A'.repeat(101) }).its('status').should('eq', 400)
    cy.apiRequest('GET', '/auth/me').its('body.firstName').should('eq', 'E2E')
  })
})
