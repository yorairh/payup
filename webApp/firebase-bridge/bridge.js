// The Firebase side of shared games on the web (docs/SHARED_GAMES.md). The Kotlin code (WebGameSync.kt) talks to this
// file with plain strings only: games go in and out as JSON text, and every call answers with JSON text instead of
// throwing, so no JavaScript objects cross into Kotlin.
//
// Answers look like {"ok":true,"value":...} or {"ok":false,"kind":"offline"|"denied"|"other","message":"..."}.

import { initializeApp } from 'firebase/app';
import { getAuth, signInAnonymously } from 'firebase/auth';
import { deleteDoc, doc, getFirestore, onSnapshot, runTransaction } from 'firebase/firestore';

// These identify the Firebase project and are meant to be public; what protects the data is firestore.rules.
const config = {
    apiKey: 'AIzaSyBXHxTi93uyWDVXlBBu9VTllYNBDlYoCmY',
    authDomain: 'payup-ae6fc.firebaseapp.com',
    projectId: 'payup-ae6fc',
    appId: '1:314248731710:web:a0cc055a123e7f8b4fc56f',
};

let started;
function firebase() {
    if (!started) {
        const app = initializeApp(config);
        started = { auth: getAuth(app), db: getFirestore(app) };
    }
    return started;
}

function kindOf(error) {
    const code = String(error && error.code);
    if (code.endsWith('unavailable') || code.endsWith('deadline-exceeded') || code.endsWith('network-request-failed')) return 'offline';
    if (code.endsWith('permission-denied') || code.endsWith('unauthenticated')) return 'denied';
    return 'other';
}

function failure(error) {
    return JSON.stringify({ ok: false, kind: kindOf(error), message: String((error && error.message) || error) });
}

async function attempt(block) {
    try {
        return JSON.stringify({ ok: true, value: await block() });
    } catch (error) {
        return failure(error);
    }
}

async function uid() {
    const { auth } = firebase();
    await auth.authStateReady();
    return auth.currentUser ? auth.currentUser.uid : (await signInAnonymously(auth)).user.uid;
}

const gameRef = id => doc(firebase().db, 'games', id);

/** Cryptographically secure random bytes as hex text. */
export function randomHex(count) {
    const bytes = crypto.getRandomValues(new Uint8Array(count));
    return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
}

export function signIn() {
    return attempt(uid);
}

/** Creates the game (revision 1), or writes the next revision of a game this user hosts, in one transaction. */
export function publish(id, hostKeyHash, contentJson) {
    return attempt(async () => {
        const me = await uid();
        const ref = gameRef(id);
        await runTransaction(firebase().db, async tx => {
            const existing = await tx.get(ref);
            const data = { ...JSON.parse(contentJson), hostUid: me };
            if (existing.exists()) {
                data.revision = existing.data().revision + 1;
                data.hostKeyHash = existing.data().hostKeyHash;
            } else {
                data.revision = 1;
                data.hostKeyHash = hostKeyHash;
            }
            tx.set(ref, data);
        });
        return null;
    });
}

export function deleteGame(id) {
    return attempt(async () => {
        await uid();
        const ref = gameRef(id);
        await runTransaction(firebase().db, async tx => { tx.delete(ref); });
        return null;
    });
}

export function takeOver(id, hostKey) {
    return attempt(async () => {
        const me = await uid();
        const { db } = firebase();
        const claim = doc(db, 'games', id, 'claims', me);
        await runTransaction(db, async tx => { tx.set(claim, { key: hostKey }); });
        await runTransaction(db, async tx => {
            const existing = await tx.get(gameRef(id));
            if (!existing.exists()) throw new Error('The game is gone');
            tx.set(gameRef(id), { ...existing.data(), revision: existing.data().revision + 1, hostUid: me });
        });
        // Tidying up only; the rules do not depend on the claim being removed.
        try { await deleteDoc(claim); } catch (ignored) { /* nothing to do */ }
        return null;
    });
}

const listeners = new Map();
let nextListener = 1;

/**
 * Starts a live listener on a game; [onEvent] gets JSON texts like {"state":"found","fromCache":false,"doc":{...}},
 * {"state":"notfound"} or {"state":"failed","kind":...,"message":...}. Returns a number for [unwatch].
 */
export function watch(id, onEvent) {
    const handle = nextListener++;
    const send = event => onEvent(JSON.stringify(event));
    listeners.set(handle, () => {});
    uid().then(() => {
        if (!listeners.has(handle)) return;
        const stop = onSnapshot(
            gameRef(id),
            { includeMetadataChanges: true },
            snapshot => {
                if (snapshot.exists()) {
                    send({ state: 'found', fromCache: snapshot.metadata.fromCache, doc: snapshot.data() });
                } else if (!snapshot.metadata.fromCache) {
                    // A missing document in the saved copy only means "not known yet" while the server has not answered.
                    send({ state: 'notfound' });
                }
            },
            error => send({ state: 'failed', kind: kindOf(error), message: String(error.message) }),
        );
        if (listeners.has(handle)) listeners.set(handle, stop); else stop();
    }, error => send({ state: 'failed', kind: kindOf(error), message: String(error.message) }));
    return handle;
}

export function unwatch(handle) {
    const stop = listeners.get(handle);
    listeners.delete(handle);
    if (stop) stop();
}
