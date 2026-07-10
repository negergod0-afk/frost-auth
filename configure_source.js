// Configure Railway services to use CLI source type so railway up works
const https = require('https');
const fs = require('fs');
const path = require('path');

const cfg = JSON.parse(fs.readFileSync(path.join(process.env.USERPROFILE, '.railway', 'config.json'), 'utf8'));
const TOKEN = cfg.user.accessToken;
const PROJECT_ID = '3a649454-94ae-4a45-a48a-3f7b9ca3dc69';
const ENV_ID     = '3a76830a-5c23-423b-92ee-e354355503bd';
const AUTH_SVC   = '16046849-6dd6-451d-a211-4237376e5e85';
const BOT_SVC    = '47f55b95-06b2-467b-a97e-9356e8f843eb';

async function gql(q) {
    return new Promise((res, rej) => {
        const b = JSON.stringify({ query: q });
        const r = https.request({
            hostname: 'backboard.railway.com', path: '/graphql/v2', method: 'POST',
            headers: { 'Authorization': `Bearer ${TOKEN}`, 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(b) }
        }, rs => { let d = ''; rs.on('data', c => d += c); rs.on('end', () => { try { res(JSON.parse(d)); } catch (e) { res({ raw: d }); } }); });
        r.on('error', rej); r.write(b); r.end();
    });
}

async function main() {
    console.log('Configuring service source types...\n');

    // Try updating service source via serviceUpdate
    for (const [name, svcId] of [['Frost-Auth', AUTH_SVC], ['Frost-Bot', BOT_SVC]]) {
        // Try to get current service info
        const info = await gql(`{
            service(id: "${svcId}") {
                id name
                source { image repo }
            }
        }`);
        console.log(`${name} source:`, JSON.stringify(info?.data?.service?.source || info?.errors?.[0]?.message));

        // Try serviceSourceUpdate mutation
        const r = await gql(`mutation {
            serviceUpdate(id: "${svcId}", input: { source: { image: null } }) {
                id name
            }
        }`);
        console.log(`${name} update:`, JSON.stringify(r?.data || r?.errors?.[0]?.message));
    }

    // Try using deploymentTriggerCreate to enable CLI uploads
    for (const [name, svcId] of [['Frost-Auth', AUTH_SVC], ['Frost-Bot', BOT_SVC]]) {
        const r = await gql(`{ deploymentTriggers(projectId:"${PROJECT_ID}", serviceId:"${svcId}", environmentId:"${ENV_ID}") { edges { node { id type } } } }`);
        console.log(`\n${name} triggers:`, JSON.stringify(r?.data || r?.errors?.[0]?.message));
    }
}
main().catch(console.error);
