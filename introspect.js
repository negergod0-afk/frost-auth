const https = require('https');
const fs = require('fs');
const path = require('path');
const cfg = JSON.parse(fs.readFileSync(path.join(process.env.USERPROFILE, '.railway', 'config.json'), 'utf8'));
const TOKEN = cfg.user.accessToken;
const AUTH_SVC = '16046849-6dd6-451d-a211-4237376e5e85';

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
    // Get Service type fields
    const r = await gql(`{ __type(name:"Service") { fields { name type { name kind ofType { name } } } } }`);
    const fields = r?.data?.__type?.fields || [];
    console.log('Service fields:', fields.map(f => f.name).join(', '));

    // Get ServiceUpdateInput fields
    const r2 = await gql(`{ __type(name:"ServiceUpdateInput") { inputFields { name type { name kind ofType { name } } } } }`);
    const ifields = r2?.data?.__type?.inputFields || [];
    console.log('\nServiceUpdateInput fields:', ifields.map(f => f.name).join(', '));

    // Get DeploymentTrigger fields
    const r3 = await gql(`{ __type(name:"DeploymentTrigger") { fields { name } } }`);
    const tfields = r3?.data?.__type?.fields || [];
    console.log('\nDeploymentTrigger fields:', tfields.map(f => f.name).join(', '));

    // Get available mutations related to deployment
    const r4 = await gql(`{ __type(name:"Mutation") { fields { name } } }`);
    const mutations = (r4?.data?.__type?.fields || []).map(f => f.name).filter(n => 
        n.toLowerCase().includes('deploy') || n.toLowerCase().includes('service') || n.toLowerCase().includes('source')
    );
    console.log('\nDeployment/Service mutations:', mutations.join(', '));

    // Try getting service info with basic fields
    const r5 = await gql(`{ service(id:"${AUTH_SVC}") { id name } }`);
    console.log('\nService basic:', JSON.stringify(r5?.data?.service || r5?.errors?.[0]?.message));
}
main().catch(console.error);
