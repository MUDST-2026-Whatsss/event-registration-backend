import { scenario } from '../support/scenarios.js'
describe('CSRF and Security Protection', () => {
  scenario('security_csrf_token', function security_csrf_token() {
    cy.request({
      method: 'GET',
      url: '/api/v1/auth/csrf',
    }).then((response) => {
      expect(response.status).to.eq(200)
      expect(response.body).to.have.property('token').that.is.a('string')
      expect(response.body).to.have.property('headerName', 'X-XSRF-TOKEN')
    })
  })

  scenario('security_csrf_missing', function security_csrf_missing() {
    cy.request({
      method: 'POST',
      url: '/api/v1/auth/login',
      failOnStatusCode: false,
      body: {
        email: 'test@example.com',
        password: 'Password123!',
      },
    }).then((response) => {
      expect(response.status).to.eq(403)
      expect(response.body).to.have.property('code', 'CSRF_TOKEN_INVALID')
    })
  })

  scenario('security_login_required', function security_login_required() {
    cy.request({
      method: 'GET',
      url: '/api/v1/auth/me',
      failOnStatusCode: false,
    }).then((response) => {
      expect(response.status).to.eq(401)
    })
  })
})
