# Spike B: upgrading the database from Room 2.6.1 to Room 3

Roadmap task 5. A throwaway port, on branch `spike-b-room3` (never merged), to prove that Room 3 can take over the database that is already on people's phones without losing games.

## Result so far

| Check | Result |
|---|---|
| Builds with Room 3.0.3 | Yes, on Kotlin 2.1.20 + KSP 2.1.20-2.0.1. AGP 8.10.1 and Gradle 8.11.1 stay as they are |
| All 106 unit tests pass | Yes |
| Schema identity hash (this is what Room checks when it opens an existing file) | **Identical**: `b7603b3068ff59a68591ae1cbf73a278` in both the Room 2.6.1 and the Room 3 version 4 schema |
| `CREATE TABLE` SQL, indices, columns | **Identical** for all 5 tables. The only difference is that Room 3's JSON leaves out empty lists and `"notNull": false` |
| Database file location | Same: Room 3 resolves a named database through `Context.getDatabasePath` |
| APK size | 8.17 MB with Room 2.6.1, 8.32 MB with Room 3 (+0.15 MB), using the phone's own SQLite driver |
| **Opens a real database on a phone** | **Waiting for the phone test below** |

The identical hash and SQL is strong evidence, but only a real file proves it.

## What had to change

- **Toolchain:** Kotlin 2.0.21 → 2.1.20, KSP 2.0.21-1.0.28 → 2.1.20-2.0.1.
- **Dependencies:** `androidx.room3:room3-runtime` and `room3-compiler` 3.0.3. There is no `room-ktx` any more.
- **Imports:** `androidx.room.*` → `androidx.room3.*`.
- **Database builder:** `Room.databaseBuilder<AppDatabase>(context, "payup.db").setDriver(AndroidSQLiteDriver())`. A driver is mandatory; this one uses the phone's own SQLite, so the file format is unchanged.
- **Migrations:** `migrate(connection: SQLiteConnection)` is now `suspend` and uses `connection.execSQL(...)` from `androidx.sqlite`. The SQL text of all four migrations is unchanged.
- **Transactions:** `db.withTransaction { }` → `db.withWriteTransaction { }` (and its `return@` labels).
- **`@Relation`:** `parentColumn`/`entityColumn` (single names) → `parentColumns`/`entityColumns` (lists). Room 3 refuses the old form with "Cannot have empty 'parentColumns'".
- **Nothing else:** the DAO code, entities, queries, `@Transaction`, `@Embedded` and Flow returns all compiled as they were.

## Findings that change the plan

1. **The toolchain upgrade and the Room upgrade cannot be separated.** Room 2.6.1 fails to build on Kotlin 2.1.20 with KSP 2 (`unexpected jvm signature V`), and the newer KSP is KSP 2 only. So roadmap tasks 7 and 9 become one step: raise the toolchain and move to Room 3 together.
2. **Kotlin 2.1.20 is enough for Room 3.0.3**, but Spike A's web build needs Kotlin 2.4.x, and Compose Multiplatform needs its own versions. The final toolchain is chosen when task 7 starts.
3. **The migrations are the only code with real risk,** and they can't be tested automatically here (the instrumented test needs a device). Their SQL is unchanged, so the review is a diff.

## Phone test (you, about 10 minutes)

The two test apps install **next to** your real PayUp under a different id (`com.payup.spike`, labelled "PayUp Spike"). Your real app and its games are never touched.

1. **Safety copy:** in your real PayUp, Settings, Backup, Save backup to phone.
2. **Install app A** (`PayUp-Spike-A-Room2.6.apk`). It is today's code with Room 2.6.1. It appears as "PayUp Spike".
3. **Restore into A:** open PayUp Spike, Settings, Backup, Restore games, pick the backup, Add games. Note how many games History shows, and open one result screen and the Leaderboard.
4. **Upgrade to Room 3:** install **app B** (`PayUp-Spike-B-Room3.apk`) over it (Android says "Update"). Open PayUp Spike. **Expected:** the same games, the same Leaderboard, no crash. Then start and finish a new game.
5. **Backup round trip in B:** Backup, Save backup to phone, then Restore games with that file. It should say everything is already on the phone.
6. **Fresh Room 3 database:** uninstall PayUp Spike, install B again, restore the 5-game sample (`payup-sample-5-games.json`), and check History.

Tell me: did step 4 open normally with all games? If the app crashes or shows an empty History, the most likely cause is a schema check failing, and that is the result we are looking for. Don't uninstall in that case; tell me first.

## Decision for task 6

Room 3 on Android is a go if step 4 passes. It is a no-go for this approach if the Room 2.6.1 file doesn't open; then the options are the Room 2.8 intermediate step or a one-time export and import through the backup file (task 2 already exists for exactly this).
