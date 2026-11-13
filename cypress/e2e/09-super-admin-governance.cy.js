import { createDraftEvent, eventRequest, publishEvent, submitForReview } from '../support/events.js'

const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

describe('Super Admin Governance', () => {
  const timestamp = Date.now()

  it('disables, re-enables and changes the roles of a user account', () => {
    const email = `cypress_managed_${timestamp}@example.com`
    const roleCode = `CYPRESS_MANAGED_${timestamp}`
    let role

    // A throwaway account, so the cached E2E sessions are never disabled or re-roled
    cy.apiRegister({ email, password: 'Password123!', firstName: 'Managed', lastName: 'User', phoneNumber: '081-234-5678' })
    cy.loginAs('superAdmin')

    // 1. Create a role, rename it and replace its permissions
    cy.request('/api/v1/admin/permissions').then((permissions) => {
      const codes = permissions.body.map((permission) => permission.code)
      cy.apiSend('POST', '/api/v1/admin/roles', {
        code: roleCode, name: 'Cypress Managed', description: 'Created by Cypress', scopeType: 'GLOBAL', status: 'ACTIVE',
        permissionCodes: [codes[0]],
      }).then((res) => {
        role = res.body
        cy.apiSend('PATCH', `/api/v1/admin/roles/${role.roleId}`,
          { name: 'Cypress Managed Renamed', description: 'Renamed by Cypress', scopeType: 'GLOBAL', status: 'ACTIVE' })
      }).then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body).to.have.property('name', 'Cypress Managed Renamed')
        cy.apiSend('PUT', `/api/v1/admin/roles/${role.roleId}/permissions`, { permissionCodes: codes.slice(0, 2) })
      }).then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body.permissions).to.have.members(codes.slice(0, 2))
      })
    })

    // 2. Find the account and read its detail
    cy.request(`/api/v1/admin/users?query=${encodeURIComponent(email)}`).then((res) => {
      expect(res.body.content).to.have.length(1)
      return cy.request(`/api/v1/admin/users/${res.body.content[0].userId}`)
    }).then((res) => {
      expect(res.body).to.have.property('email', email)
      expect(res.body).to.have.property('status', 'ACTIVE')

      // 3. Disable, then enable it again
      return cy.apiSend('PATCH', `/api/v1/admin/users/${res.body.userId}/status`, { status: 'DISABLED', version: res.body.version })
    }).then((res) => {
      expect(res.body).to.have.property('status', 'DISABLED')
      return cy.apiSend('PATCH', `/api/v1/admin/users/${res.body.userId}/status`, { status: 'ACTIVE', version: res.body.version })
    }).then((res) => {
      expect(res.body).to.have.property('status', 'ACTIVE')

      // 4. Grant the custom role, then take it away so the role can be deleted
      return cy.apiSend('PUT', `/api/v1/admin/users/${res.body.userId}/roles`, { roleCodes: ['USER', roleCode], version: res.body.version })
    }).then((res) => {
      expect(res.body.roles.map((item) => item.code)).to.have.members(['USER', roleCode])
      return cy.apiSend('PUT', `/api/v1/admin/users/${res.body.userId}/roles`, { roleCodes: ['USER'], version: res.body.version })
    }).then((res) => {
      expect(res.body.roles.map((item) => item.code)).to.deep.eq(['USER'])
      cy.apiSend('DELETE', `/api/v1/admin/roles/${role.roleId}`).its('status').should('eq', 204)
    })
  })

  it('a super admin cannot disable their own account', () => {
    cy.loginAs('superAdmin')
    cy.request('/api/v1/auth/me').then((me) => cy.request(`/api/v1/admin/users/${me.body.userId}`)).then((res) => {
      cy.apiSend('PATCH', `/api/v1/admin/users/${res.body.userId}/status`, { status: 'DISABLED', version: res.body.version },
        { failOnStatusCode: false })
    }).then((res) => {
      expect(res.status).to.eq(409)
      expect(res.body).to.have.property('code', 'INVALID_USER_STATE')
    })
  })

  it('opens a pending review and rejects a change request, keeping the published data', () => {
    // 1. The review detail matches the list entry
    createDraftEvent(`Cypress review detail ${timestamp}`, 5)
      .then((event) => submitForReview(event))
      .then((review) => cy.request(`/api/v1/admin/event-reviews/${review.reviewId}`))
      .then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body.event).to.have.property('title', `Cypress review detail ${timestamp}`)
      })

    // 2. A change request on a published event is listed, opened and rejected
    publishEvent(`Cypress keep title ${timestamp}`, 5).then((event) => {
      cy.loginAs('admin')
      cy.request('/api/v1/event-categories').then((categories) => {
        cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/change-requests?version=${event.version}`, {
          reason: 'Try a new title',
          event: eventRequest(`Cypress unwanted title ${timestamp}`, categories.body[0].eventCategoryId, 5),
        })
      }).then((res) => {
        const change = res.body
        cy.request(`/api/v1/admin/events/${event.eventId}/change-requests`).then((list) => {
          expect(list.body.map((item) => item.changeRequestId)).to.include(change.changeRequestId)
        })

        cy.loginAs('superAdmin')
        cy.request('/api/v1/admin/change-requests?status=PENDING&size=100').then((list) => {
          // The super-admin review list names the ID requestId
          expect(list.body.content.map((item) => item.requestId)).to.include(change.changeRequestId)
        })
        cy.request(`/api/v1/admin/change-requests/${change.changeRequestId}`).its('body.reason').should('eq', 'Try a new title')
        cy.apiSend('POST', `/api/v1/admin/change-requests/${change.changeRequestId}/reject`,
          { version: change.version, comment: 'Keep the agreed title' })
      }).then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body).to.have.property('status', 'REJECTED')
        cy.request(`/api/v1/events/${event.slug}`).its('body.title').should('eq', `Cypress keep title ${timestamp}`)
      })
    })
  })

  it('assigns event administrators and keeps the event owner', () => {
    publishEvent(`Cypress event admins ${timestamp}`, 5).then((event) => {
      cy.loginAs('superAdmin')
      cy.request('/api/v1/admin/event-admins').then((candidates) => {
        const admin = candidates.body.find((item) => item.email === 'e2e_admin@example.com')
        expect(admin, 'E2E admin candidate').to.exist
        cy.apiSend('PUT', `/api/v1/admin/events/${event.eventId}/admins`, { adminUserIds: [admin.userId] })
      }).then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body.map((item) => item.email)).to.include('e2e_admin@example.com')
      })
      cy.request(`/api/v1/admin/events/${event.eventId}/admins`).then((res) => {
        const owner = res.body.find((item) => item.owner)
        expect(owner, 'event owner').to.exist
        expect(owner).to.have.property('email', 'e2e_admin@example.com')
      })
    })
  })

  it('reads platform statistics and exports the audit, sign-in and session logs as Excel', () => {
    cy.loginAs('superAdmin')
    cy.request('/api/v1/admin/stats').then((res) => {
      expect(res.body.totalEvents).to.be.greaterThan(0)
      expect(res.body.totalUsers).to.be.greaterThan(0)
    })
    ;['/api/v1/admin/audit-logs/export', '/api/v1/admin/audit-logs/sign-ins/export', '/api/v1/admin/audit-logs/sessions/export']
      .forEach((url) => {
        cy.request({ url, encoding: 'binary' }).then((res) => {
          expect(res.status).to.eq(200)
          expect(res.headers['content-type']).to.include(XLSX)
          expect(res.headers['content-disposition']).to.include('.xlsx')
          // An .xlsx file is a ZIP archive, which starts with "PK"
          expect(res.body.slice(0, 2)).to.eq('PK')
        })
      })

    // Admins without the super-admin role cannot export
    cy.loginAs('admin')
    cy.request({ url: '/api/v1/admin/audit-logs/export', failOnStatusCode: false }).its('status').should('eq', 403)
    cy.request({ url: '/api/v1/admin/stats', failOnStatusCode: false }).its('status').should('eq', 403)
  })
})
