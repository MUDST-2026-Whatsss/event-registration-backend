describe('Event Categories', () => {
  it('returns a list of active event categories', () => {
    cy.request({
      method: 'GET',
      url: '/api/v1/event-categories',
    }).then((response) => {
      expect(response.status).to.eq(200)
      expect(response.body).to.be.an('array')
      expect(response.body.length).to.be.greaterThan(0)
      expect(response.body[0]).to.have.property('eventCategoryId')
      expect(response.body[0]).to.have.property('code')
    })
  })
})
