import { publishEvent } from '../support/events.js'
import { scenario } from '../support/scenarios.js'

function withTicket(run) {
  publishEvent(`Offline arrival ${Date.now()}`, 5).then(event => {
    cy.clearCookies()
    cy.apiSend('POST', `/api/v1/events/${event.eventId}/guest-registrations`, {
      attendeeName: 'Offline Guest', attendeeEmail: `offline-${Date.now()}@example.test`, consentAccepted: true,
    }).then(({ body }) => {
      cy.loginAs('admin')
      run(event, body)
    })
  })
}

describe('offline_check_in', () => {
  scenario('offline_check_in', function offline_check_in() {
    withTicket((event, ticket) => {
      const scannedAt = new Date(Date.parse(ticket.registeredAt) + 1).toISOString()
      cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/check-in`, {
        registrationNumber: ticket.registrationNumber, checkedInAt: scannedAt,
      }).then(({ body, status }) => {
        expect(status).to.eq(200)
        expect(body).to.include({ status: 'CHECKED_IN', checkedInAt: scannedAt })
      })
      cy.request(`/api/v1/admin/events/${event.eventId}/registrations`).then(({ body }) => {
        expect(body.content.find(row => row.registrationId === ticket.registrationId).checkedInAt).to.eq(scannedAt)
      })
    })
  })

  scenario('offline_check_in_invalid_time', function offline_check_in_invalid_time() {
    withTicket((event, ticket) => {
      for (const time of [new Date(Date.now() + 600_000).toISOString(), new Date(Date.parse(ticket.registeredAt) - 1000).toISOString()]) {
        cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/check-in`, {
          registrationNumber: ticket.registrationNumber, checkedInAt: time,
        }, { failOnStatusCode: false }).then(({ body, status }) => {
          expect(status).to.eq(400)
          expect(body.code).to.eq('INVALID_CHECK_IN_TIME')
        })
      }
      cy.then(() => {
        const started = Date.now()
        cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/check-in`, {
          registrationNumber: ticket.registrationNumber, checkedInAt: new Date(started + 60_000).toISOString(),
        }).then(({ body, status }) => {
          expect(status).to.eq(200)
          expect(Date.parse(body.checkedInAt)).to.be.within(started - 2000, Date.now() + 2000)
        })
      })
    })
  })
})
