package bthdg.triplet;

import bthdg.*;
import bthdg.Currency;
import bthdg.exch.*;

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

        TopsData tops = iData.getTops();
        TrianglesCalcData trianglesCalc = TrianglesCalcData.calc(tops);
        log(trianglesCalc.str());

        TreeMap<Double,OnePegCalcData> bestMap = trianglesCalc.findBestMap();
        checkOrdersToLive(bestMap, tops, iData);

        if( Triplet.s_stopRequested ) {
            System.out.println("stopRequested: do not check for NEW - process only existing");
        } else {
            checkNew(iData, bestMap, tops);
        }

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
                OrderData.OrderPlaceStatus ok = Triplet.placeOrder(m_account, order, OrderState.LIMIT_PLACED, iData);
                log("   place order = " + ok + ":  " + order);
                if (ok == OrderData.OrderPlaceStatus.OK) {
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

    private void checkOrdersToLive(TreeMap<Double, OnePegCalcData> bestMap, Map<Pair, TopData> tops, IterationData iData) throws Exception {
        if (!m_triTrades.isEmpty()) {
            List<TriTradeData> triTradesToLive = new ArrayList<TriTradeData>();
            List<TriTradeData> triTradesToDie = new ArrayList<TriTradeData>();

            for (TriTradeData triTrade : m_triTrades) {
                if (triTrade.m_state == TriTradeState.DONE) { // just do not add what is DONE
                    triTrade.log(" we have done with: " + triTrade);
                } else if (triTrade.m_state == TriTradeState.ERROR) { // just do not add what is ERROR
                    triTrade.log(" we have ERROR with: " + triTrade);
                } else if (triTrade.m_state == TriTradeState.CANCELED) { // just do not add what is CANCELED
                    triTrade.log(" we have CANCELED with: " + triTrade);
                } else if (triTrade.m_state != TriTradeState.PEG_PLACED) { // we are not in init state
                    triTradesToLive.add(triTrade);
                    triTrade.log("  order to live (not init state): " + triTrade);
                } else {
                    OnePegCalcData peg2 = triTrade.m_peg;
                    boolean toLive = false;
                    for (Map.Entry<Double, OnePegCalcData> entry : bestMap.entrySet()) {
                        OnePegCalcData peg1 = entry.getValue();
                        if (peg1.equals(peg2)) {
                            if (peg1.m_max > Triplet.s_level) {
                                double pegPrice = peg1.calcPegPrice(tops);
                                OrderData order = triTrade.m_order;
                                double orderPrice = order.m_price;

                                double priceDif = Math.abs(pegPrice - orderPrice); // like 16.220010999999998 and 16.22001
                                double minPriceStep = Btce.minPriceStep(peg2.m_pair1.m_pair); // like 0.00001
                                if (priceDif < minPriceStep * 1.5) { // check if peg order needs to be moved - do not move if price change is too small
                                    toLive = true;
                                    triTrade.log("  peg to live (" + peg2.name() + "): max1=" + peg1.m_max + "; max2=" + peg2.m_max +
                                            "; priceDif=" + order.roundPriceStr(Exchange.BTCE, priceDif) + ", pegPrice=" + pegPrice + "; order=" + order);
                                } else {
                                    toLive = false;
                                    triTrade.log("   peg order should be moved (" + peg2.name() + "). orderPrice=" + order.roundPriceStr(Exchange.BTCE) +
                                            ", pegPrice=" + Utils.X_YYYYY.format(pegPrice));
                                }
                            } else {
                                toLive = false;
                                triTrade.log("   peg order should be moved (" + peg2.name() + "). max=" + peg1.m_max +
                                        ", old max=" + peg2.m_max + "; level=" + Triplet.s_level);
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
                    if (!cancelOrder(triTrade, iData)) {
                        m_triTrades.add(triTrade); // in case of errors - add back to m_triTrades - will check in next iteration again
                    }
                }
            }
        }
    }

    private boolean cancelOrder(TriTradeData triTrade, IterationData iData) throws Exception {
        OrderData order = triTrade.m_order;
        return cancelOrder(order, iData);
    }

    public boolean cancelOrder(OrderData order, IterationData iData) throws Exception {
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
                        order.cancel();
                        m_account.releaseOrder(order);
                        iData.resetLiveOrders(); // clean cached data
                        coData.m_funds.compareFunds(m_account);
                        return true;
                    } else {
                        log("error in cancel order: " + error + "; " + order);
                        if (error.equals("invalid parameter: order_id")) {
                            log("got");
                        }
                        // todo - looks 'bad status' here meant - order already cancelled - handle specially
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
            List<TriTradeData> changed = null;
            for (TriTradeData triTrade : m_triTrades) {
                TriTradeState oldState = triTrade.m_state;
                triTrade.checkState(iData, triangleData); // <---------------------------------
                TriTradeState newState = triTrade.m_state;
                if (oldState != newState) { // state changed
                    if (changed == null) {
                        changed = new ArrayList<TriTradeData>();
                    }
                    triTrade.log(" triTrade state changed (" + oldState + "->" + newState + ") save for no-delay re-process");
                    changed.add(triTrade);
                }
            }
            List<TriTradeData> reprocess = forkIfNeeded();// fork if needed -  for partially filled orders
            if (reprocess != null) {
                if (changed == null) {
                    changed = new ArrayList<TriTradeData>();
                }
                changed.addAll(reprocess);
            }
            noDelayReprocess(iData, triangleData, changed);
        }
    }

    private void noDelayReprocess(IterationData iData, TriangleData triangleData, List<TriTradeData> changed) throws Exception {
        if (changed != null) {
            log(" we have " + changed.size() + " triTrades for no-delay re-process");
            // execute checkState() for PEG_FILLED immediately - no need to wait to run MKT orders
            for (TriTradeData triTrade : changed) {
                triTrade.log("  try no-delay re-process for: " + triTrade);
                TriTradeState state = triTrade.m_state;
                if ((state == TriTradeState.PEG_FILLED) || (state == TriTradeState.MKT1_EXECUTED)) {
                    triTrade.checkState(iData, triangleData); // place MKT order without wait.
                    state = triTrade.m_state;
                    if (((state == TriTradeState.MKT1_PLACED) && !triTrade.isMktOrderPartiallyFilled(0))
                            || ((state == TriTradeState.MKT2_PLACED) && !triTrade.isMktOrderPartiallyFilled(1))) {
                        triTrade.checkState(iData, triangleData);
                        state = triTrade.m_state;
                        if (state == TriTradeState.MKT1_EXECUTED) {
                            triTrade.checkState(iData, triangleData);
                            state = triTrade.m_state;
                            if ((state == TriTradeState.MKT2_PLACED) && !triTrade.isMktOrderPartiallyFilled(1)) {
                                triTrade.checkState(iData, triangleData);
                            }
                        }
                    }
                }
            }
        }
    }

    private List<TriTradeData> forkIfNeeded() {
        List<TriTradeData> reprocess = null;
        List<TriTradeData> forks = null;
        for (TriTradeData triTrade : m_triTrades) {
            OrderData order = triTrade.m_order;
            TriTradeState state = triTrade.m_state;
            TriTradeData forkRemainded = null;
            switch (state) {
                case PEG_PLACED:
                    if(order.isPartiallyFilled(Exchange.BTCE)) {
                        triTrade.log("PEG order is partially filled - splitting: " + order);
                        forkRemainded = triTrade.forkPeg();
                    }
                    break;
                case MKT1_PLACED:
                    if(triTrade.isMktOrderPartiallyFilled(0)) {
                        triTrade.log("MKT1 order is partially filled - splitting: " + triTrade.getMktOrder(0));
                        forkRemainded = triTrade.forkMkt(1);
                    }
                    break;
                case MKT2_PLACED:
                    if(triTrade.isMktOrderPartiallyFilled(1)) {
                        triTrade.log("MKT2 order is partially filled - splitting: " + triTrade.getMktOrder(1));
                        forkRemainded = triTrade.forkMkt(2);
                    }
                    break;
                default:
                    if(order.isPartiallyFilled(Exchange.BTCE) ||
                       triTrade.isMktOrderPartiallyFilled(0) ||
                       triTrade.isMktOrderPartiallyFilled(1)) {
                        triTrade.log("warning: unexpected state - some order is partially filled: " + triTrade);
                    }
            }
            if (forkRemainded != null) { // forked
                if (forks == null) {
                    forks = new ArrayList<TriTradeData>();
                }
                forks.add(forkRemainded);
                if (reprocess == null) {
                    reprocess = new ArrayList<TriTradeData>();
                }
                reprocess.add(triTrade);
            }
        }
        if (forks != null) {
            m_triTrades.addAll(forks);
        }
        return reprocess;
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

                if( (topData.m_ask > needPeg) && (needPeg > topData.m_bid)) {
                    log("   ! needPegPrice is between mkt edges: " + topData.m_ask + " - " + needPeg + " - " + topData.m_bid);
                    if( new Random().nextDouble() < 0.12 ) { // 12% probability
                        double price = (pegPrice + needPeg) / 2;
                        pegPrice = exchange.roundPrice(price, pair);
                        log("     try price between peg and need: " + pegPrice);
                    }
                }

                if (amount > pair.m_minOrderSize) {
                    if(Triplet.SIMULATE_ORDER_EXECUTION) {
                        iData.getNewTradesData(exchange, this); // make sure we have loaded all trades on this iteration
                    }
                    OrderData order = new OrderData(pair, side, pegPrice, amount);
                    TriTradeData ttData = new TriTradeData(order, peg);
                    OrderData.OrderPlaceStatus ok = Triplet.placeOrder(m_account, order, OrderState.LIMIT_PLACED, iData);
                    ttData.log("START:  place order = " + ok + ":  " + order);
                    if (ok == OrderData.OrderPlaceStatus.OK) {
                        m_triTrades.add(ttData);
                        if (Triplet.ONLY_ONE_ACTIVE_TRIANGLE) {
                            break; // do not create more orders
                        }
                    } else {
                        ttData.log("   place order unsuccessful: " + order); // do nothing special here
                    }
                } else {
                    log(" small amount " + Triplet.format4(amount) + " for NEW order; minAmountStep=" +
                            Triplet.format4(pair.m_minOrderSize) + " " + fromCurrency + " : " + m_account);
                    // todo: if we have other non started TriTradeData with lower maxPeg holding required currency - cancel it
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
