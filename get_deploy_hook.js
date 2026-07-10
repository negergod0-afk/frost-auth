// Check Railway account plan and get deploy hooks
const https = require('https');
const fs = require('fs');
const path = require('path');

const cfg = JSON.parse(fs.readFileSync(path.join(process.env.USERPROFILE, '.railway', 'config.json'), 'utf8'));
const TOKEN = cfg.user.accessToken;

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
    // Check subscription/plan
    const me = await gql(`{ me { name email plan { id } } }`);
    console.log('Account:', JSON.stringify(me?.data?.me));

    // Check if services have deploy hooks
    const AUTH_SVC = '16046849-6dd6-451d-a211-4237376e5e85';
    const hook = await gql(`{ serviceDeploymentTriggers(serviceId:"${AUTH_SVC}") { id type url } }`);
    console.log('Deploy hooks:', JSON.stringify(hook?.data || hook?.errors));
}
main().catch(console.error);
