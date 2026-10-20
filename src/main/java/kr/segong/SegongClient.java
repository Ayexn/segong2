package kr.segong;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * 60 세공 도우미 (클라이언트 전용, 보기만 하는 모드).
 *  - 세공 모듈 칸에 들어 있는 보석(또는 마우스를 올린 보석)의 순도/광도/강도를 읽고
 *  - 광채 개수는 내 인벤토리를 직접 세어서
 *  - 추천 개수를 화면에 표시한다.
 */
public class SegongClient implements ClientModInitializer {
    private static final String[] NAMES = {"바이올렛", "오팔", "아다만티움"};
    private static final int[] LIMITS = {0, 200, 212, 236};          // 0 = 자동
    static final String[] GRADE_NAME = {"자동", "COMMON 200", "NORMAL 212", "RARE 236"};
    static final String[] STYLE_NAME = {"타일형", "카드형", "한 줄형", "슬롯 배지"};
    private static final long HOVER_MS = 300;                         // 화면이 없을 때 툴팁 표시 유지 시간
    private static final long FLASH_MS = 2500;                        // 알림 문구 표시 시간

    /** 화면에 그릴 내용 */
    static final class View {
        int[] stats;
        int lim;                 // 0이면 등급 모름
        String how = "";
        Calc.Result r;           // lim == 0 이면 null
        long[] stock;
        String sig = "";
    }

    /** 읽어 낸 보석 한 개 */
    private static final class Gem {
        int[] stats;
        int gradeLimit;          // 글자로 읽은 등급 한도 (못 읽으면 0)
        String sig = "";         // 등급 표시 특수문자 (학습용)
        List<String> lines = new ArrayList<>();
        List<Text> texts = new ArrayList<>();
        String info = "";
    }

    static KeyBinding toggleKey, gradeKey, settingsKey;
    static boolean enabled = true;
    static int gradeMode = 0;
    static int style = 0;       // 0 타일 / 1 카드 / 2 한줄 / 3 슬롯배지
    static float scale = 1.0f;  // 0.7 ~ 1.5
    static int theme = 0;       // 0 다크 약방 / 1 우드카드 / 2 크림 (카드와 설정 화면의 디자인)
    static final String[] THEME_NAME = {"다크 약방", "우드카드", "크림"};
    static float customX = -1f, customY = -1f;   // 끌어서 옮긴 위치(화면 비율). -1 이면 자동
    private static final Map<String, Integer> learned = new HashMap<>();   // 등급 표시 문자 → 한도

    private static boolean scanning = false;
    private static Gem hoverGem;          // 마우스를 올린 마지막 보석
    private static long hoverAt;
    private static Gem moduleGem;         // 열린 화면의 모듈 칸에서 찾은 보석
    private static long moduleScanAt;
    private static Gem currentGem;        // 지금 화면에 표시 중인 보석
    private static Gem lastAnyGem;        // 가장 최근에 본 보석 (/segong dump 용)

    private static String flash = "";
    private static long flashUntil;

    @Override
    public void onInitializeClient() {
        load();
        seedDefaults();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.segong.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.segong"));
        gradeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.segong.grade", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.segong"));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.segong.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.segong"));

        // 1) 마우스를 올린 아이템의 툴팁 읽기
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (scanning) return;
            Gem g = makeGem(stack, new ArrayList<>(lines));
            if (g == null) return;
            hoverGem = g;
            hoverAt = System.currentTimeMillis();
            lastAnyGem = g;
        });

        // 2) 화면이 없을 때(게임 중) 키 입력
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) doToggle();
            while (gradeKey.wasPressed()) doGrade();
            while (settingsKey.wasPressed()) client.setScreen(new SettingsScreen(null));
        });

        // 3) 화면이 열려 있을 때 키 입력 + 그리기 (열린 화면에서는 일반 키 바인딩이 안 먹기 때문)
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            hoverGem = null;
            moduleGem = null;
            moduleScanAt = 0;
            ScreenKeyboardEvents.afterKeyPress(screen).register((s, key, scancode, mods) -> onScreenKey(s, key, scancode));
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> draw(client, ctx, mx, my, true));
            if (screen instanceof HandledScreen<?>) {
                // 카드 위에서 왼쪽 버튼을 누르면 카드를 끌어 옮긴다 (아래 화면에는 클릭을 전달하지 않음)
                ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> !(button == 0 && Overlay.hit(mx, my)));
                ScreenMouseEvents.allowMouseRelease(screen).register((s, mx, my, button) -> !(button == 0 && Overlay.isDragging()));
            }
        });
        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.currentScreen == null) draw(c, ctx, -1, -1, false);
        });

        // 4) 명령어
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("segong")
                        .then(ClientCommandManager.literal("settings").executes(c -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            mc.send(() -> mc.setScreen(new SettingsScreen(null)));   // 채팅창이 닫힌 다음에 열기
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("inv").executes(c -> {
                            long[] n = countOrbs(MinecraftClient.getInstance());
                            c.getSource().sendFeedback(Text.literal("[세공] 인벤토리 광채: 바이올렛 " + n[0] + " / 오팔 " + n[1] + " / 아다만티움 " + n[2]));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("dump").executes(c -> {
                            Gem g = lastAnyGem;
                            if (g == null) {
                                c.getSource().sendFeedback(Text.literal("[세공] 아직 읽은 보석이 없습니다. 보석에 마우스를 올린 뒤 다시 입력하세요"));
                                return Command.SINGLE_SUCCESS;
                            }
                            c.getSource().sendFeedback(Text.literal("[세공] 마지막 보석 (" + g.lines.size() + "줄)"));
                            c.getSource().sendFeedback(Text.literal(" 아이템: " + g.info));
                            c.getSource().sendFeedback(Text.literal(" 등급표시 문자: " + (g.sig.isEmpty() ? "(없음)" : g.sig) + " / 글자로 읽은 한도: " + g.gradeLimit));
                            for (int i = 0; i < g.lines.size(); i++) {
                                c.getSource().sendFeedback(Text.literal("  " + (i + 1) + ") " + esc(g.lines.get(i))));
                                if (i < 2) c.getSource().sendFeedback(Text.literal("     조각: " + runs(g.texts.get(i))));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("slots").executes(c -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (!(mc.currentScreen instanceof HandledScreen<?> hs)) {
                                c.getSource().sendFeedback(Text.literal("[세공] 세공 화면을 연 상태에서 입력하세요"));
                                return Command.SINGLE_SUCCESS;
                            }
                            int cnt = 0;
                            for (Slot sl : hs.getScreenHandler().slots) {
                                if (sl.inventory == mc.player.getInventory()) continue;
                                ItemStack st = sl.getStack();
                                if (st.isEmpty()) continue;
                                cnt++;
                                boolean gem = makeGem(st, tooltipTexts(mc, st)) != null;
                                c.getSource().sendFeedback(Text.literal("  칸" + sl.id + ": " + st.getName().getString() + " x" + st.getCount() + (gem ? "  ← 보석" : "")));
                            }
                            if (cnt == 0) c.getSource().sendFeedback(Text.literal("[세공] 내 인벤토리가 아닌 칸에 아이템이 없습니다"));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("items").executes(c -> {
                            PlayerInventory inv = MinecraftClient.getInstance().player.getInventory();
                            for (int i = 0; i < inv.size(); i++) {
                                ItemStack s = inv.getStack(i);
                                if (!s.isEmpty()) c.getSource().sendFeedback(Text.literal("  [" + i + "] " + s.getName().getString() + " x" + s.getCount()));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))));
    }

    // ---------------------------------------------------------------- 키 동작

    private static void onScreenKey(Screen screen, int key, int scancode) {
        if (screen instanceof ChatScreen) return;
        if (screen.getFocused() instanceof TextFieldWidget) return;   // 글자 입력 중에는 무시
        if (toggleKey.matchesKey(key, scancode)) doToggle();
        else if (gradeKey.matchesKey(key, scancode)) doGrade();
    }

    private static void doToggle() {
        enabled = !enabled;
        save();
        flash("세공 도우미: " + (enabled ? "켜짐" : "꺼짐 (다시 켜려면 " + keyName(toggleKey) + ")"));
    }

    private static void doGrade() {
        gradeMode = (gradeMode + 1) % 4;
        String extra = "";
        Gem g = currentGem;
        if (gradeMode > 0 && g != null && !g.sig.isEmpty()) {
            learned.put(g.sig, LIMITS[gradeMode]);      // 이 보석의 등급 표시를 기억 → 다음부터 자동
            extra = " · 이 등급 표시를 기억함";
        }
        save();
        flash("등급 한도: " + GRADE_NAME[gradeMode] + extra);
    }

    /** 설정에서 바꾼 키 이름을 그대로 보여준다. */
    static String keyName(KeyBinding kb) {
        try {
            return kb.getBoundKeyLocalizedText().getString();
        } catch (Throwable e) {
            return "?";
        }
    }

    private static void flash(String s) {
        flash = s;
        flashUntil = System.currentTimeMillis() + FLASH_MS;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null && c.currentScreen == null) c.player.sendMessage(Text.literal("§e[세공] §f" + s), true);
    }

    // ---------------------------------------------------------------- 보석 읽기

    private static List<Text> tooltipTexts(MinecraftClient client, ItemStack stack) {
        scanning = true;
        try {
            return new ArrayList<>(stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC));
        } catch (Throwable e) {
            return new ArrayList<>();
        } finally {
            scanning = false;
        }
    }

    /** 툴팁에 순도/광도/강도가 모두 있으면 Gem, 아니면 null */
    private static Gem makeGem(ItemStack stack, List<Text> texts) {
        List<String> plain = new ArrayList<>();
        for (Text t : texts) plain.add(t.getString());
        int[] st = GemParser.parseStats(plain);
        if (st == null) return null;
        Gem g = new Gem();
        g.stats = st;
        g.gradeLimit = GemParser.parseGradeLimit(plain);
        g.lines = plain;
        g.texts = texts;
        g.sig = signature(plain);
        try {
            String info = Registries.ITEM.getId(stack.getItem()) + " " + stack.getComponents();
            g.info = info.length() > 400 ? info.substring(0, 400) + "…" : info;
        } catch (Throwable e) {
            g.info = "(정보 읽기 실패)";
        }
        return g;
    }

    /** 첫 줄(등급 그림 글자 + 이름)에서 영문/숫자/한글이 아닌 글자만 모은 값. 등급 표시 구분용. */
    private static String signature(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        if (lines.isEmpty()) return "";
        lines.get(0).codePoints().forEach(cp -> {
            boolean ascii = cp < 128;
            boolean hangul = (cp >= 0xAC00 && cp <= 0xD7A3) || (cp >= 0x1100 && cp <= 0x11FF) || (cp >= 0x3130 && cp <= 0x318F);
            if (!ascii && !hangul && !Character.isWhitespace(cp) && cp != 0xA0) {
                if (sb.length() > 0) sb.append('-');
                sb.append(Integer.toHexString(cp));
            }
        });
        return sb.toString();
    }

    /** 열린 화면의 '내 인벤토리가 아닌' 칸에서 보석을 찾는다. 0.25초마다 한 번. */
    private static void scanModule(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (now - moduleScanAt < 250) return;
        moduleScanAt = now;
        moduleGem = null;
        if (!(client.currentScreen instanceof HandledScreen<?> hs)) return;
        for (Slot sl : hs.getScreenHandler().slots) {
            if (sl.inventory == client.player.getInventory()) continue;
            ItemStack st = sl.getStack();
            if (st.isEmpty()) continue;
            Gem g = makeGem(st, tooltipTexts(client, st));
            if (g != null) { moduleGem = g; lastAnyGem = g; return; }
        }
    }

    /** 내 인벤토리에서 광채 개수를 직접 센다. */
    static long[] countOrbs(MinecraftClient client) {
        long[] n = new long[3];
        if (client == null || client.player == null) return n;
        PlayerInventory inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            String name = s.getName().getString();
            if (!name.contains("광채")) continue;
            for (int k = 0; k < 3; k++) {
                if (name.contains(NAMES[k])) { n[k] += s.getCount(); break; }
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 그리기

    private static void draw(MinecraftClient client, DrawContext ctx, int mx, int my, boolean interactive) {
        if (client.player == null) return;
        Overlay.clearHit();
        long now = System.currentTimeMillis();
        if (now < flashUntil) {
            String f = "§b» " + flash;
            int w = client.textRenderer.getWidth(f), cx = client.getWindow().getScaledWidth() / 2;
            ctx.fill(cx - w / 2 - 4, 2, cx + w / 2 + 4, 14, 0xB0000000);
            ctx.drawText(client.textRenderer, f, cx - w / 2, 4, 0xFFFFFFFF, true);
        }
        if (!enabled) return;

        boolean screenOpen = client.currentScreen != null;
        Gem g = null;
        if (screenOpen) {
            scanModule(client);
            g = moduleGem;
        }
        if (g == null && hoverGem != null && (screenOpen || now - hoverAt <= HOVER_MS)) g = hoverGem;
        currentGem = g;
        if (g == null) return;

        View v = new View();
        v.stats = g.stats;
        v.sig = g.sig;
        v.stock = countOrbs(client);
        if (g.gradeLimit > 0) { v.lim = g.gradeLimit; v.how = "자동"; }
        else if (!g.sig.isEmpty() && learned.containsKey(g.sig)) { v.lim = learned.get(g.sig); v.how = "기억"; }
        else if (gradeMode > 0) { v.lim = LIMITS[gradeMode]; v.how = "수동"; }
        if (v.lim > 0) v.r = Calc.best(g.stats[0], g.stats[1], g.stats[2], v.lim, v.stock);
        Overlay.render(client, ctx, v, mx, my, interactive);
    }

    /** 설정 화면 미리보기용 예시 */
    static View sampleView() {
        View v = new View();
        v.stats = new int[]{11, 31, 18};
        v.lim = 212;
        v.how = "자동";
        v.stock = new long[]{184, 149, 248};
        v.r = Calc.best(11, 31, 18, 212, v.stock);
        return v;
    }

    static void resetPos() {
        customX = -1f;
        customY = -1f;
        save();
    }

    static void resetLearned() {
        learned.clear();
        gradeMode = 0;
        seedDefaults();
        save();
    }

    // ---------------------------------------------------------------- 점검용 글자 처리

    /** 영문/숫자/한글 외의 글자는 U+코드 형태로 보여준다 (특수문자 확인용). */
    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        s.codePoints().forEach(cp -> {
            if ((cp >= 32 && cp < 127) || (cp >= 0xAC00 && cp <= 0xD7A3)) b.appendCodePoint(cp);
            else b.append("<U+").append(Integer.toHexString(cp)).append('>');
        });
        return b.toString();
    }

    private static String runs(Text t) {
        StringBuilder b = new StringBuilder();
        t.visit((style, str) -> {
            String st = String.valueOf(style);
            if (st.length() > 110) st = st.substring(0, 110) + "…";
            b.append('[').append(esc(str)).append(" | ").append(st).append(']');
            return Optional.<Object>empty();
        }, Style.EMPTY);
        return b.toString();
    }

    // ---------------------------------------------------------------- 설정 저장

    /** 확인된 등급 그림 글자: a002 = COMMON(200), a003 = NORMAL(212), a004 = RARE(236). */
    private static void seedDefaults() {
        learned.put("a002", 200);
        learned.put("a003", 212);
        learned.put("a004", 236);
    }

    private static Path cfgPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("segong.properties");
    }

    private static void load() {
        try {
            Path p = cfgPath();
            if (!Files.exists(p)) return;
            Properties pr = new Properties();
            try (InputStream in = Files.newInputStream(p)) { pr.load(in); }
            enabled = !"false".equals(pr.getProperty("enabled", "true"));
            gradeMode = Math.max(0, Math.min(3, Integer.parseInt(pr.getProperty("grade", "0"))));
            style = Math.max(0, Math.min(STYLE_NAME.length - 1, Integer.parseInt(pr.getProperty("style", "0"))));
            scale = Math.max(0.7f, Math.min(1.5f, Float.parseFloat(pr.getProperty("scale2", "1.0"))));
            theme = Math.max(0, Math.min(THEME_NAME.length - 1, Integer.parseInt(pr.getProperty("design", "0"))));
            customX = Float.parseFloat(pr.getProperty("px", "-1"));
            customY = Float.parseFloat(pr.getProperty("py", "-1"));
            for (String k : pr.stringPropertyNames()) {
                if (k.startsWith("sig.")) learned.put(k.substring(4), Integer.parseInt(pr.getProperty(k)));
            }
        } catch (Throwable e) {
            // 설정을 못 읽어도 기본값으로 동작
        }
    }

    static void save() {
        try {
            Properties pr = new Properties();
            pr.setProperty("enabled", String.valueOf(enabled));
            pr.setProperty("grade", String.valueOf(gradeMode));
            pr.setProperty("style", String.valueOf(style));
            pr.setProperty("scale2", String.valueOf(scale));
            pr.setProperty("design", String.valueOf(theme));
            pr.setProperty("px", String.valueOf(customX));
            pr.setProperty("py", String.valueOf(customY));
            for (Map.Entry<String, Integer> e : learned.entrySet()) pr.setProperty("sig." + e.getKey(), String.valueOf(e.getValue()));
            try (OutputStream out = Files.newOutputStream(cfgPath())) { pr.store(out, "segong-helper"); }
        } catch (Throwable e) {
            // 저장 실패해도 동작에는 지장 없음
        }
    }
}
