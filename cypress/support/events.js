// Event payloads and lifecycle steps shared by the administration and registration specs.

const HOUR = 60 * 60 * 1000

export function eventRequest(title, eventCategoryId, maximumParticipants = 2) {
  const now = Date.now()
  const start = now + 7 * 24 * HOUR
  return {
    title,
    summary: 'Cypress end-to-end event',
    description: 'Created by the Cypress API suite.',
    eventCategoryId,
    eventType: 'FREE',
    price: 0,
    currency: 'THB',
    locationType: 'ONSITE',
    locationName: 'Main Hall',
    timezone: 'Asia/Bangkok',
    startAt: new Date(start).toISOString(),
    endAt: new Date(start + HOUR).toISOString(),
    registrationStartAt: new Date(now - 60 * 1000).toISOString(),
    registrationEndAt: new Date(start - HOUR).toISOString(),
    cancellationDeadlineAt: new Date(start - HOUR).toISOString(),
    maximumParticipants,
    contactEmail: 'events@example.com',
    allowCancellation: true,
    showRemainingSeats: true,
  }
}

// Creates a draft as the admin; yields the created event.
export function createDraftEvent(title, maximumParticipants) {
  cy.loginAs('admin')
  return cy.request('/api/v1/event-categories').then((categories) => {
    return cy.apiSend('POST', '/api/v1/admin/events',
      eventRequest(title, categories.body[0].eventCategoryId, maximumParticipants))
  }).then((res) => {
    expect(res.status).to.eq(201)
    expect(res.body).to.have.property('status', 'DRAFT')
    return res.body
  })
}

// Submits the event as the admin; yields its pending review as seen by the super admin.
export function submitForReview(event) {
  cy.loginAs('admin')
  cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/submit?version=${event.version}`).then((res) => {
    expect(res.status).to.eq(200)
    expect(res.body).to.have.property('status', 'PENDING_REVIEW')
  })
  cy.loginAs('superAdmin')
  return cy.request('/api/v1/admin/event-reviews?decision=PENDING&size=100').then((res) => {
    const review = res.body.content.find((item) => item.event.eventId === event.eventId)
    expect(review, 'pending review').to.exist
    return review
  })
}

// Runs create, submit and approve; yields the published event.
export function publishEvent(title, maximumParticipants) {
  return createDraftEvent(title, maximumParticipants)
    .then((event) => submitForReview(event))
    .then((review) => {
      return cy.apiSend('POST', `/api/v1/admin/event-reviews/${review.reviewId}/approve`,
        { version: review.event.version, comment: 'Approved by Cypress' })
    })
    .then((res) => {
      expect(res.status).to.eq(200)
      expect(res.body).to.have.property('decision', 'APPROVED')
      expect(res.body.event).to.have.property('status', 'PUBLISHED')
      return res.body.event
    })
}
