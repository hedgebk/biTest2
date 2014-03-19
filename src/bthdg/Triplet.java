package bthdg;

import java.text.DecimalFormat;
import java.util.Map;

public class Triplet {
    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD};
    public static final double LVL = 100.6;
    public static final DecimalFormat X_YYYY = new DecimalFormat("+0.0000;-0.0000");

    public static final Triangle T1 = new Triangle(Pair.LTC_USD, false, Pair.LTC_BTC, true, Pair.BTC_USD, true); // usd -> ltc -> btc -> usd
    public static final Triangle T2 = new Triangle(Pair.LTC_EUR, false, Pair.LTC_BTC, true, Pair.BTC_EUR, true); // eur -> ltc -> btc -> eur
    public static final Triangle T3 = new Triangle(Pair.LTC_USD, false, Pair.LTC_EUR, true, Pair.EUR_USD, true); // usd -> ltc -> eur -> usd
    public static final Triangle T4 = new Triangle(Pair.EUR_USD, true, Pair.BTC_USD, false, Pair.BTC_EUR, true); // eur -> usd -> btc -> eur

    public static void main(String[] args) {
        System.out.println("Started");
        Fetcher.LOG_LOADING = false;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
        try {
            while(true) {
                Map<Pair,TopData> tops = Fetcher.fetchTops(Exchange.BTCE, PAIRS);
//                Map<Pair,TradesData> trades = Fetcher.fetchTrades(Exchange.BTCE, PAIRS);

                TopData ltcBtcTop = tops.get(Pair.LTC_BTC);
                TopData btcUsdTop = tops.get(Pair.BTC_USD);
                TopData ltcUsdTop = tops.get(Pair.LTC_USD);
                TopData btcEurTop = tops.get(Pair.BTC_EUR);
                TopData ltcEurTop = tops.get(Pair.LTC_EUR);
                TopData eurUsdTop = tops.get(Pair.EUR_USD);

                //-------------------------------------------------
                double usdOut1  = calcMid(tops, T1, true);
                double usdOut2  = calcMkt(tops, T1, true);
                OnePegData peg1 = calcPeg(tops, T1, true);

                double usdOut3  = calcMid(tops, T1, false);
                double usdOut4  = calcMkt(tops, T1, false);
                OnePegData peg2 = calcPeg(tops, T1, false);

                //-------------------------------------------------
                double eurOut1  = calcMid(tops, T2, true);
                double eurOut2  = calcMkt(tops, T2, true);
                OnePegData peg3 = calcPeg(tops, T2, true);

                double eurOut3  = calcMid(tops, T2, false);
                double eurOut4  = calcMkt(tops, T2, false);
                OnePegData peg4 = calcPeg(tops, T2, false);

                //-------------------------------------------------
                double usdOut5  = calcMid(tops, T3, true);
                double usdOut6  = calcMkt(tops, T3, true);
                OnePegData peg5 = calcPeg(tops, T3, true);

                double usdOut7  = calcMid(tops, T3, false);
                double usdOut8  = calcMkt(tops, T3, false);
                OnePegData peg6 = calcPeg(tops, T3, false);

                //-------------------------------------------------
                // eur -> usd -> btc -> eur
                double eurOut5  = calcMid(tops, T4, true);
                double eurOut6  = calcMkt(tops, T4, true);
                OnePegData peg7 = calcPeg(tops, T4, true);

                double eurOut7  = calcMid(tops, T4, false);
                double eurOut8  = calcMkt(tops, T4, false);
                OnePegData peg8 = calcPeg(tops, T4, false);

                System.out.println(
                                   format(usdOut1) + " " + format(usdOut2) + " " + peg1.str() + " " +
                                   format(usdOut3) + " " + format(usdOut4) + " " + peg2.str() + " | " +
                                   format(eurOut1) + " " + format(eurOut2) + " " + peg3.str() + " " +
                                   format(eurOut3) + " " + format(eurOut4) + " " + peg4.str() + " | " +
                                   format(usdOut5) + " " + format(usdOut6) + " " + peg5.str() + " " +
                                   format(usdOut7) + " " + format(usdOut8) + " " + peg6.str() + " | " +
                                   format(eurOut5) + " " + format(eurOut6) + " " + peg7.str() + " " +
                                   format(eurOut7) + " " + format(eurOut8) + " " + peg8.str() + " " +
                                   ((usdOut1 > LVL) || (usdOut3 > LVL) || (eurOut1 > LVL) || (eurOut3 > LVL)
                                           || (usdOut5 > LVL) || (usdOut7 > LVL) || (eurOut5 > LVL) || (eurOut7 > LVL)? " *" : "") +
                                   ((usdOut2 > LVL) || (usdOut4 > LVL) || (eurOut2 > LVL) || (eurOut4 > LVL)
                                           || (usdOut6 > LVL) || (usdOut8 > LVL) || (eurOut6 > LVL) || (eurOut8 > LVL) ? "\t******************************" : "")
                );
//                System.out.println("=========================================================");

                Thread.sleep(3000);
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
    }

    private static OnePegData calcPeg(Map<Pair, TopData> tops, Triangle triangle, boolean forward) {
        return forward
            ? calcPeg(tops, triangle.m_pair1, triangle.m_forward1, triangle.m_pair2, triangle.m_forward2, triangle.m_pair3, triangle.m_forward3)
            : calcPeg(tops, triangle.m_pair3, !triangle.m_forward3, triangle.m_pair2, !triangle.m_forward2, triangle.m_pair1, !triangle.m_forward1);
    }

    private static OnePegData calcPeg(Map<Pair, TopData> tops, Pair pair1, boolean forward1, Pair pair2, boolean forward2, Pair pair3, boolean forward3) {
        return calcPeg(tops.get(pair1), forward1, tops.get(pair2), forward2, tops.get(pair3), forward3);
    }

    private static OnePegData calcPeg(TopData top1, boolean mul1, TopData top2, boolean mul2, TopData top3, boolean mul3) {
        double a1 = 100;
        double b1 = mulPeg(a1, top1, mul1);
        double c1 = mulMkt(b1, top2, mul2);
        double d1 = mulMkt(c1, top3, mul3);

        double a2 = 100;
        double b2 = mulMkt(a2, top1, mul1);
        double c2 = mulPeg(b2, top2, mul2);
        double d2 = mulMkt(c2, top3, mul3);

        double a3 = 100;
        double b3 = mulMkt(a3, top1, mul1);
        double c3 = mulMkt(b3, top2, mul2);
        double d3 = mulPeg(c3, top3, mul3);

        int indx;
        double max;
        if (d1 > d2) {
            if (d3 > d1) {
                max = d3;
                indx = 3;
            } else {
                max = d1;
                indx = 1;
            }
        } else {
            if (d3 > d2) {
                max = d3;
                indx = 3;
            } else {
                max = d2;
                indx = 3;
            }
        }

        return new OnePegData(indx, max);
    }

    private static double calcMkt(Map<Pair, TopData> tops, Triangle triangle, boolean forward) {
        return forward
            ? calcMkt(tops, triangle.m_pair1, triangle.m_forward1, triangle.m_pair2, triangle.m_forward2, triangle.m_pair3, triangle.m_forward3)
            : calcMkt(tops, triangle.m_pair3, !triangle.m_forward3, triangle.m_pair2, !triangle.m_forward2, triangle.m_pair1, !triangle.m_forward1);
    }

    private static double calcMkt(Map<Pair, TopData> tops, Pair pair1, boolean forward1, Pair pair2, boolean forward2, Pair pair3, boolean forward3) {
        return calcMkt(tops.get(pair1), forward1, tops.get(pair2), forward2, tops.get(pair3), forward3);
    }

    private static double calcMkt(TopData top1, boolean mul1, TopData top2, boolean mul2, TopData top3, boolean mul3) {
        double one = 100;
        double two = mulMkt(one, top1, mul1);
        double three = mulMkt(two, top2, mul2);
        double ret = mulMkt(three, top3, mul3);
        return ret;
    }

    private static double calcMid(Map<Pair, TopData> tops, Triangle triangle, boolean forward) {
        return forward
            ? calcMid(tops, triangle.m_pair1, triangle.m_forward1, triangle.m_pair2, triangle.m_forward2, triangle.m_pair3, triangle.m_forward3)
            : calcMid(tops, triangle.m_pair3, !triangle.m_forward3, triangle.m_pair2, !triangle.m_forward2, triangle.m_pair1, !triangle.m_forward1);
    }

    private static double calcMid(Map<Pair, TopData> tops, Pair pair1, boolean forward1, Pair pair2, boolean forward2, Pair pair3, boolean forward3) {
        return calcMid(tops.get(pair1), forward1, tops.get(pair2), forward2, tops.get(pair3), forward3);
    }

    private static double calcMid(TopData top1, boolean mul1, TopData top2, boolean mul2, TopData top3, boolean mul3) {
        double one = 100;
        double two = mulMid(one, top1, mul1);
        double three = mulMid(two, top2, mul2);
        double ret = mulMid(three, top3, mul3);
        return ret;
    }

    private static double mulMid(double in, TopData top, boolean mul) {
        return mul ? in * top.getMid() : in / top.getMid();
    }

    private static double mulMkt(double in, TopData top, boolean mul) {
        return mul ? in * top.m_bid : in / top.m_ask; // ASK > BID
    }

    private static double mulPeg(double in, TopData top, boolean mul) {
        return mul ? in * top.m_ask : in / top.m_bid; // ASK > BID
    }

    private static String format(double usdOut) {
        double val = usdOut - 100;
        return Utils.padLeft(X_YYYY.format(val), 7);
    }

    private static class OnePegData {
        private int m_indx;
        private double m_max;

        public OnePegData(int indx, double max) {
            m_indx = indx;
            m_max = max;
        }

        public String str() {
            if (m_max > LVL) {
                return m_indx + ":" + format(m_max);
            }
            return "         ";
        }
    }

    private static class Triangle {
        private Pair m_pair1;
        private Pair m_pair2;
        private Pair m_pair3;
        private boolean m_forward1;
        private boolean m_forward2;
        private boolean m_forward3;

        public Triangle(Pair pair1, boolean forward1, Pair pair2, boolean forward2, Pair pair3, boolean forward3) {
            m_pair1 = pair1;
            m_forward1 = forward1;
            m_pair2 = pair2;
            m_forward2 = forward2;
            m_pair3 = pair3;
            m_forward3 = forward3;
        }
    }
}
