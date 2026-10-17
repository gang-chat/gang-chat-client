// Retires the Flutter web build's service worker.
//
// The Flutter build that used to be served here could register
// `flutter_service_worker.js`, which cached the app shell. Browsers re-fetch a
// registered worker's script when the page is visited; finding this one
// instead, they install it, and it deletes Flutter's caches, unregisters
// itself and reloads the open tabs, which then load this build from the
// network. The React build registers no service worker.
self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      // Cache storage is shared by the whole origin: only remove Flutter's.
      for (const key of await caches.keys()) {
        if (key.startsWith('flutter-')) await caches.delete(key);
      }
      await self.registration.unregister();
      const windows = await self.clients.matchAll({ type: 'window' });
      for (const client of windows) client.navigate(client.url);
    })(),
  );
});
