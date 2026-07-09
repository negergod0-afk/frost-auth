// Load .env from parent dir locally; on Railway env vars come from the platform directly
try { require('dotenv').config({ path: require('path').resolve(__dirname, '..', '.env') }); } catch (_) {}
const {
    Client, GatewayIntentBits, REST, Routes,
    PermissionsBitField, ActionRowBuilder, ButtonBuilder, ButtonStyle,
    ChannelType, EmbedBuilder, StringSelectMenuBuilder, StringSelectMenuOptionBuilder
} = require('discord.js');
const axios = require('axios');

// ── Config ────────────────────────────────────────────────────────────────────
const TOKEN        = process.env.DISCORD_TOKEN;
const CLIENT_ID    = process.env.DISCORD_CLIENT_ID;
const API_KEY      = process.env.INTERNAL_API_KEY;
const API_BASE = process.env.AUTH_SERVER_URL
    ? `${process.env.AUTH_SERVER_URL}/api`
    : `http://localhost:${process.env.PORT || 3000}/api`;

if (!TOKEN || !CLIENT_ID || !API_KEY) {
    console.warn('[Bot] WARNING: DISCORD_TOKEN, DISCORD_CLIENT_ID, or INTERNAL_API_KEY missing — bot disabled.');
    module.exports = {};
    return;
}

const REDEEM_CHANNEL   = process.env.REDEEM_CHANNEL_ID   || '1524771040008470538';
const DOWNLOAD_CHANNEL = process.env.DOWNLOAD_CHANNEL_ID || '1524769686896771172';
const ROLE_LIFETIME    = process.env.ROLE_LIFETIME_ID     || '1514172082144153630';
const ROLE_MONTHLY     = process.env.ROLE_MONTHLY_ID      || '1524759971252666499';

// ── Slash Commands ────────────────────────────────────────────────────────────
const commands = [
    {
        name: 'generate',
        description: 'Generate new license keys (Admin only)',
        options: [
            {
                name: 'plan', type: 3, description: 'Monthly or Lifetime', required: true,
                choices: [{ name: 'Monthly', value: 'Monthly' }, { name: 'Lifetime', value: 'Lifetime' }]
            },
            { name: 'amount', type: 4, description: 'How many keys (max 25)', required: true }
        ],
        default_member_permissions: PermissionsBitField.Flags.Administrator.toString()
    },
    {
        name: 'revoke-license',
        description: 'Revoke a license key (Admin only)',
        options: [{ name: 'license', type: 3, description: 'License key to revoke', required: true }],
        default_member_permissions: PermissionsBitField.Flags.Administrator.toString()
    },
    {
        name: 'reset-hwid',
        description: 'Reset HWID lock for a license (Admin only)',
        options: [{ name: 'license', type: 3, description: 'License key to reset', required: true }],
        default_member_permissions: PermissionsBitField.Flags.Administrator.toString()
    },
    {
        name: 'redeem',
        description: 'Redeem your license key',
        options: [{ name: 'key', type: 3, description: 'Your license key', required: true }]
    },
    {
        name: 'download',
        description: 'Get your personalised Frost loader'
    },
    {
        name: 'mylicense',
        description: 'Check your current license status'
    }
];

// ── Bot Client ────────────────────────────────────────────────────────────────
const client = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMessages,
        GatewayIntentBits.MessageContent
    ]
});

// ── API helpers ───────────────────────────────────────────────────────────────
const apiHeaders = () => ({ 'x-api-key': API_KEY, 'Content-Type': 'application/json' });

async function apiPost(path, body) {
    const res = await axios.post(`${API_BASE}${path}`, body, { headers: apiHeaders() });
    return res.data;
}

// ── Ready ─────────────────────────────────────────────────────────────────────
client.on('ready', async () => {
    console.log(`[Bot] Logged in as ${client.user.tag}`);

    // Register slash commands globally
    const rest = new REST({ version: '10' }).setToken(TOKEN);
    try {
        await rest.put(Routes.applicationCommands(CLIENT_ID), { body: commands });
        console.log('[Bot] Slash commands registered.');
    } catch (e) {
        console.error('[Bot] Failed to register slash commands:', e.message);
    }

    // Post redeem button in redeem channel if not already there
    try {
        const channel = await client.channels.fetch(REDEEM_CHANNEL).catch(() => null);
        if (channel) {
            const messages = await channel.messages.fetch({ limit: 10 });
            const hasBtn = messages.some(m => m.author.id === client.user.id && m.components.length > 0);
            if (!hasBtn) {
                const embed = new EmbedBuilder()
                    .setTitle('🔑 Redeem Your License')
                    .setDescription('Use `/redeem <key>` here or click the button below to open a private ticket.')
                    .setColor(0x5865F2)
                    .setFooter({ text: 'Frost Client' });

                const row = new ActionRowBuilder().addComponents(
                    new ButtonBuilder()
                        .setCustomId('open_redeem_ticket')
                        .setLabel('Open Redeem Ticket')
                        .setStyle(ButtonStyle.Primary)
                );
                await channel.send({ embeds: [embed], components: [row] });
            }
        }
    } catch (e) {
        console.warn('[Bot] Could not post redeem button:', e.message);
    }
});

// ── Interactions ──────────────────────────────────────────────────────────────
client.on('interactionCreate', async interaction => {

    // ── Button: Open redeem ticket ──
    if (interaction.isButton() && interaction.customId === 'open_redeem_ticket') {
        const guild = interaction.guild;
        // Avoid duplicate tickets
        const existing = guild.channels.cache.find(c =>
            c.name === `redeem-${interaction.user.username.toLowerCase()}` &&
            c.type === ChannelType.GuildText
        );
        if (existing) {
            return interaction.reply({ content: `You already have a ticket: <#${existing.id}>`, ephemeral: true });
        }

        const ticketChannel = await guild.channels.create({
            name: `redeem-${interaction.user.username.toLowerCase()}`,
            type: ChannelType.GuildText,
            permissionOverwrites: [
                { id: guild.id, deny: [PermissionsBitField.Flags.ViewChannel] },
                {
                    id: interaction.user.id,
                    allow: [PermissionsBitField.Flags.ViewChannel, PermissionsBitField.Flags.SendMessages]
                },
                {
                    id: client.user.id,
                    allow: [PermissionsBitField.Flags.ViewChannel, PermissionsBitField.Flags.SendMessages,
                            PermissionsBitField.Flags.ManageChannels]
                }
            ]
        });

        const closeRow = new ActionRowBuilder().addComponents(
            new ButtonBuilder()
                .setCustomId('close_ticket')
                .setLabel('Close Ticket')
                .setStyle(ButtonStyle.Danger)
        );

        await ticketChannel.send({
            content: `<@${interaction.user.id}> Welcome! Use \`/redeem <key>\` in this channel to activate your license.`,
            components: [closeRow]
        });
        return interaction.reply({ content: `Ticket created: <#${ticketChannel.id}>`, ephemeral: true });
    }

    // ── Button: Close ticket ──
    if (interaction.isButton() && interaction.customId === 'close_ticket') {
        await interaction.reply({ content: 'Closing ticket in 3 seconds…', ephemeral: false });
        setTimeout(() => interaction.channel.delete().catch(() => {}), 3000);
        return;
    }

    if (!interaction.isChatInputCommand()) return;

    // ── /generate ──
    if (interaction.commandName === 'generate') {
        await interaction.deferReply({ ephemeral: true });
        const plan   = interaction.options.getString('plan');
        const amount = Math.min(interaction.options.getInteger('amount'), 25);
        const keys = [];
        for (let i = 0; i < amount; i++) {
            try {
                const data = await apiPost('/generate', { plan });
                if (data.success) keys.push(data.key);
            } catch (e) { /* skip failed individual */ }
        }
        if (keys.length === 0) return interaction.editReply('❌ Failed to generate keys. Check server logs.');
        return interaction.editReply({
            content: `Generated **${keys.length}** \`${plan}\` key${keys.length > 1 ? 's' : ''}:\n\`\`\`\n${keys.join('\n')}\n\`\`\``
        });
    }

    // ── /revoke-license ──
    if (interaction.commandName === 'revoke-license') {
        await interaction.deferReply({ ephemeral: true });
        const key = interaction.options.getString('license');
        try {
            const data = await apiPost('/revoke', { key });
            return interaction.editReply(data.success ? `✅ Revoked \`${key}\`` : `❌ Key not found.`);
        } catch (e) {
            return interaction.editReply('❌ Server error.');
        }
    }

    // ── /reset-hwid ──
    if (interaction.commandName === 'reset-hwid') {
        await interaction.deferReply({ ephemeral: true });
        const key = interaction.options.getString('license');
        try {
            const data = await apiPost('/reset', { key });
            return interaction.editReply(data.success ? `✅ HWID reset for \`${key}\`` : `❌ Key not found.`);
        } catch (e) {
            return interaction.editReply('❌ Server error.');
        }
    }

    // ── /redeem ──
    if (interaction.commandName === 'redeem') {
        await interaction.deferReply({ ephemeral: true });
        const key = interaction.options.getString('key').trim();
        try {
            const data = await apiPost('/redeem', { key, discordId: interaction.user.id });
            if (data.success) {
                const roleId = data.plan === 'Lifetime' ? ROLE_LIFETIME : ROLE_MONTHLY;
                const member = await interaction.guild.members.fetch(interaction.user.id).catch(() => null);
                if (member) await member.roles.add(roleId).catch(console.error);
                return interaction.editReply(
                    `✅ **${data.plan}** license activated! Role has been granted.\nUse \`/download\` to get your loader.`
                );
            } else {
                return interaction.editReply(`❌ ${data.message || 'Invalid license key.'}`);
            }
        } catch (e) {
            return interaction.editReply('❌ Could not reach auth server. Try again in a moment.');
        }
    }

    // ── /download ──
    if (interaction.commandName === 'download') {
        if (interaction.channelId !== DOWNLOAD_CHANNEL) {
            return interaction.reply({
                content: `Please use this command in <#${DOWNLOAD_CHANNEL}>.`,
                ephemeral: true
            });
        }
        await interaction.deferReply({ ephemeral: true });
        try {
            const data = await apiPost('/license-by-discord', { discordId: interaction.user.id });
            if (!data.success || !data.key) {
                return interaction.editReply('❌ You do not have an active license. Use `/redeem <key>` first.');
            }
            // Injection disabled until mod is ready for distribution
            return interaction.editReply(
                '⏳ Mod downloads are temporarily disabled while we finish testing. Check back soon!'
            );
        } catch (e) {
            return interaction.editReply('❌ Server error — make sure your DMs are open.');
        }
    }

    // ── /mylicense ──
    if (interaction.commandName === 'mylicense') {
        await interaction.deferReply({ ephemeral: true });
        try {
            const data = await apiPost('/license-by-discord', { discordId: interaction.user.id });
            if (!data.success || !data.key) {
                return interaction.editReply('❌ No active license found for your account.');
            }
            const masked = data.key.substring(0, 20) + '…';
            return interaction.editReply(`✅ Active license: \`${masked}\``);
        } catch (e) {
            return interaction.editReply('❌ Server error.');
        }
    }
});

client.login(TOKEN).catch(err => {
    console.error('[Bot] Login failed:', err.message);
    process.exit(1);
});
