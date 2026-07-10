// ============================================================================
// Frost Loader — Injection Script
//
// Patches FrostLoaderMod.class inside the compiled loader JAR with:
//   - INJECTED_LICENSE    (64 chars, padded with spaces)
//   - INJECTED_DISCORD_ID (64 chars, padded with spaces)
//   - INJECTED_AUTH_HOST  (64 chars, padded with spaces)
//   - INJECTED_TCP_PORT   (8  chars, padded with spaces)
//   - INJECTED_API_PORT   (8  chars, padded with spaces)
//
// Run after building loader-mod:
//   cd loader-mod && ..\gradlew build
//   cd .. && node inject_test.js
// ============================================================================
const AdmZip = require('adm-zip');
const path   = require('path');

// ── Injection targets (must match the template strings in FrostLoaderMod.java) ──
const TARGETS = {
    LICENSE:   { template: "Frost+Monthly-testing123                                        ", width: 64 },
    DISCORD:   { template: "000000000000000000                                              ", width: 64 },
    AUTH_HOST: { template: "127.0.0.1                                                       ", width: 64 },
    TCP_PORT:  { template: "4000    ", width: 8 },
    API_PORT:  { template: "3000    ", width: 8 },
};

// ── Values to inject ─────────────────────────────────────────────────────────
// Edit these before running for a real user build.
const VALUES = {
    LICENSE:   process.env.USER_LICENSE   || "Frost+Lifetime--qiOlOokkSAfZE9CpJM2WWAiWxhvLe3E",
    DISCORD:   process.env.USER_DISCORD   || "123456789012345678",
    AUTH_HOST: process.env.AUTH_HOST      || "127.0.0.1",
    TCP_PORT:  process.env.AUTH_TCP_PORT  || "4000",
    API_PORT:  process.env.AUTH_API_PORT  || "3000",
};

// ── Paths ─────────────────────────────────────────────────────────────────────
const templatePath = path.resolve(__dirname, 'loader-mod', 'build', 'libs', 'frost-loader-1.0.0.jar');
const outputPath   = path.resolve('C:\\Users\\GeftiLay\\Desktop\\FrostClient.jar');
const TARGET_CLASS = 'frost/loader/mod/FrostLoaderMod.class';

// ── Inject ────────────────────────────────────────────────────────────────────
const zip = new AdmZip(templatePath);
let patchCount = 0;

zip.getEntries().forEach(entry => {
    if (entry.entryName !== TARGET_CLASS) return;

    let data = entry.getData();
    let changed = false;

    for (const [field, { template, width }] of Object.entries(TARGETS)) {
        const rawValue = VALUES[field];
        if (rawValue === undefined) continue;

        const padded = rawValue.padEnd(width, ' ').substring(0, width);
        const needle = Buffer.from(template, 'utf8');
        const replacement = Buffer.from(padded, 'utf8');

        const idx = data.indexOf(needle);
        if (idx === -1) {
            console.warn(`[!] Template for ${field} not found in class — was JAR built with STORED compression?`);
            continue;
        }
        replacement.copy(data, idx);
        console.log(`[✓] Patched ${field} at offset ${idx}`);
        patchCount++;
        changed = true;
    }

    if (changed) {
        entry.setData(data);
    }
});

if (patchCount === 0) {
    console.error('[✗] No patches applied — injection failed. Rebuild loader-mod with STORED compression.');
    process.exit(1);
}

if (patchCount < Object.keys(TARGETS).length) {
    console.warn(`[!] Only ${patchCount}/${Object.keys(TARGETS).length} fields patched — some targets were missing.`);
}

zip.writeZip(outputPath);
console.log(`\n[✓] Injected JAR (${patchCount} patches) saved to: ${outputPath}`);
