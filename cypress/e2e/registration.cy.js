import { scenario } from '../support/scenarios.js'
const unknown = '00000000-0000-0000-0000-000000000001'

describe('registration', () => {
  
  scenario('registration_empty_list', function registration_empty_list() {
    cy.createAccount().then(account => cy.apiLogin(account.email, account.password))
    cy.apiRequest('GET', '/registrations').then(response => {
      expect(response.status).to.eq(200)
      expect(response.body).to.deep.eq([])
    })
  })
  scenario('registration_not_found', function registration_not_found() {
    cy.createAccount().then(account => cy.apiLogin(account.email, account.password))
    cy.apiRequest('GET', `/registrations/${unknown}`).then(response => {
      expect(response.status).to.eq(404)
      expect(response.body.code).to.eq('REGISTRATION_NOT_FOUND')
    })
  })
  scenario('registration_missing_event', function registration_missing_event() {
    cy.createAccount().then(account => cy.apiLogin(account.email, account.password))
    cy.apiRequest('POST', `/events/${unknown}/registrations`, { consentAccepted: true }).then(response => {
      expect(response.status).to.eq(404)
      expect(response.body.code).to.eq('EVENT_NOT_FOUND')
    })
  })
  scenario('registration_consent_required', function registration_consent_required() {
    cy.createAccount().then(account => cy.apiLogin(account.email, account.password))
    cy.apiRequest('POST', `/events/${unknown}/registrations`, { consentAccepted: false }).then(response => {
      expect(response.status).to.eq(400)
      expect(response.body.code).to.eq('VALIDATION_FAILED')
    })
  })
  scenario('registration_cancel_missing', function registration_cancel_missing() {
    cy.createAccount().then(account => cy.apiLogin(account.email, account.password))
    cy.apiRequest('POST', `/registrations/${unknown}/cancel`).then(response => {
      expect(response.status).to.eq(404)
      expect(response.body.code).to.eq('REGISTRATION_NOT_FOUND')
    })
  })
})
