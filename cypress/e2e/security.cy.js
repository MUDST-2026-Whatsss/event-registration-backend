import { scenario } from '../support/scenarios.js'
describe('CSRF and Security Protection', () => {
  scenario('security_csrf_invalid', function security_csrf_invalid() {
    cy.getCsrfToken()
    cy.request({ method: 'POST', url: '/api/v1/auth/login', failOnStatusCode: false,
      headers: { 'X-XSRF-TOKEN': 'not-the-cookie-token' }, body: { email: 'someone@example.test', password: 'Password123!' },
    }).then(response => {
      expect(response.status).to.eq(403)
      expect(response.body.code).to.eq('CSRF_TOKEN_INVALID')
    })
  })
  scenario('security_guest_denied', function security_guest_denied() {
    for (const endpoint of ['/admin/users', '/admin/roles', '/invitations']) {
      cy.apiRequest('GET', endpoint).then(response => {
        expect(response.status, endpoint).to.eq(401)
        expect(response.body.code).to.eq('UNAUTHENTICATED')
      })
    }
  })
  

  

  
})
