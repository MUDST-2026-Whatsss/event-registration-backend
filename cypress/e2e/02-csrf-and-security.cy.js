describe('CSRF and Security Protection', () => {
  it('fetches a valid CSRF token', () => {
    cy.request({
      method: 'GET',
      url: '/api/v1/auth/csrf',
    }).then((response) => {
      expect(response.status).to.eq(200)
      expect(response.body).to.have.property('token').that.is.a('string')
      expect(response.body).to.have.property('headerName', 'X-XSRF-TOKEN')
    })
  })

  it('rejects POST request without X-XSRF-TOKEN header with 403 Forbidden', () => {
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

  it('rejects unauthenticated request to /api/v1/auth/me with 401 Unauthorized', () => {
    cy.request({
      method: 'GET',
      url: '/api/v1/auth/me',
      failOnStatusCode: false,
    }).then((response) => {
      expect(response.status).to.eq(401)
    })
  })
})
