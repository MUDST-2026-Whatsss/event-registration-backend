import { publishEvent } from '../support/events.js'
import { scenario } from '../support/scenarios.js'

describe('analytics', () => {
  scenario('analytics', function analytics() {
    publishEvent(`Analytics ${Date.now()}`, 10).then(event => {
      cy.loginAs('participant')
      cy.apiSend('POST', `/api/v1/events/${event.eventId}/registrations`, { consentAccepted: true })
        .then(response => cy.apiSend('POST', `/api/v1/registrations/${response.body.registrationId}/cancel`))
      cy.clearCookies()
      cy.apiSend('POST', `/api/v1/events/${event.eventId}/guest-registrations`, {
        attendeeName: 'Analytics Guest', attendeeEmail: `analytics-${Date.now()}@example.test`, consentAccepted: true,
      }).then(response => {
        expect(response.status).to.eq(201)
        const number = response.body.registrationNumber
        cy.loginAs('admin')
        cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/check-in`, { registrationNumber: number })
        cy.request(`/api/v1/admin/events/${event.eventId}/analytics`).then(({ body }) => {
          expect(body).to.include({ signups: 2, memberSignups: 1, guestSignups: 1, dropOffs: 1,
            dropOffRate: 0.5, checkedIn: 1, confirmed: 0, noShows: 0, eventEnded: false })
          // The shared Jackson policy omits null fields from HTTP JSON.
          expect(body).not.to.have.property('showRate')
          expect(body.dailySignups.reduce((sum, day) => sum + day.count, 0)).to.eq(2)
        })
      })
    })
  })

  scenario('analytics_access', function analytics_access() {
    publishEvent(`Empty analytics ${Date.now()}`, 5).then(event => {
      cy.request(`/api/v1/admin/events/${event.eventId}/analytics`).then(({ body }) => {
        expect(body.signups).to.eq(0)
        expect(body).not.to.have.property('dropOffRate')
        expect(body).not.to.have.property('showRate')
        expect(body.dailySignups).to.deep.eq([])
      })
      cy.loginAs('participant')
      cy.request({ url: `/api/v1/admin/events/${event.eventId}/analytics`, failOnStatusCode: false })
        .its('status').should('eq', 403)
      cy.clearCookies()
      cy.request({ url: `/api/v1/admin/events/${event.eventId}/analytics`, failOnStatusCode: false })
        .its('status').should('eq', 401)
    })
  })
})
