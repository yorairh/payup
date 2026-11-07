// PayUp's service worker: after the first visit the app opens without a connection.
//  - When it installs it caches every file of the build, listed in precache.json (written by the Gradle build).
//  - The big WebAssembly files have a hash in their name, so they never change: they are served from the cache.
//  - Everything else (index.html, payup.js, the code chunks, strings, fonts) is fetched from the network first, so a new
//    version is picked up as soon as it is deployed, and the cached copy is used when the network is missing or slow.
// A new version of this file takes over the next time the app is opened.
const CACHE = 'payup-v1';
const SLOW_NETWORK_MS = 5000;

async function precache() {
    try {
        const list = await (await fetch('precache.json', { cache: 'no-cache' })).json();
        const cache = await caches.open(CACHE);
        const wanted = ['./', ...list].map(path => new URL(path, self.registration.scope).href);
        await Promise.all(wanted.map(async url => {
            try {
                const response = await fetch(url);
                if (response.ok) await cache.put(url, response);
            } catch (e) { /* a missing file is fetched later, when it is needed */ }
        }));
        // Files of older versions (their hashed names are gone from the list) are removed.
        const keep = new Set(wanted);
        for (const request of await cache.keys()) {
            const url = new URL(request.url);
            if (url.pathname !== new URL(self.registration.scope).pathname && !keep.has(url.origin + url.pathname)) {
                await cache.delete(request);
            }
        }
    } catch (e) { /* offline at install time: the cache fills while the app is used */ }
}

self.addEventListener('install', event => {
    self.skipWaiting();
    event.waitUntil(precache());
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key))))
            .then(() => self.clients.claim()),
    );
});

const isHashedAsset = url => /[0-9a-f]{16,}\.wasm$/.test(url.pathname);

async function cacheFirst(request) {
    const cache = await caches.open(CACHE);
    const cached = await cache.match(request);
    if (cached) return cached;
    const response = await fetch(request);
    if (response.ok) cache.put(request, response.clone());
    return response;
}

async function networkFirst(request) {
    const cache = await caches.open(CACHE);
    // 'no-cache' makes the browser check with the server, so an old copy in the HTTP cache is never used.
    const network = fetch(new Request(request, { cache: 'no-cache' })).then(response => {
        if (response.ok) cache.put(request, response.clone());
        return response;
    });
    const timeout = new Promise((_, reject) => setTimeout(reject, SLOW_NETWORK_MS));
    try {
        return await Promise.race([network, timeout]);
    } catch (e) {
        const cached = await cache.match(request, { ignoreSearch: true });
        if (cached) return cached;
        return network; // nothing cached: keep waiting for the network
    }
}

self.addEventListener('fetch', event => {
    const request = event.request;
    if (request.method !== 'GET') return;
    const url = new URL(request.url);
    if (url.origin !== self.location.origin) return;
    event.respondWith(isHashedAsset(url) ? cacheFirst(request) : networkFirst(request));
});
