import X.*; import java.util.*;

import app.morphe.extension.facebook.marketplace.MarketplaceOnly;
public class Test {
  static int fails=0;
  static void run(C2GO c,boolean hide){ MarketplaceOnly.capture(c); if(hide) MarketplaceOnly.onStartingTabDeterminedHideBar(); else MarketplaceOnly.onStartingTabDetermined(); }
  static void check(boolean ok,String m){ System.out.println((ok?"PASS ":"FAIL ")+m); if(!ok)fails++; }
  public static void main(String[] a){
    long MP=1606854132932955L, FEED=4748854339L;
    // 1: marketplace in the middle, feed selected -> switches by ID not position
    C2GO c=new C2GO(new C23681Hz(Arrays.asList(new X.FakeTab(FEED),new X.FakeTab(11L),new X.FakeTab(MP),new X.FakeTab(22L))));
    c.A01=0; c.A02=FEED;
    run(c,false);
    check(c.A01==2 && c.A02==MP,"selects Marketplace by id at index 2 (A01="+c.A01+")");
    check(c.A09.vis==0,"tab bar untouched when hide option off");
    // 2: different order
    c=new C2GO(new C23681Hz(Arrays.asList(new X.FakeTab(MP),new X.FakeTab(FEED))));
    c.A01=1; c.A02=FEED; run(c,true);
    check(c.A01==0 && c.A02==MP,"works when Marketplace is first");
    check(c.A09.vis==8,"tab bar hidden (GONE) in hide variant");
    // 3: marketplace absent -> no change at all
    c=new C2GO(new C23681Hz(Arrays.asList(new X.FakeTab(FEED),new X.FakeTab(11L))));
    c.A01=1; c.A02=11L; run(c,true);
    check(c.A01==1 && c.A02==11L && c.A09.vis==0,"no-op (incl. bar left visible) when Marketplace tab missing");
    // 4: garbage / null never throws
    MarketplaceOnly.capture(null); MarketplaceOnly.onStartingTabDetermined();
    MarketplaceOnly.capture(new Object()); MarketplaceOnly.onStartingTabDetermined();
    MarketplaceOnly.onStartingTabDeterminedHideBar();
    check(true,"null / wrong object does not throw");
    // 5: idempotent (A06 runs again on reset)
    c=new C2GO(new C23681Hz(Arrays.asList(new X.FakeTab(FEED),new X.FakeTab(MP))));
    run(c,false); run(c,false);
    check(c.A01==1,"idempotent on repeat call");
    System.out.println(fails==0?"ALL PASS":"FAILURES: "+fails); System.exit(fails==0?0:1);
  }
}
