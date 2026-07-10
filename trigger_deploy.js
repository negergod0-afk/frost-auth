const https = require('https');
const fs = require('fs');
const path = require('path');

const cfg = JSON.parse(fs.readFileSync(path.join(process.env.USERPROFILE, '.railway', 'config.json'), 'utf8'));
const RAIL_TOKEN = cfg.user.accessToken;
const GH_TOKEN   = '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';
const PROJECT_ID = '3a649454-94ae-4a45-a48a-3f7b9ca3dc69';
const ENV_ID     = '3a76830a-5c23-423b-92ee-e354355503bd';
const AUTH_SVC   = '16046849-6dd6-451d-a211-4237376e5e85';
const BOT_SVC    = '47f55b95-06b2-467b-a97e-9356e8f843eb';
const REPO       = 'negergod0-afk/frost-auth';

function ghApi(method, path, body) {
    return new Promise((res, rej) => {
        const b = body ? JSON.stringify(body) : null;
        const r = https.request({
            hostname: 'api.github.com', path, method,
            headers: {
                'Authorization': `Bearer ${GH_TOKEN}`, 'User-Agent': 'frost-deploy',
                'Accept': 'application/vnd.github.v3+json',
                ...(b ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(b) } : {})
            }
        }, rs => { let d = ''; rs.on('data', c => d += c); rs.on('end', () => { try { res(JSON.parse(d)); } catch(e) { res({raw:d}); } }); });
        r.on('error', rej); if (b) r.write(b); r.end();
    });
}

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
    // Step 1: Make repo public
    console.log('Making repo public...');
    const patch = await ghApi('PATCH', `/repos/${REPO}`, { private: false });
    console.log('  Repo visibility:', patch.visibility || patch.message);

    // Step 2: githubRepoDeploy for auth (root)
    console.log('\nDeploying Frost-Auth...');
    const r1 = await gql(`mutation {
        githubRepoDeploy(input: {
            projectId: "${PROJECT_ID}",
            repo: "${REPO}",
            branch: "main",
            environmentId: "${ENV_ID}"
        })
    }`);
    console.log('  Auth:', r1?.data?.githubRepoDeploy || r1?.errors?.[0]?.message);

    // Step 3: trigger env deploy for both services (one at a time)
    for (const [name, svcId] of [['Frost-Auth', AUTH_SVC], ['Frost-Bot', BOT_SVC]]) {
        const r = await gql(`mutation {
            environmentTriggersDeploy(input: {
                environmentId: "${ENV_ID}",
                projectId: "${PROJECT_ID}",
                serviceId: "${svcId}"
            })
        }`);
        console.log(`  ${name} env trigger:`, JSON.stringify(r?.data?.environmentTriggersDeploy ?? r?.errors?.[0]?.message));
    }

    console.log('\nDone — check Railway dashboard in ~2 min.');
    console.log('Auth URL: https://frost-auth-production-2c0c.up.railway.app');
}
main().catch(console.error);
