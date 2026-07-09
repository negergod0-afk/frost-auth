// ============================================================================
// FROST AUTH SERVER — Main Entry
// TCP Auth Server (Fragment Dispatch + ECDH Challenge-Response)
// HTTP API (Discord Bot Communication)
// ============================================================================
require('dotenv').config();
const net = require('net');
const nodeCrypto = require('crypto');
const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

// ── Persist FRAGMENT_SECRET on first boot ────────────────────────────────
// If FRAGMENT_SECRET is not in the environment we generate one and save it
// to .env so it survives restarts.  Changing it invalidates all sessions.
const ENV_PATH = path.resolve(__dirname, '..', '.env');
if (!process.env.FRAGMENT_SECRET) {
    const secret = nodeCrypto.randomBytes(32).toString('hex');
    process.env.FRAGMENT_SECRET = secret;
    try {
        const line = `\nFRAGMENT_SECRET=${secret}\n`;
        fs.appendFileSync(ENV_PATH, line, 'utf8');
        console.log('[Frost] Generated and persisted FRAGMENT_SECRET to .env');
    } catch (e) {
        console.warn('[Frost] Could not write .env — set FRAGMENT_SECRET env var manually!');
    }
}

const db = require('./db');
const cr = require('./crypto');

// ============================================================================
// HTTP API — For Discord Bot Only (Internal)
// ============================================================================
const app = express();
app.use(express.json());
app.use(cors());

const INTERNAL_API_KEY = process.env.INTERNAL_API_KEY;
if (!INTERNAL_API_KEY) {
    console.error('[Frost] FATAL: INTERNAL_API_KEY env var not set. Exiting.');
    process.exit(1);
}

function requireApiKey(req, res, next) {
    if (req.headers['x-api-key'] === INTERNAL_API_KEY) return next();
    return res.status(403).json({ e: 1 });
}

app.post('/api/generate', requireApiKey, async (req, res) => {
    try {
        const { plan } = req.body;
        if (!['Monthly', 'Lifetime'].includes(plan))
            return res.status(400).json({ success: false });
        const key = await db.generateLicense(plan);
        res.json({ success: true, key });
    } catch (err) { res.status(500).json({ success: false }); }
});

app.post('/api/redeem', requireApiKey, async (req, res) => {
    try {
        const { key, discordId } = req.body;
        res.json(await db.redeemLicense(key, discordId));
    } catch (err) { res.status(500).json({ success: false }); }
});

app.post('/api/revoke', requireApiKey, async (req, res) => {
    try {
        res.json(await db.revokeLicense(req.body.key));
    } catch (err) { res.status(500).json({ success: false }); }
});

app.post('/api/reset', requireApiKey, async (req, res) => {
    try {
        res.json(await db.resetHwid(req.body.key));
    } catch (err) { res.status(500).json({ success: false }); }
});

app.post('/api/license-by-discord', requireApiKey, async (req, res) => {
    try {
        const key = await db.getLicenseByDiscordId(req.body.discordId);
        res.json({ success: !!key, key });
    } catch (err) { res.status(500).json({ success: false }); }
});

// Session validation for mod double-auth
// Expects: { t: sessionToken (hex), h: hwid (hex) }
// Returns: { d: hmac(sessionToken) } on success, or { d: random } on failure.
// Shape is always identical so there is nothing to detect from the outside.
app.post('/api/session', async (req, res) => {
    try {
        const { t, h } = req.body;
        if (typeof t !== 'string' || t.length !== 64 ||
            typeof h !== 'string' || h.length !== 64) {
            return res.json({ d: nodeCrypto.randomBytes(32).toString('hex') });
        }
        // Rate-limit session checks the same as fragment endpoints
        const allowed = await db.checkRateLimit(req.ip, 30, 60000);
        if (!allowed) return res.json({ d: nodeCrypto.randomBytes(32).toString('hex') });
        await db.recordAttempt(req.ip);

        const valid = await db.validateSession(t, h);
        const ackSecret = process.env.FRAGMENT_SECRET || 'frost-session-ack';
        const payload = valid
            ? nodeCrypto.createHmac('sha256', ackSecret).update(t).digest('hex')
            : nodeCrypto.randomBytes(32).toString('hex');
        res.json({ d: payload });
    } catch (_err) {
        res.json({ d: nodeCrypto.randomBytes(32).toString('hex') });
    }
});

// ============================================================================
// FAKE "MICROSERVICE" FRAGMENTS via HTTP
// ============================================================================
app.get('/cdn-cgi/trace', async (req, res) => {
    // Fragment 1: CDN Integrity
    const hwidHex = req.query.h || '';
    if (hwidHex.length !== 64) return res.status(400).send('Bad Request');
    const hwidHash = Buffer.from(hwidHex, 'hex');
    const ip = req.ip;
    const allowed = await db.checkRateLimit(ip, 30, 60000);
    if (!allowed) return res.status(429).json({ error: 'Rate limited' });
    await db.recordAttempt(ip);
    
    const fragment = cr.computeFragment(1, hwidHash);
    res.json({
        colo: 'IAD',
        fl: '369f41',
        h: 'frostclient.net',
        ip: ip,
        ts: Date.now(),
        visit_scheme: 'https',
        uag: req.get('user-agent'),
        'cf-ray': fragment.toString('hex') + '-IAD' 
    });
});

app.post('/v1/metrics', async (req, res) => {
    // Fragment 2: Telemetry Sync
    const hwidHex = req.body.deviceId || '';
    if (hwidHex.length !== 64) return res.status(400).send('Bad Request');
    const hwidHash = Buffer.from(hwidHex, 'hex');
    const ip = req.ip;
    const allowed = await db.checkRateLimit(ip, 30, 60000);
    if (!allowed) return res.status(429).json({ error: 'Rate limited' });
    await db.recordAttempt(ip);

    const fragment = cr.computeFragment(2, hwidHash);
    res.json({
        success: true,
        traceId: 'trace_' + fragment.toString('hex')
    });
});

app.get('/api/v2/config', async (req, res) => {
    // Fragment 3: Config Fetch
    const hwidHex = req.query.v || '';
    if (hwidHex.length !== 64) return res.status(400).send('Bad Request');
    const hwidHash = Buffer.from(hwidHex, 'hex');
    const ip = req.ip;
    const allowed = await db.checkRateLimit(ip, 30, 60000);
    if (!allowed) return res.status(429).json({ error: 'Rate limited' });
    await db.recordAttempt(ip);

    const fragment = cr.computeFragment(3, hwidHash);
    res.json({
        version: "2.1.4",
        features: { telemetry: true, crash_reports: true },
        signature: fragment.toString('base64')
    });
});

app.post('/update/v1/check', async (req, res) => {
    // Fragment 4: Patch Verification
    const hwidHex = req.body.clientId || '';
    if (hwidHex.length !== 64) return res.status(400).send('Bad Request');
    const hwidHash = Buffer.from(hwidHex, 'hex');
    const ip = req.ip;
    const allowed = await db.checkRateLimit(ip, 30, 60000);
    if (!allowed) return res.status(429).json({ error: 'Rate limited' });
    await db.recordAttempt(ip);

    const fragment = cr.computeFragment(4, hwidHash);
    res.json({
        update_available: false,
        latest_version: "2.1.4",
        checksum: 'sha256-' + fragment.toString('hex')
    });
});

const API_PORT = process.env.PORT || 3000;
app.listen(API_PORT, () => {
    console.log(`[Frost] HTTP API on port ${API_PORT}`);
    console.log(`[Frost] INTERNAL_API_KEY loaded: ${INTERNAL_API_KEY ? 'yes' : 'NO — startup will fail'}`);
    console.log(`[Frost] FRAGMENT_SECRET loaded: yes`);
});

// ============================================================================
// MOD PAYLOAD
// ============================================================================
let MOD_PAYLOAD = null;
const MOD_PATH = path.resolve(__dirname, 'mod.jar');

function loadModPayload() {
    if (fs.existsSync(MOD_PATH)) {
        MOD_PAYLOAD = fs.readFileSync(MOD_PATH);
        console.log(`[Frost] Loaded mod: ${MOD_PAYLOAD.length} bytes`);
    } else {
        MOD_PAYLOAD = Buffer.concat([
            Buffer.from('PK\x03\x04'),
            Buffer.from('__FROST_WATERMARK_PLACEHOLDER_DO_NOT_REMOVE__'),
            nodeCrypto.randomBytes(2048),
            Buffer.from('__FROST_MOD_PAYLOAD_END__')
        ]);
        console.log(`[Frost] Test payload: ${MOD_PAYLOAD.length} bytes`);
    }
}
loadModPayload();
try { fs.watchFile(MOD_PATH, loadModPayload); } catch(e) {}

// ============================================================================
// TCP AUTH SERVER — Binary Protocol
// ============================================================================
//
// DISPATCH by first byte:
//   0x01–0x04: Fragment request (fake "microservice" auths)
//   0xF0:      Real ECDH challenge-response auth
//
// Fragment Protocol:
//   Client sends: [1:cmd][32:hwid_hash] = 33 bytes
//   Server sends: [16:fragment]
//   (Each looks like a different service: CDN, telemetry, config, patch)
//
// ECDH Protocol:
//   Server sends: [1:0xF0][65:serverPub][32:nonce][8:timestamp] = 106 bytes
//   Client sends: [65:clientPub][12:iv][2:ctLen][N:ciphertext][16:tag]
//   Server sends: [4:size][12:outerIv][N:outerCt][16:outerTag][32:session]
//
// Response is DOUBLE ENCRYPTED:
//   Outer: AES-256-GCM  (ECDH key)      → transport auth
//   Inner: AES-256-CTR  (composite key)  → NO auth tag = always "decrypts"
//
// On failure: same structure, same encryption, garbage inner data.
// Client has NO exception, NO boolean, NO way to detect failure
// except that ClassLoader gets garbage bytes.
// ============================================================================

const TCP_PORT = process.env.TCP_PORT || 4000;
const AUTH_TIMEOUT_MS = 15000;

const tcpServer = net.createServer((socket) => {
    const ip = socket.remoteAddress || '?';
    socket.setTimeout(AUTH_TIMEOUT_MS);
    socket.on('timeout', () => socket.destroy());
    socket.on('error', () => socket.destroy());

    let buf = Buffer.alloc(0);
    let dispatched = false;

    socket.on('data', (chunk) => {
        buf = Buffer.concat([buf, chunk]);
        if (dispatched) return;
        if (buf.length < 1) return;

        const cmd = buf[0];

        // ---- Real ECDH Auth ----
        if (cmd === cr.PROTOCOL_VERSION) {
            dispatched = true;
            startEcdhAuth(socket, ip);
            return;
        }

        // ---- Unknown: send garbage, close ----
        dispatched = true;
        socket.write(nodeCrypto.randomBytes(16));
        socket.destroy();
    });
});


// ============================================================================
// ECDH AUTH HANDLER — The Real Authentication
// ============================================================================
async function startEcdhAuth(socket, ip) {
    console.log(`[ECDH] Auth request from ${ip}`);

    // Rate limit
    const allowed = await db.checkRateLimit(ip, 10, 60000);
    if (!allowed) {
        console.log(`[ECDH] Rate limited: ${ip}`);
        await sendGarbage(socket);
        return;
    }
    await db.recordAttempt(ip);

    // Generate keypair + nonce, send challenge
    const ecdh = cr.createECDH();
    const nonce = cr.generateNonce(32);
    const challenge = cr.buildChallenge(ecdh.getPublicKey(), nonce);
    socket.write(challenge);

    // Collect client response
    let authBuf = Buffer.alloc(0);
    let processed = false;

    socket.on('data', async (chunk) => {
        if (processed) return;
        authBuf = Buffer.concat([authBuf, chunk]);

        // Minimum auth packet: 65+12+2+0+16 = 95 bytes
        if (authBuf.length < 95) return;
        const ctLen = authBuf.readUInt16BE(77);
        const needed = 79 + ctLen + 16;
        if (authBuf.length < needed) return;

        processed = true;
        await processEcdhAuth(socket, ecdh, nonce, authBuf.subarray(0, needed), ip);
    });
}

async function processEcdhAuth(socket, ecdh, nonce, data, ip) {
    // ---- Parse ----
    const parsed = cr.parseClientAuth(data);
    if (!parsed) return sendGarbage(socket);

    // ---- ECDH Shared Secret ----
    let sharedSecret;
    try {
        sharedSecret = ecdh.computeSecret(parsed.clientPubKey);
    } catch (e) {
        return sendGarbage(socket);
    }

    // ---- Derive Keys ----
    const authKey    = cr.deriveKey(sharedSecret, nonce, 'FROST_AUTH_V2');
    const payloadKey = cr.deriveKey(sharedSecret, nonce, 'FROST_PAYLOAD_V2');

    // ---- Decrypt Client Auth Data ----
    let decrypted;
    try {
        decrypted = cr.aesGcmDecrypt(authKey, parsed.iv, parsed.ciphertext, parsed.tag);
    } catch (e) {
        return sendGarbage(socket);
    }

    // ---- Parse: nonce(32) + license + \x00 + hwid ----
    if (decrypted.length < 34) return sendGarbage(socket);
    const echoNonce = decrypted.subarray(0, 32);
    const rest = decrypted.subarray(32);
    const sep = rest.indexOf(0x00);
    if (sep === -1) return sendGarbage(socket);
    const license = rest.subarray(0, sep).toString('utf8');
    const hwid = rest.subarray(sep + 1).toString('utf8');

    // ---- Verify Nonce (anti-replay) ----
    if (!echoNonce.equals(nonce)) return sendGarbage(socket);
    const nonceHex = nonce.toString('hex');
    if (await db.isNonceUsed(nonceHex)) return sendGarbage(socket);
    await db.markNonceUsed(nonceHex);

    // ---- Validate License + HWID ----
    const result = await db.validateLicenseAndHwid(license, hwid);

    // ---- Compute HWID hash for fragment derivation ----
    const hwidHash = nodeCrypto.createHash('sha256').update(hwid).digest();

    // ---- Recompute the 4 fragments (same as what fragment endpoints returned) ----
    const frags = cr.computeAllFragments(hwidHash);

    // ---- Derive Composite Key (needs all 4 fragments + ECDH secret) ----
    const compositeKey = cr.deriveCompositeKey(
        sharedSecret, frags.f1, frags.f2, frags.f3, frags.f4
    );

    if (result.valid) {
        console.log(`[ECDH] ✓ VALID: ${license.substring(0, 25)}... from ${ip}`);

        // Create session token for double-auth
        const sessionPlain = await db.createSession(license, hwid);
        const sessionKey = cr.deriveKey(sharedSecret, nonce, 'FROST_SESSION_V2');
        const sessionToken = Buffer.alloc(32);
        const sessionBuf = Buffer.from(sessionPlain, 'hex');
        for (let i = 0; i < 32; i++) {
            sessionToken[i] = (sessionBuf[i] || 0) ^ sessionKey[i];
        }

        // Watermark + Double-encrypt real mod
        const watermarked = cr.injectWatermark(MOD_PAYLOAD, hwid, license);
        const outerEnc = cr.doubleEncrypt(payloadKey, compositeKey, watermarked);
        socket.write(cr.buildResponse(outerEnc, sessionToken));
    } else {
        console.log(`[ECDH] ✗ DENIED from ${ip}`);

        // ---- INDISTINGUISHABLE FAILURE ----
        // Encrypt garbage with SAME double-layer scheme
        // Client outer-decrypts successfully, inner-decrypts "successfully" (CTR),
        // but gets garbage bytes → ClassLoader fails naturally
        const garbageSize = MOD_PAYLOAD.length + (nodeCrypto.randomInt(128) - 64);
        const garbage = nodeCrypto.randomBytes(Math.max(garbageSize, 512));
        const outerEnc = cr.doubleEncrypt(payloadKey, compositeKey, garbage);
        const fakeSession = nodeCrypto.randomBytes(32);

        // Random delay prevents timing side-channel
        await new Promise(r => setTimeout(r, 50 + nodeCrypto.randomInt(200)));

        socket.write(cr.buildResponse(outerEnc, fakeSession));
    }

    socket.end();
}

async function sendGarbage(socket) {
    const size = 512 + nodeCrypto.randomInt(2048);
    const garbage = nodeCrypto.randomBytes(4 + size);
    garbage.writeUInt32BE(size, 0);
    await new Promise(r => setTimeout(r, 100 + nodeCrypto.randomInt(300)));
    try { socket.write(garbage); socket.end(); } catch(e) { socket.destroy(); }
}

tcpServer.listen(TCP_PORT, () => {
    console.log(`[Frost] TCP Auth on port ${TCP_PORT}`);
    console.log(`[Frost] Protocol: ECDH-P256 + AES-256-GCM/CTR double-layer`);
    console.log(`[Frost] 4 fragment stages + 1 ECDH auth`);
    console.log(`[Frost] Zero booleans, zero patchable branches`);
});
