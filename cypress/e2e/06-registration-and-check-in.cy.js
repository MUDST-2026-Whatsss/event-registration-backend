import { publishEvent } from '../support/events.js'

describe('Registration, Cancellation and Check-in', () => {
  const timestamp = Date.now()

  it('participants register until the event is full, cancel, and are checked in by the admin', () => {
    publishEvent(`Cypress registration ${timestamp}`, 2).then((event) => {
      let first
      let second

      // 1. The first participant registers and cannot register twice
      cy.loginAs('participant')
      cy.apiSend('POST', `/api/v1/events/${event.eventId}/registrations`, { consentAccepted: true }).then((res) => {
        expect(res.status).to.eq(201)
        expect(res.body).to.have.property('status', 'CONFIRMED')
        expect(res.body.registrationNumber).to.match(/^REG-[A-Z0-9]{10}$/)
        expect(res.body.event).to.have.property('remainingSeats', 1)
        first = res.body
      })
      cy.apiSend('POST', `/api/v1/events/${event.eventId}/registrations`, { consentAccepted: true },
        { failOnStatusCode: false }).then((res) => {
        expect(res.status).to.eq(409)
        expect(res.body).to.have.property('code', 'ALREADY_REGISTERED')
      })
      cy.request('/api/v1/registrations').then((res) => {
        expect(res.body.map((item) => item.registrationId)).to.include(first.registrationId)
      })

      // 2. The second participant takes the last seat, so the catalogue reports the event as full
      cy.loginAs('secondParticipant')
      cy.apiSend('POST', `/api/v1/events/${event.eventId}/registrations`, { consentAccepted: true }).then((res) => {
        expect(res.status).to.eq(201)
        second = res.body
      })
      cy.request(`/api/v1/events/${event.slug}`).then((res) => {
        expect(res.body).to.have.property('remainingSeats', 0)
        expect(res.body).to.have.property('full', true)
      })

      // 3. The second participant cancels and the seat is released
      cy.then(() => cy.apiSend('POST', `/api/v1/registrations/${second.registrationId}/cancel`)).then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body).to.have.property('status', 'CANCELLED_BY_USER')
        expect(res.body.event).to.have.property('remainingSeats', 1)
      })

      // 4. A participant cannot use the staff check-in endpoint
      cy.then(() => cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/check-in`,
        { registrationNumber: first.registrationNumber }, { failOnStatusCode: false })).its('status').should('eq', 403)

      // 5. The admin sees both registrations and checks in the confirmed one
      cy.loginAs('admin')
      cy.request(`/api/v1/admin/events/${event.eventId}/registrations`).then((res) => {
        expect(res.body.totalElements).to.eq(2)
      })
      cy.then(() => cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/check-in`,
        { registrationNumber: first.registrationNumber.toLowerCase() })).then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body).to.have.property('status', 'CHECKED_IN')
        expect(res.body.checkedInAt).to.be.a('string')
      })

      // 6. Repeated, cancelled, and unknown check-ins are rejected
      cy.then(() => cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/check-in`,
        { registrationNumber: first.registrationNumber }, { failOnStatusCode: false })).then((res) => {
        expect(res.status).to.eq(409)
        expect(res.body).to.have.property('code', 'ALREADY_CHECKED_IN')
      })
      cy.then(() => cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/check-in`,
        { registrationNumber: second.registrationNumber }, { failOnStatusCode: false })).then((res) => {
        expect(res.status).to.eq(409)
        expect(res.body).to.have.property('code', 'REGISTRATION_NOT_CHECKABLE')
      })
      cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/check-in`,
        { registrationNumber: 'REG-UNKNOWN000' }, { failOnStatusCode: false }).then((res) => {
        expect(res.status).to.eq(404)
        expect(res.body).to.have.property('code', 'REGISTRATION_NOT_FOUND')
      })
    })
  })

  it('cancelling the event cancels every active registration', () => {
    publishEvent(`Cypress cancelled event ${timestamp}`, 5).then((event) => {
      cy.loginAs('participant')
      cy.apiSend('POST', `/api/v1/events/${event.eventId}/registrations`, { consentAccepted: true }).then((res) => {
        expect(res.status).to.eq(201)
        return res.body
      }).then((registration) => {
        cy.loginAs('admin')
        cy.request(`/api/v1/admin/events/${event.eventId}`).then((res) => {
          cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/cancel`,
            { reason: 'Venue closed', version: res.body.version })
        }).then((res) => {
          expect(res.status).to.eq(200)
          expect(res.body).to.have.property('status', 'CANCELLED')
        })
        cy.loginAs('participant')
        cy.request(`/api/v1/registrations/${registration.registrationId}`).then((res) => {
          expect(res.body).to.have.property('status', 'CANCELLED_EVENT')
          expect(res.body).to.have.property('cancellationReason', 'Venue closed')
        })
      })
    })
  })

  it('guests cannot register or list registrations', () => {
    // Authentication is checked before the event is looked up, so any event ID works here.
    cy.apiSend('POST', '/api/v1/events/00000000-0000-0000-0000-000000000000/registrations', { consentAccepted: true },
      { failOnStatusCode: false }).its('status').should('eq', 401)
    cy.request({ url: '/api/v1/registrations', failOnStatusCode: false }).its('status').should('eq', 401)
  })
})
