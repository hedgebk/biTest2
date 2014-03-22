package bthdg.triplet;

import bthdg.*;
import bthdg.Currency;
import bthdg.exch.BaseExch;
import bthdg.exch.Btce;

import java.text.DecimalFormat;
import java.util.*;

public class Triplet {
    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD};
    public static final double LVL = 100.6; // commission level
    public static final double LVL2 = 100.65; // min target level
    private static final double USE_ACCOUNT_FUNDS = 0.98;
    public static final DecimalFormat X_YYY = new DecimalFormat("+0.000;-0.000");
    public static final DecimalFormat X_YYYY = new DecimalFormat("0.0000");

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
        Fetcher.SIMULATE_ACCEPT_ORDER_PRICE = true;
        Fetcher.SIMULATE_ACCEPT_ORDER_PRICE_RATE = 0.99;

        try {
            Properties keys = BaseExch.loadKeys();
            Btce.init(keys);

            AccountData account = getAccount();
            System.out.println("account: " + account);

            long start = System.currentTimeMillis();
            int counter = 1;
            TriangleData td = new TriangleData(account);
            while (true) {
                log("============================================== iteration " + (counter++) + "; time=" + Utils.millisToDHMSStr(System.currentTimeMillis() - start));
                IterationData iData = new IterationData();
                td.checkState(iData);
                Thread.sleep(4000);
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
    }

    public static boolean placeOrder(AccountData account, OrderData orderData, OrderState state) {
        log("placeOrder(" + /*m_exchange.m_name +*/ ") not implemented yet: " + orderData);

        boolean success = account.allocateOrder(orderData);
        if (success) {
            orderData.m_status = OrderStatus.SUBMITTED;
            orderData.m_state = state;
        } else {
            log("ERROR: account allocateOrder unsuccessful: " + orderData + ", account: " + account);
        }
        return success;
    }

    private static double mulMid(double in, TopData top, boolean forward) {
        return forward ? in / top.getMid() : in * top.getMid();
    }

    private static double mulMkt(double in, TopData top, boolean forward) {
        return forward ? in / top.m_ask : in * top.m_bid; // ASK > BID
    }
    private static double mktPrice(TopData top, boolean forward) {
        return forward ? top.m_ask : top.m_bid; // ASK > BID
    }

    private static double mulPeg(double in, TopData top, boolean forward) {
        return forward ? in / top.m_bid : in * top.m_ask; // ASK > BID
    }
    private static double pegPrice(TopData top, boolean forward) {
        return forward ? top.m_bid : top.m_ask; // ASK > BID
    }

    private static String formatAndPad(double value) {
        return Utils.padLeft(format(value - 100), 6);
    }

    private static String format(double number) {
        return X_YYY.format(number);
    }

    private static String format4(double number) {
        return X_YYYY.format(number);
    }

    public static AccountData getAccount() throws Exception {
        AccountData account = Fetcher.fetchAccount(Exchange.BTCE);
        return account;
    }

    private static class OnePegCalcData {
        private int m_indx;
        private double m_max;
        public TriangleRotationCalcData m_parent;
        private double m_price1;
        private double m_price2;
        private double m_price3;
        private boolean m_mul1;
        private boolean m_mul2;
        private boolean m_mul3;

        public OnePegCalcData(int indx, double max, double price1, boolean mul1, double price2, boolean mul2, double price3, boolean mul3) {
            m_indx = indx;
            m_max = max;
            m_price1 = price1;
            m_price2 = price2;
            m_price3 = price3;
            m_mul1 = mul1;
            m_mul2 = mul2;
            m_mul3 = mul3;
        }

        @Override public String toString() {
            return "OnePegCalcData{" +
                    "indx=" + m_indx +
                    ", max=" + m_max +
                    ", price1=" + m_price1 +
                    ", mul1=" + m_mul1 +
                    ", price2=" + m_price2 +
                    ", mul2=" + m_mul2 +
                    ", price3=" + m_price3 +
                    ", mul3=" + m_mul3 +
                    '}';
        }

        public String str() {
            if (m_max > LVL) {
                return m_indx + ":" + formatAndPad(m_max);
            }
            return "        ";
        }

        @Override public boolean equals(Object obj) {
            if(obj == this) { return true; }
            if(obj instanceof OnePegCalcData) {
                OnePegCalcData other = (OnePegCalcData) obj;
                if(m_indx == other.m_indx) {
                    return m_parent.equals(other.m_parent);
                }
            }
            return false;
        }

        public double calcPegPrice(Map<Pair, TopData> tops) {
            Triangle triangle = m_parent.m_triangle;
            boolean rotationDirection = m_parent.m_forward;
            PairDirection pd = triangle.get(m_indx);
            boolean pairDirection = pd.m_forward;
            boolean direction = (pairDirection == rotationDirection);
            Pair pair = pd.m_pair;
            OrderSide side = direction ? OrderSide.BUY : OrderSide.SELL;
            TopData topData = tops.get(pair);
            return side.pegPrice(topData, pair);
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

        public double calcMkt(Map<Pair, TopData> tops, boolean forward) {
            return forward
                    ? calcMkt(tops, get(0).get(forward), get(1).get(forward), get(2).get(forward))
                    : calcMkt(tops, get(2).get(forward), get(1).get(forward), get(0).get(forward));
        }

        private static double calcMkt(Map<Pair, TopData> tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
            return calcMkt(tops.get(pair1.m_pair), pair1.m_forward, tops.get(pair2.m_pair), pair2.m_forward, tops.get(pair3.m_pair), pair3.m_forward);
        }

        private static double calcMkt(TopData top1, boolean mul1, TopData top2, boolean mul2, TopData top3, boolean mul3) {
            return mulMkt(mulMkt(mulMkt((double) 100, top1, mul1), top2, mul2), top3, mul3);
        }

        public double calcMid(Map<Pair, TopData> tops, boolean forward) {
            return forward
                    ? calcMid(tops, get(0).get(forward), get(1).get(forward), get(2).get(forward))
                    : calcMid(tops, get(2).get(forward), get(1).get(forward), get(0).get(forward));
        }

        private static double calcMid(Map<Pair, TopData> tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
            return calcMid(tops.get(pair1.m_pair), pair1.m_forward, tops.get(pair2.m_pair), pair2.m_forward, tops.get(pair3.m_pair), pair3.m_forward);
        }

        private static double calcMid(TopData top1, boolean mul1, TopData top2, boolean mul2, TopData top3, boolean mul3) {
            return mulMid(mulMid(mulMid((double) 100, top1, mul1), top2, mul2), top3, mul3);
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

        public OnePegCalcData[] calcPegs(Map<Pair, TopData> tops, boolean forward) {
//            log("  calc pegs forward=" + forward + " on Triangle: " + name());
            PairDirection pd0 = get(0).get(forward);
//            log("   pd0=" + pd0);
            PairDirection pd1 = get(1).get(forward);
//            log("   pd1=" + pd1);
            PairDirection pd2 = get(2).get(forward);
//            log("   pd2=" + pd2);
            return forward ? calcPegs(tops, pd0, pd1, pd2) : calcPegs(tops, pd2, pd1, pd0);
        }

        private static OnePegCalcData[] calcPegs(Map<Pair, TopData> tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
//            log("    calcPegs: pair1=" + pair1 + "; pair2=" + pair2 + "; pair3=" + pair3);
            return calcPegs(tops.get(pair1.m_pair), pair1.m_forward, tops.get(pair2.m_pair), pair2.m_forward, tops.get(pair3.m_pair), pair3.m_forward);
        }

        private static OnePegCalcData[] calcPegs(TopData top1, boolean f1, TopData top2, boolean f2, TopData top3, boolean f3) {
//            log("     calcPegs: f1=" + f1 + "; f2=" + f2 + "; f3=" + f3);
            return new OnePegCalcData[] {
                    new OnePegCalcData(0, mulMkt(mulMkt(mulPeg((double) 100, top1, f1), top2, f2), top3, f3),
                                       pegPrice(top1, f1), f1, mktPrice(top2, f2), f2, mktPrice(top3, f3), f3),
                    new OnePegCalcData(1, mulMkt(mulPeg(mulMkt((double) 100, top1, f1), top2, f2), top3, f3),
                                       mktPrice(top1, f1), f1, pegPrice(top2, f2), f2, mktPrice(top3, f3), f3),
                    new OnePegCalcData(2, mulPeg(mulMkt(mulMkt((double) 100, top1, f1), top2, f2), top3, f3),
                                       mktPrice(top1, f1), f1, mktPrice(top2, f2), f2, pegPrice(top3, f3), f3)
            };
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

        @Override public String toString() {
            return "PairDirection{" +
                    "pair=" + m_pair +
                    ", forward=" + m_forward +
                    '}';
        }
    }

    private static class TriangleRotationCalcData {
        private Triangle m_triangle;
        private boolean m_forward;
        private double m_mid;
        private double m_mkt;
        private OnePegCalcData[] m_pegs;
        private OnePegCalcData m_peg;

        @Override public boolean equals(Object obj) {
            if(obj == this) { return true; }
            if(obj instanceof TriangleRotationCalcData) {
                TriangleRotationCalcData other = (TriangleRotationCalcData) obj;
                if(m_forward == other.m_forward) {
                    return m_triangle.equals(m_triangle);
                }
            }
            return false;
        }

        public TriangleRotationCalcData(Triangle triangle, boolean forward, double mid, double mkt, OnePegCalcData[] pegs) {
            m_triangle = triangle;
            m_forward = forward;
            m_mid = mid;
            m_mkt = mkt;
            m_pegs = pegs;
            m_peg = findBestPeg();
            pegs[0].m_parent = this;
            pegs[1].m_parent = this;
            pegs[2].m_parent = this;
        }

        private OnePegCalcData findBestPeg() {
            double d1 = m_pegs[0].m_max;
            double d2 = m_pegs[1].m_max;
            double d3 = m_pegs[2].m_max;
            int indx = (d1 > d2) ? ((d3 > d1) ? 2 : 0) : ((d3 > d2) ? 2 : 1);
            return m_pegs[indx];
        }

        static TriangleRotationCalcData calc(Map<Pair, TopData> tops, Triangle triangle, boolean forward) {
//            log(" rotate forward=" + forward + " on Triangle: " + triangle.name());
            return new TriangleRotationCalcData(triangle, forward,
                                                triangle.calcMid(tops, forward),
                                                triangle.calcMkt(tops, forward),
                                                triangle.calcPegs(tops, forward));
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

        public void findBestMap(TreeMap<Double, OnePegCalcData> ret) {
            findBestMap(ret, 0);
            findBestMap(ret, 1);
            findBestMap(ret, 2);
        }

        private void findBestMap(TreeMap<Double, OnePegCalcData> ret, int indx) {
            OnePegCalcData peg = m_pegs[indx];
            double key = peg.m_max;
            if (key > LVL2) {
                ret.put(1/key, peg); // 1/key - to make items appears from high to low
            }
        }
    }

    private static class TriangleCalcData {
        private TriangleRotationCalcData m_forward;
        private TriangleRotationCalcData m_backward;

        public TriangleCalcData(TriangleRotationCalcData forward, TriangleRotationCalcData backward) {
            m_forward = forward;
            m_backward = backward;
        }

        static TriangleCalcData calc(Map<Pair, TopData> tops, Triangle triangle) {
            return new TriangleCalcData(TriangleRotationCalcData.calc(tops, triangle, true),
                                        TriangleRotationCalcData.calc(tops, triangle, false));
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

        public void findBestMap(TreeMap<Double, OnePegCalcData> ret) {
            m_forward.findBestMap(ret);
            m_backward.findBestMap(ret);
        }
    }

    private static class TrianglesCalcData extends ArrayList<TriangleCalcData> {
        public TrianglesCalcData(int length) {
            super(length);
        }

        static TrianglesCalcData calc(Map<Pair, TopData> tops) {
            TrianglesCalcData ret = new TrianglesCalcData(TRIANGLES.length);
            for (Triangle tr : TRIANGLES) {
//                log("Triangle: " + tr.name());
                TriangleCalcData calc = TriangleCalcData.calc(tops, tr);
                ret.add(calc);
            }
            return ret;
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

        public TreeMap<Double, OnePegCalcData> findBestMap() {
            TreeMap<Double, OnePegCalcData> ret = new TreeMap<Double, OnePegCalcData>();
            for (TriangleCalcData triangle : this) {
                triangle.findBestMap(ret);
            }
            return ret;  //To change body of created methods use File | Settings | File Templates.
        }
    }

    private static class IterationData implements IIterationContext {
        private Map<Pair, TopData> m_tops;
        private Map<Pair, TradesData> m_trades;
        public NewTradesAggregator m_newTrades = new NewTradesAggregator();
        private boolean m_acceptPriceSimulated;

        @Override public boolean acceptPriceSimulated() { return m_acceptPriceSimulated; }
        @Override public void acceptPriceSimulated(boolean b) { m_acceptPriceSimulated = b; }
        @Override public Map<Pair, TradesData> fetchTrades(Exchange exchange) throws Exception { return getTrades(); }
        @Override public TopData getTop(Exchange exchange, Pair pair) throws Exception { return getTops().get(pair); }

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

        @Override public Map<Pair, TradesData> getNewTradesData(Exchange exchange, TradesData.ILastTradeTimeHolder holder) throws Exception {
            return m_newTrades.getNewTradesData(this, exchange, holder);
        }
    }

    private static class TriangleData implements OrderState.IOrderExecListener, TradesData.ILastTradeTimeHolder {
        private long m_lastProcessedTradesTime;
        private AccountData m_account;
        private List<TriTradeData> m_triTrades = new ArrayList<TriTradeData>();

        @Override public long lastProcessedTradesTime() { return m_lastProcessedTradesTime; }
        @Override public void lastProcessedTradesTime(long lastProcessedTradesTime) { m_lastProcessedTradesTime = lastProcessedTradesTime; }
        @Override public void onOrderFilled(IIterationContext iContext, Exchange exchange, OrderData orderData) { /*noop*/ }

        public TriangleData(AccountData account) {
            m_account = account;
        }

        public void checkState(IterationData iData) throws Exception {
            checkOrdersState(iData, this);

            Map<Pair, TopData> tops = iData.getTops();
            TrianglesCalcData trianglesCalc = TrianglesCalcData.calc(tops);
            log(trianglesCalc.str());

            TreeMap<Double,OnePegCalcData> bestMap = trianglesCalc.findBestMap();
            checkOrdersToLive(bestMap, tops);

            checkNew(iData, bestMap, tops);
        }

        private void checkOrdersToLive(TreeMap<Double, OnePegCalcData> bestMap, Map<Pair, TopData> tops) {
            if (!m_triTrades.isEmpty()) {
                List<TriTradeData> triTradesToLive = new ArrayList<TriTradeData>();
                List<TriTradeData> triTradesToDie = new ArrayList<TriTradeData>();

                for (TriTradeData triTrade : m_triTrades) {
                    if (triTrade.m_state == TriTradeData.TriTradeState.DONE) { // just do not add what is DONE
                        log(" we have done with: " + triTrade);
                    } else if (triTrade.m_state != TriTradeData.TriTradeState.PEG_PLACED) { // we are not in init state
                        triTradesToLive.add(triTrade);
                    } else {
                        OnePegCalcData peg2 = triTrade.m_peg;
                        boolean toLive = false;
                        for (Map.Entry<Double, OnePegCalcData> entry : bestMap.entrySet()) {
                            OnePegCalcData peg1 = entry.getValue();
                            if (peg1.equals(peg2)) {
                                double pegPrice = peg1.calcPegPrice(tops);
                                double orderPrice = triTrade.m_order.m_price;
                                if (pegPrice == orderPrice) { // check if peg order needs to be moved
                                    toLive = true;
                                } else {
                                    toLive = false;
                                    log("   peg order should be moved. orderPrice=" + orderPrice + ", pegPrice=" + pegPrice);
                                }
                                break;
                            }
                        }
                        // todo: cancel also orders from the same Currency but to another available currency having bigger gain
                        (toLive ? triTradesToLive : triTradesToDie).add(triTrade);
                    }
                }
                m_triTrades = triTradesToLive;
                for (TriTradeData triTrade : triTradesToDie) {
                    if (!cancelOrder(triTrade)) {
                        // todo: in case of errors - add back to m_triTrades - will check in next iteration again
                    }
                }
            }
        }

        private boolean cancelOrder(TriTradeData triTrade) {
            OrderData order = triTrade.m_order;
            return cancelOrder(order);
        }

        public boolean cancelOrder(OrderData order) {
            if (order != null) {
                order.cancel();
                m_account.releaseOrder(order);
            }
            return true; // todo: order can be executed at this point, so cancel will fail
        }

        private void checkOrdersState(IterationData iData, TriangleData triangleData) throws Exception {
            if (!m_triTrades.isEmpty()) {
                for (TriTradeData triTrade : m_triTrades) {
                    triTrade.checkState(iData, triangleData);
                }

                forkIfNeeded(); // fork if needed -  for partially filled orders

                // execute checkState() for PEG_FILLED immediately - no need to wait to run MKT orders
                for (TriTradeData triTrade : m_triTrades) {
                    TriTradeData.TriTradeState state = triTrade.m_state;
                    if( (state == TriTradeData.TriTradeState.PEG_FILLED) || (state == TriTradeData.TriTradeState.MKT1_EXECUTED)) {
                        triTrade.checkState(iData, triangleData);
                    }
                }
            }
        }

        private void forkIfNeeded() {
            List<TriTradeData> forks = null;
            for (TriTradeData triTrade : m_triTrades) {
                TriTradeData fork = triTrade.forkIfNeeded();
                if(fork != null) { // forked
                    if (forks == null) {
                        forks = new ArrayList<TriTradeData>();
                    }
                    forks.add(fork);
                }
            }
            if (forks != null) {
                m_triTrades.addAll(forks);
            }
        }

        public void checkNew(IterationData iData, TreeMap<Double, OnePegCalcData> bestMap, Map<Pair, TopData> tops) throws Exception {
            for (Map.Entry<Double, OnePegCalcData> entry : bestMap.entrySet()) {
                OnePegCalcData peg = entry.getValue();
                double pegPrice = peg.calcPegPrice(tops);

                double maxPeg = peg.m_max;
                int indx = peg.m_indx;
                TriangleRotationCalcData rotationData = peg.m_parent;
                Triangle triangle = rotationData.m_triangle;
                boolean rotationDirection = rotationData.m_forward;
                String name = triangle.name();

                PairDirection pd = triangle.get(indx);
                String start = pd.getName();
                String start_ = pd.get(rotationDirection).getName();

                boolean pairDirection = pd.m_forward;
                boolean direction = (pairDirection == rotationDirection);
                Pair pair = pd.m_pair;
                Currency fromCurrency = pair.currencyFrom(direction);
                double available = getAvailable(fromCurrency);
                OrderSide side = direction ? OrderSide.BUY : OrderSide.SELL;
                TopData topData = tops.get(pair);
                double amount = side.isBuy() ? available/pegPrice: available;

//                str += " best: " + formatAndPad(maxPeg);

                log("#### best: " + formatAndPad(maxPeg) + "; " + name + " rotationDir=" + rotationDirection + ", indx=" + indx +
                        ", start=" + start + ", start'=" + start_ + ", pair: " + pair + ", pairDir=" + pairDirection + ", direction=" + direction +
                        "\n     top: " + topData + ", from=" + fromCurrency + "; available=" + available  + "; amount=" + amount +
                        "; side=" + side + "; pegPrice=" + format4(pegPrice));

                if (amount > pair.m_minOrderSize) {
                    iData.getNewTradesData(Exchange.BTCE, this); // make sure we have loaded all trades on this iteration
                    OrderData order = new OrderData(pair, side, pegPrice, amount);
                    boolean ok = placeOrder(m_account, order, OrderState.LIMIT_PLACED);
                    log("   place order = " + ok + ":  " + order);
                    if (ok) {
                        TriTradeData ttData = new TriTradeData(order, peg);
                        m_triTrades.add(ttData);
                    }
                } else {
                    log(" no funds for NEW order: min order size=" + pair.m_minOrderSize + ", amount " + format4(amount) + " " + fromCurrency + " : " + m_account);
                }
            }
        }

        private double getAvailable(Currency currency) {
            return m_account.available(currency) * USE_ACCOUNT_FUNDS;
        }
    }

    private static class TriTradeData {
        private OrderData m_order;
        private OrderData[] m_mktOrders;
        private OnePegCalcData m_peg;
        private TriTradeState m_state = TriTradeState.PEG_PLACED;

        public TriTradeData(OrderData order, OnePegCalcData peg) {
            m_order = order;
            m_peg = peg;
        }

        public void checkState(IterationData iData, TriangleData triangleData) throws Exception {
            m_state.checkState(iData, triangleData, this);
        }

        private void setState(TriTradeState state) {
            log("TriTradeData.setState() " + m_state + " -> " + state);
            m_state = state;
        }

        private void setMktOrder(OrderData order, int indx) {
            if (m_mktOrders == null) {
                m_mktOrders = new OrderData[2];
            }
            m_mktOrders[indx] = order;
            setState(indx == 0 ? TriTradeState.MKT1_PLACED : TriTradeState.MKT2_PLACED);
        }

        public TriTradeData forkIfNeeded() {
            if (m_order.isPartiallyFilled()) {
                double filled = m_order.m_filled;
                double remained = m_order.remained();

                log("splitting: remained=" + remained + ".  " + m_order);

                OrderData order2 = new OrderData(m_order.m_pair, m_order.m_side, m_order.m_price, remained);
                order2.m_status = OrderStatus.SUBMITTED;
                order2.m_state = m_order.m_state;
                log(" new order: " + order2);

                TriTradeData triTrade2 = new TriTradeData(order2, m_peg);

                m_order.m_state = OrderState.NONE;
                m_order.m_status = OrderStatus.FILLED;
                m_order.m_amount = filled;
                log(" existing order: " + m_order);
                m_state = TriTradeData.TriTradeState.PEG_FILLED;

                return triTrade2;
            }
            if (m_mktOrders != null) {
                forkIfNeededOnMktOrder(0);
                forkIfNeededOnMktOrder(1);
            }
            return null;
        }

        private void forkIfNeededOnMktOrder(int i) {
            if (m_mktOrders[i] != null) {
                if (m_mktOrders[i].isPartiallyFilled()) {
                    log(" partially filled mkt order, split too: " + m_mktOrders[i]);
                    // todo: fork
                }
            }
        }

        @Override public String toString() {
            return "TriTradeData[" +
                    "state=" + m_state +
                    "; order=" + m_order +
                    ((m_mktOrders != null && m_mktOrders[0] != null) ? "; mktOrder1=" + m_mktOrders[0] : "") +
                    ((m_mktOrders != null && m_mktOrders[1] != null) ? "; mktOrder2=" + m_mktOrders[1] : "") +
                    "]";
        }

        private enum TriTradeState {
            PEG_PLACED {
                @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
                    OrderData order = triTradeData.m_order;
                    log("TriTradeState.PEG_PLACED() - check order " + order + " ...");
                    order.checkState(iData, Exchange.BTCE, triangleData.m_account, null, triangleData);
                    if (order.isFilled()) {
                        triTradeData.setState(TriTradeState.PEG_FILLED);
                    } else {
                        // todo: move to PEG price if needed
                        log("   todo: move to PEG price if needed");
                    }
                }
            },
            PEG_FILLED {
                @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
                    log("TriTradeState.PEG_FILLED() - run 1st MKT order...");
                    startMktOrder(iData, triangleData, triTradeData, 1);
                }
            },
            MKT1_PLACED {
                @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
                    OrderData order = triTradeData.m_mktOrders[0];
                    log("TriTradeState.MKT1_PLACED() - check order " + order + " ...");
                    order.checkState(iData, Exchange.BTCE, triangleData.m_account, null, triangleData);
                    if (order.isFilled()) {
                        triTradeData.setState(TriTradeState.MKT1_EXECUTED);
                    } else {
                        log("1st MKT order run out of market " + order + ";  top="+iData.getTop(Exchange.BTCE, order.m_pair));
                        if(triangleData.cancelOrder(order)) {
                            triTradeData.m_mktOrders[0] = null;
                            log("placing new 1st MKT order...");
                            startMktOrder(iData, triangleData, triTradeData, 1);
                        } else {
                            log("cancel order failed: " + order);
                        }
                    }
                }
            },
            MKT1_EXECUTED{
                @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
                    log("TriTradeState.MKT1_EXECUTED() - run 2nd MKT order...");
                    startMktOrder(iData, triangleData, triTradeData, 2);
                }
            },
            MKT2_PLACED {
                @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
                    OrderData order = triTradeData.m_mktOrders[1];
                    log("TriTradeState.MKT2_PLACED() - check order " + order + " ...");
                    order.checkState(iData, Exchange.BTCE, triangleData.m_account, null, triangleData);
                    if (order.isFilled()) {
                        triTradeData.setState(TriTradeState.MKT2_EXECUTED);
                    } else {
                        log("2nd MKT order run out of market " + order + ";  top="+iData.getTop(Exchange.BTCE, order.m_pair));
                        if(triangleData.cancelOrder(order)) {
                            triTradeData.m_mktOrders[1] = null;
                            log("placing new 2nd MKT order...");
                            startMktOrder(iData, triangleData, triTradeData, 2);
                        } else {
                            log("cancel order failed: " + order);
                        }
                    }
                }
            },
            MKT2_EXECUTED {
                @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
                    log("MKT2_EXECUTED - we are done");

                    OnePegCalcData peg = triTradeData.m_peg;
                    TriangleRotationCalcData rotationData = peg.m_parent;
                    Triangle triangle = rotationData.m_triangle;
                    String name = triangle.name();
                    int startIndx = peg.m_indx;
                    boolean rotationDirection = rotationData.m_forward;
                    log(" " + name + "; start=" + startIndx + "; direction=" + rotationDirection);

                    AccountData account = triangleData.m_account;

                    double[] ends1 = triTradeData.m_order.logOrderEnds(account, 1);
                    double[] ends2 = triTradeData.m_mktOrders[0].logOrderEnds(account, 2);
                    double[] ends3 = triTradeData.m_mktOrders[1].logOrderEnds(account, 3);

                    double in = ends1[0];
                    double out = ends3[1];
                    double gain = out / in;
                    log(" @@@@@@   in=" + in + ";  out=" + out + ";  gain=" + gain);
                    log(" @@@@@@    peg: max=" + peg.m_max + "; startIndx="+startIndx+
                            "; price1=" + peg.m_price1 + "; f1=" + peg.m_mul1 +
                            "; price2=" + peg.m_price2 + "; f2=" + peg.m_mul2 +
                            "; price3=" + peg.m_price3 + "; f3=" + peg.m_mul3);

                    triTradeData.setState(DONE);
                }
            },
            DONE {
                @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
                    log("DONE state on "+this);
                }
            },
            ERROR {
                @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
                    log("ERROR state on "+this);
                }
            },
            ;

            private static void startMktOrder(IterationData iData, TriangleData triangleData, TriTradeData triTradeData, int num) throws Exception {
                log("startMktOrder(" + num + ")");
                AccountData account = triangleData.m_account;

                OrderData prevOrder = (num == 1) ? triTradeData.m_order : triTradeData.m_mktOrders[0];
                OrderSide prevSide = prevOrder.m_side;
                Currency prevEndCurrency = prevOrder.m_pair.currencyFrom(!prevSide.isBuy());
                double prevEndAmount = (prevSide.isBuy() ? prevOrder.m_amount : prevOrder.m_amount * prevOrder.m_price) * (1 - account.m_fee); // deduct commissions
                log(" prev order " + prevOrder + "; exit amount " + prevEndAmount + " " + prevEndCurrency);

                OnePegCalcData peg = triTradeData.m_peg;
                int startIndx = peg.m_indx;
                TriangleRotationCalcData rotationData = peg.m_parent;
                Triangle triangle = rotationData.m_triangle;
                boolean rotationDirection = rotationData.m_forward;
                int indx = rotationDirection ? (startIndx + num) % 3 : (startIndx + 3 - num) % 3;
                String name = triangle.name();

                PairDirection pd = triangle.get(indx);
                String start = pd.getName();
                String start_ = pd.get(rotationDirection).getName();

                boolean pairDirection = pd.m_forward;
                boolean direction = (pairDirection == rotationDirection);
                Pair pair = pd.m_pair;
                Currency fromCurrency = pair.currencyFrom(direction);
                if (prevEndCurrency != fromCurrency) {
                    log("ERROR: currencies are not matched");
                }
                double available = account.available(fromCurrency);
                if (prevEndAmount > available) {
                    log("ERROR: not enough available");
                }
                OrderSide side = direction ? OrderSide.BUY : OrderSide.SELL;
                Map<Pair, TopData> tops = iData.getTops();
                TopData topData = tops.get(pair);
                double mktPrice = side.mktPrice(topData);
                double amount = side.isBuy() ? prevEndAmount / mktPrice : prevEndAmount;

//                str += " best: " + formatAndPad(maxPeg);

                log("1st order:" + name + " rotationDir=" + rotationDirection + ", startIndx=" + startIndx + ", indx=" + indx +
                        ", start=" + start + ", start'=" + start_ + ", pair: " + pair + ", pairDir=" + pairDirection + ", direction=" + direction +
                        "; top: " + topData + ", from=" + fromCurrency + "; available=" + available + "; amount=" + amount +
                        "; side=" + side + "; mktPrice=" + format4(mktPrice));

                // todo: but check for physical min order size
//                if (amount > pair.m_minOrderSize) {
                    OrderData order = new OrderData(pair, side, mktPrice, amount);
                    boolean ok = placeOrder(account, order, OrderState.MARKET_PLACED);
                    log("   place order = " + ok);
                    if (ok) {
                        triTradeData.setMktOrder(order, num - 1);
                    } else {
                        triTradeData.setState(ERROR);
                    }
//                } else {
//                    log(" no funds for min order size=" + pair.m_minOrderSize + ", amount " + format4(amount) + " " + fromCurrency + " : " + account);
//                }
            }

            public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {}
        }
    }
}
