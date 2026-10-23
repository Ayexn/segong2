package kr.segong;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 세공 기록: 날짜별 · 보석별 · 등급별로 몇 번 세공했는지 모아 둔다.
 * 한 번 = 보석 하나를 추천대로 끝낸 때 (Tally.observePlan 이 true 를 돌려주는 때).
 * 파일 읽기/쓰기는 SegongClient 가 하고, 여기서는 글자 처리와 계산만 한다 (Minecraft 코드와 무관).
 */
final class Journal {
    private Journal() {}

    static boolean show = true;               // 카드 아래 작은 표시
    static final int KEEP_DAYS = 400;         // 이보다 오래된 날짜는 지운다
    static final String UNKNOWN = "(이름 모름)";
    static int resetHour = 0;                 // 하루가 바뀌는 시각 (0 = 자정, 설정 파일의 tally.reset)

    private static final String[] GRADES = {"COMMON", "NORMAL", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
    private static final String[] GRADE_KO = {"Common", "Normal", "Rare", "Epic", "Legendary", "Mythic"};
    private static final String[] DOW = {"월", "화", "수", "목", "금", "토", "일"};
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /** 한 줄: 보석 이름 + 등급 + 횟수 */
    static final class Row {
        final String grade, name;
        final int n;
        Row(String grade, String name, int n) { this.grade = grade; this.name = name; this.n = n; }
        String text() { return name + " " + grade + " " + n + "회"; }
    }

    // 날짜 → ("등급\t이름" → 횟수)
    private static final TreeMap<String, TreeMap<String, Integer>> days = new TreeMap<>();
    private static String lastDay = "", lastKey = "";     // 방금 더한 기록 (취소용, 게임을 껐다 켜면 잊는다)

    // ---------------------------------------------------------------- 보석 이름 · 등급 읽기

    /** 툴팁 윗줄에서 보석 이름만 뽑는다 (등급 글자·특수문자·색 코드는 뺀다). 못 찾으면 UNKNOWN. */
    static String nameOf(List<String> lines) {
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            String raw = lines.get(i);
            if (raw == null) continue;
            if (raw.indexOf(':') >= 0 || raw.indexOf('：') >= 0 || raw.indexOf('%') >= 0 || raw.indexOf('.') >= 0) continue;   // 수치 줄·설명 줄
            String s = clean(raw);
            if (s.isEmpty() || s.length() > 16) continue;
            return s;
        }
        return UNKNOWN;
    }

    /** 색 코드(§a), 특수문자, 등급 글자(COMMON 등)를 빼고 글자·숫자만 남긴다. */
    static String clean(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§' && i < raw.length()) { i++; continue; }
            if (Character.isLetterOrDigit(cp) || cp == '-' || cp == '\'') sb.appendCodePoint(cp);
            else sb.append(' ');
        }
        StringBuilder out = new StringBuilder();
        for (String w : sb.toString().trim().split("\\s+")) {
            if (w.isEmpty() || isGradeWord(w)) continue;
            if (out.length() > 0) out.append(' ');
            out.append(w);
        }
        return out.toString();
    }

    private static boolean isGradeWord(String w) {
        String u = w.toUpperCase(Locale.ROOT);
        for (String g : GRADES) if (g.equals(u)) return true;
        return false;
    }

    /** 윗 3줄에서 등급 글자를 찾는다. 없으면 한도(lim)로 짐작한다. */
    static String gradeOf(List<String> lines, int lim) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, lines.size()); i++) sb.append(lines.get(i)).append(' ');
        String up = sb.toString().toUpperCase(Locale.ROOT);
        for (int i = 0; i < GRADES.length; i++) if (up.contains(GRADES[i])) return GRADE_KO[i];
        return switch (lim) {
            case 200 -> "Common";
            case 212 -> "Normal";
            case 236 -> "Rare";
            default -> "등급?";
        };
    }

    static int rank(String grade) {
        for (int i = 0; i < GRADE_KO.length; i++) if (GRADE_KO[i].equals(grade)) return i;
        return 9;
    }

    // ---------------------------------------------------------------- 더하기 · 취소 · 지우기

    static void add(String day, String name, String grade) {
        String key = grade + "\t" + name;
        days.computeIfAbsent(day, d -> new TreeMap<>()).merge(key, 1, Integer::sum);
        lastDay = day;
        lastKey = key;
    }

    /** 방금 더한 기록을 되돌린다. 되돌린 것의 설명을 주고, 되돌릴 게 없으면 null. */
    static String undoLast() {
        if (lastDay.isEmpty()) return null;
        TreeMap<String, Integer> m = days.get(lastDay);
        Integer c = m == null ? null : m.get(lastKey);
        String what = null;
        if (c != null) {
            what = rowOf(lastKey, 1).text();
            if (c <= 1) m.remove(lastKey); else m.put(lastKey, c - 1);
            if (m.isEmpty()) days.remove(lastDay);
        }
        lastDay = "";
        lastKey = "";
        return what;
    }

    /** 방금 더한 기록의 날짜 (없으면 "") */
    static String lastDay() { return lastDay; }

    static void clear() {
        days.clear();
        lastDay = "";
        lastKey = "";
    }

    /** 오래된 날짜를 지운다. 지운 게 있으면 true (저장 필요). */
    static boolean prune(String today) {
        try {
            String cutoff = LocalDate.parse(today).minusDays(KEEP_DAYS).toString();
            var old = days.headMap(cutoff);
            if (old.isEmpty()) return false;
            old.clear();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- 보기

    private static Row rowOf(String key, int n) {
        int t = key.indexOf('\t');
        return new Row(t < 0 ? "등급?" : key.substring(0, t), t < 0 ? key : key.substring(t + 1), n);
    }

    private static List<Row> sorted(java.util.Map<String, Integer> m) {
        List<Row> out = new ArrayList<>();
        for (var e : m.entrySet()) out.add(rowOf(e.getKey(), e.getValue()));
        out.sort((a, b) -> {
            if (a.n != b.n) return b.n - a.n;
            int ra = rank(a.grade), rb = rank(b.grade);
            if (ra != rb) return ra - rb;
            return a.name.compareTo(b.name);
        });
        return out;
    }

    /** 그 날의 줄들 (많이 한 순서) */
    static List<Row> rows(String day) {
        TreeMap<String, Integer> m = days.get(day);
        return m == null ? new ArrayList<>() : sorted(m);
    }

    /** 모든 날짜를 합친 줄들 */
    static List<Row> allRows() {
        TreeMap<String, Integer> sum = new TreeMap<>();
        for (var d : days.values()) for (var e : d.entrySet()) sum.merge(e.getKey(), e.getValue(), Integer::sum);
        return sorted(sum);
    }

    static int total(String day) {
        int t = 0;
        TreeMap<String, Integer> m = days.get(day);
        if (m != null) for (int c : m.values()) t += c;
        return t;
    }

    static int totalAll() {
        int t = 0;
        for (String d : days.keySet()) t += total(d);
        return t;
    }

    static int dayCount() { return days.size(); }

    /** "10/23(목)" 모양 */
    static String shortDay(String d) {
        try {
            LocalDate x = LocalDate.parse(d);
            return x.getMonthValue() + "/" + x.getDayOfMonth() + "(" + DOW[x.getDayOfWeek().getValue() - 1] + ")";
        } catch (Exception e) {
            return d;
        }
    }

    /** 입력한 날짜 글자를 yyyy-MM-dd 로 바로잡는다 (2026-10-2 도 허용). 안 되면 null. */
    static String parseDay(String s) {
        try {
            String[] p = s.trim().split("-");
            if (p.length != 3) return null;
            return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])).toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 날짜 계산

    /** 오늘 날짜 글자 (resetHour 시 이전이면 전날로 친다). */
    static String dayKey(LocalDateTime t, int resetHour) {
        return t.minusHours(Math.max(0, Math.min(23, resetHour))).toLocalDate().toString();
    }

    /** today 에서 back 일 전 날짜 (잘못된 글자면 today 그대로) */
    static String daysAgo(String today, int back) {
        try {
            return LocalDate.parse(today).minusDays(back).toString();
        } catch (Exception e) {
            return today;
        }
    }

    /** from~to(양끝 포함) 며칠인지 (최소 1) */
    static int spanDays(String from, String to) {
        try {
            long n = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to)) + 1;
            return (int) Math.max(1, Math.min(100000, n));
        } catch (Exception e) {
            return 1;
        }
    }

    /** "23(금)" 모양 (월은 빼고) */
    static String tinyDay(String d) {
        try {
            LocalDate x = LocalDate.parse(d);
            return x.getDayOfMonth() + "(" + DOW[x.getDayOfWeek().getValue() - 1] + ")";
        } catch (Exception e) {
            return d;
        }
    }

    /** 그 날이 몇 일인지 숫자 글자 ("23") */
    static String dayOfMonth(String d) {
        try {
            return String.valueOf(LocalDate.parse(d).getDayOfMonth());
        } catch (Exception e) {
            return d;
        }
    }

    // ---------------------------------------------------------------- 통계 창에서 쓰는 기간 계산

    /** 등급 칸 4개: Common · Normal · Rare · Epic 이상(Legendary, Mythic, 등급?) */
    static final String[] COL_NAME = {"Common", "Normal", "Rare", "Epic↑"};

    static int col(String grade) {
        int r = rank(grade);
        return r >= 3 ? 3 : r;
    }

    /** 보석 하나의 기간 합계: 등급 칸별 횟수 + 전체 */
    static final class Sum {
        final String name;
        final int[] c = new int[4];
        int total;
        Sum(String name) { this.name = name; }
    }

    /** from~to(양끝 포함) 날짜들. from 이 to 보다 늦으면 빈 목록. */
    private static java.util.NavigableMap<String, TreeMap<String, Integer>> sub(String from, String to) {
        if (from.compareTo(to) > 0) return new TreeMap<>();
        return days.subMap(from, true, to, true);
    }

    /** from~to(양끝 포함) 기간의 보석별 합계. 많이 한 순서. */
    static List<Sum> sums(String from, String to) {
        TreeMap<String, Sum> by = new TreeMap<>();
        for (var d : sub(from, to).entrySet()) {
            for (var e : d.getValue().entrySet()) {
                Row r = rowOf(e.getKey(), e.getValue());
                Sum s = by.computeIfAbsent(r.name, Sum::new);
                s.c[col(r.grade)] += r.n;
                s.total += r.n;
            }
        }
        List<Sum> out = new ArrayList<>(by.values());
        out.sort((a, b) -> a.total != b.total ? b.total - a.total : a.name.compareTo(b.name));
        return out;
    }

    /** from~to 기간 횟수 합 */
    static int totalBetween(String from, String to) {
        int t = 0;
        for (String d : sub(from, to).keySet()) t += total(d);
        return t;
    }

    /** from~to 기간에 기록이 있는 날 수 */
    static int activeDays(String from, String to) {
        int n = 0;
        for (String d : sub(from, to).keySet()) if (total(d) > 0) n++;
        return n;
    }

    /** 가장 오래된 기록 날짜 (없으면 null) */
    static String firstDay() { return days.isEmpty() ? null : days.firstKey(); }

    /** 그 날 그 보석·등급의 횟수를 delta 만큼 고친다 (0 밑으로는 안 내려가고, 0이 되면 줄을 지운다). 바뀌었으면 true. */
    static boolean adjust(String day, String name, String grade, int delta) {
        if (delta == 0) return false;
        String key = grade + "\t" + name;
        TreeMap<String, Integer> m = days.get(day);
        int cur = m == null ? 0 : m.getOrDefault(key, 0);
        int nv = Math.max(0, Math.min(9999, cur + delta));
        if (nv == cur) return false;
        if (nv == 0) {
            m.remove(key);
            if (m.isEmpty()) days.remove(day);
        } else {
            days.computeIfAbsent(day, d -> new TreeMap<>()).put(key, nv);
        }
        if (day.equals(lastDay) && key.equals(lastKey) && nv == 0) { lastDay = ""; lastKey = ""; }
        return true;
    }

    // ---------------------------------------------------------------- 파일 글자 (날짜 \t 등급 \t 이름 \t 횟수)

    static String serialize() {
        StringBuilder sb = new StringBuilder();
        for (var d : days.entrySet()) {
            for (var e : d.getValue().entrySet()) {
                sb.append(d.getKey()).append('\t').append(e.getKey()).append('\t').append(e.getValue()).append('\n');
            }
        }
        return sb.toString();
    }

    /** 파일 글자를 읽어 합친다. 이상한 줄은 건너뛴다. 읽은 줄 수를 돌려준다. */
    static int parse(Iterable<String> lines) {
        int ok = 0;
        for (String ln : lines) {
            if (ln == null) continue;
            String[] p = ln.split("\t", -1);
            if (p.length != 4 || !DAY.matcher(p[0]).matches() || p[1].isEmpty() || p[2].isEmpty()) continue;
            int n;
            try {
                n = Integer.parseInt(p[3].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (n <= 0) continue;
            days.computeIfAbsent(p[0], d -> new TreeMap<>()).merge(p[1] + "\t" + p[2], n, Integer::sum);
            ok++;
        }
        return ok;
    }

    /** 점검용 한 줄 */
    static String describe(String today) {
        return "기록 " + dayCount() + "일 · 총 " + totalAll() + "회 · 오늘 " + total(today) + "회 · 표시 " + (show ? "켜짐" : "꺼짐");
    }
}
