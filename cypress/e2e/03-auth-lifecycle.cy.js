import { scenario } from '../support/scenarios.js'
describe('Authentication and User Lifecycle', () => {
  const timestamp = Date.now()
  const testUser = {
    email: `cypress_user_${timestamp}@example.com`,
    password: 'Password123!',
    firstName: 'Cypress',
    lastName: `Tester${timestamp}`,
    phoneNumber: '081-234-5678',
  }

  scenario('login', function login() {
    // 1. Register new user
    cy.apiRegister(testUser).then((res) => {
      expect([200, 201]).to.include(res.status)
      expect(res.body).to.include({ phoneNumber: '0812345678' })
      expect(res.body.roles).to.deep.eq(['USER'])
      expect(res.body).not.to.have.property('password')
    })
    cy.request({ url: '/api/v1/auth/me', failOnStatusCode: false }).its('status').should('eq', 401)

    // 2. Login with registered user credentials
    cy.apiLogin(testUser.email, testUser.password).then((res) => {
      expect(res.status).to.eq(200)
      expect(res.body).to.have.property('user')
    })

    // 3. Verify /api/v1/auth/me returns current authenticated user
    cy.request({
      method: 'GET',
      url: '/api/v1/auth/me',
    }).then((res) => {
      expect(res.status).to.eq(200)
      expect(res.body).to.have.property('email', testUser.email)
      expect(res.body).to.have.property('firstName', testUser.firstName)
      expect(res.body).to.have.property('lastName', testUser.lastName)
    })

    // 4. Logout
    cy.getCsrfToken().then((csrfToken) => {
      cy.request({
        method: 'POST',
        url: '/api/v1/auth/logout',
        headers: {
          'X-XSRF-TOKEN': csrfToken,
        },
      }).then((res) => {
        expect(res.status).to.eq(200)
      })
    })

    // 5. Verify /api/v1/auth/me is now 401 Unauthorized
    cy.request({
      method: 'GET',
      url: '/api/v1/auth/me',
      failOnStatusCode: false,
    }).then((res) => {
      expect(res.status).to.eq(401)
    })
    cy.apiRequest('POST', '/auth/refresh').its('status').should('eq', 401)
    cy.getCookie('EVENT_ACCESS_TOKEN').should('be.null')
    cy.getCookie('EVENT_REFRESH_TOKEN').should('be.null')
  })

  scenario('login_failed', function login_failed() {
    const account = { ...testUser, email: `e2e_wrong_${Date.now()}@example.test` }
    cy.apiRegister(account)
    cy.getCsrfToken().then((csrfToken) => {
      cy.request({
        method: 'POST',
        url: '/api/v1/auth/login',
        headers: {
          'X-XSRF-TOKEN': csrfToken,
        },
        body: {
          email: account.email,
          password: 'WrongPassword999!',
        },
        failOnStatusCode: false,
      }).then((res) => {
        expect([400, 401]).to.include(res.status)
      })
    })
  })
})
