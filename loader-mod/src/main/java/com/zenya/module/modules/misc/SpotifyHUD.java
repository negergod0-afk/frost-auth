package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.setting.Setting;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SpotifyHUD extends Module {

    private static SpotifyHUD INSTANCE;

    private final Setting<Double> scale = new Setting<>("Scale", 1.0, 0.60, 2.00);

    private volatile String title = "";
    private volatile String artist = "";
    private volatile boolean isPlaying = false;
    private volatile long anchorPosMs = 0;
    private volatile long anchorTimeMs = 0;
    private volatile long durMs = 0;

    private ScheduledExecutorService scheduler;
    private ExecutorService actionExec;
    private File pollScriptFile;
    private File ctrlScriptFile;
    private File artFile;

    private static final Identifier ART_ID = Identifier.of("zenya", "spotify_art");
    private NativeImageBackedTexture artTex = null;
    private volatile long artLastModified = 0L;
    private volatile boolean hasArt = false;

    private float fade = 0f;
    private long lastNanos = 0L;
    private float scrollX = 0f;
    private long scrollNanos = 0L;

    private volatile boolean hasPolledOnce = false;
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final AtomicInteger fastPollCount = new AtomicInteger(0);

    private static final int BASE_W = 228;
    private static final int BASE_H = 74;
    private static final int BASE_ART = 50;
    private static final int BASE_PAD_X = 8;
    private static final float SCROLL_PX_S = 28f;
    private static final int SCROLL_GAP = 18;
    private static final int ROW_H = 8;
    private static final int GAP_TITLE = 3;
    private static final int GAP_ART = 5;
    private static final int GAP_BAR = 4;
    private static final int GAP_TIME = 5;
    private static final int BAR_H_B = 3;
    private static final int MARGIN_R = 8;
    private static final int MARGIN_B = 8;

    private static final String POLL_SCRIPT =
        "[void][System.Reflection.Assembly]::LoadFile('C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\System.Runtime.WindowsRuntime.dll')\r\n" +
        "$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]\r\n" +
        "$g = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -like 'IAsyncOperation*' })[0]\r\n" +
        "function Aw($op,$t){$m=$g.MakeGenericMethod($t);$task=$m.Invoke($null,@($op));$task.GetAwaiter().GetResult()}\r\n" +
        "$asStreamForRead = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1 } | Select-Object -First 1\r\n" +
        "try {\r\n" +
        "  $mgr = Aw([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\r\n" +
        "  $s = $mgr.GetCurrentSession()\r\n" +
        "  if ($s) {\r\n" +
        "    $p = Aw($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\r\n" +
        "    $tl = $s.GetTimelineProperties()\r\n" +
        "    $pb = $s.GetPlaybackInfo()\r\n" +
        "    if ($p.Title) {\r\n" +
        "      if ($p.Thumbnail -and $asStreamForRead) {\r\n" +
        "        try {\r\n" +
        "          $stream = Aw($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])\r\n" +
        "          $netStream = $asStreamForRead.Invoke($null, @($stream))\r\n" +
        "          $outPath = Join-Path $env:TEMP 'zenya_spotify_art.png'\r\n" +
        "          $fs = [System.IO.File]::Create($outPath)\r\n" +
        "          $netStream.CopyTo($fs)\r\n" +
        "          $fs.Close()\r\n" +
        "          $netStream.Close()\r\n" +
        "        } catch {}\r\n" +
        "      }\r\n" +
        "      Write-Output ($p.Artist + '|||' + $p.Title + '|||' + [long]$tl.Position.TotalMilliseconds + '|||' + [long]$tl.EndTime.TotalMilliseconds + '|||' + ($pb.PlaybackStatus.ToString() -eq 'Playing'))\r\n" +
        "    }\r\n" +
        "  }\r\n" +
        "} catch {}\r\n";

    private static final String CTRL_SCRIPT =
        "param([string]$action)\r\n" +
        "[void][System.Reflection.Assembly]::LoadFile('C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\System.Runtime.WindowsRuntime.dll')\r\n" +
        "$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]\r\n" +
        "$g = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -like 'IAsyncOperation*' })[0]\r\n" +
        "function Aw($op,$t){$m=$g.MakeGenericMethod($t);$task=$m.Invoke($null,@($op));$task.GetAwaiter().GetResult()}\r\n" +
        "try {\r\n" +
        "  $mgr = Aw([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\r\n" +
        "  $s = $mgr.GetCurrentSession()\r\n" +
        "  if ($s) {\r\n" +
        "    switch ($action) {\r\n" +
        "      'next'   { Aw($s.TrySkipNextAsync()) ([bool]) | Out-Null }\r\n" +
        "      'prev'   { Aw($s.TrySkipPreviousAsync()) ([bool]) | Out-Null }\r\n" +
        "      'toggle' { Aw($s.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }\r\n" +
        "    }\r\n" +
        "  }\r\n" +
        "} catch {}\r\n";

    public SpotifyHUD() {
        super("SpotifyHUD", Category.MISC);
        setDescription("Shows currently playing media with real-time controls.");
        addSetting(scale);
        INSTANCE = this;
    }

    public static SpotifyHUD getInstance() { return INSTANCE; }

    private float sc() { return scale.getValue().floatValue(); }
    private int s(int base) { return Math.round(base * sc()); }
    private int cardW() { return Math.round(BASE_W * sc()); }
    private int cardH() { return Math.round(BASE_H * sc()); }

    private int posX() {
        if (mc == null || mc.getWindow() == null) return 0;
        return mc.getWindow().getScaledWidth() - cardW() - MARGIN_R;
    }

    private int posY() {
        if (mc == null || mc.getWindow() == null) return 0;
        return mc.getWindow().getScaledHeight() - cardH() - MARGIN_B;
    }

    @Override
    public void onEnable() { startBackground(); }

    @Override
    public void onTick() {
        if (scheduler == null || scheduler.isShutdown()) startBackground();
    }

    private void startBackground() {
        fade = 0f; scrollX = 0f; lastNanos = 0L; scrollNanos = 0L;
        hasPolledOnce = false;
        fastPollCount.set(0);
        writeScripts();
        artFile = new File(System.getenv("TEMP"), "zenya_spotify_art.png");

        Thread init = new Thread(this::poll, "zenya-spotify-init");
        init.setDaemon(true);
        init.start();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "zenya-spotify");
            t.setDaemon(true);
            return t;
        });
        actionExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "zenya-spotify-ctrl");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            int c = fastPollCount.getAndIncrement();
            if (c >= 10) return;
            poll();
            if (!title.isEmpty()) fastPollCount.set(Integer.MAX_VALUE);
        }, 800, 800, TimeUnit.MILLISECONDS);

        scheduler.scheduleAtFixedRate(this::poll, 2, 2, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        if (scheduler != null) scheduler.shutdownNow();
        if (actionExec != null) actionExec.shutdownNow();
        title = ""; artist = ""; isPlaying = false;
        anchorPosMs = 0; anchorTimeMs = 0; durMs = 0;
        fade = 0f; scrollX = 0f; hasArt = false;
        if (artTex != null) {
            try { mc.getTextureManager().destroyTexture(ART_ID); } catch (Exception ignored) {}
            artTex = null;
        }
    }

    private void writeScripts() {
        try {
            pollScriptFile = File.createTempFile("zenya_smtc_poll_", ".ps1");
            pollScriptFile.deleteOnExit();
            try (Writer w = new FileWriter(pollScriptFile, StandardCharsets.UTF_8)) { w.write(POLL_SCRIPT); }
            ctrlScriptFile = File.createTempFile("zenya_smtc_ctrl_", ".ps1");
            ctrlScriptFile.deleteOnExit();
            try (Writer w = new FileWriter(ctrlScriptFile, StandardCharsets.UTF_8)) { w.write(CTRL_SCRIPT); }
        } catch (Exception e) {
            pollScriptFile = null;
            ctrlScriptFile = null;
        }
    }

    private void poll() {
        if (pollScriptFile == null || !pollScriptFile.exists()) writeScripts();
        if (pollScriptFile == null) { hasPolledOnce = true; return; }
        if (!polling.compareAndSet(false, true)) return;
        try {
            long beforeMs = System.currentTimeMillis();
            Process proc = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass",
                    "-File", pollScriptFile.getAbsolutePath()
            ).redirectErrorStream(true).start();

            String line = null;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) {
                    l = l.trim();
                    if (l.contains("|||")) { line = l; break; }
                }
            }
            proc.waitFor(8, TimeUnit.SECONDS);
            long afterMs = System.currentTimeMillis();
            long effectiveMs = (beforeMs + afterMs) / 2;

            if (line != null) {
                String[] p = line.split("\\|\\|\\|", -1);
                if (p.length >= 2 && !p[1].trim().isEmpty()) {
                    String newTitle = p[1].trim();
                    String newArtist = p[0].trim();
                    long newPosMs = p.length > 2 ? parseLong(p[2]) : 0;
                    long newDurMs = p.length > 3 ? parseLong(p[3]) : 0;
                    boolean playing = p.length > 4 && p[4].trim().equalsIgnoreCase("True");

                    boolean trackChanged = !newTitle.equals(title);
                    boolean stateChanged = playing != isPlaying;
                    long predicted = isPlaying
                            ? anchorPosMs + Math.max(0, effectiveMs - anchorTimeMs)
                            : anchorPosMs;
                    boolean bigJump = Math.abs(newPosMs - predicted) > 4000;

                    if (trackChanged) scrollX = 0f;
                    title = newTitle;
                    artist = newArtist;
                    durMs = newDurMs;
                    isPlaying = playing;

                    if (trackChanged || stateChanged || bigJump || anchorTimeMs == 0) {
                        anchorPosMs = newPosMs;
                        anchorTimeMs = effectiveMs;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            hasPolledOnce = true;
            polling.set(false);
        }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    public static void runAction(String action) {
        if (INSTANCE == null || INSTANCE.actionExec == null || INSTANCE.ctrlScriptFile == null) return;
        long nowMs = System.currentTimeMillis();
        if ("toggle".equals(action)) {
            if (INSTANCE.isPlaying) {
                long elapsed = INSTANCE.anchorTimeMs > 0
                        ? Math.max(0, nowMs - INSTANCE.anchorTimeMs) : 0;
                INSTANCE.anchorPosMs = INSTANCE.anchorPosMs + elapsed;
                INSTANCE.anchorTimeMs = nowMs;
                INSTANCE.isPlaying = false;
            } else {
                INSTANCE.anchorTimeMs = nowMs;
                INSTANCE.isPlaying = true;
            }
        }
        INSTANCE.actionExec.submit(() -> {
            try {
                Process p = new ProcessBuilder(
                        "powershell", "-NoProfile", "-NonInteractive",
                        "-ExecutionPolicy", "Bypass",
                        "-File", INSTANCE.ctrlScriptFile.getAbsolutePath(),
                        "-action", action
                ).redirectErrorStream(true).start();
                p.waitFor(5, TimeUnit.SECONDS);
                if (INSTANCE.scheduler != null && !INSTANCE.scheduler.isShutdown()) {
                    INSTANCE.scheduler.submit(INSTANCE::poll);
                }
            } catch (Exception ignored) {}
        });
    }

    public static boolean handleClick(double mx, double my) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return false;
        if (mc == null || mc.textRenderer == null) return false;

        int bx = INSTANCE.posX(), by = INSTANCE.posY();
        int cw = INSTANCE.cardW(), ch = INSTANCE.cardH();
        var tr = mc.textRenderer;

        String prev = "\u25C0\u25C0";
        String togg = INSTANCE.isPlaying ? "\u2759\u2759" : "\u25B6";
        String next = "\u25B6\u25B6";

        int[] layout = INSTANCE.computeLayout(by, ch);
        int btnY = layout[4];
        int cx = bx + cw / 2;
        int gap = INSTANCE.s(14);
        int togX = cx - tr.getWidth(togg) / 2;
        int prvX = togX - gap - tr.getWidth(prev);
        int nxtX = togX + tr.getWidth(togg) + gap;
        int padX = INSTANCE.s(5), padY = INSTANCE.s(3);

        int[][] rects = {
            { prvX - padX, btnY - padY, tr.getWidth(prev) + 2 * padX, ROW_H + 2 * padY },
            { togX - padX, btnY - padY, tr.getWidth(togg) + 2 * padX, ROW_H + 2 * padY },
            { nxtX - padX, btnY - padY, tr.getWidth(next) + 2 * padX, ROW_H + 2 * padY }
        };
        String[] actions = { "prev", "toggle", "next" };
        for (int i = 0; i < rects.length; i++) {
            int[] r = rects[i];
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                runAction(actions[i]);
                return true;
            }
        }
        return false;
    }

    private void tryLoadArt() {
        if (artFile == null || !artFile.exists()) return;
        long mod = artFile.lastModified();
        long size = artFile.length();
        if (size < 200) return;
        if (mod == artLastModified && artTex != null) return;
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(artFile.toPath());
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                NativeImage img = NativeImage.read(in);
                if (artTex != null) {
                    try { mc.getTextureManager().destroyTexture(ART_ID); } catch (Exception ignored) {}
                }
                artTex = new NativeImageBackedTexture(() -> "spotify_art", img);
                mc.getTextureManager().registerTexture(ART_ID, artTex);
                artLastModified = mod;
                hasArt = true;
            }
        } catch (Exception ignored) {}
    }

    private int[] computeLayout(int by, int cardH) {
        int barH = Math.max(2, s(BAR_H_B));
        int contentH = ROW_H + s(GAP_TITLE) + ROW_H + s(GAP_ART) + barH + s(GAP_BAR) + ROW_H + s(GAP_TIME) + ROW_H;
        int top = by + (cardH - contentH) / 2;
        return new int[]{ top, top + ROW_H + s(GAP_TITLE), top + ROW_H + s(GAP_TITLE) + ROW_H + s(GAP_ART),
                top + ROW_H + s(GAP_TITLE) + ROW_H + s(GAP_ART) + barH + s(GAP_BAR),
                top + ROW_H + s(GAP_TITLE) + ROW_H + s(GAP_ART) + barH + s(GAP_BAR) + ROW_H + s(GAP_TIME) };
    }

    public static void render(DrawContext ctx, float tickDelta) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;
        INSTANCE.renderCard(ctx);
    }

    private void renderCard(DrawContext ctx) {
        if (mc == null || mc.player == null) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        fade += (1f - fade) * (1f - (float) Math.exp(-12f * dt));
        float a = fade;
        if (a < 0.01f) return;

        tryLoadArt();

        int cw = cardW();
        int ch = cardH();
        int artSize = s(BASE_ART);
        int padX = s(BASE_PAD_X);
        int bx = posX() + Math.round((1f - a) * (cw + s(20)));
        int by = posY();
        var tr = mc.textRenderer;

        Color accentC = ZenyaPlus.getAccentColor();
        int accent = accentC.getRed() << 16 | accentC.getGreen() << 8 | accentC.getBlue();

        int ia = clampA((int) (230 * a));
        int cBg = (ia << 24) | 0x0D1117;
        int cBorder = (clampA((int) (180 * a)) << 24) | 0x1E2632;
        int cArtBg = (clampA((int) (210 * a)) << 24) | 0x141A22;
        int cTitle = (clampA((int) (255 * a)) << 24) | 0xFFFFFF;
        int cArtist = (clampA((int) (200 * a)) << 24) | 0x8090A0;
        int cBarBg = (clampA((int) (200 * a)) << 24) | 0x1A2030;
        int cTime = (clampA((int) (170 * a)) << 24) | 0x506070;
        int cBtn = (clampA((int) (210 * a)) << 24) | 0xCCCCCC;
        int cNote = (clampA((int) (150 * a)) << 24) | accent;
        int cFill = (clampA((int) (205 * a)) << 24) | accent;
        int cSep = (clampA((int) (60 * a)) << 24) | 0xFFFFFF;
        int radius = s(6);

        RenderUtil.drawRoundedRect(ctx, bx, by, cw, ch, radius, cBg, false);
        RenderUtil.drawOutline(ctx, bx - 1, by - 1, cw + 2, ch + 2, radius + 1, 1f, cBorder, false);
        RenderUtil.drawRoundedRect(ctx, bx, by, cw, ch, radius, cBg, false);
        RenderUtil.drawRoundedRect(ctx, bx + radius, by, cw - 2 * radius, Math.max(2, s(2)), 0, cFill, false);

        int ax = bx + padX;
        int ay = by + (ch - artSize) / 2;

        if (hasArt) {
            RenderUtil.drawRoundedRect(ctx, ax, ay, artSize, artSize, s(4), cArtBg, false);
            ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                    ART_ID, ax, ay, 0, 0, artSize, artSize, artSize, artSize);
        } else {
            RenderUtil.drawRoundedRect(ctx, ax, ay, artSize, artSize, s(4), cArtBg, false);
            String noteChar = "\u266B";
            int nw = tr.getWidth(noteChar);
            ctx.drawText(tr, noteChar, ax + (artSize - nw) / 2, ay + (artSize - 9) / 2, cNote, false);
        }

        int sepX = ax + artSize + padX / 2;
        ctx.fill(sepX, by + s(6), sepX + 1, by + ch - s(6), cSep);

        int tx = ax + artSize + padX;
        int maxTw = Math.max(20, bx + cw - s(BASE_PAD_X) - tx);
        int[] layout = computeLayout(by, ch);
        int titleY = layout[0], artistY = layout[1], barY = layout[2], timeY = layout[3], btnY = layout[4];
        int barH = Math.max(2, s(BAR_H_B));

        String song = !hasPolledOnce ? "" : (title.isEmpty() ? "Nothing playing" : title);
        if (!song.isEmpty()) {
            int songW = tr.getWidth(song);
            if (songW > maxTw) {
                long sNow = System.nanoTime();
                float sdt = scrollNanos == 0L ? 0f : Math.min(0.1f, (sNow - scrollNanos) / 1_000_000_000f);
                scrollNanos = sNow;
                scrollX += SCROLL_PX_S * sdt;
                if (scrollX > songW + SCROLL_GAP) scrollX = 0f;
                int off = (int) scrollX;
                ctx.enableScissor(tx, titleY - 1, tx + maxTw, titleY + 10);
                ctx.drawText(tr, song, tx - off, titleY, cTitle, false);
                ctx.drawText(tr, song, tx - off + songW + SCROLL_GAP, titleY, cTitle, false);
                ctx.disableScissor();
            } else {
                scrollX = 0f;
                ctx.drawText(tr, song, tx, titleY, cTitle, false);
            }
        }

        if (!artist.isEmpty()) {
            ctx.drawText(tr, tr.trimToWidth(artist, maxTw), tx, artistY, cArtist, false);
        }

        RenderUtil.drawRoundedRect(ctx, tx, barY, maxTw, barH, barH / 2f, cBarBg, false);

        long nowMs = System.currentTimeMillis();
        long posMsNow;
        if (isPlaying && anchorTimeMs > 0) {
            long elapsed = Math.max(0, nowMs - anchorTimeMs);
            posMsNow = anchorPosMs + elapsed;
            if (durMs > 0) posMsNow = Math.min(durMs, posMsNow);
        } else {
            posMsNow = anchorPosMs;
        }

        if (durMs > 0 && posMsNow > 0) {
            float pct = Math.min(1f, (float) posMsNow / durMs);
            int fill = Math.max(barH, Math.round(maxTw * pct));
            RenderUtil.drawRoundedRect(ctx, tx, barY, fill, barH, barH / 2f, cFill, false);
            int dotR = Math.max(2, s(3));
            fillCircle(ctx, tx + fill, barY + barH / 2, dotR, cFill);
        }

        long pos = posMsNow / 1000;
        long dur = durMs / 1000;
        String tPos = fmt(pos);
        String tDur = dur > 0 ? fmt(dur) : "--:--";
        ctx.drawText(tr, tPos, tx, timeY, cTime, false);
        ctx.drawText(tr, tDur, tx + maxTw - tr.getWidth(tDur), timeY, cTime, false);

        String prev = "\u25C0\u25C0";
        String togg = isPlaying ? "\u2759\u2759" : "\u25B6";
        String next = "\u25B6\u25B6";
        int cx = bx + cw / 2;
        int gap = s(14);
        int togX = cx - tr.getWidth(togg) / 2;
        int prvX = togX - gap - tr.getWidth(prev);
        int nxtX = togX + tr.getWidth(togg) + gap;
        ctx.drawText(tr, prev, prvX, btnY, cBtn, false);
        ctx.drawText(tr, togg, togX, btnY, cBtn, false);
        ctx.drawText(tr, next, nxtX, btnY, cBtn, false);
    }

    private void fillCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int row = 0; row < r * 2; row++) {
            float dy = r - row - 0.5f;
            float xw = (float) Math.sqrt(Math.max(0.0, (double) r * r - (double) dy * dy));
            int xi = (int) xw;
            if (xi > 0) ctx.fill(cx - xi, cy - r + row, cx + xi, cy - r + row + 1, color);
        }
    }

    private static String fmt(long sec) {
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    private static int clampA(int a) {
        return Math.max(0, Math.min(255, a));
    }
}
