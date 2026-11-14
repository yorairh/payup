// Tests for firestore.rules, run on the local Firestore emulator (npm test).
import { after, afterEach, before, describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, getDocs, collection, setDoc, updateDoc } from 'firebase/firestore';

const KEY = 'hostKeyHostKeyHostKey22';
const KEY_HASH = createHash('sha256').update(KEY).digest('hex');
const GAME = 'game-id-game-id-game-id';

let env;

before(async () => {
    env = await initializeTestEnvironment({
        projectId: 'demo-payup',
        firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8089 },
    });
});
after(async () => { await env.cleanup(); });
afterEach(async () => { await env.clearFirestore(); });

const game = (overrides = {}) => ({
    schemaVersion: 1,
    revision: 1,
    hostUid: 'host',
    hostKeyHash: KEY_HASH,
    startedAt: 1000,
    defaultBuyIn: 5000,
    finished: false,
    roundToWhole: true,
    currency: 'ILS',
    players: [{ name: 'Dana', buyIns: [{ amount: 5000, at: 1000 }] }],
    transfers: [],
    ...overrides,
});

const as = uid => env.authenticatedContext(uid).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();
const gameDoc = db => doc(db, 'games', GAME);

/** Puts a game in the database without going through the rules. */
async function seed(data = game()) {
    await env.withSecurityRulesDisabled(async ctx => { await setDoc(gameDoc(ctx.firestore()), data); });
}

describe('reading', () => {
    it('lets a signed-in viewer read one game by its id', async () => {
        await seed();
        await assertSucceeds(getDoc(gameDoc(as('viewer'))));
    });
    it('does not let anyone list the games', async () => {
        await seed();
        await assertFails(getDocs(collection(as('viewer'), 'games')));
    });
    it('does not let a signed-out user read', async () => {
        await seed();
        await assertFails(getDoc(gameDoc(anonymous())));
    });
});

describe('creating', () => {
    it('lets a user create a game with themselves as host', async () => {
        await assertSucceeds(setDoc(gameDoc(as('host')), game()));
    });
    it('accepts the chip share setting only as a boolean', async () => {
        await assertSucceeds(setDoc(gameDoc(as('host')), game({ chipsByShare: true })));
        await assertFails(setDoc(gameDoc(as('host')), game({ chipsByShare: 'yes' })));
    });
    it('rejects a game that names someone else as host', async () => {
        await assertFails(setDoc(gameDoc(as('other')), game()));
    });
    it('rejects a first revision other than 1', async () => {
        await assertFails(setDoc(gameDoc(as('host')), game({ revision: 5 })));
    });
    it('rejects a signed-out user', async () => {
        await assertFails(setDoc(gameDoc(anonymous()), game()));
    });
    it('rejects an unknown field, a bad hash, too many players and a long currency', async () => {
        await assertFails(setDoc(gameDoc(as('host')), game({ extra: 1 })));
        await assertFails(setDoc(gameDoc(as('host')), game({ hostKeyHash: 'abc' })));
        await assertFails(setDoc(gameDoc(as('host')), game({ players: Array.from({ length: 21 }, (_, i) => ({ name: `P${i}` })) })));
        await assertFails(setDoc(gameDoc(as('host')), game({ currency: 'TOOLONGCURRENCY' })));
    });
    it('rejects a missing required field and a wrong type', async () => {
        const { players, ...withoutPlayers } = game();
        await assertFails(setDoc(gameDoc(as('host')), withoutPlayers));
        await assertFails(setDoc(gameDoc(as('host')), game({ finished: 'no' })));
        await assertFails(setDoc(gameDoc(as('host')), game({ defaultBuyIn: -1 })));
    });
});

describe('updating', () => {
    it('lets the host update with the next revision', async () => {
        await seed();
        await assertSucceeds(setDoc(gameDoc(as('host')), game({ revision: 2, finished: true, endedAt: 2000 })));
    });
    it('rejects a revision that is not the next one', async () => {
        await seed();
        await assertFails(setDoc(gameDoc(as('host')), game({ revision: 3 })));
        await assertFails(setDoc(gameDoc(as('host')), game({ revision: 1 })));
    });
    it('rejects an update from a viewer', async () => {
        await seed();
        await assertFails(setDoc(gameDoc(as('viewer')), game({ revision: 2 })));
    });
    it('rejects a viewer making themselves host without a claim', async () => {
        await seed();
        await assertFails(setDoc(gameDoc(as('viewer')), game({ revision: 2, hostUid: 'viewer' })));
    });
    it('does not let the host change the key hash', async () => {
        await seed();
        await assertFails(setDoc(gameDoc(as('host')), game({ revision: 2, hostKeyHash: '0'.repeat(64) })));
    });
});

describe('deleting', () => {
    it('lets the host delete', async () => {
        await seed();
        await assertSucceeds(deleteDoc(gameDoc(as('host'))));
    });
    it('does not let a viewer delete', async () => {
        await seed();
        await assertFails(deleteDoc(gameDoc(as('viewer'))));
    });
});

describe('taking over', () => {
    const claim = (db, uid, key) => setDoc(doc(db, 'games', GAME, 'claims', uid), { key });

    it('accepts a claim with the right key, then lets the claimer take over', async () => {
        await seed();
        const db = as('newHost');
        await assertSucceeds(claim(db, 'newHost', KEY));
        await assertSucceeds(setDoc(gameDoc(db), game({ revision: 2, hostUid: 'newHost' })));
    });
    it('rejects a claim with the wrong key', async () => {
        await seed();
        await assertFails(claim(as('newHost'), 'newHost', 'wrongKeyWrongKeyWrongKey'));
    });
    it('rejects a claim written for someone else', async () => {
        await seed();
        await assertFails(claim(as('mallory'), 'newHost', KEY));
    });
    it('stops the old host after a takeover', async () => {
        await seed();
        await assertSucceeds(claim(as('newHost'), 'newHost', KEY));
        await assertSucceeds(setDoc(gameDoc(as('newHost')), game({ revision: 2, hostUid: 'newHost' })));
        await assertFails(setDoc(gameDoc(as('host')), game({ revision: 3, hostUid: 'newHost' })));
    });
    it('keeps claims private', async () => {
        await seed();
        await assertSucceeds(claim(as('newHost'), 'newHost', KEY));
        await assertSucceeds(getDoc(doc(as('newHost'), 'games', GAME, 'claims', 'newHost')));
        await assertFails(getDoc(doc(as('viewer'), 'games', GAME, 'claims', 'newHost')));
        await assertFails(getDocs(collection(as('newHost'), 'games', GAME, 'claims')));
    });
    it('lets the claimer remove their claim', async () => {
        await seed();
        await assertSucceeds(claim(as('newHost'), 'newHost', KEY));
        await assertSucceeds(deleteDoc(doc(as('newHost'), 'games', GAME, 'claims', 'newHost')));
    });
});
