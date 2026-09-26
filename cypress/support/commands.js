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
