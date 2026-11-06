// Service worker de la app instalable (PWA / APK).
// - /assets/* (archivos con hash, nunca cambian): primero la cache, la app abre rapido.
// - Paginas (/libros, /prestamos...): primero la red; sin internet, la ultima version guardada.
// - /api/* y todo lo que no sea GET: SIEMPRE a la red. Nunca se guardan datos ni sesiones.
const CACHE = 'biblioteca-v1';

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(['/', '/manifest.json', '/icon-192.png'])));
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((claves) => Promise.all(claves.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
  );
  self.clients.claim();
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;

  if (url.pathname.startsWith('/assets/')) {
    e.respondWith(
      caches.match(req).then((guardado) => guardado || fetch(req).then((res) => {
        const copia = res.clone();
        if (res.ok) caches.open(CACHE).then((c) => c.put(req, copia));
        return res;
      }))
    );
    return;
  }

  if (req.mode === 'navigate') {
    e.respondWith(
      fetch(req)
        .then((res) => {
          const copia = res.clone();
          if (res.ok) caches.open(CACHE).then((c) => c.put('/', copia));
          return res;
        })
        .catch(() => caches.match('/'))
    );
  }
});
