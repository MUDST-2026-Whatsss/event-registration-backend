import { createDraftEvent, publishEvent } from '../support/events.js'
import { scenario } from '../support/scenarios.js'

describe('live_seats', () => {
  scenario('live_seats', function live_seats() {
    publishEvent(`Live capacity ${Date.now()}`, 3).then(event => {
      cy.task('seatWatchOpen', event.eventId).then(watch => {
        expect(watch.update).to.include({ eventId: event.eventId, remainingSeats: 3, full: false })
        cy.loginAs('participant')
        cy.apiSend('POST', `/api/v1/events/${event.eventId}/registrations`, { consentAccepted: true })
          .then(({ body }) => {
            cy.task('seatWatchWait', { id: watch.id, remainingSeats: 2 }).its('update.full').should('eq', false)
            cy.apiSend('POST', `/api/v1/registrations/${body.registrationId}/cancel`)
            cy.task('seatWatchWait', { id: watch.id, remainingSeats: 3 }).its('update.full').should('eq', false)
            cy.task('seatWatchClose', watch.id)
          })
      })
    })
  })

  scenario('live_seats_unpublished', function live_seats_unpublished() {
    createDraftEvent(`Private capacity ${Date.now()}`, 3).then(event => {
      cy.task('seatWatchOpen', event.eventId).then(watch => {
        expect(watch.closedCode).to.eq(1008)
        expect(watch).not.to.have.property('update')
        cy.task('seatWatchClose', watch.id)
      })
    })
  })
})
