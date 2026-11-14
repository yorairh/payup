# Shared games: the online data (roadmap task 22)

Decisions for sharing a live game by link. Rules, sync code and screens come in tasks 23 to 28.

## Where a game lives
- Firebase project `payup-ae6fc`, Firestore, one document per game: `games/{id}`. It holds the whole game and is rewritten
  whole by the host on every change, so viewers get atomic updates with a single live listener.
- `revision` goes up by one on every write; `schemaVersion` (1 now) lets later app versions read old games.
- Fields: the game settings (`startedAt`, `endedAt`, `defaultBuyIn`, `finished`, `voidedDifference`, `chipValue`,
  `roundToWhole`, `currency`, dealer as a position in `players`), `players` in table order (name, buy-ins with amount and
  time, `chips`, `finalAmount`, `net`), `transfers` (from name, to name, amount), plus `hostUid`, `hostKeyHash`, `revision`,
  `schemaVersion`.
- Not uploaded: the friends list, local database ids, the card-deal state, other games.

## Identity and links
- The id is 128 random bits written as 22 URL-safe characters: unguessable, and games cannot be listed.
- Viewer link: `https://payup.web.app/g/<id>` (a real path, so Android can open it in the app, task 29).
- Host link: `https://payup.web.app/h/<id>#<hostKey>`. The host key is 128 random bits after the `#`, which browsers
  never send to a server. The document stores only a hash of it (`hostKeyHash`), never the key.
- The host is the anonymous Firebase user of the device (`hostUid`). A host who reinstalls or clears the browser takes
  control back with the host link; opening it on another device hands the game over (task 27).

## On the device (database version 5)
`games` gets `shareId` (null = never shared), `isHost` (default false), `hostKey` (null except on the host's devices), and a
unique index on `shareId`, which is what keeps a watched game from being saved twice. Backups do not include these three, so
a restored game is always a plain local game.

## Watched games
When a game you watched finishes it is saved into your history once (`shareId` set, `isHost` false). It then counts in the
leaderboard like any finished game. Names are matched to your friends only when identical. Viewers cannot claim a player.

## Sync code (task 24)
- `shared/.../sync/`: `GameSync` (`signIn`, `publish`, `watch`, `delete`, `takeOver`), `SharedGame` (the online shape, JSON with absent
  fields left out), `OnlineGame` (game + revision + host), `toSharedGame()` / `toGameWithDetails()` (to and from the screens' game),
  `ShareIds` (22-character ids and host keys from a platform `RandomBytes`), `sha256Hex`.
- Games cross the platform seam as JSON text. Android (`androidApp/.../FirebaseGameSync.kt`) turns it into maps for the Firebase Android
  SDK (`google-services.json` is committed: the identifiers are public, the rules protect the data). Web (`webApp/firebase-bridge/bridge.js`)
  holds the Firebase JS SDK and the public web config, and answers with JSON text; `WebGameSync.kt` wraps it.
- `publish` is a transaction: a missing game is created with revision 1, otherwise the next revision is written. It fails when offline
  (`GameSyncException.Kind.Offline`), so the caller shows "offline" and retries; the latest state is all that is ever uploaded.
- `watch` ignores a "missing" answer that only comes from the saved copy, so offline never looks like "deleted".

## Security rules (task 23)
`firebase/firestore.rules`, tested by `firebase/test/rules.test.js` on the local emulator (`cd firebase`, `npm install`, `npm test`;
needs JDK 21 and Node). A GitHub check runs them on pull requests that touch the rules.
- Read: any signed-in (anonymous) user can `get` one game; `list` is never allowed.
- Create: signed in, `hostUid` is the caller, `revision` is 1, the shape is valid.
- Update/delete: only the current `hostUid`; each update raises `revision` by exactly 1 and cannot change `hostKeyHash`.
- Takeover: the new host first creates `games/{id}/claims/{theirUid}` holding the key; the rules accept it only if its SHA-256
  (hex) equals `hostKeyHash`; then they update the game with themselves as `hostUid`. Claims can only be read or deleted by their
  owner and cannot be listed. The client deletes its claim afterwards (clean-up, not security).
- Limits: at most 20 players and 400 transfers, `currency` up to 8 characters, amounts in a sane range, no unknown fields.
  Firestore rules cannot loop over lists, so the contents of `players` and `transfers` are not checked item by item.
- Deploying the rules is done by hand (`firebase deploy --only firestore:rules`) after the tests pass; the GitHub deploy key only
  publishes the website.
