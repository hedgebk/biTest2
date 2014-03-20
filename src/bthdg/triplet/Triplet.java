package bthdg.triplet;

import bthdg.*;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Map;

public class Triplet {
    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD};
    public static final double LVL = 100.6;
    public static final DecimalFormat X_YYY = new DecimalFormat("+0.000;-0.000");

    public static final Triangle T1 = new Triangle(Pair.LTC_USD, true,  Pair.LTC_BTC, false, Pair.BTC_USD, false); // usd -> ltc -> btc -> usd
    public static final Triangle T2 = new Triangle(Pair.LTC_EUR, true,  Pair.LTC_BTC, false, Pair.BTC_EUR, false); // eur -> ltc -> btc -> eur
    public static final Triangle T3 = new Triangle(Pair.LTC_USD, true,  Pair.LTC_EUR, false, Pair.EUR_USD, false); // usd -> ltc -> eur -> usd
    public static final Triangle T4 = new Triangle(Pair.EUR_USD, false, Pair.BTC_USD, true,  Pair.BTC_EUR, false); // eur -> usd -> btc -> eur

    public static final Triangle[] TRIANGLES = new Triangle[]{T1, T2, T3, T4};

    public static void main(String[] args) {
        System.out.println("Started");
        Fetcher.LOG_LOADING = false;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
        try {
            TriangleRotationData rotation = null;
            while (true) {
                IterationData iData = new IterationData();

                Map<Pair, TopData> tops = iData.getTops();
//                Map<Pair, TradesData> trades = iData.getTrades();

                TrianglesData triangles = calc(tops);
                String str = triangles.str();

                TriangleRotationData best = triangles.findBest();
                if (best != null) {
                    double maxPeg = best.maxPeg();
                    if (maxPeg > LVL) {
                        OnePegData peg = best.m_peg;
                        int indx = peg.m_indx;
                        Triangle triangle = best.m_triangle;
                        String name = triangle.name();

                        PairDirection pairDirection = triangle.get(indx);
                        Pair pair = pairDirection.m_pair;
                        boolean direction = pairDirection.m_forward;
                        TopData topData = tops.get(pair);

                        String start = pairDirection.getName();

                        str += "\n#### best: " + format(maxPeg) + "; name=" + name + ", indx=" + indx +
                                ", start=" + start + ", pair: " + pair + ", direction=" + direction + "; top: " + topData;

                        // todo: check account first, and available funds
                        AccountData account = iData.getAccount();

                        rotation = best;

//                        OrderData order = new OrderData(Pair.BTC_USD, OrderSide.BUY, buyPrice, amount);
//                        placeOrder(order);
                    }
                }
                System.out.println(str);

                Thread.sleep(4000);
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
    }

    public static boolean placeOrder(OrderData orderData) {
        return placeOrder(orderData, OrderState.BRACKET_PLACED);
    }

    public static boolean placeOrder(OrderData orderData, OrderState state) {
        // todo: implement
//        log("placeOrder(" + m_exchange.m_name + ") not implemented yet: " + orderData);

        boolean success = true;        // m_account.allocateOrder(orderData);
        if(success) {
            // todo: pass to exch.baseExch if needed
            orderData.m_status = OrderStatus.SUBMITTED;
            orderData.m_state = state;
        } else {
//            log("account allocateOrder unsuccessful: " + orderData + ", account: " + m_account);
        }
        return success;
    }


    private static TrianglesData calc(Map<Pair, TopData> tops) {
        TrianglesData ret = new TrianglesData(TRIANGLES.length);
        for (Triangle tr : TRIANGLES) {
            TriangleData t = calc(tops, tr);
            ret.add(t);
        }
        return ret;
    }

    private static TriangleData calc(Map<Pair, TopData> tops, Triangle triangle) {
        TriangleRotationData trf = calc(tops, triangle, true);
        TriangleRotationData trb = calc(tops, triangle, false);

        return new TriangleData(trf, trb);
    }

    private static TriangleRotationData calc(Map<Pair, TopData> tops, Triangle triangle, boolean forward) {
        double mid = calcMid(tops, triangle, forward);
        double mkt = calcMkt(tops, triangle, forward);
        OnePegData peg = calcPeg(tops, triangle, forward);
        return new TriangleRotationData(triangle, forward, mid, mkt, peg);
    }

    private static double calcMkt(Map<Pair, TopData> tops, Triangle triangle, boolean forward) {
        return calcMkt(tops, triangle.get(0).get(forward), triangle.get(1).get(forward), triangle.get(2).get(forward));
    }

    private static double calcMkt(Map<Pair, TopData> tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
        return calcMkt(tops.get(pair1.m_pair), pair1.m_forward, tops.get(pair2.m_pair), pair2.m_forward, tops.get(pair3.m_pair), pair3.m_forward);
    }

    private static double calcMkt(TopData top1, boolean mul1, TopData top2, boolean mul2, TopData top3, boolean mul3) {
        double one = 100;
        double two = mulMkt(one, top1, mul1);
        double three = mulMkt(two, top2, mul2);
        double ret = mulMkt(three, top3, mul3);
        return ret;
    }

    private static double calcMid(Map<Pair, TopData> tops, Triangle triangle, boolean forward) {
        return calcMid(tops, triangle.get(0).get(forward), triangle.get(1).get(forward), triangle.get(2).get(forward));
    }

    private static double calcMid(Map<Pair, TopData> tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
        return calcMid(tops.get(pair1.m_pair), pair1.m_forward, tops.get(pair2.m_pair), pair2.m_forward, tops.get(pair3.m_pair), pair3.m_forward);
    }

    private static double calcMid(TopData top1, boolean mul1, TopData top2, boolean mul2, TopData top3, boolean mul3) {
        double one = 100;
        double two = mulMid(one, top1, mul1);
        double three = mulMid(two, top2, mul2);
        double ret = mulMid(three, top3, mul3);
        return ret;
    }

    private static OnePegData calcPeg(Map<Pair, TopData> tops, Triangle triangle, boolean forward) {
        return calcPeg(tops, triangle.get(0).get(forward), triangle.get(1).get(forward), triangle.get(2).get(forward));
    }

    private static OnePegData calcPeg(Map<Pair, TopData> tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
        return calcPeg(tops.get(pair1.m_pair), pair1.m_forward, tops.get(pair2.m_pair), pair2.m_forward, tops.get(pair3.m_pair), pair3.m_forward);
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
                indx = 2;
            } else {
                max = d1;
                indx = 0;
            }
        } else {
            if (d3 > d2) {
                max = d3;
                indx = 2;
            } else {
                max = d2;
                indx = 1;
            }
        }

        return new OnePegData(indx, max);
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
        return Utils.padLeft(X_YYY.format(usdOut - 100), 6);
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

    public static class Triangle extends ArrayList<PairDirection> {
        public Triangle(Pair pair1, boolean forward1, Pair pair2, boolean forward2, Pair pair3, boolean forward3) {
            add(new PairDirection(pair1, forward1));
            add(new PairDirection(pair2, forward2));
            add(new PairDirection(pair3, forward3));
        }

        public String name() {
            StringBuilder sb = new StringBuilder();
            for (PairDirection pd : this) {
                String name = pd.getName();
                sb.append(name);
                sb.append(";");
            }
            return sb.toString();
        }
    }

    public static class PairDirection {
        private final Pair m_pair;
        private final boolean m_forward;

        public PairDirection(Pair pair, boolean forward) {
            m_pair = pair;
            m_forward = forward;
        }

        public PairDirection get(boolean forward) {
            if(forward) {
                return this;
            }
            return new PairDirection(m_pair, !m_forward);
        }

        public String getName() {
            return m_pair.getName(m_forward);
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

        public double maxPeg() {
            return m_peg.m_max;
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
                    (m_forward.midCrossLvl() || m_backward.midCrossLvl() ? "*" : "|") + " ";
        }

        public boolean mktCrossLvl() {
            return m_forward.mktCrossLvl() || m_backward.mktCrossLvl();
        }

        public TriangleRotationData best() {
            return (m_forward.maxPeg() > m_backward.maxPeg()) ? m_forward : m_backward;
        }
    }

    private static class TrianglesData extends ArrayList<TriangleData> {
        public TrianglesData(int length) {
            super(length);
        }

        public String str() {
            StringBuilder sb = new StringBuilder();
            boolean mktCrossLvl = false;
            for (TriangleData t : this) {
                sb.append(t.str());
                mktCrossLvl |= t.mktCrossLvl();
            }

            if (mktCrossLvl) {
                sb.append("\t******************************");
            }

            return sb.toString();
        }

        public TriangleRotationData findBest() {
            TriangleRotationData best = null;
            double max = 0;
            for (TriangleData triangle : this) {
                TriangleRotationData current = triangle.best();
                double val = current.maxPeg();
                if (val > max) {
                    max = val;
                    best = current;
                }
            }
            return best;
        }
    }

    private static class IterationData {
        private Map<Pair, TopData> m_tops;
        private Map<Pair, TradesData> m_trades;
        private Object account;

        public Map<Pair,TopData> getTops() throws Exception {
            if(m_tops == null){
                m_tops = Fetcher.fetchTops(Exchange.BTCE, PAIRS);
            }
            return m_tops;
        }

        public Map<Pair, TradesData> getTrades() throws Exception {
            if (m_trades == null) {
                m_trades = Fetcher.fetchTrades(Exchange.BTCE, PAIRS);
            }
            return m_trades;
        }

        public AccountData getAccount() throws Exception {
            AccountData account = Fetcher.fetchAccount(Exchange.BTCE);
            return account;
        }
    }
}
