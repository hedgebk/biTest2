package bthdg;

import java.util.Map;

public class Triplet {
    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD};

    public static void main(String[] args) {
        System.out.println("Started");
        Fetcher.LOG_LOADING = false;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
        try {
            while(true) {
                Map<Pair,TopData> tops = Fetcher.fetchTops(Exchange.BTCE, PAIRS);

                TopData ltcBtcTop = tops.get(Pair.LTC_BTC);
                TopData btcUsdTop = tops.get(Pair.BTC_USD);
                TopData ltcUsdTop = tops.get(Pair.LTC_USD);
                TopData btcEurTop = tops.get(Pair.BTC_EUR);
                TopData ltcEurTop = tops.get(Pair.LTC_EUR);
                TopData eurUsdTop = tops.get(Pair.EUR_USD);
//                System.out.println("btcUsdTop: " + btcUsdTop);

                //-------------------------------------------------
                // usd -> ltc -> btc -> usd
                double usdOut  = calcMid(ltcUsdTop, false, ltcBtcTop, true, btcUsdTop, true);
                double usdOut2 = calcMkt(ltcUsdTop, false, ltcBtcTop, true, btcUsdTop, true);

                double usdOut3 = calcMid(btcUsdTop, false, ltcBtcTop, false, ltcUsdTop, true);
                double usdOut4 = calcMkt(btcUsdTop, false, ltcBtcTop, false, ltcUsdTop, true);

                //-------------------------------------------------
                // eur -> ltc -> btc -> eur
                double eurOut  = calcMid(ltcEurTop, false, ltcBtcTop, true, btcEurTop, true);
                double eurOut2 = calcMkt(ltcEurTop, false, ltcBtcTop, true, btcEurTop, true);

                double eurOut3 = calcMid(btcEurTop, false, ltcBtcTop, false, ltcEurTop, true);
                double eurOut4 = calcMkt(btcEurTop, false, ltcBtcTop, false, ltcEurTop, true);

                //-------------------------------------------------
                // usd -> ltc -> eur -> usd
                double usdOut5 = calcMid(ltcUsdTop, false, ltcEurTop, true, eurUsdTop, true);
                double usdOut6 = calcMkt(ltcUsdTop, false, ltcEurTop, true, eurUsdTop, true);

                double usdOut7 = calcMid(eurUsdTop, false, ltcEurTop, false, ltcUsdTop, true);
                double usdOut8 = calcMkt(eurUsdTop, false, ltcEurTop, false, ltcUsdTop, true);

                //-------------------------------------------------
                // eur -> usd -> btc -> eur
                double eurOut5 = calcMid(eurUsdTop, true, btcUsdTop, false, btcEurTop, true);
                double eurOut6 = calcMkt(eurUsdTop, true, btcUsdTop, false, btcEurTop, true);

                double eurOut7 = calcMid(btcEurTop, false, btcUsdTop, true, eurUsdTop, false);
                double eurOut8 = calcMkt(btcEurTop, false, btcUsdTop, true, eurUsdTop, false);

                System.out.println(
                                   format(usdOut) + " " + format(usdOut2) + " " +
                                   format(usdOut3) + " " + format(usdOut4) + " | " +
                                   format(eurOut) + " " + format(eurOut2) + " " +
                                   format(eurOut3) + " " + format(eurOut4) + " | " +
                                   format(usdOut5) + " " + format(usdOut6) + " " +
                                   format(usdOut7) + " " + format(usdOut8) + " | " +
                                   format(eurOut5) + " " + format(eurOut6) + " " +
                                   format(eurOut7) + " " + format(eurOut8) + "  " +
                                   ((usdOut > 100.6) || (usdOut3 > 100.6) || (eurOut > 100.6) || (eurOut3 > 100.6)
                                           || (usdOut5 > 100.6) || (usdOut7 > 100.6) || (eurOut5 > 100.6) || (eurOut7 > 100.6)? " *" : "") +
                                   ((usdOut2 > 100.6) || (usdOut4 > 100.6) || (eurOut2 > 100.6) || (eurOut4 > 100.6)
                                           || (usdOut6 > 100.6) || (usdOut8 > 100.6) || (eurOut6 > 100.6) || (eurOut8 > 100.6) ? "\t******************************" : "")
                );
//                System.out.println("=========================================================");

                Thread.sleep(4000);
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
    }

    private static double calcMkt(TopData top1, boolean mul1, TopData top2, boolean mul2, TopData top3, boolean mul3) {
        double one = 100;
        double two = mul2(one, top1, mul1);
        double three = mul2(two, top2, mul2);
        double ret = mul2(three, top3, mul3);
        return ret;
    }

    private static double calcMid(TopData top1, boolean mul1, TopData top2, boolean mul2, TopData top3, boolean mul3) {
        double one = 100;
        double two = mul(one, top1, mul1);
        double three = mul(two, top2, mul2);
        double ret = mul(three, top3, mul3);
        return ret;
    }

    private static double mul(double in, TopData top, boolean mul) {
        return mul ? in * top.getMid() : in / top.getMid();
    }

    private static double mul2(double in, TopData top, boolean mul) {
        return mul ? in * top.m_bid : in / top.m_ask; // ASK > BID
    }

    private static String format(double usdOut) {
        return Utils.padLeft(Fetcher.format(usdOut), 8);
    }
}
