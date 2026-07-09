// ============================================================================
// FROST AUTH SERVER — Cryptographic Core
// ECDH P-256 | AES-256-GCM | AES-256-CTR | HMAC-SHA256 Key Derivation
// Fragment Generation | Watermark Injection
// ============================================================================
const crypto = require('crypto');

const CURVE = 'prime256v1';
const PROTOCOL_VERSION = 0xF0;

// Fragment commands for the 4 auxiliary auth stages
const CMD_CDN_INTEGRITY  = 0x01;
const CMD_TELEMETRY_SYNC = 0x02;
const CMD_CONFIG_FETCH   = 0x03;
const CMD_PATCH_CHECK    = 0x04;

// ---- Persistent Fragment Secret ----
// This MUST be the same across restarts. Generated once, stored in env.
const FRAGMENT_SECRET = Buffer.from(
    process.env.FRAGMENT_SECRET || crypto.randomBytes(32).toString('hex'),
    'hex'
);

// ============================================================================
// ECDH Key Exchange
// ============================================================================
function createECDH() {
    const ecdh = crypto.createECDH(CURVE);
    ecdh.generateKeys();
    return ecdh;
}

// ============================================================================
// Key Derivation — HMAC-based, context-separated
// Derives unique 256-bit keys for different protocol phases
// ============================================================================
function deriveKey(sharedSecret, nonce, context) {
    return crypto.createHmac('sha256', sharedSecret)
        .update(nonce)
        .update(Buffer.from(context, 'utf8'))
        .digest();
}

// ============================================================================
// AES-256-GCM (Authenticated — used for transport layer)
// ============================================================================
function aesGcmEncrypt(key, plaintext) {
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
    const encrypted = Buffer.concat([cipher.update(plaintext), cipher.final()]);
    const tag = cipher.getAuthTag();
    return { iv, encrypted, tag };
}

function aesGcmDecrypt(key, iv, ciphertext, tag) {
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(tag);
    return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}

// ============================================================================
// AES-256-CTR (Unauthenticated — used for inner payload layer)
// CTR mode ALWAYS "succeeds" — wrong key = garbage output, no exception
// This is critical: the client has NO way to detect wrong key via exception
// ============================================================================
function aesCtrEncrypt(key, plaintext) {
    const iv = crypto.randomBytes(16);
    const cipher = crypto.createCipheriv('aes-256-ctr', key, iv);
    const encrypted = Buffer.concat([cipher.update(plaintext), cipher.final()]);
    return { iv, encrypted };
}

// ============================================================================
// Fragment Generation
// Each fragment is 16 bytes, derived from FRAGMENT_SECRET + cmd + hwidHash
// Without FRAGMENT_SECRET, fragments cannot be computed
// Fragments are deterministic per-HWID so server can recompute them during auth
// ============================================================================
function computeFragment(cmd, hwidHash) {
    return crypto.createHmac('sha256', FRAGMENT_SECRET)
        .update(Buffer.from([cmd]))
        .update(hwidHash)
        .digest()
        .subarray(0, 16);
}

// Compute all 4 fragments for a given HWID hash
function computeAllFragments(hwidHash) {
    return {
        f1: computeFragment(CMD_CDN_INTEGRITY, hwidHash),
        f2: computeFragment(CMD_TELEMETRY_SYNC, hwidHash),
        f3: computeFragment(CMD_CONFIG_FETCH, hwidHash),
        f4: computeFragment(CMD_PATCH_CHECK, hwidHash),
    };
}

// ============================================================================
// Composite Key Derivation
// Final decryption key = HMAC(ecdhSharedSecret, f1 || f2 || f3 || f4)
// Missing ANY fragment = wrong composite key = garbage decryption
// ============================================================================
function deriveCompositeKey(sharedSecret, f1, f2, f3, f4) {
    return crypto.createHmac('sha256', sharedSecret)
        .update(f1)
        .update(f2)
        .update(f3)
        .update(f4)
        .digest();
}

// ============================================================================
// Double-Layer Encryption
// Outer: AES-256-GCM (transport auth, ECDH key)
// Inner: AES-256-CTR (payload protection, composite key — NO auth tag)
// ============================================================================
function doubleEncrypt(payloadKey, compositeKey, payload) {
    // Inner: CTR with composite key (no auth tag — always "decrypts")
    const inner = aesCtrEncrypt(compositeKey, payload);
    const innerData = Buffer.concat([inner.iv, inner.encrypted]); // 16 + N bytes

    // Outer: GCM with ECDH-derived key (transport authenticated)
    const outer = aesGcmEncrypt(payloadKey, innerData);

    return outer; // { iv, encrypted, tag }
}

// ============================================================================
// Nonce / License Generation
// ============================================================================
function generateNonce(size = 32) {
    return crypto.randomBytes(size);
}

// License format: Frost+{Plan}-{192 bits of crypto entropy}
// 2^192 ≈ 6.3 × 10^57 possibilities — brute force impossible
function generateLicenseKey(plan) {
    const entropy = crypto.randomBytes(24).toString('base64url');
    return `Frost+${plan}-${entropy}`;
}

// ============================================================================
// Protocol Packet Builders
// ============================================================================

// Challenge: [1:ver][65:serverPub][32:nonce][8:timestamp] = 106 bytes
function buildChallenge(serverPubKey, nonce) {
    const buf = Buffer.alloc(106);
    buf[0] = PROTOCOL_VERSION;
    serverPubKey.copy(buf, 1);
    nonce.copy(buf, 66);
    buf.writeBigUInt64BE(BigInt(Date.now()), 98);
    return buf;
}

// Parse client auth: [65:clientPub][12:iv][2:ctLen][N:ct][16:tag]
function parseClientAuth(data) {
    if (data.length < 95) return null;
    const clientPubKey = data.subarray(0, 65);
    const iv = data.subarray(65, 77);
    const ctLen = data.readUInt16BE(77);
    if (data.length < 79 + ctLen + 16) return null;
    const ciphertext = data.subarray(79, 79 + ctLen);
    const tag = data.subarray(79 + ctLen, 79 + ctLen + 16);
    return { clientPubKey, iv, ciphertext, tag };
}

// Response: [4:totalSize][12:outerIv][N:outerCt][16:outerTag][32:sessionToken]
function buildResponse(outerEncrypted, sessionToken) {
    const { iv, encrypted, tag } = outerEncrypted;
    const totalSize = 12 + encrypted.length + 16 + 32;
    const buf = Buffer.alloc(4 + totalSize);
    buf.writeUInt32BE(totalSize, 0);
    iv.copy(buf, 4);
    encrypted.copy(buf, 16);
    tag.copy(buf, 16 + encrypted.length);
    sessionToken.copy(buf, 16 + encrypted.length + 16);
    return buf;
}

// ============================================================================
// Watermark Injection — Unique per-user mod binary
// ============================================================================
function injectWatermark(modBytes, hwid, license) {
    const placeholder = Buffer.from('__FROST_WATERMARK_PLACEHOLDER_DO_NOT_REMOVE__');
    const watermark = crypto.createHmac('sha256', 'frost-wm-v2')
        .update(hwid).update(license).digest();
    const idx = modBytes.indexOf(placeholder);
    if (idx === -1) return modBytes;
    const result = Buffer.from(modBytes);
    for (let i = 0; i < placeholder.length && i < watermark.length; i++) {
        result[idx + i] = watermark[i];
    }
    return result;
}

module.exports = {
    PROTOCOL_VERSION,
    FRAGMENT_SECRET,
    CMD_CDN_INTEGRITY, CMD_TELEMETRY_SYNC, CMD_CONFIG_FETCH, CMD_PATCH_CHECK,
    createECDH, deriveKey,
    aesGcmEncrypt, aesGcmDecrypt,
    aesCtrEncrypt,
    computeFragment, computeAllFragments,
    deriveCompositeKey, doubleEncrypt,
    generateNonce, generateLicenseKey,
    buildChallenge, parseClientAuth, buildResponse,
    injectWatermark
};
