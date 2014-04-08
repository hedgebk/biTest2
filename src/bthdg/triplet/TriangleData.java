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

    public void forkAndCheckFilledIfNeeded(IterationData iData, TriTradeData triTradeData, OrderData order, TriTradeState stateForFilled) throws Exception {
        // order can become partially filled - need to fork check
        TriTradeData fork = triTradeData.forkIfNeeded();
        if (fork != null) {
            triTradeData.log("  forked. adding non executed part to triTrades list. " + fork);
            m_triTrades.add(fork); // add non-executed part to the list
        }
        if (order.isFilled()) { // reprocess immediately
            triTradeData.log("  order is filled - reprocess " + triTradeData);
            triTradeData.setState(stateForFilled);
            triTradeData.checkState(iData, this); // <---------------------------------
        }
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

            double minOrderToCreate = Btce.minOrderToCreate(pair);
            if (amount > minOrderToCreate) {
                iData.getNewTradesData(Exchange.BTCE, this); // make sure we have loaded all trades on this iteration
                OrderData order = new OrderData(pair, side, bracketPrice, amount);
                OrderData.OrderPlaceStatus ok = Triplet.placeOrder(m_account, order, OrderState.LIMIT_PLACED, iData);
                log("   place order = " + ok + ":  " + order);
                if (ok == OrderData.OrderPlaceStatus.OK) {
                    TriTradeData ttData = new TriTradeData(order, bestPeg);
                    m_triTrades.add(ttData);
                }
            } else {
                log(" no funds for NEW bracket order: minOrderToCreate=" + minOrderToCreate +
                        ", amount " + Exchange.BTCE.roundAmount(amount, pair) + " " + fromCurrency + " : " + m_account);
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
//            List<TriTradeData> changed = null;
            List<TriTradeData> triTrades = new ArrayList<TriTradeData>(m_triTrades); // iterate over the copy - avoid concurrent modifications
            for (TriTradeData triTrade : triTrades) {
//                TriTradeState oldState = triTrade.m_state;
                triTrade.checkState(iData, triangleData); // <---------------------------------
//                TriTradeState newState = triTrade.m_state;
//                if (oldState != newState) { // state changed
//                    if (changed == null) {
//                        changed = new ArrayList<TriTradeData>();
//                    }
//                    triTrade.log(" triTrade state changed (" + oldState + "->" + newState + ") save for no-delay re-process");
//                    changed.add(triTrade);
//                }
            }
//            List<TriTradeData> reprocess = forkIfNeeded(m_triTrades);// fork if needed -  for partially filled orders
//            if (reprocess != null) {
//                if (changed == null) {
//                    changed = new ArrayList<TriTradeData>();
//                }
//                changed.addAll(reprocess);
//            }
//            noDelayReprocess(iData, triangleData, changed);
        }
    }

//    private void noDelayReprocess(IterationData iData, TriangleData triangleData, List<TriTradeData> reprocess) throws Exception {
//        if (reprocess != null) {
//            log(" we have " + reprocess.size() + " triTrades for no-delay re-process");
//            // execute checkState() for PEG_FILLED immediately - no need to wait to run MKT orders
//            for (TriTradeData triTrade : reprocess) {
//                noDelayReprocessOne(iData, triangleData, triTrade);
//            }
//        }
//    }

//    private void noDelayReprocessOne(IterationData iData, TriangleData triangleData, TriTradeData triTrade) throws Exception {
//        triTrade.log("  try no-delay re-process for: " + triTrade);
//        TriTradeState state = triTrade.m_state;
//        if ((state == TriTradeState.PEG_FILLED) || (state == TriTradeState.MKT1_EXECUTED)) {
//            triTrade.checkState(iData, triangleData); // place MKT order without wait.
//            state = triTrade.m_state;
//            if (((state == TriTradeState.MKT1_PLACED) && !triTrade.isMktOrderPartiallyFilled(0))
//                    || ((state == TriTradeState.MKT2_PLACED) && !triTrade.isMktOrderPartiallyFilled(1))) {
//                triTrade.checkState(iData, triangleData);
//                state = triTrade.m_state;
//                if (state == TriTradeState.MKT1_EXECUTED) {
//                    triTrade.checkState(iData, triangleData);
//                    state = triTrade.m_state;
//                    if ((state == TriTradeState.MKT2_PLACED) && !triTrade.isMktOrderPartiallyFilled(1)) {
//                        triTrade.checkState(iData, triangleData);
//                    }
//                }
//            }
//        }
//    }

    private static List<TriTradeData> forkIfNeeded(List<TriTradeData> triTrades) {
        List<TriTradeData> forks = null;
        List<TriTradeData> reprocess = null;
        for (TriTradeData triTrade : triTrades) {
            TriTradeData forkRemainded = triTrade.forkIfNeeded();
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
            triTrades.addAll(forks);
        }
        return reprocess;
    }

    public void checkNew(IterationData iData, TreeMap<Double, OnePegCalcData> bestMap, Map<Pair, TopData> tops) throws Exception {
        Exchange exchange = Exchange.BTCE;
        //noinspection PointlessBooleanExpression,ConstantConditions
        if(Triplet.ONLY_ONE_ACTIVE_TRIANGLE && !m_triTrades.isEmpty()) {
            return; // do not create new order if some already exist
        }
//        ArrayList<TriTradeData> reprocess = null;
        for (Map.Entry<Double, OnePegCalcData> entry : bestMap.entrySet()) {
            OnePegCalcData peg = entry.getValue();
            double maxPeg = peg.m_max;
            if (maxPeg > Triplet.s_level) {
                TriTradeData newTriTrade = createNewOne(iData, tops, exchange, peg, maxPeg);
                if (newTriTrade != null) { // order placed
                    m_triTrades.add(newTriTrade);

                    forkAndCheckFilledIfNeeded(iData, newTriTrade, newTriTrade.m_order, TriTradeState.PEG_FILLED);

//                    OrderData order = newTriTrade.m_order;
//
//                    // todo: in case of partially/filled - better put reprocess code immediately here - no need to delay
//
//                    // we may have PARTIALLY/FILLED peg order just at the time of placing - no need to wait - reprocess immediately - on this iteration
//                    if ((order.m_status == OrderStatus.FILLED) || (order.m_status == OrderStatus.PARTIALLY_FILLED)) {
//                        if (reprocess == null) {
//                            reprocess = new ArrayList<TriTradeData>();
//                        }
//                        reprocess.add(newTriTrade);
//                    }
                    if (Triplet.ONLY_ONE_ACTIVE_TRIANGLE) {
                        break; // do not create more orders
                    }
                }
            }
        }

//        if (reprocess != null) {
//            log("  we have " + reprocess.size() + " triTrades which have PARTIALLY/FILLED peg order just at the time of placing - reprocess them");
//            List<TriTradeData> forked = forkIfNeeded(reprocess);// reprocess will be updated with forks if needed
//            if (forked != null) {
//                log("   + forked " + forked.size() + " triTrades ");
//            }
//            noDelayReprocess(iData, this, reprocess);
//        }
    }

    private TriTradeData createNewOne(IterationData iData, Map<Pair, TopData> tops, Exchange exchange,
                                      OnePegCalcData peg, double maxPeg) throws Exception {
        String name = peg.name();
        double pegPrice = peg.calcPegPrice(tops);
        double mkt1Price = peg.calcMktPrice(tops, 0);
        double mkt2Price = peg.calcMktPrice(tops, 1);

        PairDirection pd = peg.m_pair1;
        Pair pair = pd.m_pair;
        boolean direction = pd.m_forward;
        OrderSide side = direction ? OrderSide.BUY : OrderSide.SELL;
        TopData topData = tops.get(pair);

        Currency fromCurrency = pair.currencyFrom(direction);
        double available = getAvailable(fromCurrency);
        String availableStr = exchange.roundAmountStr(available, pair);
        double amount = side.isBuy() ? available/pegPrice: available;

        pegPrice = exchange.roundPrice(pegPrice, pair);
        String pegPriceStr = exchange.roundPriceStr(pegPrice, pair);
        amount = exchange.roundAmount(amount, pair);
        String amountStr = exchange.roundAmountStr(amount, pair);
        double needPeg = peg.m_need;
        String needPegStr = exchange.roundPriceStr(needPeg, pair);

        log("#### best: " + Triplet.formatAndPad(maxPeg) + "; " + name + ", pair: " + pair + ", direction=" + direction +
                ", from=" + fromCurrency + "; available=" + availableStr + "; amount=" + amountStr + "; side=" + side +
                "; pegPrice=" + pegPriceStr +"; needPeg=" + needPegStr + "; top: " + topData.toString(Exchange.BTCE, pair));

        double minOrderToCreate = Btce.minOrderToCreate(pair);
        if (amount >= minOrderToCreate) {
            String mkt1PriceStr = exchange.roundPriceStr(mkt1Price, pair);
            String mkt2PriceStr = exchange.roundPriceStr(mkt2Price, pair);
            log("   expected prices: " + pegPriceStr + " -> " + mkt1PriceStr + " -> " + mkt2PriceStr);

            // todo: try price between peg and need, but not for all pairs
//            if( (topData.m_ask > needPeg) && (needPeg > topData.m_bid)) {
//                log("   ! needPegPrice is between mkt edges: " + topData.m_ask + " - " + needPegStr + " - " + topData.m_bid);
//                if( new Random().nextDouble() < 0.12 ) { // 12% probability
//                    double price = (pegPrice + needPeg) / 2;
//                    pegPrice = exchange.roundPrice(price, pair);
//                    log("     ! try price between peg and need: " + pegPrice);
//                }
//            }

            if (Triplet.SIMULATE_ORDER_EXECUTION) {
                iData.getNewTradesData(exchange, this); // make sure we have loaded all trades on this iteration
            }
            OrderData order = new OrderData(pair, side, pegPrice, amount);
            TriTradeData ttData = new TriTradeData(order, peg);
            OrderData.OrderPlaceStatus ok = Triplet.placeOrder(m_account, order, OrderState.LIMIT_PLACED, iData);
            ttData.log("START:  place order = " + ok + ":  " + order.toString(Exchange.BTCE));
            if (ok == OrderData.OrderPlaceStatus.OK) {
                return ttData;
            }
            ttData.log("   place order unsuccessful: " + order.toString(Exchange.BTCE)); // do nothing special here
        } else {
            log(" small amount " + amountStr + " for NEW order; minOrderToCreate=" +
                    exchange.roundAmountStr(minOrderToCreate, pair) + " " + fromCurrency + " : " + m_account);
            // todo: if we have other non started TriTradeData with lower maxPeg holding required currency - cancel it
        }
        return null;
    }

    private double getAvailable(Currency currency) {
        return m_account.available(currency) * Triplet.USE_ACCOUNT_FUNDS;
    }

    private static void log(String s) {
        Log.log(s);
    }
}
