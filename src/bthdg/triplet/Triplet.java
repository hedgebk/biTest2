package bthdg.triplet;

import bthdg.*;

import java.text.DecimalFormat;
import java.util.Map;

public class Triplet {
    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD};
    public static final double LVL = 100.6;
    public static final DecimalFormat X_YYY = new DecimalFormat("+0.000;-0.000");

    public static final Triangle T1 = new Triangle(Pair.LTC_USD, false, Pair.LTC_BTC, true,  Pair.BTC_USD, true); // usd -> ltc -> btc -> usd
    public static final Triangle T2 = new Triangle(Pair.LTC_EUR, false, Pair.LTC_BTC, true,  Pair.BTC_EUR, true); // eur -> ltc -> btc -> eur
    public static final Triangle T3 = new Triangle(Pair.LTC_USD, false, Pair.LTC_EUR, true,  Pair.EUR_USD, true); // usd -> ltc -> eur -> usd
    public static final Triangle T4 = new Triangle(Pair.EUR_USD, true,  Pair.BTC_USD, false, Pair.BTC_EUR, true); // eur -> usd -> btc -> eur

    public static void main(String[] args) {
        System.out.println("Started");
        Fetcher.LOG_LOADING = false;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
        try {
            while(true) {
                Map<Pair,TopData> tops = Fetcher.fetchTops(Exchange.BTCE, PAIRS);
//                Map<Pair,TradesData> trades = Fetcher.fetchTrades(Exchange.BTCE, PAIRS);

                TriangleData t1 = calc(tops, T1);
                TriangleData t2 = calc(tops, T2);
                TriangleData t3 = calc(tops, T3);
                TriangleData t4 = calc(tops, T4);

                System.out.println(
                                    t1.str() +
                                    t2.str() +
                                    t3.str() +
                                    t4.str() +
                                    (t1.mktCrossLvl() || t2.mktCrossLvl() || t3.mktCrossLvl() || t4.mktCrossLvl() ? "\t******************************" : "")
                );
//                System.out.println("=========================================================");

                Thread.sleep(3000);
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
    }

    private static TriangleData calc(Map<Pair, TopData> tops, Triangle triangle) {
        TriangleRotationData trf = calc(tops, triangle, true);
        TriangleRotationData trb = calc(tops, triangle, false);

        return new TriangleData(trf, trb);
    }

    private static TriangleRotationData calc(Map<Pair, TopData> tops, Triangle triangle, boolean forward) {
        double mid  = calcMid(tops, triangle, forward);
        double mkt = calcMkt(tops, triangle, forward);
        OnePegData peg = calcPeg(tops, triangle, forward);
        return new TriangleRotationData(triangle, forward, mid, mkt, peg);
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
        return Utils.padLeft(X_YYY.format(val), 6);
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
            return "        ";
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

    private static class TriangleRotationData {
        private Triangle m_triangle;
        private boolean m_forward;
        private double m_mid;
        private double m_mkt;
        private OnePegData m_peg;

        public TriangleRotationData(Triangle triangle, boolean forward, double mid, double mkt, OnePegData peg) {
            m_triangle = triangle;
            m_forward = forward;
            m_mid = mid;
            m_mkt = mkt;
            m_peg = peg;
        }

        public String str() {
            return format(m_mid) + " " + format(m_mkt) + " " + m_peg.str();
        }

        public boolean midCrossLvl() {
            return (m_mid > LVL) || (m_mid > LVL);
        }

        public boolean mktCrossLvl() {
            return (m_mkt > LVL) || (m_mkt > LVL);
        }
    }

    private static class TriangleData {
        private TriangleRotationData m_forward;
        private TriangleRotationData m_backward;

        public TriangleData(TriangleRotationData forward, TriangleRotationData backward) {
            m_forward = forward;
            m_backward = backward;
        }

        public String str() {
            return m_forward.str() + " " + m_backward.str() + " " +
                    ( m_forward.midCrossLvl() || m_backward.midCrossLvl() ? "*" : "|" ) + " ";
        }

        public boolean mktCrossLvl() {
            return m_forward.mktCrossLvl() || m_backward.mktCrossLvl();
        }
    }
}
