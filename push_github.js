// Create a private GitHub repo and print the push instructions
// Reads GitHub token from git credential manager or env
const https = require('https');
const { execSync } = require('child_process');

// Try to get github token from git credential manager
let token = process.env.GITHUB_TOKEN;
if (!token) {
    try {
        // Try reading from git credentials
        const creds = execSync('git credential fill', { input: 'protocol=https\nhost=github.com\n', timeout: 3000 }).toString();
        const match = creds.match(/password=(.+)/);
        if (match) token = match[1].trim();
    } catch (_) {}
}

if (!token) {
    console.log('No GITHUB_TOKEN found.');
    console.log('\nTo deploy to Railway via GitHub:');
    console.log('1. Create a private repo on github.com called "frost-auth"');
    console.log('2. Run these commands:');
    console.log('   git remote add origin https://github.com/YOUR_USERNAME/frost-auth.git');
    console.log('   git push -u origin main');
    console.log('3. In Railway dashboard: go to Frost-Auth service → Settings → Source → Connect GitHub → select frost-auth repo');
    console.log('4. Do the same for Frost-Bot, pointing at the bot/ subfolder as the root directory');
    console.log('\nAlternatively, set GITHUB_TOKEN env var and re-run this script.');
    process.exit(0);
}
