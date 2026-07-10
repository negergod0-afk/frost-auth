const https = require('https');
const fs = require('fs');
const path = require('path');
const cfg = JSON.parse(fs.readFileSync(path.join(process.env.USERPROFILE, '.railway', 'config.json'), 'utf8'));
const T = cfg.user.accessToken;

async function gql(q) {
    return new Promise((res, rej) => {
        const b = JSON.stringify({ query: q });
        const r = https.request({
            hostname: 'backboard.railway.com', path: '/graphql/v2', method: 'POST',
            headers: { 'Authorization': `Bearer ${T}`, 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(b) }
        }, rs => { let d = ''; rs.on('data', c => d += c); rs.on('end', () => { try { res(JSON.parse(d)); } catch(e) { res({raw:d}); } }); });
        r.on('error', rej); r.write(b); r.end();
    });
}

async function main() {
    const AUTH = '16046849-6dd6-451d-a211-4237376e5e85';
    const BOT  = '47f55b95-06b2-467b-a97e-9356e8f843eb';
    const DEPID = '4b8c8f1d-22f9-4383-832c-8e213be0ebaf';

    // Get deployment details
    const r1 = await gql(`{ deployment(id:"${DEPID}") { id status createdAt } }`);
    console.log('Auth latest deploy:', JSON.stringify(r1?.data?.deployment || r1?.errors?.[0]?.message));

    // Build logs for this deployment
    const r2 = await gql(`{ buildLogs(deploymentId:"${DEPID}", limit:50) { message severity } }`);
    const bl = r2?.data?.buildLogs || [];
    console.log('\nBuild logs (' + bl.length + '):');
    for (const l of bl.slice(-20)) console.log(' ', l.message);

    // Runtime logs
    const r3 = await gql(`{ deploymentLogs(deploymentId:"${DEPID}", limit:30) { message } }`);
    const rl = r3?.data?.deploymentLogs || [];
    console.log('\nRuntime logs (' + rl.length + '):');
    for (const l of rl.slice(-15)) console.log(' ', l.message);
}
main().catch(console.error);
