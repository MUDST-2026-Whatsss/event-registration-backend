import { createDraftEvent, eventRequest, publishEvent, submitForReview } from '../support/events.js'

describe('Event Administration Lifecycle', () => {
  const timestamp = Date.now()

  it('admin edits a draft, submits it, withdraws it, and deletes it', () => {
    createDraftEvent(`Cypress draft ${timestamp}`, 5).then((draft) => {
      // 1. Edit the draft title
      cy.request('/api/v1/event-categories').then((categories) => {
        cy.apiSend('PATCH', `/api/v1/admin/events/${draft.eventId}?version=${draft.version}`,
          eventRequest(`Cypress draft edited ${timestamp}`, categories.body[0].eventCategoryId, 5))
      }).then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body).to.have.property('title', `Cypress draft edited ${timestamp}`)
        return res.body
      }).then((edited) => {
        // 2. Submit for review, then withdraw back to draft
        cy.apiSend('POST', `/api/v1/admin/events/${edited.eventId}/submit?version=${edited.version}`)
      }).then((res) => {
        expect(res.body).to.have.property('status', 'PENDING_REVIEW')
        cy.apiSend('POST', `/api/v1/admin/events/${res.body.eventId}/withdraw?version=${res.body.version}`)
      }).then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body).to.have.property('status', 'DRAFT')
        // 3. Delete the draft
        cy.apiSend('DELETE', `/api/v1/admin/events/${res.body.eventId}?version=${res.body.version}`)
      }).then((res) => {
        expect(res.status).to.eq(204)
        cy.request({ url: `/api/v1/admin/events/${draft.eventId}`, failOnStatusCode: false })
          .its('status').should('eq', 404)
      })
    })
  })

  it('super admin rejects a submitted event with a comment', () => {
    createDraftEvent(`Cypress rejected ${timestamp}`, 5)
      .then((event) => submitForReview(event))
      .then((review) => {
        cy.apiSend('POST', `/api/v1/admin/event-reviews/${review.reviewId}/reject`,
          { version: review.event.version, comment: 'Missing venue details' })
      })
      .then((res) => {
        expect(res.status).to.eq(200)
        expect(res.body).to.have.property('decision', 'REJECTED')
        expect(res.body).to.have.property('comment', 'Missing venue details')
        expect(res.body.event).to.have.property('status', 'REJECTED')
      })
  })

  it('publishes an approved event to the public catalogue and applies an approved change request', () => {
    publishEvent(`Cypress published ${timestamp}`, 5).then((event) => {
      // 1. The event is public by ID in the catalogue and by slug in the detail endpoint
      cy.request('/api/v1/events').then((res) => {
        const summary = res.body.find((item) => item.eventId === event.eventId)
        expect(summary, 'catalogue entry').to.exist
        expect(summary).to.have.property('remainingSeats', 5)
      })
      cy.request(`/api/v1/events/${event.slug}`).its('body.title').should('eq', `Cypress published ${timestamp}`)

      // 2. The admin requests a title change on the published event
      cy.loginAs('admin')
      cy.request('/api/v1/event-categories').then((categories) => {
        cy.apiSend('POST', `/api/v1/admin/events/${event.eventId}/change-requests?version=${event.version}`, {
          reason: 'Rename the event',
          event: eventRequest(`Cypress renamed ${timestamp}`, categories.body[0].eventCategoryId, 5),
        })
      }).then((res) => {
        expect(res.status).to.eq(201)
        expect(res.body).to.have.property('status', 'PENDING')
        return res.body
      }).then((change) => {
        // 3. The super admin approves it and the public title changes
        cy.loginAs('superAdmin')
        cy.apiSend('POST', `/api/v1/admin/change-requests/${change.changeRequestId}/approve`,
          { version: change.version, comment: 'Looks good' })
      }).then((res) => {
        expect(res.status).to.eq(200)
        cy.request(`/api/v1/events/${event.slug}`).its('body.title').should('eq', `Cypress renamed ${timestamp}`)
      })
    })
  })

  it('rejects event creation with an invalid schedule', () => {
    cy.loginAs('admin')
    cy.request('/api/v1/event-categories').then((categories) => {
      const request = eventRequest(`Cypress invalid ${timestamp}`, categories.body[0].eventCategoryId, 5)
      request.endAt = request.startAt
      cy.apiSend('POST', '/api/v1/admin/events', request, { failOnStatusCode: false })
    }).then((res) => {
      expect(res.status).to.eq(400)
      expect(res.body).to.have.property('code', 'VALIDATION_FAILED')
    })
  })
})
