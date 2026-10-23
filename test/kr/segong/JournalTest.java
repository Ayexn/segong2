package kr.segong;

import java.util.*;

public class JournalTest {
    static int fails = 0, checks = 0;
    static void eq(String what, Object got, Object want) {
        checks++;
        if (!Objects.equals(got, want)) { fails++; System.out.println("FAIL " + what + ": 받은 값 [" + got + "] / 기대 [" + want + "]"); }
    }
    static List<String> L(String... s) { return Arrays.asList(s); }

    public static void main(String[] a) {
        // ---- 이름 · 등급 읽기
        List<String> t1 = L("COMMON 키론", "코룸 정동석을 감정한 보석입니다.", "◇ 순도 : 20%", "◇ 광도 : 19%", "◇ 강도 : 47%");
        eq("이름1", Journal.nameOf(t1), "키론"); eq("등급1", Journal.gradeOf(t1, 0), "Common");
        List<String> t2 = L(" NORMAL 테라온", "설명 .", "순도 : 50%");
        eq("이름2(특수문자)", Journal.nameOf(t2), "테라온"); eq("등급2", Journal.gradeOf(t2, 0), "Normal");
        List<String> t3 = L("§aRARE §f실바니움", "순도 : 1%");
        eq("이름3(색코드)", Journal.nameOf(t3), "실바니움"); eq("등급3", Journal.gradeOf(t3, 0), "Rare");
        List<String> t4 = L("COMMON", "라온", "순도 : 1%");
        eq("이름4(등급만 있는 줄)", Journal.nameOf(t4), "라온");
        eq("이름5(없음)", Journal.nameOf(L("순도 : 1%", "광도 : 2%", "강도 : 3%")), Journal.UNKNOWN);
        eq("이름6(빈 목록)", Journal.nameOf(new ArrayList<>()), Journal.UNKNOWN);
        eq("이름7(긴 설명은 건너뜀)", Journal.nameOf(L("이 보석은 아주아주 긴 설명 문장입니다 정말로", "제피르")), "제피르");
        eq("이름8(영문 소문자 등급)", Journal.nameOf(L("epic 아스트랄")), "아스트랄");
        eq("이름9(탭/줄바꿈)", Journal.nameOf(L("RARE\t피로시아\n")), "피로시아");
        eq("등급 한도로 짐작 200", Journal.gradeOf(L("키론"), 200), "Common");
        eq("등급 한도로 짐작 212", Journal.gradeOf(L("키론"), 212), "Normal");
        eq("등급 한도로 짐작 236", Journal.gradeOf(L("키론"), 236), "Rare");
        eq("등급 모름", Journal.gradeOf(L("키론"), 0), "등급?");
        eq("등급 Epic", Journal.gradeOf(L("EPIC 세피아"), 0), "Epic");
        eq("등급 Mythic", Journal.gradeOf(L("MYTHIC 넬트"), 0), "Mythic");

        // ---- 더하기 · 정렬 · 합계
        Journal.clear();
        String d1 = "2026-10-21", d2 = "2026-10-22", d3 = "2026-10-23";
        Journal.add(d1, "키론", "Common"); Journal.add(d1, "키론", "Common"); Journal.add(d1, "테라온", "Rare");
        Journal.add(d2, "키론", "Normal"); Journal.add(d2, "키론", "Common");
        for (int i = 0; i < 3; i++) Journal.add(d3, "테라온", "Rare");
        Journal.add(d3, "키론", "Common");
        eq("오늘 합계", Journal.total(d3), 4);
        eq("전체 합계", Journal.totalAll(), 9);
        eq("날짜 수", Journal.dayCount(), 3);
        List<Journal.Row> r3 = Journal.rows(d3);
        eq("정렬 1등", r3.get(0).text(), "테라온 Rare 3회");
        eq("정렬 2등", r3.get(1).text(), "키론 Common 1회");
        List<Journal.Row> all = Journal.allRows();
        eq("전체 1등 (키론 Common 4)", all.get(0).text(), "키론 Common 4회");
        eq("전체 종류 수", all.size(), 3);
        eq("같은 횟수면 등급 순서", Journal.rows(d2).get(0).text(), "키론 Common 1회");
        eq("같은 이름 다른 등급은 따로", Journal.rows(d2).size(), 2);
        eq("기록 없는 날", Journal.total("2026-01-01"), 0);

        // ---- 취소
        eq("마지막 날짜", Journal.lastDay(), d3);
        eq("취소 설명", Journal.undoLast(), "키론 Common 1회");
        eq("취소 뒤 합계", Journal.total(d3), 3);
        eq("키론 Common 줄이 사라짐", Journal.rows(d3).size(), 1);
        eq("두 번 연속 취소는 안 됨", Journal.undoLast(), null);
        Journal.add("2026-10-24", "라온", "Rare");
        eq("하루 한 건만 있던 날 취소", Journal.undoLast(), "라온 Rare 1회");
        eq("빈 날짜는 사라짐", Journal.dayCount(), 3);

        // ---- 파일 글자 왕복
        String ser = Journal.serialize();
        Journal.clear();
        eq("지운 뒤", Journal.totalAll(), 0);
        eq("읽은 줄 수", Journal.parse(Arrays.asList(ser.split("\n"))), 5);
        eq("왕복 뒤 전체", Journal.totalAll(), 8);
        eq("왕복 뒤 오늘", Journal.total(d3), 3);
        eq("왕복 뒤 같은 글자", Journal.serialize(), ser);
        int before = Journal.totalAll();
        eq("이상한 줄은 건너뜀", Journal.parse(L("망가진 줄", "2026-13-xx\ta\tb\t1", "2026-10-21\tRare\t키론\tabc", "2026-10-21\tRare\t키론\t0", "2026-10-21\tRare\t키론\t-3", "2026-10-21\t\t키론\t1", "", null, "2026-10-21\tRare\t키론\t2\t3")), 0);
        eq("이상한 줄은 무시", Journal.totalAll(), before);
        eq("정상 줄 합침", Journal.parse(L("2026-10-21\tRare\t키론\t2")), 1);
        eq("합침 결과", Journal.totalAll(), before + 2);

        // ---- 오래된 날짜 지우기
        Journal.clear();
        Journal.add("2024-01-01", "키론", "Common");
        Journal.add("2026-10-01", "키론", "Common");
        eq("오래된 것 지움", Journal.prune("2026-10-23"), true);
        eq("남은 날짜", Journal.dayCount(), 1);
        eq("지울 게 없으면 false", Journal.prune("2026-10-23"), false);
        eq("이상한 오늘 글자는 무시", Journal.prune("abc"), false);

        // ---- 날짜 글자
        eq("요일 목", Journal.shortDay("2026-10-23"), "10/23(금)");
        eq("요일 수", Journal.shortDay("2026-10-22"), "10/22(목)");
        eq("이상한 날짜 그대로", Journal.shortDay("???"), "???");
        eq("날짜 입력 고치기", Journal.parseDay("2026-10-2"), "2026-10-02");
        eq("날짜 입력 그대로", Journal.parseDay("2026-10-22"), "2026-10-22");
        eq("날짜 입력 틀림", Journal.parseDay("2026-13-01"), null);
        eq("날짜 입력 틀림2", Journal.parseDay("어제"), null);

        // ---- 하루가 바뀌는 시각
        eq("초기화 6시: 새벽 3시는 전날", Journal.dayKey(java.time.LocalDateTime.of(2026, 10, 21, 3, 0), 6), "2026-10-20");
        eq("초기화 6시: 6시 정각은 오늘", Journal.dayKey(java.time.LocalDateTime.of(2026, 10, 21, 6, 0), 6), "2026-10-21");
        eq("초기화 0시", Journal.dayKey(java.time.LocalDateTime.of(2026, 10, 21, 0, 5), 0), "2026-10-21");
        eq("이상한 시각은 0~23 으로", Journal.dayKey(java.time.LocalDateTime.of(2026, 10, 21, 0, 5), 99), "2026-10-20");
        eq("며칠 전", Journal.daysAgo("2026-10-23", 6), "2026-10-17");
        eq("달 넘어 며칠 전", Journal.daysAgo("2026-03-02", 3), "2026-02-27");
        eq("며칠 전(이상한 글자)", Journal.daysAgo("abc", 3), "abc");
        eq("기간 일수", Journal.spanDays("2026-10-17", "2026-10-23"), 7);
        eq("기간 일수(하루)", Journal.spanDays("2026-10-23", "2026-10-23"), 1);
        eq("기간 일수(거꾸로는 1)", Journal.spanDays("2026-10-25", "2026-10-23"), 1);
        eq("기간 일수(이상한 글자)", Journal.spanDays("x", "y"), 1);
        eq("짧은 날짜", Journal.tinyDay("2026-10-23"), "23(금)");
        eq("일 숫자", Journal.dayOfMonth("2026-10-03"), "3");

        // ---- 통계 창 계산 (기간 합계 · 등급 칸 · 보석별 합계)
        Journal.clear();
        Journal.add("2026-10-17", "키론", "Common");
        Journal.add("2026-10-17", "키론", "Common");
        Journal.add("2026-10-19", "키론", "Rare");
        Journal.add("2026-10-19", "테라온", "Epic");
        Journal.add("2026-10-19", "테라온", "Legendary");
        Journal.add("2026-10-21", "테라온", "Rare");
        Journal.add("2026-10-21", "테라온", "Rare");
        Journal.add("2026-10-21", "테라온", "Rare");
        Journal.add("2026-10-23", "라온", "등급?");
        Journal.add("2026-10-23", "라온", "Normal");
        eq("등급 칸 Common", Journal.col("Common"), 0);
        eq("등급 칸 Normal", Journal.col("Normal"), 1);
        eq("등급 칸 Rare", Journal.col("Rare"), 2);
        eq("등급 칸 Epic", Journal.col("Epic"), 3);
        eq("등급 칸 Legendary", Journal.col("Legendary"), 3);
        eq("등급 칸 Mythic", Journal.col("Mythic"), 3);
        eq("등급 칸 모름", Journal.col("등급?"), 3);
        eq("기간 합계 전체", Journal.totalBetween("2026-10-17", "2026-10-23"), 10);
        eq("기간 합계 중간", Journal.totalBetween("2026-10-18", "2026-10-21"), 6);
        eq("기간 합계 거꾸로", Journal.totalBetween("2026-10-23", "2026-10-17"), 0);
        eq("기간 합계 하루", Journal.totalBetween("2026-10-21", "2026-10-21"), 3);
        eq("기록한 날 수", Journal.activeDays("2026-10-17", "2026-10-23"), 4);
        eq("기록한 날 수(좁게)", Journal.activeDays("2026-10-20", "2026-10-22"), 1);
        eq("가장 오래된 날", Journal.firstDay(), "2026-10-17");
        List<Journal.Sum> sm = Journal.sums("2026-10-17", "2026-10-23");
        eq("보석 종류 수", sm.size(), 3);
        eq("1등 보석 (테라온 5)", sm.get(0).name + " " + sm.get(0).total, "테라온 5");
        eq("테라온 칸별", Arrays.toString(sm.get(0).c), "[0, 0, 3, 2]");
        eq("2등 보석 (키론 3)", sm.get(1).name + " " + sm.get(1).total, "키론 3");
        eq("키론 칸별", Arrays.toString(sm.get(1).c), "[2, 0, 1, 0]");
        eq("3등 보석 (라온 2)", sm.get(2).name + " " + sm.get(2).total, "라온 2");
        eq("라온 칸별", Arrays.toString(sm.get(2).c), "[0, 1, 0, 1]");
        eq("기간 밖은 제외", Journal.sums("2026-10-20", "2026-10-22").size(), 1);
        eq("기간 거꾸로는 빈 목록", Journal.sums("2026-10-23", "2026-10-17").size(), 0);

        // ---- 통계 창에서 고치기 (+ / -)
        Journal.clear();
        eq("기록이 없으면 가장 오래된 날은 null", Journal.firstDay(), null);
        eq("기록이 없으면 보석별 합계도 빈 목록", Journal.sums("2026-01-01", "2026-12-31").size(), 0);
        Journal.add("2026-10-22", "키론", "Common");
        eq("+1", Journal.adjust("2026-10-22", "키론", "Common", 1), true);
        eq("+1 뒤 횟수", Journal.total("2026-10-22"), 2);
        eq("-1", Journal.adjust("2026-10-22", "키론", "Common", -1), true);
        eq("-1 뒤 횟수", Journal.total("2026-10-22"), 1);
        eq("-1 로 0 이 되면 줄이 사라짐", Journal.adjust("2026-10-22", "키론", "Common", -1), true);
        eq("줄 사라진 뒤", Journal.rows("2026-10-22").size(), 0);
        eq("빈 날짜도 사라짐", Journal.dayCount(), 0);
        eq("없는 줄을 빼려 하면 false", Journal.adjust("2026-10-22", "키론", "Common", -1), false);
        eq("0 만큼은 아무 일 없음", Journal.adjust("2026-10-22", "키론", "Common", 0), false);
        eq("없는 줄에 + 하면 새로 생김", Journal.adjust("2026-10-22", "라온", "Rare", 1), true);
        eq("새로 생긴 줄", Journal.rows("2026-10-22").get(0).text(), "라온 Rare 1회");
        for (int i = 0; i < 12000; i++) Journal.adjust("2026-10-22", "라온", "Rare", 1);
        eq("위쪽 한도 9999", Journal.rows("2026-10-22").get(0).n, 9999);
        Journal.clear();
        Journal.add("2026-10-23", "키론", "Common");
        Journal.adjust("2026-10-23", "키론", "Common", -1);
        eq("방금 기록을 손으로 지운 뒤 취소는 안 됨", Journal.undoLast(), null);
        eq("취소 뒤 방금 기록 없음", Journal.lastDay(), "");

        System.out.println(fails == 0 ? "ALL OK (" + checks + ")" : "실패 " + fails + "건 / " + checks);
        System.exit(fails == 0 ? 0 : 1);
    }
}
