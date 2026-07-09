package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class FakeStats extends Module {
    private static FakeStats instance;

    private final Setting<String> money = new Setting<>("Money", "67b");
    private final Setting<String> shards = new Setting<>("Shards", "frost");
    private final Setting<String> kills = new Setting<>("Kills", "69");
    private final Setting<String> deaths = new Setting<>("Deaths", "1");
    private final Setting<String> playtime = new Setting<>("Playtime", "67d");

    private ScoreboardObjective capturedSidebarObjective;
    private String capturedObjectiveName;
    private ScoreboardObjective fakeObjective;
    private FakeValues cachedFakeValues;
    private Object trackedWorld;
    private String lastSignature = "";
    private boolean dirty;
    private long lastUpdateMillis;

    public static FakeStats getInstance() {
        return instance;
    }

    public FakeStats() {
        super("FakeStats", Category.DONUT);
        instance = this;
        setDescription("Replaces sidebar scoreboard stats locally.");
        addSetting(money);
        addSetting(shards);
        addSetting(kills);
        addSetting(deaths);
        addSetting(playtime);
    }

    @Override
    public void onEnable() {
        trackedWorld = mc.world;
        capturedSidebarObjective = null;
        capturedObjectiveName = null;
        fakeObjective = null;
        lastSignature = "";

        money.setValue("67b");
        shards.setValue("frost");
        kills.setValue("69");
        deaths.setValue("1");
        playtime.setValue("67d");

        cachedFakeValues = new FakeValues(
                defaultIfEmpty(money.getValue(), "0"),
                defaultIfEmpty(shards.getValue(), "0"),
                defaultIfEmpty(kills.getValue(), "0"),
                defaultIfEmpty(deaths.getValue(), "0"),
                defaultIfEmpty(playtime.getValue(), "0m"));
        dirty = true;
    }

    @Override
    public void onDisable() {
        trackedWorld = null;
        lastSignature = "";
        dirty = false;

        ScoreboardObjective capturedObjective = capturedSidebarObjective;
        ScoreboardObjective fakeObjectiveToRemove = fakeObjective;
        capturedSidebarObjective = null;
        capturedObjectiveName = null;
        fakeObjective = null;

        if (mc.world != null) {
            Scoreboard scoreboard = mc.world.getScoreboard();
            try {
                if (fakeObjectiveToRemove != null) {
                    scoreboard.removeObjective(fakeObjectiveToRemove);
                }
                if (capturedObjective != null && scoreboard.getObjectives().contains(capturedObjective)) {
                    scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, capturedObjective);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onTick() {
        if (mc.world == null) {
            capturedSidebarObjective = null;
            capturedObjectiveName = null;
            fakeObjective = null;
            trackedWorld = null;
            lastSignature = "";
            return;
        }

        if (mc.world != trackedWorld) {
            capturedSidebarObjective = null;
            capturedObjectiveName = null;
            fakeObjective = null;
            lastSignature = "";
            trackedWorld = mc.world;
            dirty = true;
        }

        ensureSidebarCapture();
        if (capturedSidebarObjective == null) {
            return;
        }

        Scoreboard scoreboard = mc.world.getScoreboard();
        if (!scoreboard.getObjectives().contains(capturedSidebarObjective)) {
            capturedSidebarObjective = null;
            ensureSidebarCapture();
            if (capturedSidebarObjective == null) {
                return;
            }
        }

        ScoreboardObjective sourceObjective = capturedSidebarObjective;
        ArrayList<SidebarLine> sidebarLines = new ArrayList<>();
        List<ScoreboardEntry> entries = new ArrayList<>(scoreboard.getScoreboardEntries(sourceObjective));
        entries.removeIf(ScoreboardEntry::hidden);
        entries.sort(Comparator.comparingInt(ScoreboardEntry::value).reversed());
        if (entries.size() > 15) {
            entries = new ArrayList<>(entries.subList(0, 15));
        }

        for (ScoreboardEntry entry : entries) {
            int score = entry.value();
            Text lineText;
            if (entry.display() != null) {
                lineText = entry.display().copy();
            } else {
                Text nameText = entry.name() != null ? entry.name().copy() : Text.literal(entry.owner());
                lineText = Team.decorateName(scoreboard.getScoreHolderTeam(entry.owner()), nameText).copy();
            }
            sidebarLines.add(new SidebarLine(score, lineText));
        }

        MutableText title = sourceObjective.getDisplayName() != null
                ? sourceObjective.getDisplayName().copy()
                : Text.literal("Donut SMP");

        FakeValues fakeValues = new FakeValues(
                defaultIfEmpty(money.getValue(), "0"),
                defaultIfEmpty(shards.getValue(), "0"),
                defaultIfEmpty(kills.getValue(), "0"),
                defaultIfEmpty(deaths.getValue(), "0"),
                defaultIfEmpty(playtime.getValue(), "0m"));

        if (cachedFakeValues == null || !cachedFakeValues.toKey().equals(fakeValues.toKey())) {
            cachedFakeValues = fakeValues;
            dirty = true;
        }

        StringBuilder signatureBuilder = new StringBuilder(title.getString());
        for (SidebarLine line : sidebarLines) {
            signatureBuilder.append('\n').append(line.score()).append(':').append(line.text().getString());
        }
        signatureBuilder.append('\n').append(cachedFakeValues != null ? cachedFakeValues.toKey() : "");

        SidebarSnapshot snapshot = new SidebarSnapshot(title, sidebarLines, signatureBuilder.toString());

        if (!snapshot.signature().equals(lastSignature) || fakeObjective == null) {
            dirty = true;
        }

        if (dirty && cachedFakeValues != null && System.currentTimeMillis() - lastUpdateMillis >= 500L) {
            lastUpdateMillis = System.currentTimeMillis();

            ScoreboardObjective existingObjective = scoreboard.getNullableObjective("water_fake_stats");
            if (existingObjective != null) {
                scoreboard.removeObjective(existingObjective);
            }

            fakeObjective = scoreboard.addObjective(
                    "water_fake_stats",
                    ScoreboardCriterion.DUMMY,
                    snapshot.title().copy(),
                    ScoreboardCriterion.RenderType.INTEGER,
                    true,
                    BlankNumberFormat.INSTANCE);
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, fakeObjective);

            List<SidebarLine> snapshotLines = snapshot.lines();
            int statIndex = 0;
            String[] fakeStatValues = new String[]{
                    cachedFakeValues.money(),
                    cachedFakeValues.shards(),
                    cachedFakeValues.kills(),
                    cachedFakeValues.deaths(),
                    cachedFakeValues.playtime()
            };

            for (int lineIndex = 0; lineIndex < snapshotLines.size(); ++lineIndex) {
                SidebarLine line = snapshotLines.get(lineIndex);
                ScoreHolder holder = ScoreHolder.fromName("fake_stats_line_" + lineIndex);
                var scoreAccess = scoreboard.getOrCreateScore(holder, fakeObjective);
                scoreAccess.setScore(snapshotLines.size() - lineIndex);

                Text displayText;
                if (line.text().getString().trim().matches(".*\\d.*") && statIndex < fakeStatValues.length) {
                    displayText = replaceFirstNumber(line.text(), fakeStatValues[statIndex], extractTextSegments(line.text()));
                    ++statIndex;
                } else {
                    displayText = line.text().copy();
                }

                scoreAccess.setDisplayText(displayText);
                scoreAccess.setNumberFormat(BlankNumberFormat.INSTANCE);
            }

            lastSignature = snapshot.signature();
            dirty = false;
        }

        if (fakeObjective != null && scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR) != fakeObjective) {
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, fakeObjective);
        }
    }

    public Text fakeFooterText(Text text) {
        if (cachedFakeValues == null) {
            return text;
        }

        String sourceText = text.getString();
        Matcher matcher = Pattern.compile("(\\$\\s*)([0-9][0-9.,]*[KkMmBbTt]?)").matcher(sourceText);
        if (!matcher.find()) {
            return text;
        }

        String updated = sourceText.substring(0, matcher.start(2))
                + cachedFakeValues.money()
                + sourceText.substring(matcher.end(2));
        List<TextSegment> segments = extractTextSegments(text);
        Style style = segments.isEmpty() ? Style.EMPTY : segments.get(0).style();
        return Text.literal(updated).setStyle(style);
    }

    private Text replaceFirstNumber(Text source, String replacement, List<TextSegment> segments) {
        String sourceText = source.getString();
        int digitIndex = -1;
        for (int i = 0; i < sourceText.length(); ++i) {
            char ch = sourceText.charAt(i);
            if (Character.isDigit(ch) || ch == '-' && i + 1 < sourceText.length() && Character.isDigit(sourceText.charAt(i + 1))) {
                digitIndex = i;
                break;
            }
        }

        if (digitIndex < 0) {
            MutableText rebuilt = Text.empty();
            for (TextSegment segment : segments) {
                rebuilt.append(Text.literal(segment.value()).setStyle(segment.style()));
            }
            return rebuilt;
        }

        MutableText prefix = Text.empty();
        int remaining = digitIndex;
        for (TextSegment segment : segments) {
            if (remaining <= 0) {
                break;
            }
            String value = segment.value();
            int take = Math.min(value.length(), remaining);
            prefix.append(Text.literal(value.substring(0, take)).setStyle(segment.style()));
            remaining -= take;
        }

        Style replacementStyle = Style.EMPTY;
        int stylePos = digitIndex;
        for (TextSegment segment : segments) {
            if (!segment.value().isEmpty()) {
                replacementStyle = segment.style();
            }
            if (stylePos < segment.value().length()) {
                replacementStyle = segment.style();
                break;
            }
            stylePos -= segment.value().length();
        }

        prefix.append(Text.literal(replacement).setStyle(replacementStyle));
        return prefix;
    }

    private void ensureSidebarCapture() {
        if (mc.world == null) {
            return;
        }

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective sidebarObjective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (sidebarObjective != null && !"water_fake_stats".equals(sidebarObjective.getName())) {
            capturedSidebarObjective = sidebarObjective;
            capturedObjectiveName = sidebarObjective.getName();
            return;
        }

        if (capturedSidebarObjective != null && scoreboard.getObjectives().contains(capturedSidebarObjective)) {
            return;
        }

        if (capturedObjectiveName != null
                && (sidebarObjective = scoreboard.getNullableObjective(capturedObjectiveName)) != null
                && !"water_fake_stats".equals(sidebarObjective.getName())) {
            capturedSidebarObjective = sidebarObjective;
            return;
        }

        for (ScoreboardObjective objective : scoreboard.getObjectives()) {
            if ("water_fake_stats".equals(objective.getName())) {
                continue;
            }
            capturedSidebarObjective = objective;
            capturedObjectiveName = objective.getName();
            return;
        }
    }

    private static List<TextSegment> extractTextSegments(Text text) {
        ArrayList<TextSegment> segments = new ArrayList<>();
        text.visit((style, string) -> {
            if (!string.isEmpty()) {
                segments.add(new TextSegment(string, style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return segments;
    }

    private static String defaultIfEmpty(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return fallback;
        }
        return value;
    }

    record FakeValues(String money, String shards, String kills, String deaths, String playtime) {
        String toKey() {
            return money + "|" + shards + "|" + kills + "|" + deaths + "|" + playtime;
        }
    }

    record SidebarSnapshot(Text title, List<SidebarLine> lines, String signature) {
    }

    record SidebarLine(int score, Text text) {
    }

    record TextSegment(String value, Style style) {
    }
}
