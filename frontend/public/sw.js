// Trafonsjakk service worker. Deliberately minimal: the app always loads from the network, so a new
// deploy shows up straight away and nobody sees stale ratings. The only thing kept offline is a short
// page that says there's no connection, shown instead of the browser's own error when a page can't load.
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
