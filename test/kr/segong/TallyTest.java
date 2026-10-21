package kr.segong;
import java.time.LocalDateTime;
public class TallyTest {
  static int fails=0; static void check(boolean ok,String m){ if(!ok){fails++; System.out.println("  FAIL: "+m);} else System.out.println("  ok: "+m); }
  static void fresh(){ Tally.count=0; Tally.day="D1"; Plan.clear(); }
  public static void main(String[] x){
    // --- 순수 규칙 (계획 번호 + 남은 개수)
    fresh();
    boolean r;
    r=Tally.observePlan(101,3); check(!r&&Tally.count==0,"새 계획(남은 3): 아직 안 셈");
    r=Tally.observePlan(101,2); check(!r&&Tally.count==0,"하나 넣음(남은 2): 안 셈");
    r=Tally.observePlan(101,1); check(!r&&Tally.count==0,"또 넣음(남은 1): 안 셈");
    r=Tally.observePlan(101,0); check(r&&Tally.count==1,"추천 수에 도달(남은 0): 1회");
    r=Tally.observePlan(101,0); check(!r&&Tally.count==1,"계속 0이어도 또 세지 않음");
    r=Tally.observePlan(101,1); r|=Tally.observePlan(101,0); check(!r&&Tally.count==1,"읽기 흔들림으로 남은 개수가 튀어도 같은 계획은 두 번 안 셈");
    r=Tally.observePlan(102,0); check(!r&&Tally.count==1,"처음부터 넣을 게 없던 보석은 안 셈");
    r=Tally.observePlan(103,2); r=Tally.observePlan(103,0); check(r&&Tally.count==2,"다음 보석을 끝내면 2회");
    // 끝나기 전에 보석을 바꾸면 안 셈
    r=Tally.observePlan(104,4); r=Tally.observePlan(104,2); r=Tally.observePlan(105,0);
    check(Tally.count==2,"중간에 다른 보석(이미 끝난)으로 바뀌면 안 셈 count="+Tally.count);
    // 날짜
    Tally.count=5; Tally.day="D1";
    check(!Tally.rollover("D1")&&Tally.count==5,"같은 날은 그대로");
    check(Tally.rollover("D2")&&Tally.count==0&&Tally.day.equals("D2"),"날짜가 바뀌면 0부터");
    check(Tally.dayKey(LocalDateTime.of(2026,10,21,3,0),6).equals("2026-10-20"),"초기화 6시: 새벽 3시는 전날");
    check(Tally.dayKey(LocalDateTime.of(2026,10,21,6,0),6).equals("2026-10-21"),"초기화 6시: 6시 정각은 오늘");
    check(Tally.dayKey(LocalDateTime.of(2026,10,21,0,5),0).equals("2026-10-21"),"초기화 0시");
    Tally.add(-10); check(Tally.count==0,"빼기는 0 밑으로 안 내려감");
    Tally.add(3); Tally.add(-1); check(Tally.count==2,"더하기/빼기");
    Tally.reset(); check(Tally.count==0,"0으로");

    // --- 실제 계획(Plan)과 같이: 광채를 하나씩 넣는 흐름
    fresh(); long t=1000;
    int[] st={11,31,18}; long[] stock={184,149,248};
    long[] eff=new long[3];
    Calc.Result r0=Plan.resolve(st.clone(),212,"s",stock.clone(),eff,t);
    Tally.observePlan(r0.plan,r0.n);
    int[] todo={r0.a,r0.b,r0.c}; int total=r0.n;
    System.out.println("  계획 #"+r0.plan+" 넣을 개수 "+r0.a+"/"+r0.b+"/"+r0.c+" (총 "+total+")");
    check(total>0,"추천이 있음");
    int put=0; boolean earlyCount=false;
    for(int k=0;k<3;k++) for(int i=0;i<todo[k];i++){
      st[k]+=10; stock[k]-=1; t+=500; put++;
      Calc.Result rr=Plan.resolve(st.clone(),212,"s",stock.clone(),eff,t);
      Tally.observePlan(rr.plan,rr.n);
      if(put<total && Tally.count!=0) earlyCount=true;
      if(rr.plan!=r0.plan) { check(false,"계획 번호가 바뀌면 안 됨"); }
    }
    check(!earlyCount,"넣는 동안(마지막 전)에는 안 오름");
    check(Tally.count==1,"다 넣으면 정확히 1회 count="+Tally.count);
    // 광채를 UI 칸으로 옮겨 인벤토리가 줄어도(보석 수치는 그대로) 안 셈
    fresh(); t+=20000; st=new int[]{11,31,18}; stock=new long[]{184,149,248};
    Calc.Result q0=Plan.resolve(st.clone(),212,"s",stock.clone(),eff,t); Tally.observePlan(q0.plan,q0.n);
    stock=new long[]{180,149,248}; t+=300; Calc.Result q1=Plan.resolve(st.clone(),212,"s",stock.clone(),eff,t); Tally.observePlan(q1.plan,q1.n);
    check(Tally.count==0&&q1.plan==q0.plan,"인벤토리 광채만 줄고 보석 수치가 그대로면 안 셈 / 같은 계획");
    // 중간에 추천을 다시 계산(Plan.clear)해도 한 번만 셈
    for(int i=0;i<2;i++){ st[0]+=10; stock[0]-=1; t+=500; Calc.Result z=Plan.resolve(st.clone(),212,"s",stock.clone(),eff,t); Tally.observePlan(z.plan,z.n); }
    Plan.clear();
    int cnt=0; Calc.Result y=Plan.resolve(st.clone(),212,"s",stock.clone(),eff,t+500); Tally.observePlan(y.plan,y.n);
    int[] td={y.a,y.b,y.c};
    for(int k=0;k<3;k++) for(int i=0;i<td[k];i++){ st[k]+=10; stock[k]-=1; t+=500; Calc.Result z=Plan.resolve(st.clone(),212,"s",stock.clone(),eff,t); Tally.observePlan(z.plan,z.n); }
    check(Tally.count==1,"중간에 계획을 다시 세워도 끝나면 1회 count="+Tally.count);
    // 이미 끝난 보석을 다시 열면(10초 뒤 새 계획, 남은 0) 안 셈
    t+=20000; Calc.Result d0=Plan.resolve(st.clone(),212,"s",stock.clone(),eff,t); Tally.observePlan(d0.plan,d0.n);
    check(Tally.count==1&&d0.n==0,"끝난 보석을 다시 열어도 안 셈 (남은 "+d0.n+")");
    System.out.println(Tally.describe(t));
    System.out.println(fails==0?"ALL OK":"FAILS="+fails);
  }
}
