import { scenario } from '../support/scenarios.js'

describe('forgot_password', () => {
  scenario('forgot_password', function forgot_password() {
    // Unknown test addresses exercise the generic response without sending email.
    cy.apiRequest('POST', '/auth/forgot-password', { email: `unknown-${Date.now()}@example.test` }).then(response => {
      expect(response.status).to.eq(200)
      expect(response.body.message).to.eq("If an account exists for this email, we'll send a password reset link.")
    })
  })
  scenario('forgot_password_invalid_email', function forgot_password_invalid_email() {
    cy.apiRequest('POST', '/auth/forgot-password', { email: 'not-an-email' }).then(response => {
      expect(response.status).to.eq(400)
      expect(response.body.code).to.eq('VALIDATION_FAILED')
    })
  })
})
