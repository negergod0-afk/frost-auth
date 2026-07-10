// Delete broken services and recreate them fresh
const https = require('https');
const fs = require('fs');
const path = require('path');

// Get fresh token from config
const cfg = JSON.parse(fs.readFileSync(path.join(process.env.USERPROFILE, '.railway', 'config.json'), 'utf8'));
const TOKEN = cfg.user.accessToken;
const PROJECT_ID = '3a649454-94ae-4a45-a48a-3f7b9ca3dc69';
const ENV_ID     = '3a76830a-5c23-423b-92ee-e354355503bd';

// Old broken service IDs
const OLD_AUTH = '17fa5109-9c7e-419b-9a11-2f68dae257cd';
const OLD_BOT  = 'b5a18f0c-0fa9-4f82-a94e-8d8e8a752949';

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

async function setVar(svcId, name, value) {
    const r = await gql(`mutation { variableUpsert(input: { projectId:"${PROJECT_ID}", environmentId:"${ENV_ID}", serviceId:"${svcId}", name:"${name}", value:"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}" }) }`);
    console.log(`  ${name}:`, r.errors ? r.errors[0].message : 'OK');
}

async function main() {
    console.log('Token:', TOKEN.slice(0, 15) + '...');

    // Delete old services
    console.log('\nDeleting old broken services...');
    for (const id of [OLD_AUTH, OLD_BOT]) {
        const r = await gql(`mutation { serviceDelete(id:"${id}") }`);
        console.log(`  Delete ${id.slice(0,8)}:`, r.errors ? r.errors[0].message : 'OK');
    }

    // Create fresh Frost-Auth service
    console.log('\nCreating Frost-Auth...');
    const r1 = await gql(`mutation { serviceCreate(input: { projectId:"${PROJECT_ID}", name:"Frost-Auth" }) { id name } }`);
    const authId = r1?.data?.serviceCreate?.id;
    console.log('  ID:', authId);
    if (!authId) { console.error('Failed:', JSON.stringify(r1.errors)); return; }

    // Create fresh Frost-Bot service
    console.log('\nCreating Frost-Bot...');
    const r2 = await gql(`mutation { serviceCreate(input: { projectId:"${PROJECT_ID}", name:"Frost-Bot" }) { id name } }`);
    const botId = r2?.data?.serviceCreate?.id;
    console.log('  ID:', botId);
    if (!botId) { console.error('Failed:', JSON.stringify(r2.errors)); return; }

    // Set env vars on Frost-Auth
    console.log('\nSetting Frost-Auth vars...');
    await setVar(authId, 'INTERNAL_API_KEY', '19be5c33ff5175a71e04c5b27f2aaf9816e6d71f43dab5e4c316c258628eca1f');
    await setVar(authId, 'FRAGMENT_SECRET',  '437efe5b89d10f871f905b0a063160d18add137fdbe7d85a8839338da805eebd');
    await setVar(authId, 'TCP_PORT', '4000');
    await setVar(authId, 'NODE_ENV', 'production');

    // Set env vars on Frost-Bot
    console.log('\nSetting Frost-Bot vars...');
    await setVar(botId, 'INTERNAL_API_KEY',  '19be5c33ff5175a71e04c5b27f2aaf9816e6d71f43dab5e4c316c258628eca1f');
    await setVar(botId, 'AUTH_SERVER_URL',   'https://frost-auth-production.up.railway.app');
    await setVar(botId, 'NODE_ENV', 'production');
    // DISCORD_TOKEN and DISCORD_CLIENT_ID must be added in Railway dashboard

    // Create domain for Frost-Auth
    console.log('\nCreating domain for Frost-Auth...');
    const rd = await gql(`mutation { serviceDomainCreate(input: { serviceId:"${authId}", environmentId:"${ENV_ID}" }) { domain } }`);
    const domain = rd?.data?.serviceDomainCreate?.domain;
    console.log('  Domain:', domain || JSON.stringify(rd.errors));

    // Update AUTH_SERVER_URL on bot with real domain
    if (domain) {
        await setVar(botId, 'AUTH_SERVER_URL', `https://${domain}`);
        console.log('  Updated AUTH_SERVER_URL to https://' + domain);
    }

    // Write new service IDs to railway config
    const configPath = require('path').join(process.env.USERPROFILE, '.railway', 'config.json');
    const config = JSON.parse(require('fs').readFileSync(configPath, 'utf8'));
    const rootPath = 'C:\\Users\\GeftiLay\\Documents\\Frost-Client-src-1111(1)';
    const botPath  = rootPath + '\\bot';
    config.projects[rootPath] = { ...config.projects[rootPath], service: authId };
    config.projects[botPath]  = { ...config.projects[botPath],  service: botId  };
    require('fs').writeFileSync(configPath, JSON.stringify(config, null, 2));
    console.log('\nConfig updated with new service IDs.');
    console.log('\nDone!');
    console.log('Auth service ID:', authId);
    console.log('Bot service ID: ', botId);
    console.log('\nNext: deploy both services');
}
main().catch(console.error);
