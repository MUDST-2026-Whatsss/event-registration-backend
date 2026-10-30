// IDs also select terminal cases; only the requested cases are registered.
export function scenario(name, test) {
  const selected = typeof Cypress.expose === 'function' ? Cypress.expose('test_cases') : Cypress.env('test_cases')
  if (!selected?.length || selected.includes(name)) it(name, test)
}
