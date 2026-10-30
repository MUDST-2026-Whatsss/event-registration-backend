import { scenario } from '../support/scenarios.js'

describe('reset_password', () => {
  scenario('reset_password_invalid_token', function reset_password_invalid_token() {
    cy.apiRequest('POST', '/auth/reset-password', { token: 'invalid-classroom-token', newPassword: 'NewPassword123!' }).then(response => {
      expect(response.status).to.eq(400)
      expect(response.body.code).to.eq('INVALID_PASSWORD_RESET_TOKEN')
    })
  })
  scenario('reset_password_weak', function reset_password_weak() {
    cy.apiRequest('POST', '/auth/reset-password', { token: 'invalid-classroom-token', newPassword: 'weakpass' }).then(response => {
      expect(response.status).to.eq(400)
      expect(response.body.code).to.eq('VALIDATION_FAILED')
    })
  })
})
