package bthdg.triplet;

import bthdg.*;
import bthdg.exch.BaseExch;
import bthdg.exch.Btce;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

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
        Fetcher.USE_ACCOUNT_TEST_STR = true;

        try {
            Properties keys = BaseExch.loadKeys();
            Btce.init(keys);

            AccountData account = getAccount();
            System.out.println("account: " + account);

            TriangleData td = new TriangleData(account);
            while (true) {
                IterationData iData = new IterationData();
                td.checkState(iData);
                Thread.sleep(4000);
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
    }

    public static boolean placeOrder(AccountData account, OrderData orderData) {
        return placeOrder(account, orderData, OrderState.MARKET_PLACED);
    }

    public static boolean placeOrder(AccountData account, OrderData orderData, OrderState state) {
        // todo: implement
        log("placeOrder(" + /*m_exchange.m_name +*/ ") not implemented yet: " + orderData);

        boolean success = account.allocateOrder(orderData);
success = true;
        if(success) {
            // todo: pass to exch.baseExch if needed
            orderData.m_status = OrderStatus.SUBMITTED;
            orderData.m_state = state;
        } else {
            log("account allocateOrder unsuccessful: " + orderData + ", account: " + account);
        }
        return success;
    }


    private static TrianglesCalcData calc(Map<Pair, TopData> tops) {
        TrianglesCalcData ret = new TrianglesCalcData(TRIANGLES.length);
        for (Triangle tr : TRIANGLES) {
            TriangleCalcData t = calc(tops, tr);
            ret.add(t);
        }
        return ret;
    }

    private static TriangleCalcData calc(Map<Pair, TopData> tops, Triangle triangle) {
        TriangleRotationCalcData trf = calc(tops, triangle, true);
        TriangleRotationCalcData trb = calc(tops, triangle, false);

        return new TriangleCalcData(trf, trb);
    }

    private static TriangleRotationCalcData calc(Map<Pair, TopData> tops, Triangle triangle, boolean forward) {
        double mid = calcMid(tops, triangle, forward);
        double mkt = calcMkt(tops, triangle, forward);
        OnePegCalcData peg = calcPeg(tops, triangle, forward);
        return new TriangleRotationCalcData(triangle, forward, mid, mkt, peg);
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

    private static OnePegCalcData calcPeg(Map<Pair, TopData> tops, Triangle triangle, boolean forward) {
        return calcPeg(tops, triangle.get(0).get(forward), triangle.get(1).get(forward), triangle.get(2).get(forward));
    }

    private static OnePegCalcData calcPeg(Map<Pair, TopData> tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
        return calcPeg(tops.get(pair1.m_pair), pair1.m_forward, tops.get(pair2.m_pair), pair2.m_forward, tops.get(pair3.m_pair), pair3.m_forward);
    }

    private static OnePegCalcData calcPeg(TopData top1, boolean mul1, TopData top2, boolean mul2, TopData top3, boolean mul3) {
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

        return new OnePegCalcData(indx, max);
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

    private static String formatAndPad(double value) {
        return Utils.padLeft(format(value - 100), 6);
    }

    private static String format(double number) {
        return X_YYY.format(number);
    }

    public static AccountData getAccount() throws Exception {
        AccountData account = Fetcher.fetchAccount(Exchange.BTCE);
        return account;
    }

    private static class OnePegCalcData {
        private int m_indx;
        private double m_max;

        public OnePegCalcData(int indx, double max) {
            m_indx = indx;
            m_max = max;
        }

        public String str() {
            if (m_max > LVL) {
                return m_indx + ":" + formatAndPad(m_max);
            }
            return "        ";
        }
    }

    private static void log(String s) {
        Log.log(s);
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

    private static class TriangleRotationCalcData {
        private Triangle m_triangle;
        private boolean m_forward;
        private double m_mid;
        private double m_mkt;
        private OnePegCalcData m_peg;

        public TriangleRotationCalcData(Triangle triangle, boolean forward, double mid, double mkt, OnePegCalcData peg) {
            m_triangle = triangle;
            m_forward = forward;
            m_mid = mid;
            m_mkt = mkt;
            m_peg = peg;
        }

        public String str() {
            return formatAndPad(m_mid) + " " + formatAndPad(m_mkt) + " " + m_peg.str();
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

    private static class TriangleCalcData {
        private TriangleRotationCalcData m_forward;
        private TriangleRotationCalcData m_backward;

        public TriangleCalcData(TriangleRotationCalcData forward, TriangleRotationCalcData backward) {
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

        public TriangleRotationCalcData best() {
            return (m_forward.maxPeg() > m_backward.maxPeg()) ? m_forward : m_backward;
        }
    }

    private static class TrianglesCalcData extends ArrayList<TriangleCalcData> {
        public TrianglesCalcData(int length) {
            super(length);
        }

        public String str() {
            StringBuilder sb = new StringBuilder();
            boolean mktCrossLvl = false;
            for (TriangleCalcData t : this) {
                sb.append(t.str());
                mktCrossLvl |= t.mktCrossLvl();
            }

            if (mktCrossLvl) {
                sb.append("\t******************************");
            }

            return sb.toString();
        }

        public TriangleRotationCalcData findBest() {
            TriangleRotationCalcData best = null;
            double max = 0;
            for (TriangleCalcData triangle : this) {
                TriangleRotationCalcData current = triangle.best();
                double val = current.maxPeg();
                if (val > max) {
                    max = val;
                    best = current;
                }
            }
            return best;
        }
    }

    private static class IterationData implements IIterationContext {
        private Map<Pair, TopData> m_tops;
        private Map<Pair, TradesData> m_trades;
        private Object account;
        public NewTradesAggregator m_newTrades = new NewTradesAggregator();

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

        @Override public boolean acceptPriceSimulated() {
            log("not implemented: acceptPriceSimulated()");
            return false;
        }

        @Override public void acceptPriceSimulated(boolean b) {
            log("not implemented: acceptPriceSimulated(boolean)");
        }

        @Override public Map<Pair, TradesData> fetchTrades(Exchange exchange) throws Exception {
            return getTrades();
        }

        @Override public Map<Pair, TradesData> getNewTradesData(Exchange exchange, TradesData.ILastTradeTimeHolder holder) {
            log("not implemented: getNewTradesData()");
            return null;
        }
    }

    private static enum TriangleState {
        NONE {
            public void checkState(IterationData iData, TriangleData tData) {
                tData.setState(WAIT);
            }
        },
        WAIT {
            public void checkState(IterationData iData, TriangleData tData) throws Exception {
                tData.calculate(iData);
            }
        },
        PEG {
            public void checkState(IterationData iData, TriangleData tData) throws Exception {
                tData.checkPeg(iData, tData);
            }
        }
        ;

        public void checkState(IterationData iData, TriangleData tData) throws Exception {}
    }

    private static class TriangleData implements OrderState.IOrderExecListener, TradesData.ILastTradeTimeHolder {
        private long m_lastProcessedTradesTime;
        private AccountData m_account;
        private OrderData m_order;
        private TriangleRotationCalcData m_rotation;
        private TriangleState m_state = TriangleState.NONE;

        public void setState(TriangleState state) { m_state = state; }
        @Override public long lastProcessedTradesTime() { return m_lastProcessedTradesTime; }
        @Override public void lastProcessedTradesTime(long lastProcessedTradesTime) { m_lastProcessedTradesTime = lastProcessedTradesTime; }
        @Override public void onOrderFilled(IIterationContext iContext, Exchange exchange, OrderData orderData) { /*noop*/ }

        public TriangleData(AccountData account) {
            m_account = account;
        }

        public void checkState(IterationData iData) throws Exception {
            m_state.checkState(iData, this);
        }

        public void calculate(IterationData iData) throws Exception {
            Map<Pair, TopData> tops = iData.getTops();

            TrianglesCalcData triangles = calc(tops);
            String str = triangles.str();

            TriangleRotationCalcData best = triangles.findBest();
            if (best != null) {
                double maxPeg = best.maxPeg();
                if (maxPeg > LVL) {
                    OnePegCalcData peg = best.m_peg;
                    int indx = peg.m_indx;
                    Triangle triangle = best.m_triangle;
                    String name = triangle.name();

                    PairDirection pairDirection = triangle.get(indx);
                    Pair pair = pairDirection.m_pair;
                    boolean direction = pairDirection.m_forward;
                    TopData topData = tops.get(pair);

                    String start = pairDirection.getName();
                    Currency fromCurrency = pair.currency(direction);
                    double available = m_account.available(fromCurrency);
                    OrderSide side = direction ? OrderSide.BUY : OrderSide.SELL;
                    double pegPrice = side.pegPrice(topData, pair);

//                    str += "\n#### best: " + formatAndPad(maxPeg) + "; name=" + name + ", indx=" + indx +
//                            ", start=" + start + ", pair: " + pair + ", direction=" + direction +
//                            "; top: " + topData + ", fromCurrency=" + fromCurrency + "; available=" + available +
//                            "; side=" + side + "; pegPrice=" + format(pegPrice);

//                    m_order = new OrderData(pair, side, pegPrice, available);
//                    placeOrder(m_account, m_order);
//
//                    m_rotation = best;
//                    setState(TriangleState.PEG);
                }
            }
            System.out.println(str);
        }

        public void checkPeg(IterationData iData, TriangleData tData) throws Exception {
            // check peg order state
            if (m_order != null) { // order can be not placed in case of error
                m_order.checkState(iData, Exchange.BTCE, tData.m_account, this, tData);
            } else {
                log("ERROR: order is null");
            }
        }
    }
}
