require('./server/index.js');

// Bot runs as a separate Railway service.
// Only load it locally if DISCORD_TOKEN is explicitly set.
if (process.env.DISCORD_TOKEN) {
    try {
        require('./bot/index.js');
    } catch (e) {
        console.warn('[start.js] Bot failed to load:', e.message);
    }
}
