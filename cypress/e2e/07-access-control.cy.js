describe('Role-based Access Control', () => {
  it('blocks guests and participants from administration endpoints', () => {
    cy.request({ url: '/api/v1/admin/events', failOnStatusCode: false }).its('status').should('eq', 401)

    cy.loginAs('participant')
    cy.request({ url: '/api/v1/admin/events', failOnStatusCode: false }).its('status').should('eq', 403)
    cy.request({ url: '/api/v1/admin/users', failOnStatusCode: false }).its('status').should('eq', 403)
    cy.request({ url: '/api/v1/admin/audit-logs', failOnStatusCode: false }).its('status').should('eq', 403)
  })

  it('lets an admin manage events but not super-admin governance', () => {
    cy.loginAs('admin')
    cy.request('/api/v1/admin/events').its('status').should('eq', 200)
    cy.request('/api/v1/admin/events/stats').its('body').should('have.property', 'total')
    cy.request('/api/v1/admin/registrations/recent').its('status').should('eq', 200)

    cy.request({ url: '/api/v1/admin/users', failOnStatusCode: false }).its('status').should('eq', 403)
    cy.request({ url: '/api/v1/admin/event-reviews', failOnStatusCode: false }).its('status').should('eq', 403)
    cy.request({ url: '/api/v1/admin/audit-logs', failOnStatusCode: false }).its('status').should('eq', 403)
  })

  it('lets a super admin read users, roles, permissions, and audit history', () => {
    cy.loginAs('superAdmin')
    cy.request('/api/v1/admin/users').then((res) => {
      expect(res.status).to.eq(200)
      expect(res.body.totalElements).to.be.greaterThan(0)
    })
    cy.request('/api/v1/admin/users/stats').its('status').should('eq', 200)
    cy.request('/api/v1/admin/roles').then((res) => {
      expect(res.body.map((role) => role.code)).to.include.members(['USER', 'ADMIN', 'SUPER_ADMIN'])
    })
    cy.request('/api/v1/admin/permissions').its('body').should('have.length.greaterThan', 0)
    cy.request('/api/v1/admin/audit-logs').its('status').should('eq', 200)
    cy.request('/api/v1/admin/audit-logs/sign-ins').its('status').should('eq', 200)
    cy.request('/api/v1/admin/audit-logs/sessions').its('status').should('eq', 200)
    cy.request('/api/v1/admin/event-admins').its('status').should('eq', 200)
  })

  it('lets a super admin create and delete a custom role', () => {
    const code = `CYPRESS_ROLE_${Date.now()}`
    cy.loginAs('superAdmin')
    cy.request('/api/v1/admin/permissions').then((res) => {
      cy.apiSend('POST', '/api/v1/admin/roles', {
        code,
        name: 'Cypress Role',
        description: 'Created by Cypress',
        scopeType: 'GLOBAL',
        status: 'ACTIVE',
        permissionCodes: [res.body[0].code],
      })
    }).then((res) => {
      expect(res.status).to.eq(200)
      expect(res.body).to.have.property('code', code)
      cy.apiSend('DELETE', `/api/v1/admin/roles/${res.body.roleId}`)
    }).then((res) => {
      expect(res.status).to.eq(204)
    })
  })
})
