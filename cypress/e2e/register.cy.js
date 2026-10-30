import { scenario } from '../support/scenarios.js'

function account(values = {}) {
  return { email: `e2e_${Date.now()}_${Cypress._.random(1000000000)}@example.test`, password: 'Password123!',
    firstName: 'สมชาย', lastName: 'Student', phoneNumber: '081-234-5678', ...values }
}
function invalid(values, field) {
  cy.apiRequest('POST', '/auth/register', account(values)).then(response => {
    expect(response.status).to.eq(400)
    expect(response.body.code).to.eq('VALIDATION_FAILED')
    expect(response.body.fieldErrors).to.have.property(field)
  })
}
describe('register', () => {
  
  scenario('register_duplicate', function register_duplicate() {
    const user = account()
    cy.apiRequest('POST', '/auth/register', user).its('status').should('eq', 201)
    cy.apiRequest('POST', '/auth/register', { ...user, email: user.email.toUpperCase() }).then(response => {
      expect(response.status).to.eq(409)
      expect(response.body.code).to.eq('EMAIL_ALREADY_REGISTERED')
    })
  })
  scenario('register_invalid_email', function register_invalid_email() { invalid({ email: 'invalid-email' }, 'email') })
  scenario('register_invalid_phone', function register_invalid_phone() { invalid({ phoneNumber: '0--------' }, 'phoneNumber') })
  scenario('register_weak_password', function register_weak_password() { invalid({ password: 'weakpass' }, 'password') })
  scenario('register_empty_name', function register_empty_name() { invalid({ firstName: '   ' }, 'firstName') })
  scenario('register_name_too_long', function register_name_too_long() { invalid({ firstName: 'A'.repeat(101) }, 'firstName') })
})
