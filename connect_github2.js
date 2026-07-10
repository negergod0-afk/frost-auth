const https = require('https');
const fs = require('fs');
const path = require('path');

const cfg = JSON.parse(fs.readFileSync(path.join(process.env.USERPROFILE, '.railway', 'config.json'), 'utf8'));
const RAIL_TOKEN = cfg.user.accessToken;
const PROJECT_ID = '3a649454-94ae-4a45-a48a-3f7b9ca3dc69';
const ENV_ID     = '3a76830a-5c23-423b-92ee-e354355503bd';
const AUTH_SVC   = '16046849-6dd6-451d-a211-4237376e5e85';
const BOT_SVC    = '47f55b95-06b2-467b-a97e-9356e8f843eb';
const REPO       = 'negergod0-afk/frost-auth';

async function gql(q) {
    return new Promise((res, rej) => {
        const b = JSON.stringify({ query: q });
        const r = https.request({
            hostname: 'backboard.railway.com', path: '/graphql/v2', method: 'POST',
            headers: { 'Authorization': `Bearer ${RAIL_TOKEN}`, 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(b) }
        }, rs => { let d = ''; rs.on('data', c => d += c); rs.on('end', () => { try { res(JSON.parse(d)); } catch(e) { res({raw:d}); } }); });
        r.on('error', rej); r.write(b); r.end();
    });
}

async function main() {
    // 1. Connect Frost-Auth service to repo root
    console.log('Connecting Frost-Auth to GitHub repo (root)...');
    const r1 = await gql(`mutation {
        deploymentTriggerCreate(input: {
            projectId: "${PROJECT_ID}",
            environmentId: "${ENV_ID}",
            serviceId: "${AUTH_SVC}",
            provider: "github",
            repository: "${REPO}",
            branch: "main",
            rootDirectory: "/",
            checkSuites: false
        }) { id }
    }`);
    console.log('  Auth trigger:', JSON.stringify(r1?.data?.deploymentTriggerCreate || r1?.errors?.[0]?.message));

    // 2. Connect Frost-Bot service to repo /bot subdirectory
    console.log('\nConnecting Frost-Bot to GitHub repo (/bot)...');
    const r2 = await gql(`mutation {
        deploymentTriggerCreate(input: {
            projectId: "${PROJECT_ID}",
            environmentId: "${ENV_ID}",
            serviceId: "${BOT_SVC}",
            provider: "github",
            repository: "${REPO}",
            branch: "main",
            rootDirectory: "/bot",
            checkSuites: false
        }) { id }
    }`);
    console.log('  Bot trigger:', JSON.stringify(r2?.data?.deploymentTriggerCreate || r2?.errors?.[0]?.message));

    // 3. Trigger immediate deployment of both
    console.log('\nTriggering deployments...');
    const d1 = await gql(`mutation {
        serviceInstanceDeploy(serviceId: "${AUTH_SVC}", environmentId: "${ENV_ID}", latestCommit: true)
    }`);
    console.log('  Auth deploy:', JSON.stringify(d1?.data || d1?.errors?.[0]?.message));

    const d2 = await gql(`mutation {
        serviceInstanceDeploy(serviceId: "${BOT_SVC}", environmentId: "${ENV_ID}", latestCommit: true)
    }`);
    console.log('  Bot deploy:', JSON.stringify(d2?.data || d2?.errors?.[0]?.message));

    console.log('\nDone! Check railway status in ~2 minutes.');
    console.log('Auth URL: https://frost-auth-production-2c0c.up.railway.app');
}
main().catch(console.error);
