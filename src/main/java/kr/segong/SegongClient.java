package kr.segong;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    static boolean icons = true;               // 광채 아이콘 표시
    static boolean lock = true;                // 추천 고정 (처음 정한 목표를 광채 넣는 동안 유지)
    static final ItemStack[] orbIcon = new ItemStack[3];   // 본 적 있는 광채 아이템 (그림 표시용)
    private static final String[] orbSnbt = new String[3];  // 그 아이템을 글자로 저장한 것 (설정 파일 icon.0~2). 게임을 껐다 켜도 그림을 쓰려고
    private static final boolean[] orbRestoreFailed = new boolean[3];
    static float customX = -1f, customY = -1f;   // 끌어서 옮긴 위치(화면 비율). -1 이면 자동
    private static final Map<String, Integer> learned = new HashMap<>();   // 등급 표시 문자 → 한도

    private static boolean scanning = false;
    private static Gem hoverGem;          // 마우스를 올린 마지막 보석
    private static long hoverAt;
    private static Gem moduleGem;         // 열린 화면의 모듈 칸에서 찾은 보석
    private static long moduleScanAt;
    private static Gem currentGem;        // 지금 화면에 표시 중인 보석
    private static Gem lastAnyGem;        // 가장 최근에 본 보석 (/세공 보석 용)
    private static View lastView;         // 가장 최근에 그린 추천 (/세공 제보 용)
    private static long lastViewAt;
    private static String lastScreen = "";
    private static long[] uiOrbs = new long[3];    // 열린 화면의 '인벤토리 밖' 칸에서 본 광채 아이템 개수 (점검용, 계산에는 안 씀)
    private static int uiItems;                    // 열린 화면의 '인벤토리 밖' 칸 중 아이템이 있는 칸 수

    // 카드 깜빡임 방지: 보석을 잠깐 못 읽어도(칸 갱신·화면 새로고침 순간) 직전 보석으로 이어서 보여준다
    private static final long GEM_GRACE_MS = 1500;
    private static Gem shownGem;          // 마지막으로 카드에 그린 보석
    private static long shownAt;
    private static boolean inGap;         // 지금 '잠깐 못 읽는 중'인지 (횟수를 한 번만 세려고)
    private static int gapSaved;          // 점검용: 직전 보석으로 이어서 보여준 횟수
    private static String gapWhy = "";    // 점검용: 마지막 이유
    private static int noGradeFrames;     // 점검용: 보석은 읽었는데 등급 한도를 못 구한 화면 수
    private static int screenInits;       // 점검용: 화면이 새로 열린 횟수

    // 최근 일어난 일 기록 (/세공 제보 에 들어간다): 깜빡임·보유·횟수가 이상할 때 원인을 찾으려고
    private static final int EV_MAX = 60;
    private static final String[] evText = new String[EV_MAX];
    private static final long[] evAt = new long[EV_MAX];
    private static int evCount;
    private static String lastEv = "";
    private static boolean prevScreenOpen, prevFound;
    private static String logStats = "", logStock = "";
    private static int logPlan = -1;

    static void ev(String text) {
        int i = evCount % EV_MAX;
        evText[i] = text;
        evAt[i] = System.currentTimeMillis();
        evCount++;
    }

    /** 같은 글이 연달아 나오면 한 번만 적는다 (오류가 계속 날 때 기록이 가득 차지 않게). */
    private static void evOnce(String text) {
        if (text.equals(lastEv)) return;
        lastEv = text;
        ev(text);
    }

    static List<String> buildLog() {
        List<String> out = new ArrayList<>();
        out.add("[세공] 최근 일어난 일 (위가 오래된 것, 시간은 지금부터 몇 초 전인지)");
        long now = System.currentTimeMillis();
        int n = Math.min(evCount, EV_MAX);
        for (int k = evCount - n; k < evCount; k++) {
            int i = k % EV_MAX;
            out.add(String.format("%6.1f초 전  %s", (now - evAt[i]) / 1000.0, evText[i]));
        }
        if (n == 0) out.add(" (아직 기록이 없어요)");
        return out;
    }

    private static String lostWhy(boolean screenOpen) {
        if (!screenOpen) return "화면이 잠깐 없었음";
        return uiItems == 0 ? "칸이 전부 비어 있었음 (서버가 칸을 다시 채우는 중?)" : "칸 " + uiItems + "개에 아이템은 있는데 보석으로 못 읽음";
    }

    private static String flash = "";
    private static long flashUntil;

    // 오류 방어 (기록과 규칙은 Guard 에 있다): 그리기·툴팁 읽기 중 오류가 나도 게임이 꺼지지 않게 하고, 반복되면 모드만 잠시 끈다
    static void noteError(Throwable e) {
        Guard.note(e);
        evOnce("오류: " + Guard.lastError);
    }

    private static void onDrawError(Throwable e) {
        evOnce("그리기 오류: " + Guard.describe(e));
        if (Guard.drawFailed(e, System.currentTimeMillis())) {
            flash("오류가 반복돼서 세공 도우미를 잠시 껐어요 (" + keyName(toggleKey) + " 키로 다시 켜기 · /세공 제보 로 알려 주세요)");
        }
    }

    /** 아이콘 그리기 실패: 아이콘만 끄고 나머지는 계속 보여준다. */
    static void iconFailed(Throwable e) {
        if (Guard.iconFailed(e)) flash("아이콘을 그리지 못해서 아이콘만 껐어요 (/세공 제보 로 알려 주세요)");
    }

    /** 그릴 광채 아이콘 (꺼져 있거나 아직 못 봤거나 그리기에 실패했으면 null) */
    static ItemStack iconFor(int k) {
        if (!icons || Guard.iconBroken) return null;
        ItemStack s = orbIcon[k];
        if (s == null || s.isEmpty()) {
            restoreIcon(k);      // 지금 인벤토리에 없어도, 전에 본 그림이 저장돼 있으면 꺼내 쓴다
            s = orbIcon[k];
        }
        return (s == null || s.isEmpty()) ? null : s;
    }

    /** 저장해 둔 광채 그림을 되살린다. 게임에 들어와 있어야 읽을 수 있고, 실패하면 다시 시도하지 않는다. */
    private static void restoreIcon(int k) {
        String snbt = orbSnbt[k];
        if (snbt == null || snbt.isEmpty() || orbRestoreFailed[k]) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.world == null) return;
        try {
            NbtElement nbt = StringNbtReader.parse(snbt);
            Optional<ItemStack> st = ItemStack.fromNbt(c.world.getRegistryManager(), nbt);
            if (st.isPresent() && !st.get().isEmpty()) orbIcon[k] = st.get();
            else orbRestoreFailed[k] = true;
        } catch (Throwable e) {
            orbRestoreFailed[k] = true;
        }
    }

    /** 광채 그림을 하나 기억하고 설정 파일에도 저장한다 (처음 볼 때 한 번만). */
    private static void rememberIcon(MinecraftClient client, int k, ItemStack s) {
        if (orbIcon[k] != null && !orbIcon[k].isEmpty()) return;
        ItemStack one = s.copyWithCount(1);
        orbIcon[k] = one;
        try {
            if (client != null && client.world != null) {
                String snbt = one.toNbt(client.world.getRegistryManager()).asString();
                if (!snbt.equals(orbSnbt[k])) {
                    orbSnbt[k] = snbt;
                    save();
                }
            }
        } catch (Throwable e) {
            // 저장하지 못해도 이번 접속 동안은 그림을 쓴다
        }
    }

    /** 오류로 꺼진 상태를 풀고 다시 시작한다. */
    static void rearm() {
        Guard.rearm();
    }

    @Override
    public void onInitializeClient() {
        load();
        loadJournal();
        seedDefaults();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.segong.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.segong"));
        gradeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.segong.grade", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.segong"));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.segong.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.segong"));

        // 1) 마우스를 올린 아이템의 툴팁 읽기
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (scanning) return;
            try {
                Gem g = makeGem(stack, new ArrayList<>(lines));
                if (g == null) return;
                hoverGem = g;
                hoverAt = System.currentTimeMillis();
                lastAnyGem = g;
            } catch (Throwable e) {
                noteError(e);
            }
        });

        // 2) 화면이 없을 때(게임 중) 키 입력
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                while (toggleKey.wasPressed()) doToggle();
                while (gradeKey.wasPressed()) doGrade();
                while (settingsKey.wasPressed()) client.setScreen(new SettingsScreen(null));
                tickWelcome(client);
            } catch (Throwable e) {
                noteError(e);
            }
        });

        // 3) 화면이 열려 있을 때 키 입력 + 그리기 (열린 화면에서는 일반 키 바인딩이 안 먹기 때문)
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            screenInits++;
            ev("화면 새로 열림: " + screen.getClass().getSimpleName());
            hoverGem = null;
            moduleGem = null;
            moduleScanAt = 0;
            ScreenKeyboardEvents.afterKeyPress(screen).register((s, key, scancode, mods) -> {
                try {
                    onScreenKey(s, key, scancode);
                } catch (Throwable e) {
                    noteError(e);
                }
            });
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

        // 4) 명령어: /세공 ... (하위 명령은 한글이 기본이고, 예전 영어 이름과 /segong 도 그대로 된다)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> cmd = ClientCommandManager.literal("세공").executes(SegongClient::cmdHelp);   // /세공 만 쳐도 도움말
            sub(cmd, "도움말", "help", SegongClient::cmdHelp);
            sub(cmd, "제보", "report", SegongClient::cmdReport);
            sub(cmd, "설정", "settings", SegongClient::cmdSettings);
            sub(cmd, "인벤", "inv", SegongClient::cmdInv);
            sub(cmd, "인벤토리", "inventory", SegongClient::cmdInv);
            sub(cmd, "아이템", "items", SegongClient::cmdItems);
            sub(cmd, "칸", "slots", SegongClient::cmdSlots);
            sub(cmd, "보석", "dump", SegongClient::cmdDump);
            cmd.then(statsNode("통계"));
            cmd.then(statsNode("stats"));
            var root = dispatcher.register(cmd);
            dispatcher.register(ClientCommandManager.literal("segong").executes(SegongClient::cmdHelp).redirect(root));   // 예전 이름(/segong)도 그대로 쓸 수 있게
        });
    }

    // ---------------------------------------------------------------- 명령어

    /** 같은 동작을 한글 이름과 영어 이름 둘 다로 부를 수 있게 붙인다. */
    private static void sub(LiteralArgumentBuilder<FabricClientCommandSource> parent, String ko, String en, Command<FabricClientCommandSource> run) {
        parent.then(ClientCommandManager.literal(ko).executes(run));
        parent.then(ClientCommandManager.literal(en).executes(run));
    }

    /** /세공 통계 : 통계 창을 연다 (뒤에 무슨 글자를 붙여도 그냥 연다)  (영어: stats) */
    private static LiteralArgumentBuilder<FabricClientCommandSource> statsNode(String name) {
        return ClientCommandManager.literal(name).executes(SegongClient::cmdStats)
                .then(ClientCommandManager.argument("etc", StringArgumentType.greedyString()).executes(SegongClient::cmdStats));
    }

    private static int cmdStats(CommandContext<FabricClientCommandSource> c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.send(() -> mc.setScreen(new StatsScreen(null)));   // 채팅창이 닫힌 다음에 열기
        return Command.SINGLE_SUCCESS;
    }

    private static void say(CommandContext<FabricClientCommandSource> c, String s) {
        c.getSource().sendFeedback(Text.literal(s));
    }

    private static int cmdHelp(CommandContext<FabricClientCommandSource> c) {
        for (String line : helpLines()) say(c, line);
        return Command.SINGLE_SUCCESS;
    }

    private static int cmdReport(CommandContext<FabricClientCommandSource> c) {
        runReport(MinecraftClient.getInstance(), line -> say(c, line));
        return Command.SINGLE_SUCCESS;
    }

    private static int cmdSettings(CommandContext<FabricClientCommandSource> c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.send(() -> mc.setScreen(new SettingsScreen(null)));   // 채팅창이 닫힌 다음에 열기
        return Command.SINGLE_SUCCESS;
    }

    private static int cmdInv(CommandContext<FabricClientCommandSource> c) {
        long[] n = countOrbs(MinecraftClient.getInstance(), true);
        say(c, "[세공] 인벤토리 광채: 바이올렛 " + n[0] + " / 오팔 " + n[1] + " / 아다만티움 " + n[2]);
        return Command.SINGLE_SUCCESS;
    }

    private static int cmdDump(CommandContext<FabricClientCommandSource> c) {
        Gem g = lastAnyGem;
        if (g == null) {
            say(c, "[세공] 아직 읽은 보석이 없습니다. 보석에 마우스를 올린 뒤 다시 입력하세요");
            return Command.SINGLE_SUCCESS;
        }
        say(c, "[세공] 마지막 보석 (" + g.lines.size() + "줄)");
        say(c, " 아이템: " + g.info);
        say(c, " 등급표시 문자: " + (g.sig.isEmpty() ? "(없음)" : g.sig) + " / 글자로 읽은 한도: " + g.gradeLimit);
        for (int i = 0; i < g.lines.size(); i++) {
            say(c, "  " + (i + 1) + ") " + esc(g.lines.get(i)));
            if (i < 2) say(c, "     조각: " + runs(g.texts.get(i)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int cmdSlots(CommandContext<FabricClientCommandSource> c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof HandledScreen<?> hs)) {
            say(c, "[세공] 세공 화면을 연 상태에서 입력하세요");
            return Command.SINGLE_SUCCESS;
        }
        int cnt = 0;
        for (Slot sl : hs.getScreenHandler().slots) {
            if (sl.inventory == mc.player.getInventory()) continue;
            ItemStack st = sl.getStack();
            if (st.isEmpty()) continue;
            cnt++;
            boolean gem = makeGem(st, tooltipTexts(mc, st)) != null;
            say(c, "  칸" + sl.id + ": " + st.getName().getString() + " x" + st.getCount() + (gem ? "  ← 보석" : ""));
        }
        if (cnt == 0) say(c, "[세공] 내 인벤토리가 아닌 칸에 아이템이 없습니다");
        return Command.SINGLE_SUCCESS;
    }

    private static int cmdItems(CommandContext<FabricClientCommandSource> c) {
        PlayerInventory inv = MinecraftClient.getInstance().player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isEmpty()) say(c, "  [" + i + "] " + st.getName().getString() + " x" + st.getCount());
        }
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- 도움말 · 제보 · 처음 안내

    /** /세공 도움말 에 보여줄 글 (키 이름은 조작 설정에서 바꾼 대로) */
    static List<String> helpLines() {
        List<String> out = new ArrayList<>();
        out.add("§e[세공] §f세공 도우미 v" + modVersion() + " 도움말");
        out.add(" §e키§f (조작 설정에서 바꿀 수 있어요): " + keyName(toggleKey) + " 켜기/끄기 · " + keyName(gradeKey) + " 등급 바꾸기 · " + keyName(settingsKey) + " 설정 창");
        out.add(" §e/세공 설정§f : 설정 창 열기");
        out.add(" §e/세공 통계§f : 통계 창 열기 (어떤 보석을 몇 번 세공했는지, 날짜별·등급별. 창에서 고치거나 지울 수도 있어요)");
        out.add(" §e/세공 제보§f : 문제가 생겼을 때, 제보할 내용을 한 번에 복사");
        out.add(" §e/세공 인벤§f : 인벤토리 광채 개수 · §e아이템§f · §e칸§f · §e보석§f : 점검용");
        out.add(" §7(영어 이름 settings · stats · report · inv 와 /segong 도 그대로 돼요)");
        return out;
    }

    /** /세공 제보 : 점검 + 최근 기록을 한 번에 클립보드로 복사한다 (복사가 안 되면 채팅창에 그대로 보여줌). */
    static void runReport(MinecraftClient mc, java.util.function.Consumer<String> say) {
        List<String> lines = new ArrayList<>(buildCheck(mc));
        lines.add("");
        lines.addAll(buildLog());
        String msg;
        try {
            GLFW.glfwSetClipboardString(mc.getWindow().getHandle(), String.join("\n", lines));
            msg = "[세공] 제보 내용 " + lines.size() + "줄을 복사했어요. 디스코드에 Ctrl+V 로 붙여넣어 주세요";
        } catch (Throwable e) {
            for (String ln : lines) say.accept(ln);
            msg = "[세공] 복사는 못 했어요. 위 글자를 그대로 알려 주세요";
        }
        say.accept(msg);
    }

    private static boolean welcomed;      // 처음 안내를 이미 보여줬는지 (설정 파일 welcomed)
    private static int welcomeWait;

    /** 처음 켠 뒤 게임에 들어가면 안내를 채팅창에 딱 한 번만 보여준다. */
    private static void tickWelcome(MinecraftClient client) {
        if (welcomed) return;
        if (client.player == null) {
            welcomeWait = 0;
            return;
        }
        if (++welcomeWait < 100) return;      // 접속하고 5초 뒤에 (서버 입장 메시지에 묻히지 않게)
        welcomed = true;
        save();
        client.player.sendMessage(Text.literal("§e[세공] §f세공 도우미가 켜졌어요! 설정은 §e" + keyName(settingsKey)
                + "§f 키, 명령어 목록은 §e/세공 도움말§f 을 입력하세요."), false);
    }

    // ---------------------------------------------------------------- 오늘 날짜

    /** 오늘 날짜 글자 (초기화 시각 이전이면 전날) */
    static String today() {
        return Journal.dayKey(java.time.LocalDateTime.now(), Journal.resetHour);
    }

    // ---------------------------------------------------------------- 키 동작

    private static void onScreenKey(Screen screen, int key, int scancode) {
        if (screen instanceof ChatScreen) return;
        if (screen.getFocused() instanceof TextFieldWidget) return;   // 글자 입력 중에는 무시
        if (toggleKey.matchesKey(key, scancode)) doToggle();
        else if (gradeKey.matchesKey(key, scancode)) doGrade();
    }

    private static void doToggle() {
        if (Guard.autoOff) {      // 오류로 꺼진 상태에서는 한 번 눌러서 다시 켠다
            rearm();
            Plan.clear();
            flash("세공 도우미: 다시 켰어요");
            return;
        }
        enabled = !enabled;
        Plan.clear();      // 껐다 켜면 추천을 새로 계산
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

    /** 열린 화면의 '내 인벤토리가 아닌' 칸에서 보석을 찾는다. 0.25초마다 한 번. (광채 아이템 수도 점검용으로 센다) */
    private static void scanModule(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (now - moduleScanAt < (moduleGem == null ? 50 : 250)) return;      // 보석을 못 찾은 동안은 더 자주 다시 읽는다
        moduleScanAt = now;
        moduleGem = null;
        long[] ui = new long[3];
        int items = 0;
        if (client.currentScreen instanceof HandledScreen<?> hs) {
            for (Slot sl : hs.getScreenHandler().slots) {
                if (sl.inventory == client.player.getInventory()) continue;
                ItemStack st = sl.getStack();
                if (st.isEmpty()) continue;
                items++;
                String name = st.getName().getString();
                if (name.contains("광채")) {
                    for (int k = 0; k < 3; k++) {
                        if (name.contains(NAMES[k])) { ui[k] += st.getCount(); rememberIcon(client, k, st); break; }
                    }
                }
                if (moduleGem == null) {
                    Gem g = makeGem(st, tooltipTexts(client, st));
                    if (g != null) { moduleGem = g; lastAnyGem = g; }
                }
            }
        }
        uiOrbs = ui;
        uiItems = items;
    }

    private static long[] orbCache = new long[3];
    private static long orbCacheAt;
    private static final long ORB_CACHE_MS = 200;     // 이 시간 안에는 다시 세지 않고 방금 센 값을 쓴다

    /** 내 인벤토리에서 광채 개수를 센다 (0.2초 안에 다시 부르면 방금 센 값). */
    static long[] countOrbs(MinecraftClient client) {
        return countOrbs(client, false);
    }

    /** fresh=true 면 캐시를 쓰지 않고 지금 바로 센다. */
    static long[] countOrbs(MinecraftClient client, boolean fresh) {
        long now = System.currentTimeMillis();
        if (!fresh && now - orbCacheAt < ORB_CACHE_MS) return orbCache.clone();
        long[] n = scanOrbs(client);
        orbCache = n;
        orbCacheAt = now;
        return n.clone();
    }

    /** 내 인벤토리에서 광채 개수를 직접 센다. */
    private static long[] scanOrbs(MinecraftClient client) {
        long[] n = new long[3];
        if (client == null || client.player == null) return n;
        PlayerInventory inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            String name = s.getName().getString();
            if (!name.contains("광채")) continue;
            for (int k = 0; k < 3; k++) {
                if (name.contains(NAMES[k])) {
                    n[k] += s.getCount();
                    rememberIcon(client, k, s);      // 그림용으로 하나 기억 (처음 한 번만)
                    break;
                }
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 그리기

    private static void draw(MinecraftClient client, DrawContext ctx, int mx, int my, boolean interactive) {
        if (client.player == null) return;
        try {
            Overlay.clearHit();
            long now = System.currentTimeMillis();
            if (now < flashUntil) {
                String f = "§b» " + flash;
                int w = client.textRenderer.getWidth(f), cx = client.getWindow().getScaledWidth() / 2;
                ctx.fill(cx - w / 2 - 4, 2, cx + w / 2 + 4, 14, 0xB0000000);
                ctx.drawText(client.textRenderer, f, cx - w / 2, 4, 0xFFFFFFFF, true);
            }
            if (!enabled || Guard.autoOff) return;
            if (client.currentScreen instanceof StatsScreen) return;     // 통계 창 위에는 카드를 그리지 않는다
            drawBody(client, ctx, mx, my, interactive, now);
        } catch (Throwable e) {
            onDrawError(e);      // 오류가 나도 게임은 계속 돌아간다
        }
    }

    private static void drawBody(MinecraftClient client, DrawContext ctx, int mx, int my, boolean interactive, long now) {
        boolean screenOpen = client.currentScreen != null;
        if (screenOpen != prevScreenOpen) {
            prevScreenOpen = screenOpen;
            ev(screenOpen ? "화면 열린 상태: " + client.currentScreen.getClass().getSimpleName() : "화면 없음 (게임 화면)");
        }
        Gem g = null;
        if (screenOpen) {
            scanModule(client);
            g = moduleGem;
        }
        if (g == null && hoverGem != null && (screenOpen || now - hoverAt <= HOVER_MS)) g = hoverGem;
        boolean found = g != null;
        if (found != prevFound) {
            prevFound = found;
            ev(found ? "보석 읽음" : "보석 못 읽음 - " + lostWhy(screenOpen));
        }
        if (found) {
            inGap = false;
            shownGem = g;
            shownAt = now;
            String st = triple(g.stats[0], g.stats[1], g.stats[2]);
            if (!st.equals(logStats)) {
                ev("보석 수치 " + (logStats.isEmpty() ? "" : logStats + " → ") + st);
                logStats = st;
            }
        } else if (shownGem != null && now - shownAt <= GEM_GRACE_MS) {
            g = shownGem;       // 잠깐 못 읽었을 뿐이면 카드를 지우지 않는다 (깜빡임 방지)
            if (!inGap) {
                inGap = true;
                gapSaved++;
                gapWhy = lostWhy(screenOpen);
            }
        }
        currentGem = g;
        if (g == null) return;

        View v = new View();
        v.stats = g.stats;
        v.sig = g.sig;
        v.stock = countOrbs(client);      // 보유 = 내 인벤토리를 그대로 센 값 (오른쪽 인벤토리 창과 같다)
        String sk = triple(v.stock[0], v.stock[1], v.stock[2]);
        if (!sk.equals(logStock)) {
            ev("인벤토리 광채 " + (logStock.isEmpty() ? "" : logStock + " → ") + sk);
            logStock = sk;
        }
        if (g.gradeLimit > 0) { v.lim = g.gradeLimit; v.how = "자동"; }
        else if (!g.sig.isEmpty() && learned.containsKey(g.sig)) { v.lim = learned.get(g.sig); v.how = "기억"; }
        else if (gradeMode > 0) { v.lim = LIMITS[gradeMode]; v.how = "수동"; }
        if (v.lim > 0) {
            long[] eff = new long[3];
            Calc.Result rp = Plan.resolve(g.stats, v.lim, g.sig, v.stock, eff, now);   // 처음 정한 목표를 유지하고 남은 개수만 줄어든다
            v.r = lock ? rp : Calc.best(g.stats[0], g.stats[1], g.stats[2], v.lim, v.stock);
            if (rp.plan != logPlan) {
                logPlan = rp.plan;
                ev("추천 새로 정함 #" + rp.plan + " (넣을 개수 " + triple(rp.a, rp.b, rp.c) + ")");
            }
            if (Tally.observePlan(rp.plan, rp.n)) {      // 추천한 개수를 다 넣어서 목표에 도달하면 기록 한 줄 추가
                String jn = Journal.nameOf(g.lines), jg = Journal.gradeOf(g.lines, v.lim);
                Journal.add(today(), jn, jg);              // 어떤 보석을 세공했는지도 남긴다
                saveJournal();
                ev("세공 기록: " + jn + " " + jg);
            }
        }
        if (v.lim <= 0) noGradeFrames++;
        lastView = v;
        lastViewAt = now;
        lastScreen = screenOpen ? client.currentScreen.getClass().getSimpleName() : "(화면 없음)";
        Overlay.render(client, ctx, v, mx, my, interactive);
        if (Journal.show) Overlay.renderJournal(client, ctx, false);
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

    /** 설정을 모두 기본값으로 (학습한 등급 표시는 그대로 둔다) */
    static void resetAll() {
        enabled = true;
        gradeMode = 0;
        style = 0;
        scale = 1.0f;
        theme = 0;
        icons = true;
        lock = true;
        Journal.resetHour = 0;
        Journal.show = true;
        customX = -1f;
        customY = -1f;
        Plan.clear();
        rearm();
        save();
    }

    /** 설치된 모드 버전 (jar 이름과 같은 값) */
    private static String modVer(String id) {
        try {
            return FabricLoader.getInstance().getModContainer(id)
                    .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
        } catch (Throwable e) {
            return "?";
        }
    }

    static String modVersion() {
        try {
            return FabricLoader.getInstance().getModContainer("segong")
                    .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
        } catch (Throwable e) {
            return "?";
        }
    }

    private static String onOff(boolean b) { return b ? "켜짐" : "꺼짐"; }

    private static String triple(long a, long b, long c) { return a + "/" + b + "/" + c; }

    /** /세공 제보 에 들어가는 점검 내용 (문제가 생겼을 때 이 글자를 그대로 알려 주면 된다) */
    static List<String> buildCheck(MinecraftClient mc) {
        List<String> out = new ArrayList<>();
        out.add("[세공] 점검 v" + modVersion());
        out.add(" 환경: 마인크래프트 " + modVer("minecraft") + " · Fabric API " + modVer("fabric-api"));
        out.add(" 설정: " + onOff(enabled) + " · " + STYLE_NAME[style] + " · " + THEME_NAME[theme] + " · 아이콘 " + onOff(icons)
                + " · 추천 고정 " + onOff(lock) + " · 글자 " + String.format("%.2f", scale) + "배 · 등급 " + GRADE_NAME[gradeMode]);
        out.add(" 오류: 총 " + Guard.total + "번" + (Guard.total > 0 ? " · 마지막 " + Guard.lastError : "")
                + (Guard.autoOff ? " · 반복돼서 잠시 꺼짐 (" + keyName(toggleKey) + " 키로 다시 켜기)" : "")
                + (Guard.iconBroken ? " · 아이콘 그리기 실패로 아이콘 꺼짐" : ""));
        out.add(" 세공 기록: " + Journal.describe(today()) + " · " + Tally.describe());
        long[] inv = countOrbs(mc, true);
        out.add(" 인벤토리 광채(실제): 바이올렛/오팔/아다만티움 " + triple(inv[0], inv[1], inv[2]));
        out.add(" 깜빡임 방지: 보석을 잠깐 못 읽어서 직전 보석으로 이어 보여준 횟수 " + gapSaved + (gapSaved > 0 ? " (마지막 이유: " + gapWhy + ")" : "")
                + " · 화면 새로 열림 " + screenInits + "번 · 등급을 못 구한 화면 " + noGradeFrames);
        out.add(" 광채 그림을 본 적: " + (orbIcon[0] != null ? "O" : "X") + "/" + (orbIcon[1] != null ? "O" : "X") + "/" + (orbIcon[2] != null ? "O" : "X")
                + " · 저장됨 " + (orbSnbt[0] != null ? "O" : "X") + "/" + (orbSnbt[1] != null ? "O" : "X") + "/" + (orbSnbt[2] != null ? "O" : "X")
                + (orbRestoreFailed[0] || orbRestoreFailed[1] || orbRestoreFailed[2] ? " · 저장된 그림 읽기 실패 있음" : ""));
        View v = lastView;
        if (v == null) {
            out.add(" 아직 읽은 보석이 없어요. 세공 화면에서 보석이 보이는 상태로 한 번 연 뒤 다시 입력하세요");
        } else {
            long sec = (System.currentTimeMillis() - lastViewAt) / 1000;
            out.add(" 마지막으로 그린 화면: " + lastScreen + " (" + sec + "초 전)");
            out.add(" 보석 수치(순도/광도/강도): " + triple(v.stats[0], v.stats[1], v.stats[2]) + " · 등급 한도 " + (v.lim > 0 ? v.lim + " (" + v.how + ")" : "못 읽음")
                    + " · 등급표시 " + (v.sig.isEmpty() ? "(없음)" : v.sig));
            if (v.r != null) {
                out.add(" 추천(남은 개수): " + triple(v.r.a, v.r.b, v.r.c) + " · 총 " + v.r.n + "개 · 합 " + v.r.sum + " / " + v.lim
                        + (v.r.missing > 0 ? " · 부족 " + v.r.missing + "개" : ""));
            }
            out.add(" 화면에 보여준 보유: " + triple(v.stock[0], v.stock[1], v.stock[2]));
        }
        out.add(" 열린 화면의 인벤토리 밖 칸: 아이템 " + uiItems + "칸 · 그중 광채 " + triple(uiOrbs[0], uiOrbs[1], uiOrbs[2]) + " (마지막으로 본 값)");
        List<String> plans = Plan.describe();
        out.add(" 기억 중인 계획 " + plans.size() + "개" + (plans.isEmpty() ? "" : " (▶ = 지금 쓰는 것)"));
        out.addAll(plans);
        return out;
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
            icons = !"false".equals(pr.getProperty("icons", "true"));
            lock = !"false".equals(pr.getProperty("lock", "true"));
            Journal.show = !"false".equals(pr.getProperty("journal.on", "true"));
            Journal.resetHour = Math.max(0, Math.min(23, Integer.parseInt(pr.getProperty("tally.reset", "0"))));   // 예전 이름 그대로 (하루가 바뀌는 시각)
            for (int k = 0; k < 3; k++) orbSnbt[k] = pr.getProperty("icon." + k);
            welcomed = "true".equals(pr.getProperty("welcomed", "false"));
            customX = Float.parseFloat(pr.getProperty("px", "-1"));
            customY = Float.parseFloat(pr.getProperty("py", "-1"));
            for (String k : pr.stringPropertyNames()) {
                if (k.startsWith("sig.")) learned.put(k.substring(4), Integer.parseInt(pr.getProperty(k)));
            }
        } catch (Throwable e) {
            // 설정을 못 읽어도 기본값으로 동작
        }
    }

    private static Path journalPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("segong-journal.tsv");
    }

    /** 보석별 기록 파일(config/segong-journal.tsv)을 읽는다. 못 읽으면 빈 기록으로 시작하고 망가진 파일은 .bad 로 남긴다. */
    private static void loadJournal() {
        Path p = journalPath();
        try {
            if (Files.exists(p)) Journal.parse(Files.readAllLines(p, java.nio.charset.StandardCharsets.UTF_8));
            if (Journal.prune(today())) saveJournal();
        } catch (Throwable e) {
            try {
                Files.move(p, p.resolveSibling("segong-journal.tsv.bad"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable ignore) {
                // 옮기지 못해도 동작에는 지장 없음
            }
        }
    }

    /** 보석별 기록을 파일로 저장한다 (임시 파일에 쓴 뒤 바꿔치기해서, 쓰다가 꺼져도 파일이 깨지지 않게). */
    static void saveJournal() {
        try {
            Path p = journalPath();
            Path tmp = p.resolveSibling("segong-journal.tsv.tmp");
            Files.writeString(tmp, Journal.serialize(), java.nio.charset.StandardCharsets.UTF_8);
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable e) {
            // 저장 실패해도 동작에는 지장 없음
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
            pr.setProperty("icons", String.valueOf(icons));
            pr.setProperty("lock", String.valueOf(lock));
            pr.setProperty("journal.on", String.valueOf(Journal.show));
            pr.setProperty("tally.reset", String.valueOf(Journal.resetHour));
            for (int k = 0; k < 3; k++) if (orbSnbt[k] != null) pr.setProperty("icon." + k, orbSnbt[k]);
            pr.setProperty("welcomed", String.valueOf(welcomed));
            pr.setProperty("px", String.valueOf(customX));
            pr.setProperty("py", String.valueOf(customY));
            for (Map.Entry<String, Integer> e : learned.entrySet()) pr.setProperty("sig." + e.getKey(), String.valueOf(e.getValue()));
            try (OutputStream out = Files.newOutputStream(cfgPath())) { pr.store(out, "segong-helper"); }
        } catch (Throwable e) {
            // 저장 실패해도 동작에는 지장 없음
        }
    }
}
