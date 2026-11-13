describe('Account Self-service', () => {
  const timestamp = Date.now()
  const password = 'Password123!'
  const newPassword = 'Changed456!'
  const user = {
    email: `cypress_account_${timestamp}@example.com`,
    password,
    firstName: 'Account',
    lastName: 'Owner',
    phoneNumber: '081-234-5678',
  }

  before(() => {
    cy.apiRegister(user).its('status').should('eq', 201)
  })

  it('updates the profile, refreshes the session and changes the password', () => {
    cy.apiLogin(user.email, password)

    // 1. Profile changes are saved and returned by /me
    cy.apiSend('PATCH', '/api/v1/auth/me', { firstName: 'Updated', lastName: 'Name', phoneNumber: '089-999-0000' })
      .then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body).to.include({ firstName: 'Updated', lastName: 'Name' })
      })
    cy.request('/api/v1/auth/me').its('body.firstName').should('eq', 'Updated')
    cy.apiSend('PATCH', '/api/v1/auth/me', { phoneNumber: '12345' }, { failOnStatusCode: false }).then((res) => {
      expect(res.status).to.eq(400)
      expect(res.body).to.have.property('code', 'VALIDATION_FAILED')
    })

    // 2. The refresh cookie issues a new session for the same user
    cy.apiSend('POST', '/api/v1/auth/refresh').then((res) => {
      expect(res.status).to.eq(200)
      expect(res.body.user).to.have.property('email', user.email)
    })

    // 3. The wrong current password is refused; the right one changes it
    cy.apiSend('POST', '/api/v1/auth/change-password', { currentPassword: 'NotMyPassword1', newPassword },
      { failOnStatusCode: false }).then((res) => {
      expect(res.status).to.eq(400)
      expect(res.body).to.have.property('code', 'INVALID_CURRENT_PASSWORD')
    })
    cy.apiSend('POST', '/api/v1/auth/change-password', { currentPassword: password, newPassword })
      .its('status').should('eq', 200)

    // 4. Changing the password ends the session, and the new password signs in.
    // (The old password is not tried here: the suite stays under the per-IP login rate limit.)
    cy.request({ url: '/api/v1/auth/me', failOnStatusCode: false }).its('status').should('eq', 401)
    cy.apiLogin(user.email, newPassword).its('body.user.email').should('eq', user.email)
  })

  it('accepts a reset request without revealing whether the email exists and rejects an invalid token', () => {
    cy.clearCookies()
    cy.apiSend('POST', '/api/v1/auth/forgot-password', { email: user.email }).its('status').should('eq', 200)
    cy.apiSend('POST', '/api/v1/auth/forgot-password', { email: `nobody_${timestamp}@example.com` })
      .its('status').should('eq', 200)
    cy.apiSend('POST', '/api/v1/auth/reset-password', { token: 'not-a-real-token', newPassword },
      { failOnStatusCode: false }).then((res) => {
      expect(res.status).to.eq(400)
      expect(res.body).to.have.property('code', 'INVALID_PASSWORD_RESET_TOKEN')
    })
  })
})
