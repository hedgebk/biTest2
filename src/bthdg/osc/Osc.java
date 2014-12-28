package bthdg.osc;

import bthdg.exch.Pair;
import bthdg.exch.TradeData;
import bthdg.util.Utils;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;
import bthdg.ws.OkCoinWs;

public class Osc {
    public static int LEN1 = 14;
    public static int LEN2 = 14;
    public static int K = 3;
    public static int D = 3;
    private static final long BAR_SIZE = Utils.toMillis("15s");
    public static int PHASES = 3;

    private OscCalculator[] m_calcs = new OscCalculator[PHASES];

    public static void main(String[] args) {
        new Osc().run();
        try {
            Thread thread = Thread.currentThread();
            synchronized (thread) {
                thread.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void run() {
        IWs ws = OkCoinWs.create();
//        HuobiWs.main(args);
//        BtcnWs.main(args);
//        BitstampWs.main(args);

        for (int i = 0; i < PHASES; i++) {
            long offset = BAR_SIZE / PHASES * i;
            m_calcs[i] = new OscCalculator(LEN1, LEN2, K, D, BAR_SIZE, offset) {
                @Override public void fine(long stamp, double stoch1, double stoch2) {
                    System.out.println(" fine " + stamp + ": " + stoch1 + "; " + stoch2);
                }

                @Override public void bar(Long barStart, double stoch1, double stoch2) {
                    System.out.println(" ------------bar " + barStart + ": " + stoch1 + "; " + stoch2);
                }
            };
        }

        ws.subscribeTrades(Pair.BTC_CNH, new ITradesListener() {
            @Override public void onTrade(TradeData tdata) {
                System.out.println("got Trade=" + tdata);
                for (int i = 0; i < PHASES; i++) {
                    m_calcs[i].update(tdata.m_timestamp, tdata.m_price);
                }
            }
        });
    }
}
