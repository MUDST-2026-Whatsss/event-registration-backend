import { scenario } from '../support/scenarios.js'

describe('login', () => {
  

  

  scenario('login_unknown_account', function login_unknown_account() {
    cy.apiRequest('POST', '/auth/login', { email: `missing-${Date.now()}@example.test`, password: 'Password123!' }).then(response => {
      expect(response.status).to.eq(401)
      expect(response.body).to.include({ code: 'INVALID_CREDENTIALS', message: 'Incorrect email or password.' })
    })
  })

  scenario('login_normalized_email', function login_normalized_email() {
    cy.createAccount().then(account => {
      cy.apiLogin(`  ${account.email.toUpperCase()}  `, account.password).its('status').should('eq', 200)
      cy.apiRequest('GET', '/auth/me').its('body.email').should('eq', account.email)
    })
  })

  scenario('login_empty', function login_empty() {
    cy.apiRequest('POST', '/auth/login', { email: '', password: '' }).then(response => {
      expect(response.status).to.eq(400)
      expect(response.body.code).to.eq('VALIDATION_FAILED')
    })
  })

  scenario('login_locked', function login_locked() {
    cy.createAccount().then(account => {
      for (let attempt = 0; attempt < 5; attempt++) {
        cy.apiRequest('POST', '/auth/login', { email: account.email, password: 'WrongPassword123!' }).its('status').should('eq', 401)
      }
      cy.apiRequest('POST', '/auth/login', { email: account.email, password: account.password }).then(response => {
        expect(response.status).to.eq(423)
        expect(response.body.code).to.eq('ACCOUNT_LOCKED')
      })
    })
  })
})
