package kr.segong;

/**
 * 보석 하나를 "끝냈는지" 알아보는 곳.
 * 보석 하나를 "추천한 개수만큼 다 넣어서" 순도/광도/강도가 추천 수에 도달하면(남은 추천 개수가 0이 되면) 1번 끝낸 것으로 본다.
 * 광채를 하나 넣을 때마다 세지 않는다. 처음부터 넣을 게 없던 보석(이미 끝난 보석)도 세지 않는다.
 * 끝났을 때 observePlan 이 true 를 돌려주면 SegongClient 가 Journal(세공 기록)에 한 줄 더한다.
 * Minecraft 코드와 무관한 순수 로직.
 */
final class Tally {
    private Tally() {}

    private static int curPlan = -1;         // 지금 보고 있는 계획 번호
    private static boolean armed;            // 이 계획이 '넣을 광채가 남은 상태'로 시작했는지
    private static int lastDoneId = -1;      // 마지막으로 끝난 계획 번호 (같은 계획을 두 번 세지 않으려고)
    private static int lastRemaining = -1;   // 점검용: 마지막으로 본 남은 개수

    /**
     * 카드를 그릴 때마다 부른다.
     * @param planId     추천 계획 번호 (Plan 이 붙여 준다. 보석이 바뀌거나 새로 계산하면 바뀐다)
     * @param remaining  아직 더 넣어야 하는 추천 개수의 합 (바이올렛 + 오팔 + 아다만티움)
     * @return 이번에 보석 하나가 끝났으면 true (기록 필요)
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
            return true;
        }
        return false;
    }

    /** 점검용: 지금 상태 한 줄 */
    static String describe() {
        return "지금 계획 #" + curPlan + " 남은 " + (lastRemaining < 0 ? "?" : String.valueOf(lastRemaining)) + "개" + (armed ? " (끝나면 기록)" : "");
    }
}
