// ============================================================================
// Frost Auth Server - Database Layer
// Uses Node.js built-in node:sqlite (Node 22.5+, no native compilation)
// All DB calls are synchronous under the hood, wrapped in async for the API
// ============================================================================
const { DatabaseSync } = require('node:sqlite');
const path = require('path');
const crypto = require('./crypto');

const dbPath = path.resolve(__dirname, 'frost_auth.db');
const db = new DatabaseSync(dbPath);

// ---- Schema Setup ----
db.exec(`
    CREATE TABLE IF NOT EXISTS licenses (
        key TEXT PRIMARY KEY,
        plan TEXT NOT NULL,
        discord_id TEXT,
        hwid TEXT,
        redeemed_at INTEGER,
        expires_at INTEGER,
        created_at INTEGER DEFAULT (strftime('%s','now') * 1000),
        is_active INTEGER DEFAULT 1
    );
    CREATE TABLE IF NOT EXISTS sessions (
        token TEXT PRIMARY KEY,
        license_key TEXT NOT NULL,
        hwid TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        expires_at INTEGER NOT NULL,
        used INTEGER DEFAULT 0
    );
    CREATE TABLE IF NOT EXISTS used_nonces (
        nonce_hex TEXT PRIMARY KEY,
        used_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS rate_limits (
        ip TEXT NOT NULL,
        attempt_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_rate_ip ON rate_limits(ip, attempt_at);
    CREATE INDEX IF NOT EXISTS idx_nonce_time ON used_nonces(used_at);
    CREATE INDEX IF NOT EXISTS idx_session_expire ON sessions(expires_at);
`);

// ---- Cleanup every 5 minutes ----
setInterval(() => {
    const now = Date.now();
    db.prepare(`DELETE FROM used_nonces WHERE used_at < ?`).run(now - 300000);
    db.prepare(`DELETE FROM rate_limits WHERE attempt_at < ?`).run(now - 60000);
    db.prepare(`DELETE FROM sessions WHERE expires_at < ?`).run(now);
}, 300000);

// ============================================================================
// LICENSE MANAGEMENT
// ============================================================================

async function generateLicense(plan) {
    const key = crypto.generateLicenseKey(plan);
    db.prepare(`INSERT INTO licenses (key, plan) VALUES (?, ?)`).run(key, plan);
    return key;
}

async function redeemLicense(key, discordId) {
    const row = db.prepare(`SELECT * FROM licenses WHERE key = ? AND is_active = 1`).get(key);
    if (!row) return { success: false, message: 'Invalid license key' };
    if (row.discord_id) return { success: false, message: 'License already redeemed' };

    const now = Date.now();
    const expiresAt = row.plan === 'Monthly' ? now + 30 * 24 * 60 * 60 * 1000 : null;

    db.prepare(`UPDATE licenses SET discord_id = ?, redeemed_at = ?, expires_at = ? WHERE key = ?`)
      .run(discordId, now, expiresAt, key);
    return { success: true, plan: row.plan };
}

async function revokeLicense(key) {
    const result = db.prepare(`UPDATE licenses SET is_active = 0 WHERE key = ?`).run(key);
    return { success: result.changes > 0 };
}

async function resetHwid(key) {
    const result = db.prepare(`UPDATE licenses SET hwid = NULL WHERE key = ?`).run(key);
    return { success: result.changes > 0 };
}

async function getLicenseByDiscordId(discordId) {
    const row = db.prepare(`SELECT key FROM licenses WHERE discord_id = ? AND is_active = 1`).get(discordId);
    return row ? row.key : null;
}

// ============================================================================
// AUTH VALIDATION
// ============================================================================

async function checkRateLimit(ip, maxAttempts = 10, windowMs = 60000) {
    const cutoff = Date.now() - windowMs;
    const row = db.prepare(`SELECT COUNT(*) as count FROM rate_limits WHERE ip = ? AND attempt_at > ?`).get(ip, cutoff);
    return (row?.count || 0) < maxAttempts;
}

async function recordAttempt(ip) {
    db.prepare(`INSERT INTO rate_limits (ip, attempt_at) VALUES (?, ?)`).run(ip, Date.now());
}

async function isNonceUsed(nonceHex) {
    const row = db.prepare(`SELECT 1 FROM used_nonces WHERE nonce_hex = ?`).get(nonceHex);
    return !!row;
}

async function markNonceUsed(nonceHex) {
    db.prepare(`INSERT OR IGNORE INTO used_nonces (nonce_hex, used_at) VALUES (?, ?)`).run(nonceHex, Date.now());
}

async function validateLicenseAndHwid(license, hwid) {
    const row = db.prepare(`SELECT * FROM licenses WHERE key = ? AND is_active = 1`).get(license);
    if (!row) return { valid: false };
    if (!row.discord_id) return { valid: false };
    if (row.expires_at && Date.now() > row.expires_at) return { valid: false };

    if (!row.hwid) {
        db.prepare(`UPDATE licenses SET hwid = ? WHERE key = ?`).run(hwid, license);
        return { valid: true, plan: row.plan, discordId: row.discord_id };
    }
    if (row.hwid !== hwid) return { valid: false };
    return { valid: true, plan: row.plan, discordId: row.discord_id };
}

// ============================================================================
// SESSION MANAGEMENT
// ============================================================================

async function createSession(licenseKey, hwid) {
    const token = require('crypto').randomBytes(32).toString('hex');
    const now = Date.now();
    db.prepare(`INSERT INTO sessions (token, license_key, hwid, created_at, expires_at) VALUES (?, ?, ?, ?, ?)`)
      .run(token, licenseKey, hwid, now, now + 30000);
    return token;
}

async function validateSession(token, hwid) {
    const row = db.prepare(
        `SELECT * FROM sessions WHERE token = ? AND hwid = ? AND used = 0 AND expires_at > ?`
    ).get(token, hwid, Date.now());
    if (!row) return false;
    db.prepare(`UPDATE sessions SET used = 1 WHERE token = ?`).run(token);
    return true;
}

module.exports = {
    generateLicense, redeemLicense, revokeLicense, resetHwid, getLicenseByDiscordId,
    checkRateLimit, recordAttempt, isNonceUsed, markNonceUsed,
    validateLicenseAndHwid, createSession, validateSession
};
