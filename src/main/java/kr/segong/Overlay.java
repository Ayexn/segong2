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
        if (style == 2) badges(mc, ctx, v);
    }

    private static int[] anchor(int pos, int style, int w, int h, int sw, int sh) {
        if (pos == 0) pos = style == 0 ? 2 : (style == 1 ? 4 : 5);
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
            case 1 -> layoutB(p, v, known);
            case 2 -> layoutC(p, v, known);
            default -> layoutA(p, v, known);
        };
    }

    // ---------------------------------------------------------------- A 카드형
    private static int[] layoutA(Pen p, SegongClient.View v, int[] known) {
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
        int w = p.tr.getWidth(line) + 16, h = 17;
        if (known != null) p.frame(known[0], known[1]);
        p.text(line, 8, 5, WHITE);
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
        int w = Math.max(p.tr.getWidth(l1), p.tr.getWidth(l2)) + 16, h = 30;
        if (known != null) p.frame(known[0], known[1]);
        p.text(l1, (w - p.tr.getWidth(l1)) / 2, 5, WHITE);
        p.text(l2, (w - p.tr.getWidth(l2)) / 2, 17, WHITE);
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
