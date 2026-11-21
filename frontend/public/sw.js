// Trafonsjakk service worker. Deliberately minimal: the app always loads from the network, so a new
// deploy shows up straight away and nobody sees stale ratings. The only thing kept offline is a short
// page that says there's no connection, shown instead of the browser's own error when a page can't load.
// It also shows push notifications ("Ola logged a game against you") and opens the page when one is tapped.
const CACHE = 'trafonsjakk-offline-v1'
const OFFLINE = '/offline.html'

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.add(new Request(OFFLINE, { cache: 'reload' }))))
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  // Only page loads; API calls, scripts and images go straight to the network as usual
  if (event.request.mode !== 'navigate') return
  event.respondWith(fetch(event.request).catch(() => caches.match(OFFLINE)))
})

self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    // Not ours, or empty: show a plain notification
  }
  event.waitUntil(
    self.registration.showNotification(data.title || 'Trafonsjakk', {
      body: data.body || '',
      tag: data.tag,
      icon: '/icon-192.png',
      badge: '/icon-192.png',
      data: { url: typeof data.url === 'string' ? data.url : '/' },
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = new URL(event.notification.data?.url || '/', self.location.origin)
  // Only ever our own pages
  if (url.origin !== self.location.origin) return
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((open) => {
      const tab = open.find((c) => 'focus' in c)
      if (tab) return tab.navigate(url.href).then((c) => (c ?? tab).focus())
      return self.clients.openWindow(url.href)
    }),
  )
})
