import { randomUUID } from 'node:crypto'

const watches = new Map()

function waitFor(watch, accept) {
  if (watch.last && accept(watch.last)) return Promise.resolve({ update: watch.last })
  if (watch.closedCode != null) return Promise.resolve({ closedCode: watch.closedCode })
  return new Promise((resolve, reject) => {
    const waiter = { accept, resolve: result => { clearTimeout(timer); watch.waiters.delete(waiter); resolve(result) } }
    const timer = setTimeout(() => {
      watch.waiters.delete(waiter)
      reject(new Error('No matching live seat update arrived within 15 seconds.'))
    }, 15000)
    watch.waiters.add(waiter)
  })
}

export function closeSeatWatches() {
  for (const watch of watches.values()) {
    watch.socket.close()
    for (const waiter of watch.waiters) waiter.resolve({ closedCode: 1000 })
  }
  watches.clear()
}

export function seatTasks(config) {
  return {
    async seatWatchOpen(eventId) {
      if (!/^[0-9a-f-]{36}$/i.test(eventId)) throw new Error('A test event UUID is required.')
      const url = new URL('/api/v1/ws/seats', config.baseUrl)
      url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
      const id = randomUUID()
      const socket = new WebSocket(url)
      const watch = { socket, last: null, closedCode: null, waiters: new Set() }
      watches.set(id, watch)
      socket.addEventListener('open', () => socket.send(JSON.stringify({ subscribe: eventId })))
      socket.addEventListener('message', message => {
        watch.last = JSON.parse(String(message.data))
        for (const waiter of watch.waiters) {
          if (waiter.accept(watch.last)) waiter.resolve({ update: watch.last })
        }
      })
      socket.addEventListener('close', event => {
        watch.closedCode = event.code
        for (const waiter of watch.waiters) waiter.resolve({ closedCode: event.code })
      })
      socket.addEventListener('error', () => {
        watch.closedCode = 1006
        for (const waiter of watch.waiters) waiter.resolve({ closedCode: 1006 })
      })
      return { id, ...await waitFor(watch, () => true) }
    },
    async seatWatchWait({ id, remainingSeats }) {
      const watch = watches.get(id)
      if (!watch) throw new Error('The test socket is not open.')
      return waitFor(watch, update => update.remainingSeats === remainingSeats)
    },
    seatWatchClose(id) {
      const watch = watches.get(id)
      if (watch) watch.socket.close()
      watches.delete(id)
      return null
    },
  }
}
