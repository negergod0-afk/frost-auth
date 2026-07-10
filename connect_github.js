const https = require('https');
const fs = require('fs');
const path = require('path');

const cfg = JSON.parse(fs.readFileSync(path.join(process.env.USERPROFILE, '.railway', 'config.json'), 'utf8'));
const RAIL_TOKEN  = cfg.user.accessToken;
const GH_TOKEN    = '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';
const PROJECT_ID  = '3a649454-94ae-4a45-a48a-3f7b9ca3dc69';
const ENV_ID      = '3a76830a-5c23-423b-92ee-e354355503bd';
const AUTH_SVC    = '16046849-6dd6-451d-a211-4237376e5e85';
const BOT_SVC     = '47f55b95-06b2-467b-a97e-9356e8f843eb';
const REPO        = 'negergod0-afk/frost-auth';

async function gql(q) {
    return new Promise((res, rej) => {
        const b = JSON.stringify({ query: q });
        const r = https.request({
            hostname: 'backboard.railway.com', path: '/graphql/v2', method: 'POST',
            headers: { 'Authorization': `Bearer ${RAIL_TOKEN}`, 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(b) }
        }, rs => { let d = ''; rs.on('data', c => d += c); rs.on('end', () => { try { res(JSON.parse(d)); } catch(e) { res({raw: d}); } }); });
        r.on('error', rej); r.write(b); r.end();
    });
}

// Introspect available mutations to find how to connect GitHub
async function main() {
    // Find serviceConnect or similar mutation
    const r = await gql(`{
        __type(name: "Mutation") {
            fields {
                name
                args { name type { name kind ofType { name } } }
            }
        }
    }`);
    const mutations = r?.data?.__type?.fields || [];
    const relevant = mutations.filter(m =>
        m.name.toLowerCase().includes('github') ||
        m.name.toLowerCase().includes('repo') ||
        m.name.toLowerCase().includes('source') ||
        m.name.toLowerCase().includes('connect') ||
        m.name.toLowerCase().includes('trigger') ||
        m.name.toLowerCase().includes('deploy')
    );
    console.log('Relevant mutations:');
    for (const m of relevant) {
        const args = m.args.map(a => `${a.name}: ${a.type?.name || a.type?.ofType?.name}`).join(', ');
        console.log(`  ${m.name}(${args})`);
    }

    // Also check ServiceUpdateInput for github fields
    const r2 = await gql(`{ __type(name:"ServiceUpdateInput") { inputFields { name type { name kind ofType { name } } } } }`);
    console.log('\nServiceUpdateInput:', (r2?.data?.__type?.inputFields || []).map(f => f.name).join(', '));

    // Check DeploymentTriggerCreateInput
    const r3 = await gql(`{ __type(name:"DeploymentTriggerCreateInput") { inputFields { name type { name } } } }`);
    console.log('\nDeploymentTriggerCreateInput:', (r3?.data?.__type?.inputFields || []).map(f => f.name).join(', '));
}
main().catch(console.error);
