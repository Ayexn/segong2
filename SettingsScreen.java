package kr.segong;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 세공 도우미 설정 화면 (우드 & 크림 디자인). 게임 중 설정 키 또는 /segong settings 로 연다. */
class SettingsScreen extends Screen {
    // 색 (우드 & 크림)
    private static final int PANEL = 0xFFF3E4CF, EDGE_OUT = 0xFF3B2412, EDGE_IN = 0xFF6B4423;
    private static final int TITLE = 0xFF6B3F17, TXT = 0xFF4A2C14, SUB = 0xFF8A6A45, LINE = 0xFFB9874F;
    private static final int BTN = 0xFFE3C9A2, BTN_HI = 0xFFFFF2D8, BTN_LO = 0xFFB48A55, BTN_BD = 0xFF4A2C14, BTN_TX = 0xFF4A2C14;
    private static final int SEL = 0xFFF2B631, SEL_HI = 0xFFFFE08A, SEL_LO = 0xFFB9801A, SEL_TX = 0xFF3B2412;
    private static final int RED = 0xFFB5533C, RED_HI = 0xFFF0A08C, RED_LO = 0xFF6D2A1B;
    private static final int TRACK = 0xFFD9BF96;

    private static final class Btn {
        int x, y, w, h;
        String label;
        BooleanSupplier selected;
        boolean red;
        Runnable act;
    }

    private final Screen parent;
    private final List<Btn> btns = new ArrayList<>();
    private int pl, pt, pw, ph;          // 패널 위치/크기
    private int cx0;                     // 컨트롤 시작 x
    private int sliderX, sliderY, sliderW = 190, sliderH = 14;
    private boolean sliderDrag;

    SettingsScreen(Screen parent) {
        super(Text.literal("세공 도우미 설정"));
        this.parent = parent;
    }

    private Btn add(int x, int y, String label, BooleanSupplier sel, Runnable act, boolean red) {
        Btn b = new Btn();
        b.x = x; b.y = y; b.label = label; b.selected = sel; b.act = act; b.red = red;
        b.w = textRenderer.getWidth(label) + 14; b.h = 18;
        btns.add(b);
        return b;
    }

    @Override
    protected void init() {
        btns.clear();
        pw = Math.min(width - 16, 400);
        ph = Math.min(height - 16, 238);
        pl = (width - pw) / 2;
        pt = (height - ph) / 2;
        cx0 = pl + 92;
        int y = pt + 38, gap = 25;

        // 동작
        int x = cx0;
        Btn b = add(x, y, "켜짐", () -> SegongClient.enabled, () -> { SegongClient.enabled = true; SegongClient.save(); }, false);
        x += b.w + 4;
        add(x, y, "꺼짐", () -> !SegongClient.enabled, () -> { SegongClient.enabled = false; SegongClient.save(); }, false);

        // 표시 방식
        y += gap; x = cx0;
        for (int i = 0; i < SegongClient.STYLE_NAME.length; i++) {
            final int k = i;
            b = add(x, y, SegongClient.STYLE_NAME[i], () -> SegongClient.style == k, () -> { SegongClient.style = k; SegongClient.save(); }, false);
            x += b.w + 4;
        }

        // 글자 크기 (슬라이더)
        y += gap;
        sliderX = cx0; sliderY = y + 2;

        // 위치
        y += gap; x = cx0;
        add(x, y, "위치 초기화 (자동)", () -> SegongClient.customX < 0, () -> SegongClient.resetPos(), false);

        // 등급
        y += gap; x = cx0;
        for (int i = 0; i < SegongClient.GRADE_NAME.length; i++) {
            final int k = i;
            String lab = i == 0 ? "자동" : SegongClient.GRADE_NAME[i].split(" ")[0];
            b = add(x, y, lab, () -> SegongClient.gradeMode == k, () -> { SegongClient.gradeMode = k; SegongClient.save(); }, false);
            x += b.w + 4;
        }

        // 아래 버튼
        int by = pt + ph - 28;
        add(pl + 14, by, "학습한 등급 표시 지우기", () -> false, () -> SegongClient.resetLearned(), true);
        Btn close = add(0, by, "닫기", () -> false, this::close, false);
        close.w = 56;
        close.x = pl + pw - 14 - close.w;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private static boolean in(Btn b, double mx, double my) {
        return mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h;
    }

    private void setSlider(double mx) {
        double t = Math.max(0, Math.min(1, (mx - sliderX) / sliderW));
        float v = 0.7f + (float) t * 0.8f;
        SegongClient.scale = Math.round(v * 20f) / 20f;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Btn b : btns) {
                if (in(b, mx, my)) { b.act.run(); return true; }
            }
            if (mx >= sliderX - 2 && mx <= sliderX + sliderW + 2 && my >= sliderY - 3 && my <= sliderY + sliderH + 3) {
                sliderDrag = true; setSlider(mx); return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (sliderDrag) { setSlider(mx); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (sliderDrag) { sliderDrag = false; SegongClient.save(); return true; }
        return super.mouseReleased(mx, my, button);
    }

    // 입체감 있는 사각형 (위·왼쪽은 밝게, 아래·오른쪽은 어둡게)
    private static void bevel(DrawContext c, int x, int y, int w, int h, int fill, int hi, int lo, int bd) {
        c.fill(x, y, x + w, y + h, bd);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
        c.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
        c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
        c.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, lo);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);

        // 패널
        c.fill(pl - 3, pt - 3, pl + pw + 3, pt + ph + 3, EDGE_OUT);
        c.fill(pl - 2, pt - 2, pl + pw + 2, pt + ph + 2, EDGE_IN);
        c.fill(pl, pt, pl + pw, pt + ph, PANEL);

        String title = "세공 도우미 설정";
        String ver = version();
        c.drawText(textRenderer, title, pl + (pw - textRenderer.getWidth(title)) / 2, pt + 9, TITLE, false);
        c.fill(pl + pw / 2 - 70, pt + 22, pl + pw / 2 + 70, pt + 23, LINE);
        if (!ver.isEmpty()) c.drawText(textRenderer, "v" + ver, pl + pw - 10 - textRenderer.getWidth("v" + ver), pt + 9, SUB, false);

        // 줄 이름
        int gap = 25, y = pt + 38;
        String[] labels = {"동작", "표시 방식", "글자 크기", "위치", "등급"};
        String[] subs = {null, null, null, "세공 화면에서 카드를 끌어 이동", "자동이 안 될 때만"};
        for (int i = 0; i < labels.length; i++) {
            c.drawText(textRenderer, labels[i], pl + 14, y + gap * i + 5 - (subs[i] != null ? 4 : 0), TXT, false);
            if (subs[i] != null) c.drawText(textRenderer, subs[i], pl + 14, y + gap * i + 7, SUB, false);
        }

        // 슬라이더
        c.fill(sliderX - 1, sliderY - 1, sliderX + sliderW + 1, sliderY + sliderH + 1, BTN_BD);
        c.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, TRACK);
        float t = (SegongClient.scale - 0.7f) / 0.8f;
        int hx = sliderX + Math.round(t * sliderW);
        c.fill(sliderX, sliderY, hx, sliderY + sliderH, SEL);
        bevel(c, hx - 4, sliderY - 2, 8, sliderH + 4, BTN, BTN_HI, BTN_LO, BTN_BD);
        c.drawText(textRenderer, String.format("%.2f배", SegongClient.scale), sliderX + sliderW + 8, sliderY + 3, TXT, false);

        // 버튼
        for (Btn b : btns) {
            boolean sel = b.selected.getAsBoolean();
            boolean hov = in(b, mx, my);
            int fill, hi, lo, tx;
            if (b.red) { fill = RED; hi = RED_HI; lo = RED_LO; tx = 0xFFFFFFFF; }
            else if (sel) { fill = SEL; hi = SEL_HI; lo = SEL_LO; tx = SEL_TX; }
            else { fill = BTN; hi = BTN_HI; lo = BTN_LO; tx = BTN_TX; }
            if (hov && !sel) { fill = lighten(fill); }
            bevel(c, b.x, b.y, b.w, b.h, fill, hi, lo, BTN_BD);
            c.drawText(textRenderer, b.label, b.x + (b.w - textRenderer.getWidth(b.label)) / 2, b.y + 5, tx, false);
        }

        // 아래 안내
        long[] n = SegongClient.countOrbs(client);
        String l1 = "인벤토리 광채  바이올렛 " + n[0] + " · 오팔 " + n[1] + " · 아다만티움 " + n[2];
        c.drawText(textRenderer, l1, pl + 14, pt + ph - 52, 0xFF7A4DA8, false);
        String l2 = SegongClient.keyName(SegongClient.toggleKey) + " 켜기/끄기 · " + SegongClient.keyName(SegongClient.gradeKey)
                + " 등급 · " + SegongClient.keyName(SegongClient.settingsKey) + " 설정 (키는 조작 설정에서 변경)";
        c.drawText(textRenderer, l2, pl + 14, pt + ph - 40, SUB, false);

        // 실제 위치 미리보기
        Overlay.render(client, c, SegongClient.sampleView(), -1, -1, false);
    }

    /** 설치된 모드 버전 (jar 이름과 같은 값) */
    private static String version() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("segong")
                    .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
        } catch (Throwable e) {
            return "";
        }
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 18), g = Math.min(255, ((c >> 8) & 0xFF) + 18), b = Math.min(255, (c & 0xFF) + 18);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
