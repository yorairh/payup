// The Web Worker behind Room's WebWorkerSQLiteDriver: it runs SQLite (compiled to WebAssembly) and keeps the
// database file in the browser's private file storage (OPFS).
//
// Based on the example worker in https://github.com/danysantiago/room-web-demo (Apache 2.0), with two changes:
//  - it uses SQLite's "opfs-sahpool" file system, which works without the COOP/COEP headers (cross-origin isolation)
//    that the plain "opfs" file system needs, so it runs on any static host and on iPhone Safari;
//  - every close request gets an answer, and the per-message logging is gone.
// The sahpool file system holds an exclusive lock on its files, so only one tab can open the database. The app
// guards against a second tab before it gets here (see SingleTabGuard.kt).
import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

let sqlite3 = null;
let pool = null;

// Active database connections and prepared statements, by the ids sent back to the driver.
const databases = new Map();
const statements = new Map();
let nextDatabaseId = 0;
let nextStatementId = 0;

async function ensureReady() {
    if (!sqlite3) sqlite3 = await sqlite3InitModule();
    if (!pool) pool = await sqlite3.installOpfsSAHPoolVfs({ name: 'payup', initialCapacity: 6 });
}

function fail(id, error) {
    postMessage({ id: id, error: (error && error.message) ? error.message : String(error) });
}

async function openRequest(id, requestData) {
    try {
        await ensureReady();
        const fileName = requestData.fileName.startsWith('/') ? requestData.fileName : '/' + requestData.fileName;
        const databaseId = nextDatabaseId++;
        databases.set(databaseId, new pool.OpfsSAHPoolDb(fileName));
        postMessage({ id: id, data: { databaseId: databaseId } });
    } catch (error) {
        fail(id, error);
    }
}

function prepareRequest(id, requestData) {
    try {
        const database = databases.get(requestData.databaseId);
        if (!database) return fail(id, 'Invalid database ID: ' + requestData.databaseId);
        const statementId = nextStatementId++;
        const statement = database.prepare(requestData.sql);
        statements.set(statementId, statement);
        const columnNames = [];
        for (let i = 0; i < statement.columnCount; i++) {
            columnNames.push(sqlite3.capi.sqlite3_column_name(statement, i));
        }
        postMessage({
            id: id,
            data: {
                statementId: statementId,
                parameterCount: sqlite3.capi.sqlite3_bind_parameter_count(statement),
                columnNames: columnNames,
            },
        });
    } catch (error) {
        fail(id, error);
    }
}

function stepRequest(id, requestData) {
    const statement = statements.get(requestData.statementId);
    if (!statement) return fail(id, 'Invalid statement ID: ' + requestData.statementId);
    try {
        const result = { rows: [], columnTypes: [] };
        statement.reset();
        statement.clearBindings();
        for (let i = 0; i < requestData.bindings.length; i++) {
            statement.bind(i + 1, requestData.bindings[i]);
        }
        while (statement.step()) {
            if (!result.columnTypes.length) {
                for (let i = 0; i < statement.columnCount; i++) {
                    result.columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, i));
                }
            }
            result.rows.push(statement.get([]));
        }
        postMessage({ id: id, data: result });
    } catch (error) {
        fail(id, error);
    }
}

function closeRequest(id, requestData) {
    try {
        if (requestData.statementId !== undefined && requestData.statementId !== null) {
            const statement = statements.get(requestData.statementId);
            if (!statement) return fail(id, 'Invalid statement ID: ' + requestData.statementId);
            statement.finalize();
            statements.delete(requestData.statementId);
        }
        if (requestData.databaseId !== undefined && requestData.databaseId !== null) {
            const database = databases.get(requestData.databaseId);
            if (!database) return fail(id, 'Invalid database ID: ' + requestData.databaseId);
            database.close();
            databases.delete(requestData.databaseId);
        }
        postMessage({ id: id, data: {} });
    } catch (error) {
        fail(id, error);
    }
}

const commandMap = { open: openRequest, prepare: prepareRequest, step: stepRequest, close: closeRequest };

// Requests are handled one after another, so an open always finishes before the first prepare.
let queue = Promise.resolve();
onmessage = (e) => {
    const request = e.data;
    if (!request || !request.data || request.data.cmd == null) {
        fail(request && request.id, "Invalid request, missing 'data' or 'cmd'.");
        return;
    }
    const handler = commandMap[request.data.cmd];
    if (!handler) {
        fail(request.id, "Invalid request, unknown command: '" + request.data.cmd + "'.");
        return;
    }
    queue = queue.then(() => handler(request.id, request.data));
};
