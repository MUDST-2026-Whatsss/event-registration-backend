describe('Authentication and User Lifecycle', () => {
  const timestamp = Date.now()
  const testUser = {
    email: `cypress_user_${timestamp}@example.com`,
    password: 'Password123!',
    firstName: 'Cypress',
    lastName: `Tester${timestamp}`,
    phoneNumber: '081-234-5678',
  }

  it('registers a new user and allows login, profile inspection, and logout', () => {
    // 1. Register new user
    cy.apiRegister(testUser).then((res) => {
      expect([200, 201]).to.include(res.status)
    })

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
  })

  it('rejects login with invalid password', () => {
    cy.getCsrfToken().then((csrfToken) => {
      cy.request({
        method: 'POST',
        url: '/api/v1/auth/login',
        headers: {
          'X-XSRF-TOKEN': csrfToken,
        },
        body: {
          email: testUser.email,
          password: 'WrongPassword999!',
        },
        failOnStatusCode: false,
      }).then((res) => {
        expect([400, 401]).to.include(res.status)
      })
    })
  })
})
