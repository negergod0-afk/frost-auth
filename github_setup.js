const https = require('https');
const { execSync } = require('child_process');

const GH_TOKEN = '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';
const GH_USER  = 'negergod0';
const ROOT     = 'c:\\Users\\GeftiLay\\Documents\\Frost-Client-src-1111(1)';

function api(method, path, body) {
    return new Promise((res, rej) => {
        const b = body ? JSON.stringify(body) : null;
        const r = https.request({
            hostname: 'api.github.com', path, method,
            headers: {
                'Authorization': `Bearer ${GH_TOKEN}`,
                'User-Agent': 'frost-deploy',
                'Accept': 'application/vnd.github.v3+json',
                'Content-Type': 'application/json',
                ...(b ? { 'Content-Length': Buffer.byteLength(b) } : {})
            }
        }, rs => { let d = ''; rs.on('data', c => d += c); rs.on('end', () => { try { res(JSON.parse(d)); } catch(e) { res({raw:d}); } }); });
        r.on('error', rej);
        if (b) r.write(b);
        r.end();
    });
}

function run(cmd, cwd) {
    console.log('  $', cmd);
    return execSync(cmd, { cwd: cwd || ROOT, stdio: 'pipe', encoding: 'utf8' });
}

async function createRepo(name) {
    // Check if exists first
    const existing = await api('GET', `/repos/${GH_USER}/${name}`);
    if (existing.id) {
        console.log(`  Repo ${name} already exists: ${existing.html_url}`);
        return existing;
    }
    const r = await api('POST', '/user/repos', { name, private: true, auto_init: false, description: 'Frost Auth' });
    if (r.id) {
        console.log(`  Created: ${r.html_url}`);
        return r;
    }
    console.log('  Error:', JSON.stringify(r));
    return null;
}

async function main() {
    const remote = `https://${GH_TOKEN}@github.com/${GH_USER}/frost-auth.git`;

    // Create repo
    console.log('Creating frost-auth repo...');
    const repo = await createRepo('frost-auth');
    if (!repo) { console.error('Failed to create repo'); return; }

    // Set remote and push
    console.log('\nPushing code...');
    try { run('git remote remove origin'); } catch(_) {}
    run(`git remote add origin ${remote}`);
    run('git add -A');
    try { run('git commit -m "Deploy auth server and bot"'); } catch(_) { console.log('  Nothing new to commit'); }
    run('git branch -M main');
    run('git push -u origin main --force');
    console.log('  Pushed to GitHub!');

    console.log('\nDone! Repo URL: https://github.com/' + GH_USER + '/frost-auth');
    console.log('\nNext: connect this repo to Railway services:');
    console.log('  Frost-Auth → root directory /');
    console.log('  Frost-Bot  → root directory /bot');
}
main().catch(console.error);
