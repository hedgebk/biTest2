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

        long millis = iData.millisFromTopsLoad();
        if (millis > 900) { // much time passed from tops loading to consider new triangles
            iData.resetTops(); // recalculate top/best data
            System.out.println(" !! much time passed from tops loading (" + millis + "ms) - recalculate top/best data");
        }

        TopsData tops = iData.getTops();
        TrianglesCalcData trianglesCalc = TrianglesCalcData.calc(tops);
        log(trianglesCalc.str());

        TreeMap<Double, OnePegCalcData> bestMap = trianglesCalc.findBestMap();
        checkOrdersToLive(bestMap, tops, iData);

        if (Triplet.s_stopRequested) {
            System.out.println("stopRequested: do not check for NEW - process only existing");
        } else if (Triplet.s_notEnoughFundsCounter > 5) {
            System.out.println("warning: notEnoughFundsCounter=" + Triplet.s_notEnoughFundsCounter + " - need account sync: do not check for NEW - process only existing");
        } else {
            millis = iData.millisFromTopsLoad();
            if (millis > 1000) { // much time passed from last Tops loading to consider new triangles
                iData.resetTops(); // recalculate top/best data
                System.out.println(" !! much time passed from tops loading (" + millis + "ms) - recalculate top/best data");

                tops = iData.getTops();
                trianglesCalc = TrianglesCalcData.calc(tops);
                log(trianglesCalc.str());

                bestMap = trianglesCalc.findBestMap();
            }
//                checkMkt(iData, trianglesCalc, tops);
            checkNew(iData, bestMap, tops);
//            checkBrackets(iData, bestMap, tops, m_account);
        }
    }

    private void checkMkt(IterationData iData, TrianglesCalcData calc, TopsData tops) {
        calc.checkMkt();
    }

    public void forkAndCheckFilledIfNeeded(IterationData iData, TriTradeData triTradeData, OrderData order, TriTradeState stateForFilled) throws Exception {
        // order can become partially filled - need to fork check
        TriTradeData fork = triTradeData.forkIfNeeded();
        if (fork != null) {
            triTradeData.log("  forked. adding non executed part to triTrades list. " + fork);
            m_triTrades.add(fork); // add non-executed part to the list
        }
        if (order.isFilled()) { // reprocess immediately
            triTradeData.m_waitMktOrderStep = 0;
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
                    TriTradeData ttData = new TriTradeData(order, bestPeg, false);
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
                } else { // only PEG_PLACED here
                    boolean toLive = false;
                    if( Triplet.s_stopRequested ) {
                        triTrade.log("   peg order should be cancelled - stopRequested");
                    } else {
                        toLive = checkPegToLive(bestMap, tops, triTrade, toLive);
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
                        log("  order cancel error - add back to m_triTrades - will check in next iteration again: " + triTrade.toString(Exchange.BTCE));
                        m_triTrades.add(triTrade); // in case of errors - add back to m_triTrades - will check in next iteration again
                    }
                }
            }
        }
    }

    private boolean checkPegToLive(TreeMap<Double, OnePegCalcData> bestMap, Map<Pair, TopData> tops, TriTradeData triTrade, boolean toLive) {
        OnePegCalcData tradePeg = triTrade.m_peg;
        boolean doMktOffset = triTrade.m_doMktOffset;
        for (Map.Entry<Double, OnePegCalcData> entry : bestMap.entrySet()) {
            OnePegCalcData peg1 = entry.getValue();
            if (peg1.equals(tradePeg)) {
                double pegMax = doMktOffset ? peg1.m_max10 : peg1.m_max;
                if (pegMax > Triplet.s_level) {
                    double pegPrice = peg1.calcPegPrice(tops);
                    OrderData order = triTrade.m_order;
                    double orderPrice = order.m_price;

                    double ratio1 = tradePeg.pegRatio1(tops, m_account); // commission is applied to ratio
                    double ratio2 = doMktOffset
                                ? tradePeg.mktRatio2(tops, m_account, Triplet.MINUS_MKT_OFFSET)
                                : tradePeg.mktRatio2(tops, m_account);
                    double ratio3 = doMktOffset
                        ? tradePeg.mktRatio3(tops, m_account, Triplet.MINUS_MKT_OFFSET )
                        : tradePeg.mktRatio3(tops, m_account); // commission is applied to ratio
                    double ratio = ratio1 * ratio2 * ratio3;
                    triTrade.log("checkPegToLive() ratio1=" + ratio1 + "; ratio2=" + ratio2 + "; ratio3=" + ratio3 + "; ratio=" + ratio);

                    double priceDif = Math.abs(pegPrice - orderPrice); // like 16.220010999999998 and 16.22001
                    double minOurPriceStep = Btce.minOurPriceStep(tradePeg.m_pair1.m_pair); // like 0.00001
                    double minExchPriceStep = Btce.minExchPriceStep(tradePeg.m_pair1.m_pair); // like 0.00001
                    if (priceDif < minOurPriceStep + minExchPriceStep) { // check if peg order needs to be moved - do not jump over itself - do not move if price change is too small
                        if (ratio < 1) {
                            log("warning: ratio < 1" + ratio);
                        }
                        toLive = true;
                        triTrade.log("  peg to live (" + tradePeg.name() + "): pegMax=" + pegMax + "; tradePegMax=" + tradePeg.m_max +
                                "; priceDif=" + order.roundPriceStr(Exchange.BTCE, priceDif) + ", pegPrice=" + pegPrice + "; order=" + order);
                    } else {
                        toLive = false;
                        triTrade.log("   peg order should be moved (" + tradePeg.name() + "). orderPrice=" + orderPrice +
                                ", pegPrice=" + pegPrice + "; priceDif=" + Utils.X_YYYYYYYY.format(priceDif) +
                                "; minOurPriceStep=" + Utils.X_YYYYYYYY.format(minOurPriceStep) +
                                "; minExchPriceStep=" + Utils.X_YYYYYYYY.format(minExchPriceStep)
                        );
                    }
                } else {
                    toLive = false;
                    triTrade.log("   peg order should be REmoved (" + tradePeg.name() + "). max=" + pegMax +
                            ", old max=" + tradePeg.m_max + "; level=" + Triplet.s_level);
                }
                break;
            }
        }
        return toLive;
    }

    private boolean cancelOrder(TriTradeData triTrade, IterationData iData) throws Exception {
        OrderData order = triTrade.m_order;
        return cancelOrder(order, iData);
    }

    public boolean cancelOrder(OrderData order, IterationData iData) throws Exception {
        if (order != null) {
            log(" cancelOrder " + iData.millisFromStart() + "ms: " + order);
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
            List<TriTradeData> triTrades = new ArrayList<TriTradeData>(m_triTrades); // iterate over the copy - avoid concurrent modifications
            for (TriTradeData triTrade : triTrades) {
                triTrade.checkState(iData, triangleData); // <---------------------------------
                triTrade.m_iterationsNum++;
            }
        }
    }

    public void checkNew(IterationData iData, TreeMap<Double, OnePegCalcData> bestMap, Map<Pair, TopData> tops) throws Exception {
        if (m_triTrades.size() >= Triplet.NUMBER_OF_ACTIVE_TRIANGLES) {
            log("do not create new orders - NUMBER_OF_ACTIVE_TRIANGLES=" + Triplet.NUMBER_OF_ACTIVE_TRIANGLES + " reached");
            return; // do not create new order - NUMBER_OF_ACTIVE_TRIANGLES reached
        }
//        boolean oneStarted = checkNew(iData, bestMap, tops, false);
//        if (Triplet.TRY_WITH_MKT_OFFSET && !oneStarted) {  // no max trades started - try max10
//            checkNew(iData, bestMap, tops, true);
//        }

        // resort by max10 with preferred
        TreeMap<Double, OnePegCalcData> best10Map = new TreeMap<Double, OnePegCalcData>();
        for (OnePegCalcData opcd : bestMap.values()) {
            double key = 1 / opcd.m_max10;
            Pair pair = opcd.m_pair1.m_pair;
            if((pair == Pair.BTC_USD) || (pair == Pair.LTC_USD)) {
                key /= 2; // prefer pairs with good liquidity
            }
            if(opcd.m_max10 - opcd.m_max > 0.1) {
                key /= 1.5; // prefer pairs big max-max10 diff
            }
            best10Map.put(key, opcd);
        }

        // resort by max with preferred
        TreeMap<Double, OnePegCalcData> bestMapPref = new TreeMap<Double, OnePegCalcData>();
        for (OnePegCalcData opcd : bestMap.values()) {
            double key = 1 / opcd.m_max;
            Pair pair = opcd.m_pair1.m_pair;
            if((pair == Pair.BTC_USD) || (pair == Pair.LTC_USD)) {
                key /= 1.5; // prefer pairs with good liquidity
            }
            if(pair == Pair.LTC_BTC) {
                key /= 1.25; // prefer pairs with good liquidity
            }
            bestMapPref.put(key, opcd);
        }

        boolean oneStarted = false;
        if (Triplet.TRY_WITH_MKT_OFFSET ) {
            oneStarted = checkNew(iData, best10Map, tops, true); // try max10 with prefer sort
        }
        if (!oneStarted) {
            oneStarted = checkNew(iData,
                    Triplet.PREFER_LIQUID_PAIRS ? bestMapPref : bestMap,
                    tops, false); // then try regular peg>level
            if (Triplet.TRY_WITH_MKT_OFFSET && !oneStarted) {
                checkNew(iData, bestMap, tops, true); // then try the rest max10
            }
        }
    }

    private boolean checkNew(IterationData iData, TreeMap<Double, OnePegCalcData> bestMap, Map<Pair, TopData> tops,
                             boolean doMktOffset) throws Exception {
        boolean oneStarted = false;
        for (Map.Entry<Double, OnePegCalcData> entry : bestMap.entrySet()) {
            long millis = iData.millisFromTopsLoad();
            if (millis > 1500) { // much time passed from tops loading to consider new triangles
                System.out.println(" !! much time passed from tops loading (" + millis + "ms) - no more new");
                break;
            }
            OnePegCalcData peg = entry.getValue();
            double maxPeg = doMktOffset ? peg.m_max10 : peg.m_max;
            double level = Triplet.s_level;
            Pair startPair = peg.m_pair1.m_pair;
            if ((startPair == Pair.LTC_BTC) || (startPair == Pair.BTC_USD) || (startPair == Pair.LTC_USD)) {
                level -= 0.02; // reduce level for pairs with higher liquidity
            }

            if(maxPeg > 100.4) {
                log("BEST: max" + (doMktOffset ? "" : "*") + "=" + peg.m_max +
                        ", max10" + (doMktOffset ? "*" : "") + "=" + peg.m_max10 +
                        "; level=" + level + "; need=" + peg.m_need + ": " + peg.name());
            }
            if (maxPeg > level) {
                if (m_triTrades.size() >= Triplet.NUMBER_OF_ACTIVE_TRIANGLES) {
                    log("do not create new order - NUMBER_OF_ACTIVE_TRIANGLES=" + Triplet.NUMBER_OF_ACTIVE_TRIANGLES + " reached");
                    continue; // do not create more orders
                }
                if (oneStarted) {
                    if (Triplet.START_ONE_TRIANGLE_PER_ITERATION) {
                        log("do not create more orders - START_ONE_TRIANGLE_PER_ITERATION");
                        continue; // do not start multiple trades in one iteration
                    }
                }
                TriTradeData newTriTrade = createNewOne(iData, tops, peg, doMktOffset);
                if (newTriTrade != null) { // order placed
                    oneStarted = true;
                    m_triTrades.add(newTriTrade);
                    forkAndCheckFilledIfNeeded(iData, newTriTrade, newTriTrade.m_order, TriTradeState.PEG_JUST_FILLED);
                }
            }
        }
        return oneStarted;
    }

    private TriTradeData createNewOne(IterationData iData, Map<Pair, TopData> tops,
                                      OnePegCalcData peg, boolean doMktOffset) throws Exception {
        double maxPeg = doMktOffset ? peg.m_max10 : peg.m_max;
        String name = peg.name();
        double pegPrice = peg.calcPegPrice(tops);
        double mkt1Price = doMktOffset ? peg.calcMktPrice(tops, 0, Triplet.MINUS_MKT_OFFSET) : peg.calcMktPrice(tops, 0);
        double mkt2Price = doMktOffset ? peg.calcMktPrice(tops, 1, Triplet.MINUS_MKT_OFFSET) : peg.calcMktPrice(tops, 1);

        PairDirection pd1 = peg.m_pair1;
        Pair pair1 = pd1.m_pair;
        boolean direction = pd1.m_forward;
        OrderSide side = pd1.getSide();
        TopData top = tops.get(pair1);
        TopData top2 = tops.get(peg.m_pair2.m_pair);
        TopData top3 = tops.get(peg.m_pair3.m_pair);

        Currency fromCurrency = pair1.currencyFrom(direction);
        double available = getAvailable(fromCurrency);
        String availableStr = Exchange.BTCE.roundAmountStr(available, pair1);
        double amount = side.isBuy() ? available / pegPrice : available;

        pegPrice = Exchange.BTCE.roundPrice(pegPrice, pair1);
        String pegPriceStr = Exchange.BTCE.roundPriceStr(pegPrice, pair1);
        amount = Exchange.BTCE.roundAmount(amount, pair1);
        String amountStr = Exchange.BTCE.roundAmountStr(amount, pair1);
        double needPeg = peg.m_need;
        String needPegStr = Exchange.BTCE.roundPriceStr(needPeg, pair1);

        log("#### " +
                (doMktOffset ? "mkt-offset" : "best") +
                ": " + Triplet.formatAndPad(maxPeg) + "; " + name + ", pair: " + pair1 + ", direction=" + direction +
                ", from=" + fromCurrency + "; available=" + availableStr + "; amount=" + amountStr + "; side=" + side +
                "; pegPrice=" + pegPriceStr + "; needPeg=" + needPegStr + "; top: " + top.toString(Exchange.BTCE, pair1));

        double minOrderToCreate = Btce.minOrderToCreate(pair1);
        if (amount >= minOrderToCreate) {
            Pair pair2 = peg.m_pair2.m_pair;
            String mkt1PriceStr = Exchange.BTCE.roundPriceStr(mkt1Price, pair2);
            Pair pair3 = peg.m_pair3.m_pair;
            String mkt2PriceStr = Exchange.BTCE.roundPriceStr(mkt2Price, pair3);

            // todo: try price between peg and need, but not for all pairs
//            if( (top.m_ask > needPeg) && (needPeg > top.m_bid)) {
//                log("   ! needPegPrice is between mkt edges: " + top.m_ask + " - " + needPegStr + " - " + top.m_bid);
//                if( new Random().nextDouble() < 0.12 ) { // 12% probability
//                    double price = (pegPrice + needPeg) / 2;
//                    pegPrice = exchange.roundPrice(price, pair);
//                    log("     ! try price between peg and need: " + pegPrice);
//                }
//            }

            if (Triplet.SIMULATE_ORDER_EXECUTION) {
                iData.getNewTradesData(Exchange.BTCE, this); // make sure we have loaded all trades on this iteration
            }
            OrderData order = new OrderData(pair1, side, pegPrice, amount);
            TriTradeData ttData = new TriTradeData(order, peg, doMktOffset);
            String bid1Str = Exchange.BTCE.roundPriceStr(top.m_bid, pair1);
            String ask1Str = Exchange.BTCE.roundPriceStr(top.m_ask, pair1);
            String bid2Str = Exchange.BTCE.roundPriceStr(top2.m_bid, pair2);
            String ask2Str = Exchange.BTCE.roundPriceStr(top2.m_ask, pair2);
            String bid3Str = Exchange.BTCE.roundPriceStr(top3.m_bid, pair3);
            String ask3Str = Exchange.BTCE.roundPriceStr(top3.m_ask, pair3);
            ttData.log("   expected prices: [" + bid1Str + "; " + pegPriceStr  + "; " + ask1Str  + "] -> " +
                                           "[" + bid2Str + "; " + mkt1PriceStr + "; " + ask2Str + "] -> " +
                                           "[" + bid3Str + "; " + mkt2PriceStr + "; " + ask3Str + "]");
            OrderData.OrderPlaceStatus ok = Triplet.placeOrder(m_account, order, OrderState.LIMIT_PLACED, iData);
            ttData.log("START:  place order = " + ok + ":  " + order.toString(Exchange.BTCE));
            if (ok == OrderData.OrderPlaceStatus.OK) {
                return ttData;
            }
            ttData.log("   place order unsuccessful: " + order.toString(Exchange.BTCE)); // do nothing special here
        } else {
            log(" small amount " + amountStr + " for NEW order; minOrderToCreate=" +
                    Exchange.BTCE.roundAmountStr(minOrderToCreate, pair1) + " " + fromCurrency + " : " + m_account);
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
