package kr.segong;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 세공 통계 창 (/세공 통계 또는 설정 창의 "통계 보기").
 * 위: 기간 탭(오늘 · 7일 · 30일 · 전체) + 날짜별 막대 / 아래: 보석 × 등급 표 (막대를 누르면 그 날만 자세히, + / - 로 고치기).
 * 그림 파일 없이 사각형(fill)과 글자만으로 그린다. 매 화면마다 눌리는 칸 목록을 새로 만든다.
 */
class StatsScreen extends Screen {
    // 색 (테마에 따라 applyTheme() 가 채운다)
    private int TITLE, TXT, SUB, LINE, ACC, GOLDTX, OKC, ZERO, WELL_LINE, ROW_LINE;
    private int BTN, BTN_HI, BTN_LO, BTN_BD, BTN_TX, SEL, SEL_HI, SEL_LO, SEL_TX, RED, RED_HI, RED_LO, RED_BD;
    private int BAR, BAR_HI, TODAY, TODAY_HI, HOVER, PICK;
    private boolean wood, apo;      // wood = 어두운 판(약방·우드카드), apo = 다크 약방

    private void applyTheme() {
        apo = SegongClient.theme == 0;
        wood = SegongClient.theme <= 1;
        if (apo) {
            TITLE = Apo.B_TXT; TXT = Apo.TXT; SUB = Apo.SUB; LINE = Apo.CURL; ACC = Apo.INFO; GOLDTX = Apo.GOLD_HI; OKC = Apo.OK;
            ZERO = 0xFF8A6A48; WELL_LINE = 0x44FFFFFF; ROW_LINE = 0x18FFFFFF;
            BTN = Apo.BTN; BTN_HI = Apo.BTN_HI; BTN_LO = Apo.P_LO; BTN_BD = Apo.S_OUT; BTN_TX = Apo.TXT;
            SEL = Apo.SEL; SEL_HI = Apo.SEL_HI; SEL_LO = Apo.P_LO; SEL_TX = Apo.SEL_TX;
            RED = Apo.RD; RED_HI = Apo.RD_HI; RED_LO = Apo.RD_LO; RED_BD = Apo.RD_OUT;
            BAR = Apo.SLIDER; BAR_HI = Apo.BAND_HI; TODAY = Apo.GOLD; TODAY_HI = Apo.GOLD_HI;
            HOVER = 0x22FFFFFF; PICK = 0x30FFD76A;
        } else if (wood) {
            TITLE = Overlay.W_CREAM; TXT = Overlay.W_CREAM; SUB = Overlay.W_SUB; LINE = Overlay.W_SH; ACC = 0xFFE3C4FF; GOLDTX = 0xFFFFE08A; OKC = Overlay.W_OK;
            ZERO = 0xFFA88458; WELL_LINE = 0x44FFFFFF; ROW_LINE = 0x18FFFFFF;
            BTN = 0xFFA8763E; BTN_HI = 0xFFD9A868; BTN_LO = 0xFF5A3A18; BTN_BD = Overlay.W_OUT; BTN_TX = Overlay.W_CREAM;
            SEL = Overlay.W_GOLD; SEL_HI = 0xFFFFF0B0; SEL_LO = 0xFFA87A28; SEL_TX = Overlay.W_OUT;
            RED = 0xFF9A3B2A; RED_HI = 0xFFD98A78; RED_LO = 0xFF5A2018; RED_BD = Overlay.W_OUT;
            BAR = 0xFFD9A868; BAR_HI = 0xFFF0CC8A; TODAY = 0xFFFFE08A; TODAY_HI = 0xFFFFF6D0;
            HOVER = 0x22FFFFFF; PICK = 0x30FFD76A;
        } else {
            TITLE = 0xFF6B3F17; TXT = 0xFF4A2C14; SUB = 0xFF8A6A45; LINE = 0xFFB9874F; ACC = 0xFF7A4DA8; GOLDTX = 0xFFB86A00; OKC = 0xFF2F8A4A;
            ZERO = 0xFFB9A07A; WELL_LINE = 0x55B9874F; ROW_LINE = 0x30B9874F;
            BTN = 0xFFE3C9A2; BTN_HI = 0xFFFFF2D8; BTN_LO = 0xFFB48A55; BTN_BD = 0xFF4A2C14; BTN_TX = 0xFF4A2C14;
            SEL = 0xFFF2B631; SEL_HI = 0xFFFFE08A; SEL_LO = 0xFFB9801A; SEL_TX = 0xFF3B2412;
            RED = 0xFFB5533C; RED_HI = 0xFFF0A08C; RED_LO = 0xFF6D2A1B; RED_BD = 0xFF3B2412;
            BAR = 0xFFC9A268; BAR_HI = 0xFFE3C9A2; TODAY = 0xFFF2B631; TODAY_HI = 0xFFFFE08A;
            HOVER = 0x18000000; PICK = 0x38F2B631;
        }
    }

    /** 눌리는 칸 (그릴 때마다 새로 모은다) */
    private static final class Hit {
        int x, y, w, h;
        Runnable act;
    }

    private static final String[] TAB = {"오늘", "7일", "30일", "전체"};

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private int tab = 1;                 // 0 오늘 · 1 7일 · 2 30일 · 3 전체
    private String selDay;               // 막대를 눌러 고른 날 (없으면 null)
    private int scroll;                  // 표를 위로 넘긴 줄 수
    private int rowsTotal, rowsVisible;  // 표의 전체 줄 수 · 한 번에 보이는 줄 수 (그릴 때 정해진다)
    private int wellX, wellY, wellW, wellH;
    private boolean confirmClear, wasArmed;
    private String notice = "";
    private long noticeUntil;

    StatsScreen(Screen parent) {
        super(Text.literal("세공 통계"));
        this.parent = parent;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private void say(String s) {
        notice = s;
        noticeUntil = System.currentTimeMillis() + 5000;
    }

    // ---------------------------------------------------------------- 입력

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                    wasArmed = confirmClear;
                    confirmClear = false;        // 다른 걸 누르면 "지우기 확인"은 풀린다 (지우기 버튼은 wasArmed 로 확인)
                    h.act.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** 마우스 휠: 표를 위아래로 넘긴다 (휠이 안 먹어도 ▲▼ 버튼으로 넘길 수 있다) */
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (rowsTotal > rowsVisible && mx >= wellX && mx < wellX + wellW && my >= wellY && my < wellY + wellH) {
            scroll += vertical > 0 ? -1 : 1;
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 그리기 도구

    private int w(String s) { return textRenderer.getWidth(s); }

    private void txt(DrawContext c, String s, int x, int y, int col) {
        c.drawText(textRenderer, s, x, y, col, wood);
    }

    private void txtR(DrawContext c, String s, int right, int y, int col) {
        txt(c, s, right - w(s), y, col);
    }

    private void txtC(DrawContext c, String s, int cx, int y, int col) {
        txt(c, s, cx - w(s) / 2, y, col);
    }

    /** 칸 폭에 맞게 글자를 줄인다 */
    private String fit(String s, int maxW) {
        if (w(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && w(t + "..") > maxW) t = t.substring(0, t.length() - 1);
        return t + "..";
    }

    private Hit hit(int x, int y, int bw, int bh, Runnable act) {
        Hit h = new Hit();
        h.x = x; h.y = y; h.w = bw; h.h = bh; h.act = act;
        hits.add(h);
        return h;
    }

    // 입체감 있는 사각형 (위·왼쪽은 밝게, 아래·오른쪽은 어둡게)
    private static void bevel(DrawContext c, int x, int y, int bw, int bh, int fill, int hi, int lo, int bd) {
        c.fill(x, y, x + bw, y + bh, bd);
        c.fill(x + 1, y + 1, x + bw - 1, y + bh - 1, fill);
        c.fill(x + 1, y + 1, x + bw - 1, y + 2, hi);
        c.fill(x + 1, y + 1, x + 2, y + bh - 1, hi);
        c.fill(x + 1, y + bh - 2, x + bw - 1, y + bh - 1, lo);
        c.fill(x + bw - 2, y + 1, x + bw - 1, y + bh - 1, lo);
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 18), g = Math.min(255, ((c >> 8) & 0xFF) + 18), b = Math.min(255, (c & 0xFF) + 18);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 버튼을 그리고 눌리는 칸으로 등록한다. 폭을 돌려준다. bw <= 0 이면 글자 폭에 맞춘다. dim 이면 눌러도 아무 일 없다. */
    private int button(DrawContext c, int mx, int my, int x, int y, int bw, int bh, String label,
                       boolean sel, boolean red, boolean dim, Runnable act) {
        if (bw <= 0) bw = w(label) + (bh >= 18 ? 14 : 10);
        boolean hov = !dim && mx >= x && mx < x + bw && my >= y && my < y + bh;
        int fill, hi, lo, tx, bd;
        if (dim) { fill = BTN; hi = BTN; lo = BTN; tx = ZERO; bd = BTN_BD; }
        else if (red) { fill = RED; hi = RED_HI; lo = RED_LO; tx = 0xFFFFFFFF; bd = RED_BD; }
        else if (sel) { fill = SEL; hi = SEL_HI; lo = SEL_LO; tx = SEL_TX; bd = BTN_BD; }
        else { fill = BTN; hi = BTN_HI; lo = BTN_LO; tx = BTN_TX; bd = BTN_BD; }
        if (hov && !sel) fill = lighten(fill);
        int base = bh >= 18 ? 4 : 3;
        if (apo) {
            Apo.pill(c, x, y, bw, bh, fill, bd, hi, lo, bh >= 18 ? 2 : 1);
            c.drawText(textRenderer, label, x + (bw - w(label)) / 2, y + base + (sel ? 1 : 0), tx, !sel && !dim);
        } else {
            bevel(c, x, y, bw, bh, fill, hi, lo, bd);
            c.drawText(textRenderer, label, x + (bw - w(label)) / 2, y + base + 1, tx, wood && !sel && !dim);
        }
        if (!dim) hit(x, y, bw, bh, act);
        return bw;
    }

    /** 오목하게 들어간 칸 */
    private void well(DrawContext c, int x, int y, int ww, int hh) {
        if (apo) {
            Apo.slot(c, x, y, ww, hh);
        } else if (wood) {
            Overlay.woodSlot(c, x, y, ww, hh);
        } else {
            c.fill(x, y, x + ww, y + hh, 0xFF6B4423);
            c.fill(x + 1, y + 1, x + ww - 1, y + hh - 1, 0xFFFFF6E6);
        }
    }

    /** 작은 삼각형 (위쪽 up=true / 아래쪽) */
    private static void arrow(DrawContext c, int cx, int cy, boolean up, int col) {
        for (int i = 0; i < 3; i++) {
            int half = up ? i : 2 - i;
            int y = cy - 1 + i;
            c.fill(cx - half, y, cx + half + 1, y + 1, col);
        }
    }

    // ---------------------------------------------------------------- 화면

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        applyTheme();
        hits.clear();

        int pw = Math.min(width - 16, 440), ph = Math.min(height - 16, 290);
        int pl = (width - pw) / 2, pt = (height - ph) / 2;
        String today = SegongClient.today();
        long now = System.currentTimeMillis();

        // 틀 + 제목
        String title = "세공 통계";
        if (apo) {
            c.getMatrices().push();
            c.getMatrices().translate((float) pl, (float) pt, 0f);
            Apo.frame(c, pw, ph, false);
            c.getMatrices().pop();
            int bw = 150, bx = pl + (pw - bw) / 2;
            Apo.banner(c, bx, pt + 4, bw, 17);
            c.drawText(textRenderer, title, bx + (bw - w(title)) / 2, pt + 8, TITLE, false);
        } else {
            if (wood) {
                c.getMatrices().push();
                c.getMatrices().translate((float) pl, (float) pt, 0f);
                Overlay.woodFrame(c, pw, ph);
                c.getMatrices().pop();
            } else {
                c.fill(pl - 3, pt - 3, pl + pw + 3, pt + ph + 3, 0xFF3B2412);
                c.fill(pl - 2, pt - 2, pl + pw + 2, pt + ph + 2, 0xFF6B4423);
                c.fill(pl, pt, pl + pw, pt + ph, 0xFFF3E4CF);
            }
            c.drawText(textRenderer, title, pl + (pw - w(title)) / 2, pt + 9, TITLE, wood);
            c.fill(pl + pw / 2 - 70, pt + 22, pl + pw / 2 + 70, pt + 23, LINE);
        }

        int side = apo ? 18 : 14;
        int left = pl + side, right = pl + pw - side;

        // 기간 탭 + 합계
        String day = selDay != null ? selDay : (tab == 0 ? today : null);     // 표에서 자세히 보는 날 (없으면 기간 전체)
        int ty = pt + (apo ? 28 : 30);
        int x = left;
        for (int i = 0; i < TAB.length; i++) {
            final int k = i;
            x += button(c, mx, my, x, ty, 0, 18, TAB[i], tab == i, false, false, () -> { tab = k; selDay = null; scroll = 0; }) + 4;
        }
        String from = rangeFrom(today), to = today;
        int sum = Journal.totalBetween(from, to);
        String summary;
        if (tab == 0) {
            summary = "오늘 " + sum + "회";
        } else {
            String f = Journal.firstDay();
            String effFrom = (f != null && f.compareTo(from) > 0) ? f : from;
            int span = Journal.spanDays(effFrom, to);
            summary = "합계 " + sum + "회 · 하루 평균 " + String.format(Locale.ROOT, "%.1f", sum / (double) span) + "회";
        }
        txtR(c, summary, right, ty + 5, ACC);

        // 날짜 막대
        int chh = ph >= 280 ? 68 : ph >= 250 ? 56 : 48;
        int cy = ty + 24;
        drawChart(c, mx, my, left, cy, right - left, chh, today, day);

        // 아래 버튼 · 안내 줄
        int by = pt + ph - (apo ? 32 : 28);
        int noteY = by - 12;
        boolean hasNotice = now < noticeUntil;
        String msg = hasNotice ? notice : (day != null ? "줄 오른쪽의 - + 로 횟수를 고칠 수 있어요" : "막대를 누르면 그날만 자세히 볼 수 있어요");
        txt(c, fit(msg, right - left), left, noteY, hasNotice ? OKC : SUB);

        // 표
        wellX = left;
        wellY = cy + chh + 6;
        wellW = right - left;
        wellH = noteY - 4 - wellY;
        well(c, wellX, wellY, wellW, wellH);
        if (day != null) drawDay(c, mx, my, day, today); else drawMatrix(c, from, to);

        // 맨 아래 버튼
        x = left;
        boolean canUndo = !Journal.lastDay().isEmpty();
        x += button(c, mx, my, x, by, 0, 18, "방금 기록 취소", false, false, !canUndo, this::undo) + 6;
        button(c, mx, my, x, by, 0, 18, confirmClear ? "한 번 더 누르면 모두 지워요" : "전체 기록 지우기", false, true, false, this::clearAll);
        int cw = 56;
        button(c, mx, my, right - cw, by, cw, 18, "닫기", false, false, false, this::close);
    }

    /** 탭이 보여줄 기간의 첫날 */
    private String rangeFrom(String today) {
        switch (tab) {
            case 0: return today;
            case 1: return Journal.daysAgo(today, 6);
            case 2: return Journal.daysAgo(today, 29);
            default: {
                String f = Journal.firstDay();
                return f == null || f.compareTo(today) > 0 ? today : f;
            }
        }
    }

    // ---------------------------------------------------------------- 날짜 막대

    private void drawChart(DrawContext c, int mx, int my, int cx, int cy, int cw, int chh, String today, String day) {
        well(c, cx, cy, cw, chh);
        int n = tab <= 1 ? 7 : 30;
        String[] ds = new String[n];
        int[] vs = new int[n];
        int max = 1;
        for (int i = 0; i < n; i++) {
            ds[i] = Journal.daysAgo(today, n - 1 - i);
            vs[i] = Journal.total(ds[i]);
            max = Math.max(max, vs[i]);
        }
        int inner = cw - 8;
        int slot = inner / n;
        int x0 = cx + 4 + (inner - slot * n) / 2;
        int base = cy + chh - 14;                      // 막대 바닥
        int maxH = chh - 14 - 12 - 4;                  // 막대 최대 높이 (위에 숫자 자리 12)
        int bw = n <= 7 ? Math.min(26, slot - 10) : Math.max(3, slot - 4);
        for (int i = 0; i < n; i++) {
            final String d = ds[i];
            int sx = x0 + i * slot;
            boolean isToday = d.equals(today), picked = d.equals(day);
            boolean hov = mx >= sx && mx < sx + slot && my >= cy + 2 && my < cy + chh - 2;
            if (picked) c.fill(sx, cy + 3, sx + slot, cy + chh - 3, PICK);
            else if (hov) c.fill(sx, cy + 3, sx + slot, cy + chh - 3, HOVER);
            int mid = sx + slot / 2;
            int bh = vs[i] == 0 ? 0 : Math.max(2, Math.round(maxH * vs[i] / (float) max));
            if (bh > 0) {
                c.fill(mid - bw / 2, base - bh, mid - bw / 2 + bw, base, isToday ? TODAY : BAR);
                c.fill(mid - bw / 2, base - bh, mid - bw / 2 + bw, base - bh + 1, isToday ? TODAY_HI : BAR_HI);
            } else {
                c.fill(mid - Math.max(2, bw / 2), base - 1, mid - Math.max(2, bw / 2) + Math.max(4, bw), base, WELL_LINE);
            }
            if ((vs[i] > 0 || n <= 7) && (slot >= 12 || i == n - 1 || picked)) {
                String num = String.valueOf(vs[i]);
                txtC(c, num, mid, base - bh - 10, isToday ? GOLDTX : (vs[i] == 0 ? ZERO : TXT));
            }
            String lab = n <= 7 ? Journal.tinyDay(d) : ((n - 1 - i) % 5 == 0 ? Journal.dayOfMonth(d) : "");
            if (!lab.isEmpty()) txtC(c, lab, mid, cy + chh - 11, isToday ? GOLDTX : (picked ? TXT : SUB));
            hit(sx, cy + 2, slot, chh - 4, () -> { selDay = d.equals(selDay) ? null : d; scroll = 0; });
        }
    }

    // ---------------------------------------------------------------- 보석 × 등급 표 (기간 전체)

    private void drawMatrix(DrawContext c, String from, String to) {
        List<Journal.Sum> rows = Journal.sums(from, to);
        int left = wellX + 8, contentRight = wellX + wellW - 20;
        int nameW = 96, totW = 48;
        int colW = Math.max(30, (contentRight - left - nameW - totW) / 4);
        int colX = left + nameW;
        int hy = wellY + 5;
        txt(c, "보석", left, hy, ACC);
        for (int g = 0; g < 4; g++) {
            txtC(c, Journal.COL_NAME[g], colX + g * colW + colW / 2, hy, Overlay.gradeCol(g == 3 ? "Epic" : Journal.COL_NAME[g], SegongClient.theme));
        }
        txtR(c, "합계", contentRight, hy, ACC);
        c.fill(wellX + 6, hy + 11, wellX + wellW - 6, hy + 12, WELL_LINE);

        int rowsTop = wellY + 19, rowH = 13;
        int footH = 15;
        int rowsBottom = wellY + wellH - 4 - footH;
        rowsVisible = Math.max(1, (rowsBottom - rowsTop) / rowH);
        rowsTotal = rows.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));

        int[] colTot = new int[4];
        int all = 0;
        for (Journal.Sum s : rows) {
            for (int g = 0; g < 4; g++) colTot[g] += s.c[g];
            all += s.total;
        }
        if (rows.isEmpty()) {
            txtC(c, "이 기간에는 기록이 없어요", wellX + wellW / 2, rowsTop + 8, SUB);
        }
        for (int r = 0; r < rowsVisible && scroll + r < rows.size(); r++) {
            Journal.Sum s = rows.get(scroll + r);
            int y = rowsTop + r * rowH;
            txt(c, fit(s.name, nameW - 8), left, y, TXT);
            for (int g = 0; g < 4; g++) {
                int v = s.c[g];
                txtC(c, v == 0 ? "·" : String.valueOf(v), colX + g * colW + colW / 2, y, v == 0 ? ZERO : (v >= 10 ? GOLDTX : TXT));
            }
            txtR(c, s.total + "회", contentRight, y, ACC);
        }
        int fy = wellY + wellH - 4 - footH + 2;
        c.fill(wellX + 6, fy - 2, wellX + wellW - 6, fy - 1, WELL_LINE);
        txt(c, "합계", left, fy + 2, ACC);
        for (int g = 0; g < 4; g++) txtC(c, String.valueOf(colTot[g]), colX + g * colW + colW / 2, fy + 2, OKC);
        txtR(c, all + "회", contentRight, fy + 2, GOLDTX);
        scrollBar(c, rowsTop, rowsTop + rowsVisible * rowH);
    }

    // ---------------------------------------------------------------- 하루 자세히 (+ / - 로 고치기)

    private void drawDay(DrawContext c, int mx, int my, String day, String today) {
        List<Journal.Row> rows = Journal.rows(day);
        int left = wellX + 8, contentRight = wellX + wellW - 20;
        int hy = wellY + 5;
        String head = Journal.shortDay(day) + (day.equals(today) ? " 오늘" : "") + " · " + Journal.total(day) + "회";
        txt(c, head, left, hy + 2, ACC);
        boolean back = selDay != null && !(tab == 0 && day.equals(today));
        if (back) {
            String lab = tab == 0 ? "오늘로" : "기간 전체 보기";
            int bw = w(lab) + 10;
            button(c, mx, my, contentRight - bw, hy - 2, bw, 14, lab, false, false, false, () -> { selDay = null; scroll = 0; });
        }
        c.fill(wellX + 6, hy + 14, wellX + wellW - 6, hy + 15, WELL_LINE);

        int rowsTop = wellY + 21, rowH = 18;
        int rowsBottom = wellY + wellH - 3;
        rowsVisible = Math.max(1, (rowsBottom - rowsTop) / rowH);
        rowsTotal = rows.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        int mxCount = 1;
        for (Journal.Row r : rows) mxCount = Math.max(mxCount, r.n);
        if (rows.isEmpty()) {
            txtC(c, "이 날은 기록이 없어요", wellX + wellW / 2, rowsTop + 10, SUB);
        }
        int gradeX = left + 86, barX = left + 146, barRight = contentRight - 82;
        for (int i = 0; i < rowsVisible && scroll + i < rows.size(); i++) {
            final Journal.Row r = rows.get(scroll + i);
            int y = rowsTop + i * rowH;
            int col = Overlay.gradeCol(r.grade, SegongClient.theme);
            txt(c, fit(r.name, 78), left, y + 3, TXT);
            txt(c, r.grade, gradeX, y + 3, col);
            int bw = Math.max(10, barRight - barX);
            c.fill(barX, y + 4, barX + bw, y + 12, apo ? Apo.S_OUT : WELL_LINE);
            if (apo) c.fill(barX + 1, y + 5, barX + bw - 1, y + 11, Apo.TRACK);
            int fw = Math.max(3, Math.round((bw - 2) * r.n / (float) mxCount));
            c.fill(barX + 1, y + 5, barX + 1 + fw, y + 11, col);
            c.fill(barX + 1, y + 5, barX + 1 + fw, y + 6, 0x55FFFFFF);
            txtR(c, r.n + "회", contentRight - 50, y + 3, GOLDTX);
            button(c, mx, my, contentRight - 44, y + 1, 18, 14, "-", false, false, false, () -> change(day, r, -1));
            button(c, mx, my, contentRight - 24, y + 1, 18, 14, "+", false, false, false, () -> change(day, r, 1));
            c.fill(wellX + 6, y + rowH - 1, wellX + wellW - 6, y + rowH, ROW_LINE);
        }
        scrollBar(c, rowsTop, rowsTop + rowsVisible * rowH);
    }

    /** 표 오른쪽의 ▲ ▼ 버튼과 위치 막대 (줄이 넘칠 때만) */
    private void scrollBar(DrawContext c, int top, int bottom) {
        if (rowsTotal <= rowsVisible) return;
        int bx = wellX + wellW - 17, bw = 13;
        int trackTop = top + 14, trackBottom = bottom - 14;
        // ▲
        hit(bx, top, bw, 12, () -> scroll = Math.max(0, scroll - 1));
        c.fill(bx, top, bx + bw, top + 12, BTN_BD);
        c.fill(bx + 1, top + 1, bx + bw - 1, top + 11, BTN);
        arrow(c, bx + bw / 2, top + 5, true, BTN_TX);
        // ▼
        hit(bx, bottom - 12, bw, 12, () -> scroll = Math.min(rowsTotal - rowsVisible, scroll + 1));
        c.fill(bx, bottom - 12, bx + bw, bottom, BTN_BD);
        c.fill(bx + 1, bottom - 11, bx + bw - 1, bottom - 1, BTN);
        arrow(c, bx + bw / 2, bottom - 7, false, BTN_TX);
        // 막대
        if (trackBottom - trackTop > 8) {
            c.fill(bx + 4, trackTop, bx + bw - 4, trackBottom, WELL_LINE);
            int th = Math.max(6, (trackBottom - trackTop) * rowsVisible / rowsTotal);
            int range = Math.max(1, rowsTotal - rowsVisible);
            int ty = trackTop + (trackBottom - trackTop - th) * scroll / range;
            c.fill(bx + 3, ty, bx + bw - 3, ty + th, SEL);
        }
    }

    // ---------------------------------------------------------------- 고치기

    private void change(String day, Journal.Row r, int delta) {
        if (Journal.adjust(day, r.name, r.grade, delta)) {
            SegongClient.saveJournal();
            say(r.name + " " + r.grade + " " + (delta > 0 ? "+1" : "-1") + " (" + Journal.shortDay(day) + ")");
        }
    }

    private void undo() {
        String what = Journal.undoLast();
        if (what == null) {
            say("취소할 기록이 없어요 (방금 쌓인 기록 하나만 취소할 수 있어요)");
            return;
        }
        SegongClient.saveJournal();
        say("방금 기록을 취소했어요: " + what);
    }

    private void clearAll() {
        if (!wasArmed) {
            confirmClear = true;
            return;
        }
        Journal.clear();
        SegongClient.saveJournal();
        selDay = null;
        scroll = 0;
        say("모든 세공 기록을 지웠어요");
    }
}
