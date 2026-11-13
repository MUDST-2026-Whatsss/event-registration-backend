import { publishEvent } from '../support/events.js'

// A valid 1x1 PNG
const PNG_BASE64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=='

describe('Paid Registration, Invitations, Analytics and Event Images', () => {
  const timestamp = Date.now()

  it('members and guests complete the mock payment and the admin sees both in the analytics', () => {
    // The Compose stack runs the local profile, which enables the classroom mock payment. No real payment is made.
    const guestEmail = `e2e_paid_guest_${timestamp}@example.com`

    publishEvent(`Cypress paid ${timestamp}`, 3, { eventType: 'PAID', price: 99 }).then((event) => {
      let member
      let guest

      // 1. A member's seat is held as PENDING_PAYMENT until the payment is confirmed
      cy.loginAs('participant')
      cy.apiSend('POST', `/api/v1/events/${event.eventId}/registrations`, { consentAccepted: true }).then((res) => {
        expect(res.status).to.eq(201)
        expect(res.body).to.have.property('status', 'PENDING_PAYMENT')
        expect(res.body.event).to.have.property('remainingSeats', 2)
        member = res.body
      })
      cy.then(() => cy.apiSend('POST', `/api/v1/registrations/${member.registrationId}/mock-payment/confirm`)).then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body).to.have.property('status', 'CONFIRMED')
        expect(res.body.confirmedAt).to.be.a('string')
      })
      // Confirming again returns the same registration instead of failing or taking another seat
      cy.then(() => cy.apiSend('POST', `/api/v1/registrations/${member.registrationId}/mock-payment/confirm`)).then((res) => {
        expect(res.body).to.have.property('registrationId', member.registrationId)
        expect(res.body.event).to.have.property('remainingSeats', 2)
      })

      // 2. Another member cannot confirm someone else's registration
      cy.loginAs('secondParticipant')
      cy.then(() => cy.apiSend('POST', `/api/v1/registrations/${member.registrationId}/mock-payment/confirm`, undefined,
        { failOnStatusCode: false })).its('status').should('be.oneOf', [403, 404])

      // 3. A guest pays with the ticket number and email; a wrong email is refused
      cy.clearCookies()
      cy.apiSend('POST', `/api/v1/events/${event.eventId}/guest-registrations`,
        { attendeeName: 'Paid Guest', attendeeEmail: guestEmail, consentAccepted: true }).then((res) => {
        expect(res.status).to.eq(201)
        expect(res.body).to.have.property('status', 'PENDING_PAYMENT')
        guest = res.body
      })
      cy.then(() => cy.apiSend('POST', '/api/v1/guest-registrations/mock-payment/confirm',
        { registrationNumber: guest.registrationNumber, email: 'someone-else@example.com' },
        { failOnStatusCode: false })).its('status').should('eq', 404)
      cy.then(() => cy.apiSend('POST', '/api/v1/guest-registrations/mock-payment/confirm',
        { registrationNumber: guest.registrationNumber, email: guestEmail.toUpperCase() })).then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body).to.have.property('status', 'CONFIRMED')
        expect(res.body).to.have.property('registeredAsGuest', true)
      })

      // 4. The public guest endpoint cannot confirm a member's registration
      cy.then(() => cy.apiSend('POST', '/api/v1/guest-registrations/mock-payment/confirm',
        { registrationNumber: member.registrationNumber, email: 'e2e_participant@example.com' },
        { failOnStatusCode: false })).its('status').should('eq', 404)

      // 5. The admin analytics count one member and one guest signup, both confirmed
      cy.loginAs('admin')
      cy.request(`/api/v1/admin/events/${event.eventId}/analytics`).then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body).to.include({ signups: 2, memberSignups: 1, guestSignups: 1, confirmed: 2, checkedIn: 0, eventEnded: false })
        expect(res.body.dailySignups).to.be.an('array').and.not.be.empty
      })
    })
  })

  it('an invited participant sees the published event in their invitations', () => {
    // Make sure the participant account exists before the admin looks it up
    cy.loginAs('participant')
    cy.loginAs('admin')
    cy.request('/api/v1/admin/events/invitees/lookup?email=e2e_participant@example.com').then((res) => {
      expect(res.body).to.have.property('email', 'e2e_participant@example.com')
      return publishEvent(`Cypress invitation ${timestamp}`, 5, { invitedUserIds: [res.body.userId] })
    }).then((event) => {
      cy.loginAs('participant')
      cy.request('/api/v1/invitations').then((res) => {
        expect(res.body.map((item) => item.event.eventId)).to.include(event.eventId)
      })
      cy.loginAs('secondParticipant')
      cy.request('/api/v1/invitations').then((res) => {
        expect(res.body.map((item) => item.event.eventId)).not.to.include(event.eventId)
      })
    })
  })

  it('an admin uploads an event image, it is served publicly, and the owner deletes it', () => {
    cy.loginAs('admin')
    uploadImage(Uint8Array.from(atob(PNG_BASE64), (char) => char.charCodeAt(0)), 'cover.png').then(({ status, body }) => {
      expect(status).to.eq(201)
      expect(body).to.include({ contentType: 'image/png', width: 1, height: 1 })
      const [, ownerId, fileName] = body.objectKey.split('/')

      // Anyone can load the image without signing in
      cy.clearCookies()
      cy.request(`/api/v1/media/event-images/${ownerId}/${fileName}`).then((res) => {
        expect(res.status).to.eq(200)
        expect(res.headers['content-type']).to.include('image/png')
      })

      // The owner deletes it and it is no longer served
      cy.loginAs('admin')
      cy.apiSend('DELETE', `/api/v1/event-images/${ownerId}/${fileName}`).its('status').should('eq', 204)
      cy.request({ url: `/api/v1/media/event-images/${ownerId}/${fileName}`, failOnStatusCode: false }).then((res) => {
        expect(res.status).to.eq(404)
        expect(res.body).to.have.property('code', 'MEDIA_NOT_FOUND')
      })
    })
  })

  it('rejects an upload that is not an image', () => {
    cy.loginAs('admin')
    uploadImage(new TextEncoder().encode('not an image'), 'fake.png').then(({ status, body }) => {
      expect(status).to.eq(400)
      expect(body).to.have.property('code', 'INVALID_IMAGE_FILE')
    })
  })
})

// cy.request cannot send multipart form data, so the upload runs through fetch in the browser.
// The API serves no HTML page to visit, so an empty page on the same origin is stubbed.
function uploadImage(bytes, fileName) {
  cy.intercept('GET', '/__cypress-upload', { statusCode: 200, body: '<!doctype html><title>upload</title>', headers: { 'content-type': 'text/html' } })
  cy.visit('/__cypress-upload')
  return cy.getCsrfToken().then((csrfToken) => cy.window().then(async (win) => {
    const form = new win.FormData()
    form.append('file', new win.Blob([bytes], { type: 'image/png' }), fileName)
    const res = await win.fetch('/api/v1/event-images', {
      method: 'POST', body: form, credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': csrfToken },
    })
    return { status: res.status, body: await res.json() }
  }))
}
