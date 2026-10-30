Cypress.Commands.add('getCsrfToken', () => {
  return cy.request({
    method: 'GET',
    url: '/api/v1/auth/csrf',
  }).then((response) => {
    expect(response.status).to.eq(200)
    expect(response.body).to.have.property('token')
    return response.body.token
  })
})

Cypress.Commands.add('apiRegister', (user) => {
  return cy.getCsrfToken().then((csrfToken) => {
    return cy.request({
      method: 'POST',
      url: '/api/v1/auth/register',
      headers: {
        'X-XSRF-TOKEN': csrfToken,
      },
      body: user,
    })
  })
})

Cypress.Commands.add('apiLogin', (email, password) => {
  return cy.getCsrfToken().then((csrfToken) => {
    return cy.request({
      method: 'POST',
      url: '/api/v1/auth/login',
      headers: {
        'X-XSRF-TOKEN': csrfToken,
      },
      body: {
        email,
        password,
      },
    })
  })
})

// Sends a state-changing request with the CSRF header. Pass failOnStatusCode: false to assert errors.
Cypress.Commands.add('apiSend', (method, url, body, options = {}) => {
  return cy.getCsrfToken().then((csrfToken) => {
    return cy.request({
      method,
      url,
      headers: {
        'X-XSRF-TOKEN': csrfToken,
      },
      body,
      ...options,
    })
  })
})

// Fixed accounts so reruns against the same database reuse them instead of creating new ones.
const PASSWORD = 'Password123!'
const E2E_USERS = {
  admin: { email: 'e2e_admin@example.com', role: 'ADMIN', firstName: 'E2E', lastName: 'Admin' },
  superAdmin: { email: 'e2e_super_admin@example.com', role: 'SUPER_ADMIN', firstName: 'E2E', lastName: 'SuperAdmin' },
  participant: { email: 'e2e_participant@example.com', role: 'USER', firstName: 'E2E', lastName: 'Participant' },
  secondParticipant: { email: 'e2e_participant2@example.com', role: 'USER', firstName: 'E2E', lastName: 'Second' },
}

// Registration only grants USER, so administrator roles are inserted directly into the Compose
// PostgreSQL container. Running the same insert twice does nothing.
Cypress.Commands.add('grantRole', (email, roleCode) => {
  const sql = `insert into auth_user_roles (user_id, role_id) select u.user_id, r.role_id from auth_users u, auth_roles r where u.email = '${email}' and r.role_code = '${roleCode}' on conflict do nothing`
  return cy.exec(`echo "${sql}" | docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'`)
})

// Logs in as one of the E2E_USERS. The session is cached across specs to stay under the login rate limit.
Cypress.Commands.add('loginAs', (key) => {
  const user = E2E_USERS[key]
  cy.session(['e2e', user.email], () => {
    cy.getCsrfToken().then((csrfToken) => {
      cy.request({
        method: 'POST',
        url: '/api/v1/auth/register',
        headers: {
          'X-XSRF-TOKEN': csrfToken,
        },
        body: {
          email: user.email,
          password: PASSWORD,
          firstName: user.firstName,
          lastName: user.lastName,
          phoneNumber: '081-234-5678',
        },
        failOnStatusCode: false,
      }).then((res) => {
        expect([200, 201, 409]).to.include(res.status)
      })
    })
    if (user.role !== 'USER') {
      cy.grantRole(user.email, user.role)
    }
    cy.apiLogin(user.email, PASSWORD)
    if (user.role !== 'USER') {
      cy.apiSend('POST', '/api/v1/auth/select-role', { role: user.role })
    }
  }, {
    cacheAcrossSpecs: true,
    validate() {
      cy.request('/api/v1/auth/me').its('body.role').should('eq', user.role)
    },
  })
})
