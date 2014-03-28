package bthdg.triplet;

import bthdg.*;
import bthdg.Currency;
import bthdg.exch.Btce;
import bthdg.exch.CancelOrderData;
import bthdg.exch.TopData;
import bthdg.exch.TradesData;

import java.util.*;

public class TriangleData implements OrderState.IOrderExecListener, TradesData.ILastTradeTimeHolder {
    private long m_lastProcessedTradesTime;
    public AccountData m_account;
    public List<TriTradeData> m_triTrades = new ArrayList<TriTradeData>();

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

//            checkBrackets(iData, bestMap, tops, m_account);
    }

    private void checkBrackets(IterationData iData, TreeMap<Double, OnePegCalcData> bestMap, Map<Pair, TopData> tops, AccountData account) throws Exception {
        List<Map.Entry<Pair, Integer>> list = new ArrayList<Map.Entry<Pair, Integer>>(iData.m_tradesAgg.m_tradesMap.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<Pair, Integer>>() {  // sort by frequency
            @Override public int compare(Map.Entry<Pair, Integer> o1, Map.Entry<Pair, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
        for (Map.Entry<Pair, Integer> x : list) {
            log(" checkBrackets:" + x);
            Pair pair = x.getKey(); // pairs sorted by trades frequency
            checkBrackets(iData, bestMap, tops, account, pair, true);
            checkBrackets(iData, bestMap, tops, account, pair, false);
        }
    }

    private void checkBrackets(IterationData iData, TreeMap<Double, OnePegCalcData> bestMap, Map<Pair, TopData> tops, AccountData account, Pair pair, boolean forward) throws Exception {
        PairDirection pd1 = new PairDirection(pair, forward);
        Currency fromCurrency = pd1.currencyFrom();
        double available = getAvailable(fromCurrency);
        double allocated = account.allocated(fromCurrency);
        log("   pair:" + pd1 + "; from currency=" + fromCurrency + "; available=" + available + "; allocated=" + allocated);
        if (available > allocated) {
            OnePegCalcData bestPeg = findBestBracketPeg(bestMap, pd1);
            log("    bestPeg=" + bestPeg);
            double bracketPrice = bestPeg.m_need;
            TopData top = tops.get(pair);
            OrderSide side = forward ? OrderSide.BUY : OrderSide.SELL;
            double amount = side.isBuy() ? available / bracketPrice : available;
            log("    bracketPrice:" + bracketPrice + "; side=" + side + "; amount=" + amount + "; top=" + top);

            if (amount > pair.m_minOrderSize) {
                iData.getNewTradesData(Exchange.BTCE, this); // make sure we have loaded all trades on this iteration
                OrderData order = new OrderData(pair, side, bracketPrice, amount);
                boolean ok = Triplet.placeOrder(m_account, order, OrderState.LIMIT_PLACED);
                log("   place order = " + ok + ":  " + order);
                if (ok) {
                    TriTradeData ttData = new TriTradeData(order, bestPeg);
                    m_triTrades.add(ttData);
                }
            } else {
                log(" no funds for NEW bracket order: min order size=" + pair.m_minOrderSize +
                        ", amount " + Triplet.format4(amount) + " " + fromCurrency + " : " + m_account);
            }
        }
    }

    private OnePegCalcData findBestBracketPeg(TreeMap<Double, OnePegCalcData> bestMap, PairDirection pd1) {
        OnePegCalcData bestPeg = null;
        double bestMax = 0;
        for (OnePegCalcData peg : bestMap.values()) {
            String name = peg.name();
            log("    check Peg: " + name + ";  " + peg);
            PairDirection pd2 = peg.m_pair1;
            if (pd1.equals(pd2)) {
                double max = peg.m_max;
                if (max > bestMax) {
                    log("     better: max=" + max + ", bestMax=" + bestMax);
                    bestMax = max;
                    bestPeg = peg;
                } else {
log("     NOT better: max=" + max + ", bestMax=" + bestMax);
                }
            }
        }
        return bestPeg;
    }

    private void checkOrdersToLive(TreeMap<Double, OnePegCalcData> bestMap, Map<Pair, TopData> tops) throws Exception {
        if (!m_triTrades.isEmpty()) {
            List<TriTradeData> triTradesToLive = new ArrayList<TriTradeData>();
            List<TriTradeData> triTradesToDie = new ArrayList<TriTradeData>();

            for (TriTradeData triTrade : m_triTrades) {
                if (triTrade.m_state == TriTradeState.DONE) { // just do not add what is DONE
                    log(" we have done with: " + triTrade);
                } else if (triTrade.m_state == TriTradeState.ERROR) { // just do not add what is ERROR
                    log(" we have ERROR with: " + triTrade);
                } else if (triTrade.m_state == TriTradeState.CANCELED) { // just do not add what is CANCELED
                    log(" we have CANCELED with: " + triTrade);
                } else if (triTrade.m_state != TriTradeState.PEG_PLACED) { // we are not in init state
                    triTradesToLive.add(triTrade);
                    log("  order to live (not init state): " + triTrade);
                } else {
                    OnePegCalcData peg2 = triTrade.m_peg;
                    boolean toLive = false;
                    for (Map.Entry<Double, OnePegCalcData> entry : bestMap.entrySet()) {
                        OnePegCalcData peg1 = entry.getValue();
                        if (peg1.equals(peg2)) {
                            if (peg1.m_max > Triplet.s_level) {
                                double pegPrice = peg1.calcPegPrice(tops);
                                double orderPrice = triTrade.m_order.m_price;

                                double priceDif = Math.abs(pegPrice - orderPrice); // like 16.220010999999998 and 16.22001
                                double minPriceStep = Btce.minPriceStep(peg2.m_pair1.m_pair); // like 0.00001
                                if (priceDif < minPriceStep / 2) { // check if peg order needs to be moved - do not move if price change is too small
                                    toLive = true;
                                    log("  peg to live (" + peg2.name() + "): max1=" + peg1.m_max + "; max2=" + peg2.m_max +
                                            "; priceDif=" + priceDif + ", pegPrice=" + pegPrice + "; order=" + triTrade.m_order);
                                } else {
                                    toLive = false;
                                    log("   peg order should be moved (" + peg2.name() + "). orderPrice=" + Utils.X_YYYYY.format(orderPrice) +
                                            ", pegPrice=" + Utils.X_YYYYY.format(pegPrice));
                                }
                            } else {
                                toLive = false;
                                log("   peg order should be moved (" + peg2.name() + "). max=" + peg1.m_max +
                                        ", old max=" + peg2.m_max);
                            }

                            break;
                        }
                    }
                    // todo: cancel also orders from the same Currency but to another available currency having bigger gain
                    (toLive ? triTradesToLive : triTradesToDie).add(triTrade);
                }
            }
            m_triTrades = triTradesToLive;
            if(!triTradesToDie.isEmpty()) {
                log(" we have " + triTradesToDie.size() + " orders to die");
                for (TriTradeData triTrade : triTradesToDie) {
                    if (!cancelOrder(triTrade)) {
                        // todo: in case of errors - add back to m_triTrades - will check in next iteration again
                    }
                }
            }
        }
    }

    private boolean cancelOrder(TriTradeData triTrade) throws Exception {
        OrderData order = triTrade.m_order;
        return cancelOrder(order);
    }

    public boolean cancelOrder(OrderData order) throws Exception {
        if (order != null) {
            log(" cancelOrder: " + order);
            if (order.canCancel()) {
                if (Fetcher.SIMULATE_ORDER_EXECUTION) {
                    order.cancel();
                    m_account.releaseOrder(order);
                    return true;
                } else {
                    String orderId = order.m_orderId;
                    CancelOrderData coData = Fetcher.calcelOrder(Exchange.BTCE, orderId);
                    String error = coData.m_error;
                    if (error == null) {
                        // todo: update/merge account data here
                        order.cancel();
                        m_account.releaseOrder(order);
                        return true;
                    } else {
                        log("error in cancel order: " + error + "; " + order);
                        if (error.equals("invalid parameter: order_id")) {
                            log("got");
                        }
                    }
                }
            } else {
                log("error: can not cancel order: " + order);
            }
        }
        return false;
    }

    private void checkOrdersState(IterationData iData, TriangleData triangleData) throws Exception {
        if (!m_triTrades.isEmpty()) {
            for (TriTradeData triTrade : m_triTrades) {
                triTrade.checkState(iData, triangleData);
            }

            forkIfNeeded(); // fork if needed -  for partially filled orders

            // execute checkState() for PEG_FILLED immediately - no need to wait to run MKT orders
            for (TriTradeData triTrade : m_triTrades) {
                TriTradeState state = triTrade.m_state;
                if ((state == TriTradeState.PEG_FILLED) || (state == TriTradeState.MKT1_EXECUTED)) {
                    triTrade.checkState(iData, triangleData); // place MKT order without wait.
                    state = triTrade.m_state;
                    if ((state == TriTradeState.MKT1_PLACED) || (state == TriTradeState.MKT2_PLACED)) {
                        triTrade.checkState(iData, triangleData);
                        state = triTrade.m_state;
                        if (state == TriTradeState.MKT1_EXECUTED) {
                            triTrade.checkState(iData, triangleData);
                            state = triTrade.m_state;
                            if (state == TriTradeState.MKT2_PLACED) {
                                triTrade.checkState(iData, triangleData);
                            }
                        }
                    }
                }
            }
        }
    }

    private void forkIfNeeded() {
        List<TriTradeData> forks = null;
        for (TriTradeData triTrade : m_triTrades) {
            TriTradeData fork = triTrade.forkPegIfNeeded();
            if (fork != null) { // forked
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
        Exchange exchange = Exchange.BTCE;
        //noinspection PointlessBooleanExpression,ConstantConditions
        if(Triplet.ONLY_ONE_ACTIVE_TRIANGLE && !m_triTrades.isEmpty()) {
            return; // do not create new order if some already exist
        }
        for (Map.Entry<Double, OnePegCalcData> entry : bestMap.entrySet()) {
            OnePegCalcData peg = entry.getValue();
            double maxPeg = peg.m_max;
            if (maxPeg > Triplet.s_level) {
                String name = peg.name();
                double pegPrice = peg.calcPegPrice(tops);

                PairDirection pd = peg.m_pair1;
                Pair pair = pd.m_pair;
                boolean direction = pd.m_forward;
                OrderSide side = direction ? OrderSide.BUY : OrderSide.SELL;
                TopData topData = tops.get(pair);

                Currency fromCurrency = pair.currencyFrom(direction);
                double available = getAvailable(fromCurrency);
                double amount = side.isBuy() ? available/pegPrice: available;

                pegPrice = exchange.roundPrice(pegPrice, pair);
                amount = exchange.roundAmount(amount, pair);
                double needPeg = peg.m_need;

                log("#### best: " + Triplet.formatAndPad(maxPeg) + "; " + name + ", pair: " + pair + ", direction=" + direction +
                        ", from=" + fromCurrency + "; available=" + Triplet.format5(available) + "; amount=" + amount + "; side=" + side +
                        "; pegPrice=" + Triplet.format5(pegPrice) +"; needPeg=" + Triplet.format5(needPeg) + "; top: " + topData);

                if (amount > pair.m_minOrderSize) {
                    if(Triplet.SIMULATE_ORDER_EXECUTION) {
                        iData.getNewTradesData(exchange, this); // make sure we have loaded all trades on this iteration
                    }
                    OrderData order = new OrderData(pair, side, pegPrice, amount);
                    boolean ok = Triplet.placeOrder(m_account, order, OrderState.LIMIT_PLACED);
                    log("   place order = " + ok + ":  " + order);
                    if (ok) {
                        TriTradeData ttData = new TriTradeData(order, peg);
                        m_triTrades.add(ttData);
                        if (Triplet.ONLY_ONE_ACTIVE_TRIANGLE) {
                            break; // do not create more orders
                        }
                    } else {
                        log("   place order unsuccessful: " + order);
                        // do nothing special here
                    }
                } else {
                    log(" no funds for NEW order: min order size=" + pair.m_minOrderSize +
                            ", amount " + Triplet.format4(amount) + " " + fromCurrency + " : " + m_account);
                }
            }
        }
    }

    private double getAvailable(Currency currency) {
        return m_account.available(currency) * Triplet.USE_ACCOUNT_FUNDS;
    }

    private static void log(String s) {
        Log.log(s);
    }
}
