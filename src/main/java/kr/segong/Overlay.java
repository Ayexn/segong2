package kr.segong;

import kr.segong.mixin.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;

/** 추천 결과를 화면에 그리는 3가지 방식 (A 카드형 / B 한 줄형 / C 슬롯 배지형). */
final class Overlay {
    static final int VIO = 0xFFB06CF0, OPA = 0xFF5FD0E8, ADA = 0xFFCFD6E4;
    static final int GREEN = 0xFF7BE495, GRAY = 0xFF9AA3B8, YEL = 0xFFFFD76A, WHITE = 0xFFFFFFFF;
    static final int[] COL = {VIO, OPA, ADA};
    static final String[] NAME = {"바이올렛", "오팔", "아다만티움"};
    static final String[] LABEL = {"순도", "광도", "강도"};

    // 마지막으로 그린 카드의 위치(화면 좌표)와 끌기 상태
    private static int hitX, hitY, hitW, hitH;
    private static boolean dragging, prevDown;
    private static int grabDX, grabDY;

    private Overlay() {}

    /** 그리기 도구. on=false 이면 크기만 재고 실제로는 그리지 않는다. */
    private static final class Pen {
        final TextRenderer tr; final DrawContext ctx; final boolean on;
        Pen(TextRenderer tr, DrawContext ctx, boolean on) { this.tr = tr; this.ctx = ctx; this.on = on; }
        void rect(int x1, int y1, int x2, int y2, int c) { if (on) ctx.fill(x1, y1, x2, y2, c); }
        int text(String s, int x, int y, int c) { if (on) ctx.drawText(tr, s, x, y, c, true); return tr.getWidth(s); }
        void textRight(String s, int right, int y, int c) { text(s, right - tr.getWidth(s), y, c); }
        /** 그림자 없는 글자 (밝은 바탕용) */
        int plain(String s, int x, int y, int c) { if (on) ctx.drawText(tr, s, x, y, c, false); return tr.getWidth(s); }
        void plainCenter(String s, int cx, int y, int c) { plain(s, cx - tr.getWidth(s) / 2, y, c); }
        /** 크게 키운 글자 (왼쪽 x 기준) */
        void scaled(String s, int x, int y, int c, float k) {
            if (!on) return;
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate((float) x, (float) y, 0f);
            m.scale(k, k, 1f);
            ctx.drawText(tr, s, 0, 0, c, false);
            m.pop();
        }
        void bigRight(String s, int right, int y, int c) {
            if (!on) return;
            int w = tr.getWidth(s) * 2;
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate((float) (right - w), (float) y, 0f);
            m.scale(2f, 2f, 1f);
            ctx.drawText(tr, s, 0, 0, c, true);
            m.pop();
        }
        void frame(int w, int h) {
            rect(0, 0, w, h, 0xE0101420);
            rect(0, 0, w, 1, 0x40FFFFFF); rect(0, h - 1, w, h, 0x40FFFFFF);
            rect(0, 0, 1, h, 0x40FFFFFF); rect(w - 1, 0, w, h, 0x40FFFFFF);
        }
    }

    static String gradeLabel(int lim) {
        return switch (lim) {
            case 200 -> "COMMON 200";
            case 212 -> "NORMAL 212";
            case 236 -> "RARE 236";
            default -> String.valueOf(lim);
        };
    }

    static void clearHit() { hitW = 0; hitH = 0; }

    static boolean hit(double mx, double my) {
        return hitW > 0 && mx >= hitX && mx < hitX + hitW && my >= hitY && my < hitY + hitH;
    }

    static boolean isDragging() { return dragging; }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    static void render(MinecraftClient mc, DrawContext ctx, SegongClient.View v, int mx, int my, boolean interactive) {
        float s = SegongClient.scale;
        int style = SegongClient.style;
        int[] sz = layout(new Pen(mc.textRenderer, ctx, false), v, style, null);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int pw = Math.round(sz[0] * s), ph = Math.round(sz[1] * s);
        int[] xy;
        if (SegongClient.customX >= 0 && SegongClient.customY >= 0) {
            xy = new int[]{clamp(Math.round(SegongClient.customX * sw), 0, Math.max(0, sw - pw)),
                           clamp(Math.round(SegongClient.customY * sh), 0, Math.max(0, sh - ph))};
        } else {
            xy = anchor(0, style, pw, ph, sw, sh);
        }
        boolean hover = false;
        if (interactive) {
            boolean down = false;
            try {
                down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            } catch (Throwable e) {
                // 읽지 못하면 끌기 없이 표시만
            }
            boolean inside = mx >= xy[0] && mx < xy[0] + pw && my >= xy[1] && my < xy[1] + ph;
            if (down && !prevDown && inside) { dragging = true; grabDX = mx - xy[0]; grabDY = my - xy[1]; }
            boolean wasDragging = dragging;
            if (!down) dragging = false;
            if (dragging) {
                int nx = clamp(mx - grabDX, 0, Math.max(0, sw - pw)), ny = clamp(my - grabDY, 0, Math.max(0, sh - ph));
                xy = new int[]{nx, ny};
                SegongClient.customX = (float) nx / sw;
                SegongClient.customY = (float) ny / sh;
            } else if (wasDragging) {
                SegongClient.save();
            }
            prevDown = down;
            hover = inside || dragging;
            hitX = xy[0]; hitY = xy[1]; hitW = pw; hitH = ph;
        }
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate((float) xy[0], (float) xy[1], 0f);
        m.scale(s, s, 1f);
        layout(new Pen(mc.textRenderer, ctx, true), v, style, sz);
        m.pop();
        if (hover) {
            int g = 0xFFFFD76A;
            ctx.fill(xy[0] - 1, xy[1] - 1, xy[0] + pw + 1, xy[1], g);
            ctx.fill(xy[0] - 1, xy[1] + ph, xy[0] + pw + 1, xy[1] + ph + 1, g);
            ctx.fill(xy[0] - 1, xy[1] - 1, xy[0], xy[1] + ph + 1, g);
            ctx.fill(xy[0] + pw, xy[1] - 1, xy[0] + pw + 1, xy[1] + ph + 1, g);
            if (xy[1] >= 11) ctx.drawText(mc.textRenderer, "끌어서 이동", xy[0], xy[1] - 10, 0xFFFFD76A, true);
        }
        if (style == 3) badges(mc, ctx, v);
    }

    private static int[] anchor(int pos, int style, int w, int h, int sw, int sh) {
        if (pos == 0) pos = style <= 1 ? 2 : (style == 2 ? 4 : 5);
        switch (pos) {
            case 1: return new int[]{6, 6};
            case 2: return new int[]{6, (sh - h) / 2};
            case 3: return new int[]{sw - w - 6, 6};
            case 4: return new int[]{(sw - w) / 2, 6};
            default: return new int[]{(sw - w) / 2, sh - h - 6};
        }
    }

    private static int[] layout(Pen p, SegongClient.View v, int style, int[] known) {
        return switch (style) {
            case 1 -> layoutA(p, v, known);   // 카드형
            case 2 -> layoutB(p, v, known);   // 한 줄형
            case 3 -> layoutC(p, v, known);   // 슬롯 배지형
            default -> layoutT(p, v, known);  // 타일형
        };
    }


    // ---------------------------------------------------------------- 우드카드 (나무판 + 못) 공통 그리기
    static final int W_OUT = 0xFF2A1608, W_HI = 0xFFC8955A, W_SH = 0xFF4A2C12, W_BODY = 0xFF8A5A2B, W_GROOVE = 0xFF6A4220;
    static final int W_PLANK = 0xFF7A4F26, W_NAIL = 0xFFBFC4CC, W_NAIL_D = 0xFF3A3F48;
    static final int W_CREAM = 0xFFFFF3D6, W_SUB = 0xFFE0C99A, W_OK = 0xFFFFE08A, W_RED = 0xFFFFB0A0, W_GOLD = 0xFFE0B060;
    static final int[] W_COL = {0xFFE3C4FF, 0xFFB5F2FF, 0xFFFFFFFF};

    /** 나무판 틀: 모서리를 살짝 깎은 바깥선 + 밝은/어두운 테두리 + 판자 줄 + 모서리 못 */
    static void woodFrame(DrawContext c, int w, int h) {
        c.fill(1, 0, w - 1, h, W_OUT);
        c.fill(0, 1, w, h - 1, W_OUT);
        c.fill(1, 1, w - 1, 2, W_HI);
        c.fill(1, 1, 2, h - 1, W_HI);
        c.fill(1, h - 2, w - 1, h - 1, W_SH);
        c.fill(w - 2, 1, w - 1, h - 1, W_SH);
        c.fill(2, 2, w - 2, h - 2, W_BODY);
        c.fill(3, 3, w - 3, h - 3, W_GROOVE);
        c.fill(4, 4, w - 4, h - 4, W_BODY);
        for (int y = 14; y < h - 5; y += 14) c.fill(4, y, w - 4, y + 1, W_PLANK);
        nail(c, 2, 2); nail(c, w - 6, 2); nail(c, 2, h - 6); nail(c, w - 6, h - 6);
    }

    private static void nail(DrawContext c, int x, int y) {
        c.fill(x, y, x + 4, y + 4, W_NAIL_D);
        c.fill(x + 1, y + 1, x + 3, y + 3, W_NAIL);
    }

    /** 오목하게 들어간 칸 */
    static void woodSlot(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, W_OUT);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFA8763E);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 2, W_GROOVE);
        c.fill(x + 1, y + 1, x + w - 2, y + 2, W_SH);
        c.fill(x + 1, y + 1, x + 2, y + h - 2, W_SH);
    }

    /** 나무 버튼 (선택되면 금색) */
    static void woodButton(DrawContext c, int x, int y, int w, int h, boolean sel, boolean red) {
        int fill = red ? 0xFF9A3B2A : sel ? W_GOLD : 0xFFA8763E;
        int hi = red ? 0xFFD98A78 : sel ? 0xFFFFF0B0 : 0xFFD9A868;
        int lo = red ? 0xFF5A2018 : sel ? 0xFFA87A28 : 0xFF5A3A18;
        c.fill(x, y, x + w, y + h, W_OUT);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
        c.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
        c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
        c.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, lo);
    }

    // ---------------------------------------------------------------- 타일형 (우드카드 / 크림)
    private static final int T_PANEL = 0xFFF3E4CF, T_EDGE_OUT = 0xFF3B2412, T_EDGE_IN = 0xFF6B4423;
    private static final int T_TITLE = 0xFF6B3F17, T_TXT = 0xFF4A2C14, T_SUB = 0xFF8A6A45, T_OK = 0xFF2F8A4A, T_RED = 0xFFB5533C;
    private static final int T_TILE = 0xFFFFF6E6, T_TRACK = 0xFFD9BF96, T_GOLD = 0xFFF2B631;
    private static final int[] T_COL = {0xFF8A3FC9, 0xFF1F8FA8, 0xFF6B7386};      // 글자색
    private static final int[] T_BAR = {0xFFB06CF0, 0xFF5FD0E8, 0xFFCFD6E4};      // 띠 색

    private static int[] layoutT(Pen p, SegongClient.View v, int[] known) {
        final int th = SegongClient.theme;                 // 0 다크 약방 / 1 우드카드 / 2 크림
        final boolean apo = th == 0, wood = th <= 1;       // wood = 두꺼운 틀 디자인 (약방·우드카드)
        final int cTitle = wood ? W_CREAM : T_TITLE;
        final int cTxt = apo ? Apo.TXT : wood ? W_CREAM : T_TXT;
        final int cSub = apo ? Apo.SUB : wood ? W_SUB : T_SUB;
        final int cOk = apo ? Apo.OK : wood ? W_OK : T_OK;
        final int cRed = wood ? W_RED : T_RED;
        final int cEdge = apo ? Apo.S_OUT : wood ? W_OUT : T_EDGE_OUT;
        final int[] cCol = wood ? W_COL : T_COL;
        int w = wood ? 214 : 206;
        int m = wood ? 10 : 8;                 // 안쪽 여백
        int bm = apo ? 18 : m;                 // 맨 아래 줄 여백 (모서리 장식 피하기)
        int[] st = v.stats;
        if (known != null) {
            int kw = known[0], kh = known[1];
            if (apo) {
                if (p.on) Apo.frame(p.ctx, kw, kh, false);
            } else if (wood) {
                if (p.on) woodFrame(p.ctx, kw, kh);
            } else {
                p.rect(0, 0, kw, kh, T_EDGE_OUT);
                p.rect(1, 1, kw - 1, kh - 1, T_EDGE_IN);
                p.rect(3, 3, kw - 3, kh - 3, T_PANEL);
            }
        }
        int top = wood ? 3 : 0;
        if (apo) {
            if (p.on) Apo.banner(p.ctx, m - 1, 4, 96, 15);
            p.plainCenter("세공 도우미", m - 1 + 48, 8, Apo.B_TXT);
        } else if (wood) {
            p.text("세공 도우미", m, 8 + top, cTitle);
        } else {
            p.plain("세공 도우미", m, 8, cTitle);
        }
        if (v.lim > 0) {
            String g = gradeLabel(v.lim);
            int tw = p.tr.getWidth(g);
            if (apo) {
                int bx = w - m - tw - 12;
                if (p.on) Apo.pill(p.ctx, bx, 5, tw + 12, 14, Apo.P_FILL, Apo.OUT, Apo.P_HI, Apo.P_LO, 1);
                p.plain(g, bx + 6, 8, Apo.P_TXT);
            } else {
                p.rect(w - m - tw - 8, 5 + top, w - m, 18 + top, cEdge);
                p.rect(w - m - tw - 7, 6 + top, w - m - 1, 17 + top, T_GOLD);
                p.plain(g, w - m - tw - 3, 8 + top, T_EDGE_OUT);
            }
        }
        if (v.r == null) {
            int y = 24 + top;
            p.plain("현재 " + st[0] + " / " + st[1] + " / " + st[2] + " = " + (st[0] + st[1] + st[2]), m, y, cTxt); y += 12;
            p.plain("등급을 못 읽음 → " + SegongClient.keyName(SegongClient.gradeKey) + " 키로 선택", m, y, cRed); y += 12;
            if (!v.sig.isEmpty()) { p.plain("표시 " + v.sig, m, y, cSub); y += 12; }
            return new int[]{w, y + (apo ? 12 : 8)};
        }
        int[] cnt = {v.r.a, v.r.b, v.r.c};
        int tileW = (w - 2 * m - 8) / 3, tileH = 56, ty = 22 + top;
        for (int k = 0; k < 3; k++) {
            int tx = m + k * (tileW + 4);
            if (apo) {
                if (p.on) Apo.slot(p.ctx, tx, ty, tileW, tileH);
                p.rect(tx + 3, ty + 3, tx + tileW - 3, ty + 6, T_BAR[k]);
            } else if (wood) {
                if (p.on) woodSlot(p.ctx, tx, ty, tileW, tileH);
                p.rect(tx + 2, ty + 3, tx + tileW - 2, ty + 6, T_BAR[k]);
            } else {
                p.rect(tx, ty, tx + tileW, ty + tileH, T_EDGE_OUT);
                p.rect(tx + 1, ty + 1, tx + tileW - 1, ty + tileH - 1, T_TILE);
                p.rect(tx + 1, ty + 1, tx + tileW - 1, ty + 4, T_BAR[k]);
            }
            if (wood) p.text(NAME[k], tx + (tileW - p.tr.getWidth(NAME[k])) / 2, ty + 8, cCol[k]);
            else p.plainCenter(NAME[k], tx + tileW / 2, ty + 7, cCol[k]);
            String num = String.valueOf(cnt[k]);
            int unit = p.tr.getWidth("개");
            int total = p.tr.getWidth(num) * 3 + 2 + unit;
            int nx = tx + (tileW - total) / 2;
            p.scaled(num, nx, ty + 19, cCol[k], 3f);
            p.plain("개", nx + p.tr.getWidth(num) * 3 + 2, ty + 31, cCol[k]);
            p.plainCenter("보유 " + v.stock[k], tx + tileW / 2, ty + tileH - 11, cSub);
        }
        int y = ty + tileH + 6;
        if (wood) p.text("합 " + v.r.sum + " / " + v.lim, m, y, cOk); else p.plain("합 " + v.r.sum + " / " + v.lim, m, y, cOk);
        String right = "광채 " + v.r.n + "개";
        p.plain(right, w - m - p.tr.getWidth(right), y, cSub);
        y += 12;
        p.rect(m, y, w - m, y + 8, cEdge);
        p.rect(m + 1, y + 1, w - m - 1, y + 7, apo ? Apo.TRACK : wood ? W_SH : T_TRACK);
        int fillW = Math.round((w - 2 * m - 2) * Math.min(1f, (float) v.r.sum / v.lim));
        p.rect(m + 1, y + 1, m + 1 + fillW, y + 7, cOk);
        y += 12;
        int[] after = new int[3];
        for (int k = 0; k < 3; k++) after[k] = Math.min(100, st[k] + cnt[k] * 10);
        p.plain(LABEL[0] + " " + after[0], bm, y, cTxt);
        p.plainCenter(LABEL[1] + " " + after[1], w / 2, y, cTxt);
        String last = LABEL[2] + " " + after[2];
        p.plain(last, w - bm - p.tr.getWidth(last), y, cTxt);
        return new int[]{w, y + (apo ? 20 : wood ? 19 : 16)};
    }

    // ---------------------------------------------------------------- A 카드형 (다크 약방)
    private static int[] layoutAP(Pen p, SegongClient.View v, int[] known) {
        int w = 184;
        int[] st = v.stats;
        if (known != null && p.on) Apo.frame(p.ctx, known[0], known[1], false);
        if (p.on) Apo.banner(p.ctx, 9, 4, 96, 15);
        p.plainCenter("세공 도우미", 57, 8, Apo.B_TXT);
        if (v.lim > 0) {
            String g = gradeLabel(v.lim);
            int pw2 = p.tr.getWidth(g) + 12, bx = w - 8 - pw2;
            if (p.on) Apo.pill(p.ctx, bx, 5, pw2, 14, Apo.P_FILL, Apo.OUT, Apo.P_HI, Apo.P_LO, 1);
            p.plain(g, bx + 6, 8, Apo.P_TXT);
        }
        if (v.r == null) {
            int y = 26;
            p.text("현재 " + st[0] + " / " + st[1] + " / " + st[2] + " = " + (st[0] + st[1] + st[2]), 12, y, Apo.TXT); y += 12;
            p.text("등급을 못 읽음 → " + SegongClient.keyName(SegongClient.gradeKey) + " 키로 선택", 12, y, Apo.RED); y += 12;
            if (!v.sig.isEmpty()) { p.text("표시 " + v.sig, 12, y, Apo.SUB); y += 12; }
            return new int[]{w, y + 14};
        }
        int[] cnt = {v.r.a, v.r.b, v.r.c};
        for (int k = 0; k < 3; k++) {
            int y0 = 24 + 20 * k;
            if (p.on) { Apo.slot(p.ctx, 10, y0, 164, 18); Apo.medal(p.ctx, 21, y0 + 9, 11, COL[k]); }
            p.text(NAME[k], 31, y0 + 5, W_COL[k]);
            p.text("보유 " + v.stock[k], 94, y0 + 5, Apo.SUB);
            p.bigRight(String.valueOf(cnt[k]), 170, y0 + 1, W_COL[k]);
        }
        // 장식 구분선
        p.rect(12, 86, 82, 87, Apo.CURL);
        p.rect(102, 86, 172, 87, Apo.CURL);
        if (p.on) Apo.diamond(p.ctx, 92, 86);
        for (int k = 0; k < 3; k++) {
            int y = 94 + 12 * k;
            int cur = st[k], after = Math.min(100, cur + cnt[k] * 10);
            if (p.on) Apo.dot(p.ctx, 15, y + 4, COL[k]);
            p.text(LABEL[k], 23, y, Apo.SUB);
            p.rect(41, y + 1, 123, y + 8, Apo.S_OUT);
            p.rect(42, y + 2, 122, y + 7, Apo.TRACK);
            p.rect(42, y + 2, 42 + 80 * after / 100, y + 7, (0x88 << 24) | (COL[k] & 0xFFFFFF));
            p.rect(42, y + 2, 42 + 80 * cur / 100, y + 7, COL[k]);
            p.rect(124, y + 2, 125, y + 7, Apo.S_RIM);
            p.rect(125, y + 3, 126, y + 6, Apo.S_RIM);
            p.rect(126, y + 4, 127, y + 5, Apo.S_RIM);
            p.textRight(cur + " → " + after, w - 14, y, Apo.TXT);
        }
        p.text("합 " + v.r.sum + " / " + v.lim, 18, 137, Apo.OK);
        p.textRight("광채 " + v.r.n + "개", w - 18, 137, Apo.SUB);
        return new int[]{w, 156};
    }

    // ---------------------------------------------------------------- A 카드형 (우드카드)
    private static int[] layoutAW(Pen p, SegongClient.View v, int[] known) {
        int w = 184;
        int[] st = v.stats;
        if (known != null && p.on) woodFrame(p.ctx, known[0], known[1]);
        p.text("세공 도우미", 10, 9, W_CREAM);
        if (v.lim > 0) {
            String g = gradeLabel(v.lim);
            int bx = w - 8 - (p.tr.getWidth(g) + 8);
            p.rect(bx, 7, w - 8, 20, W_OUT);
            p.rect(bx + 1, 8, w - 9, 19, T_GOLD);
            p.plain(g, bx + 4, 10, W_OUT);
        }
        if (v.r == null) {
            int y = 26;
            p.text("현재 " + st[0] + " / " + st[1] + " / " + st[2] + " = " + (st[0] + st[1] + st[2]), 10, y, W_CREAM); y += 12;
            p.text("등급을 못 읽음 → " + SegongClient.keyName(SegongClient.gradeKey) + " 키로 선택", 10, y, W_RED); y += 12;
            if (!v.sig.isEmpty()) { p.text("표시 " + v.sig, 10, y, W_SUB); y += 12; }
            return new int[]{w, y + 8};
        }
        int[] cnt = {v.r.a, v.r.b, v.r.c};
        for (int k = 0; k < 3; k++) {
            int y0 = 24 + 20 * k;
            if (p.on) woodSlot(p.ctx, 8, y0, 168, 17);
            p.rect(13, y0 + 5, 19, y0 + 11, T_BAR[k]);
            p.text(NAME[k], 23, y0 + 5, W_COL[k]);
            p.text("보유 " + v.stock[k], 92, y0 + 5, W_SUB);
            p.bigRight(String.valueOf(cnt[k]), 170, y0 + 1, W_COL[k]);
        }
        p.rect(8, 86, 176, 87, W_SH);
        p.rect(8, 87, 176, 88, 0xFFA8763E);
        for (int k = 0; k < 3; k++) {
            int y = 92 + 12 * k;
            int cur = st[k], after = Math.min(100, cur + cnt[k] * 10);
            p.text(LABEL[k], 10, y, W_SUB);
            p.rect(36, y + 3, 116, y + 7, W_OUT);
            p.rect(36, y + 3, 36 + 80 * after / 100, y + 7, (0x66 << 24) | (T_BAR[k] & 0xFFFFFF));
            p.rect(36, y + 3, 36 + 80 * cur / 100, y + 7, W_COL[k]);
            p.textRight(cur + " → " + after, w - 10, y, W_CREAM);
        }
        p.text("합 " + v.r.sum + " / " + v.lim, 10, 130, W_OK);
        p.textRight("광채 " + v.r.n + "개", w - 10, 130, W_SUB);
        return new int[]{w, 146};
    }

    // ---------------------------------------------------------------- A 카드형
    private static int[] layoutA(Pen p, SegongClient.View v, int[] known) {
        if (SegongClient.theme == 0) return layoutAP(p, v, known);
        if (SegongClient.theme == 1) return layoutAW(p, v, known);
        int w = 176, y = 4;
        if (known != null) p.frame(known[0], known[1]);
        p.text("§e세공 도우미", 6, y + 2, WHITE);
        if (v.lim > 0) p.textRight("§6" + gradeLabel(v.lim) + " §8" + v.how, w - 6, y + 2, WHITE);
        y += 14;
        int[] st = v.stats;
        if (v.r == null) {
            p.text("§f현재 " + st[0] + " / " + st[1] + " / " + st[2] + " = " + (st[0] + st[1] + st[2]), 6, y, WHITE); y += 11;
            p.text("§c등급을 못 읽음 → " + SegongClient.keyName(SegongClient.gradeKey) + " 키로 선택", 6, y, WHITE); y += 11;
            if (!v.sig.isEmpty()) { p.text("§8표시 " + v.sig, 6, y, WHITE); y += 11; }
            return new int[]{w, y + 3};
        }
        int[] cnt = {v.r.a, v.r.b, v.r.c};
        for (int k = 0; k < 3; k++) {
            int y0 = y;
            p.rect(6, y0 + 6, 12, y0 + 12, COL[k]);
            p.text(NAME[k], 16, y0 + 6, COL[k]);
            p.text("보유 " + v.stock[k], 84, y0 + 7, GRAY);
            p.bigRight(String.valueOf(cnt[k]), w - 6, y0 + 2, COL[k]);
            y += 19;
        }
        p.rect(6, y + 1, w - 6, y + 2, 0x22FFFFFF);
        y += 4;
        for (int k = 0; k < 3; k++) {
            int cur = st[k], after = Math.min(100, cur + cnt[k] * 10);
            p.text(LABEL[k], 6, y + 1, GRAY);
            p.rect(32, y + 3, 112, y + 7, 0x33FFFFFF);
            p.rect(32, y + 3, 32 + 80 * after / 100, y + 7, (0x80 << 24) | (COL[k] & 0xFFFFFF));
            p.rect(32, y + 3, 32 + 80 * cur / 100, y + 7, COL[k]);
            p.textRight(cur + " → " + after, w - 6, y + 1, WHITE);
            y += 11;
        }
        y += 3;
        p.text("합 " + v.r.sum + " / " + v.lim, 6, y, GREEN);
        p.textRight("광채 " + v.r.n + "개", w - 6, y, GRAY);
        return new int[]{w, y + 14};
    }

    // ---------------------------------------------------------------- B 한 줄형
    private static int[] layoutB(Pen p, SegongClient.View v, int[] known) {
        String line;
        if (v.r == null) {
            line = "§f현재 " + v.stats[0] + "/" + v.stats[1] + "/" + v.stats[2] + " §7· §c등급 못 읽음 (" + SegongClient.keyName(SegongClient.gradeKey) + ")";
        } else {
            line = "§6" + gradeLabel(v.lim) + " §7│ §d바이올렛 §f§l" + v.r.a + " §b오팔 §f§l" + v.r.b + " §7아다만티움 §f§l" + v.r.c + " §7│ §a합 " + v.r.sum;
        }
        final int th = SegongClient.theme;      // 0 약방 / 1 우드 / 2 크림
        boolean wood = th == 1, apo = th == 0;
        int w = p.tr.getWidth(line) + (apo ? 24 : wood ? 22 : 16), h = apo ? 21 : wood ? 19 : 17;
        if (known != null) {
            if (apo) { if (p.on) Apo.frame(p.ctx, known[0], known[1], true); }
            else if (wood) { if (p.on) woodFrame(p.ctx, known[0], known[1]); }
            else p.frame(known[0], known[1]);
        }
        p.text(line, apo ? 12 : wood ? 11 : 8, apo || wood ? 6 : 5, WHITE);
        return new int[]{w, h};
    }

    // ---------------------------------------------------------------- C 슬롯 배지형 (요약 상자 + 슬롯 배지)
    private static int[] layoutC(Pen p, SegongClient.View v, int[] known) {
        String l1, l2;
        if (v.r == null) {
            l1 = "§c등급을 못 읽음 → " + SegongClient.keyName(SegongClient.gradeKey) + " 키로 선택";
            l2 = "§7현재 " + v.stats[0] + " / " + v.stats[1] + " / " + v.stats[2];
        } else {
            l1 = "§a합 " + v.r.sum + " / " + v.lim + " §7· §6" + gradeLabel(v.lim);
            int[] cnt = {v.r.a, v.r.b, v.r.c};
            int[] after = new int[3];
            for (int k = 0; k < 3; k++) after[k] = Math.min(100, v.stats[k] + cnt[k] * 10);
            l2 = "§7순도 " + after[0] + " · 광도 " + after[1] + " · 강도 " + after[2];
        }
        final int th = SegongClient.theme;      // 0 약방 / 1 우드 / 2 크림
        boolean wood = th <= 1, apo = th == 0;
        int w = Math.max(p.tr.getWidth(l1), p.tr.getWidth(l2)) + (wood ? 24 : 16), h = wood ? 34 : 30;
        if (known != null) {
            if (apo) { if (p.on) Apo.frame(p.ctx, known[0], known[1], true); }
            else if (wood) { if (p.on) woodFrame(p.ctx, known[0], known[1]); }
            else p.frame(known[0], known[1]);
        }
        int off = wood ? 3 : 0;
        p.text(l1, (w - p.tr.getWidth(l1)) / 2, 5 + off, WHITE);
        p.text(l2, (w - p.tr.getWidth(l2)) / 2, 17 + off, WHITE);
        return new int[]{w, h};
    }

    /** 쓸 광채 종류마다, 내 인벤토리 첫 칸에 초록 테두리와 "N개" 배지를 그린다. */
    private static void badges(MinecraftClient mc, DrawContext ctx, SegongClient.View v) {
        if (v.r == null || mc.player == null) return;
        if (!(mc.currentScreen instanceof HandledScreen<?> hs)) return;
        Object o = hs;
        if (!(o instanceof HandledScreenAccessor acc)) return;   // 통로가 없으면 배지는 생략
        int ox, oy;
        try { ox = acc.segong_getX(); oy = acc.segong_getY(); } catch (Throwable e) { return; }
        int[] cnt = {v.r.a, v.r.b, v.r.c};
        for (int k = 0; k < 3; k++) {
            if (cnt[k] <= 0) continue;
            for (Slot sl : hs.getScreenHandler().slots) {
                if (sl.inventory != mc.player.getInventory()) continue;
                ItemStack st = sl.getStack();
                if (st.isEmpty()) continue;
                String name = st.getName().getString();
                if (!name.contains("광채") || !name.contains(Overlay.NAME[k])) continue;
                int sx = ox + sl.x, sy = oy + sl.y;
                int g = 0xFF18E07A;
                ctx.fill(sx - 1, sy - 1, sx + 17, sy, g);
                ctx.fill(sx - 1, sy + 16, sx + 17, sy + 17, g);
                ctx.fill(sx - 1, sy - 1, sx, sy + 17, g);
                ctx.fill(sx + 16, sy - 1, sx + 17, sy + 17, g);
                String t = cnt[k] + "개";
                int tw = mc.textRenderer.getWidth(t);
                int bx = sx + 8 - (tw + 6) / 2, by = sy - 13;
                ctx.fill(bx, by, bx + tw + 6, by + 11, 0xFF18C06A);
                ctx.drawText(mc.textRenderer, t, bx + 3, by + 2, WHITE, true);
                break;
            }
        }
    }
}
