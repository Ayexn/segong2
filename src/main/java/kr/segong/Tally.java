package kr.segong;

import java.time.LocalDateTime;

/**
 * 오늘 세공 횟수.
 * 보석 하나를 "추천한 개수만큼 다 넣어서" 순도/광도/강도가 추천 수에 도달하면(남은 추천 개수가 0이 되면) 1번으로 센다.
 * 광채를 하나 넣을 때마다 세지 않는다. 처음부터 넣을 게 없던 보석(이미 끝난 보석)도 세지 않는다.
 * 날짜가 바뀌면 0부터 다시 센다 (초기화 시각은 resetHour, 기본 0시).
 * Minecraft 코드와 무관한 순수 로직.
 */
final class Tally {
    private Tally() {}

    static boolean show = true;    // 카드에 표시할지
    static int resetHour = 0;      // 하루가 바뀌는 시각 (0 = 자정)
    static int count;              // 오늘 횟수
    static String day = "";        // count 가 속한 날짜 (yyyy-MM-dd)

    private static int curPlan = -1;         // 지금 보고 있는 계획 번호
    private static boolean armed;            // 이 계획이 '넣을 광채가 남은 상태'로 시작했는지
    private static int lastDoneId = -1;      // 마지막으로 센 계획 번호 (같은 계획을 두 번 세지 않으려고)
    private static int lastRemaining = -1;   // 점검용: 마지막으로 본 남은 개수

    /** 오늘 날짜 글자 (resetHour 시 이전이면 전날로 친다). */
    static String dayKey(LocalDateTime t, int resetHour) {
        return t.minusHours(Math.max(0, Math.min(23, resetHour))).toLocalDate().toString();
    }

    /** 날짜가 바뀌었으면 0부터 다시 센다. 바뀌었으면 true (저장 필요). */
    static boolean rollover(String today) {
        if (today.equals(day)) return false;
        day = today;
        count = 0;
        return true;
    }

    /**
     * 카드를 그릴 때마다 부른다.
     * @param planId     추천 계획 번호 (Plan 이 붙여 준다. 보석이 바뀌거나 새로 계산하면 바뀐다)
     * @param remaining  아직 더 넣어야 하는 추천 개수의 합 (바이올렛 + 오팔 + 아다만티움)
     * @return 횟수가 올랐으면 true (저장 필요)
     */
    static boolean observePlan(int planId, int remaining) {
        lastRemaining = remaining;
        if (planId != curPlan) {                 // 새 계획: 넣을 광채가 남아 있어야 '시작'으로 본다
            curPlan = planId;
            armed = remaining > 0 && planId != lastDoneId;
            return false;
        }
        if (remaining > 0) {
            if (planId != lastDoneId) armed = true;
            return false;
        }
        if (armed) {                             // 남은 개수가 0이 됨 = 추천 수에 도달
            armed = false;
            lastDoneId = planId;
            count++;
            return true;
        }
        return false;
    }

    static void add(int delta) {
        count = Math.max(0, count + delta);
    }

    static void reset() {
        count = 0;
    }

    /** 점검용: 지금 상태 한 줄 */
    static String describe(long now) {
        return "오늘(" + day + ") " + count + "회 · 표시 " + (show ? "켜짐" : "꺼짐") + " · 초기화 " + resetHour + "시"
                + " · 지금 계획 #" + curPlan + " 남은 " + (lastRemaining < 0 ? "?" : String.valueOf(lastRemaining)) + "개" + (armed ? " (끝나면 +1)" : "");
    }
}
