// Get the latest deployment for each service and trigger a full rebuild
const https = require('https');
const TOKEN  = '336Zy1nEQfoHpD0wSYDawhSpA58odyP2_031JWQfZQr';
const PROJECT_ID = '3a649454-94ae-4a45-a48a-3f7b9ca3dc69';
const ENV_ID     = '3a76830a-5c23-423b-92ee-e354355503bd';
const AUTH_SVC   = '17fa5109-9c7e-419b-9a11-2f68dae257cd';
const BOT_SVC    = 'b5a18f0c-0fa9-4f82-a94e-8d8e8a752949';

async function gql(q) {
    return new Promise((res,rej)=>{
        const b=JSON.stringify({query:q});
        const r=https.request({hostname:'backboard.railway.com',path:'/graphql/v2',method:'POST',
            headers:{'Authorization':`Bearer ${TOKEN}`,'Content-Type':'application/json','Content-Length':Buffer.byteLength(b)}},
            rs=>{let d='';rs.on('data',c=>d+=c);rs.on('end',()=>{try{res(JSON.parse(d));}catch(e){res({raw:d});}});});
        r.on('error',rej);r.write(b);r.end();
    });
}

async function main() {
    // Check what the latest deployments are
    for (const [name, svcId] of [['Frost-Auth', AUTH_SVC], ['Frost-Bot', BOT_SVC]]) {
        const r = await gql(`{
            service(id: "${svcId}") {
                id name
                deployments(first: 5) { edges { node { id status createdAt } } }
            }
        }`);
        const svc = r?.data?.service;
        if (!svc) { console.log(name, 'not found:', JSON.stringify(r.errors)); continue; }
        const deps = svc.deployments?.edges || [];
        console.log(`\n${name}:`);
        for (const { node: d } of deps) {
            console.log(`  ${d.id.slice(0,8)} | ${d.status} | ${new Date(d.createdAt).toLocaleTimeString()}`);
        }

        // Trigger redeploy on latest FAILED one that might have source
        const latest = deps[0]?.node;
        if (latest && (latest.status === 'FAILED' || latest.status === 'CRASHED')) {
            const r2 = await gql(`mutation { deploymentRedeploy(id:"${latest.id}") { id status } }`);
            console.log(`  → Redeploy triggered:`, r2?.data?.deploymentRedeploy || r2?.errors?.[0]?.message);
        }
    }
}
main().catch(console.error);
