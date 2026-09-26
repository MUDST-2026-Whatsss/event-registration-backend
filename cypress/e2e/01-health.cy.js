describe('Health Probes', () => {
  it('returns 200 OK from the legacy /api/health endpoint', () => {
    cy.request({
      method: 'GET',
      url: '/api/health',
    }).then((response) => {
      expect(response.status).to.eq(200)
      expect(response.body).to.eq('OK')
    })
  })

  it('returns 200 UP from the /actuator/health endpoint', () => {
    cy.request({
      method: 'GET',
      url: '/actuator/health',
    }).then((response) => {
      expect(response.status).to.eq(200)
      expect(response.body).to.have.property('status', 'UP')
    })
  })
})
