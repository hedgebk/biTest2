package bthdg.triplet;

import bthdg.*;
import bthdg.exch.Currency;
import bthdg.exch.*;
import bthdg.util.Utils;

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

        if (Triplet.USE_TRI_MKT) {
            checkTriMkt(trianglesCalc);
        }

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

            if (m_triTrades.size() >= Triplet.NUMBER_OF_ACTIVE_TRIANGLES) {
                log("do not create new orders - NUMBER_OF_ACTIVE_TRIANGLES=" + Triplet.NUMBER_OF_ACTIVE_TRIANGLES + " reached");
            } else {
//                checkMkt(iData, trianglesCalc, tops);
                if (Triplet.USE_NEW) {
                    checkNew(iData, bestMap, tops);
                }
                if (iData.trianglesStarted() < Triplet.START_TRIANGLES_PER_ITERATION) {
                    if (Triplet.USE_BRACKETS) {
                        checkBrackets(iData, bestMap, tops, m_account);
                    }
                }
            }
        }
    }

    private void checkTriMkt(TrianglesCalcData trianglesCalc) {
        if( trianglesCalc.m_mktCrossLvl ) {
            for (TriangleCalcData triangle : trianglesCalc) {
                if( triangle.m_forward.m_mkt >= Triplet.TRI_MKT_LVL) {
                    checkTriMkt(triangle.m_forward);
                } else if( triangle.m_backward.m_mkt >= Triplet.TRI_MKT_LVL) {
                    checkTriMkt(triangle.m_backward);
                }
            }
        }
    }

    private void checkTriMkt(TriangleRotationCalcData triangleRotationCalcData) {
// TODO: here
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

    private boolean checkBrackets(IterationData iData, TreeMap<Double, OnePegCalcData> bestMap, final TopsData tops, AccountData account) throws Exception {
        boolean oneStarted = false;
        List<OnePegCalcData> calcDatas = new ArrayList<OnePegCalcData>(bestMap.values());
        Collections.sort(calcDatas, new SortByBracketDistance(tops));

        for (OnePegCalcData calcData : calcDatas) {
            long millis = iData.millisFromTopsLoad();
            if (millis > 2500) { // much time passed from tops loading to consider new triangles
                System.out.println(" !! much time passed from tops loading (" + millis + "ms) - no more new brackets");
                break;
            }

            double distance = calcData.getBracketDistance(tops);
            if (distance < Triplet.BRACKET_DISTANCE_MAX) {
                TopData topData = tops.get(calcData.m_pair1.m_pair);
                double bidAskDiff = topData.getBidAskDiff();

                double bracketPrice = calcData.m_bracketPrice;
                log(" " + calcData.name() + " distance=" + Utils.X_YYYYY.format(distance) +
                        "; bidAskDiff=" + Utils.X_YYYYY.format(bidAskDiff) +
                        "; bid=" + Utils.X_YYYYY.format(topData.m_bid) +
                        "; mid=" + Utils.X_YYYYY.format(topData.getMid()) +
                        "; ask=" + Utils.X_YYYYY.format(topData.m_ask) +
                        "; bracket=" + Utils.X_YYYYY.format(bracketPrice));

                // do not create multiple tri-trades for the pair-direction
                TriTradeData ttd = findTriTradeData(calcData.m_pair1);
// todo: move this later above distance check
                if (ttd != null) {
                    log("  do not start multiple tri-trades on the same pair-direction. existing: " + ttd);
                    continue; // do not start multiple tri-trades
                }

                // for now - do not create multiple tri-trades for the same path; later allow if existing started order is too small (allocated < available)
                ttd = findTriTradeData(calcData);
// todo: move this later above distance check
                if (ttd != null) {
                    log("  do not start multiple tri-trades on the same path. existing: " + ttd);
                    continue; // do not start multiple tri-trades
                }

                TopData top2 = tops.get(calcData.m_pair2.m_pair);
                double mkt2 = calcData.calcMktPrice(tops, calcData.m_pair2);

                TopData top3 = tops.get(calcData.m_pair3.m_pair);
                double mkt3 = calcData.calcMktPrice(tops, calcData.m_pair3);

                double ratio1 = calcData.pegRatio1(account, bracketPrice);
                double ratio2 = calcData.mktRatio2(tops, account);
                double ratio3 = calcData.mktRatio3(tops, account);
                double ratio = ratio1 * ratio2 * ratio3;

                log("  MKT2" +
                        ": bid=" + Utils.X_YYYYY.format(top2.m_bid) +
                        "; mkt=" + Utils.X_YYYYY.format(mkt2) +
                        "; ask=" + Utils.X_YYYYY.format(top2.m_ask) +
                        "   MKT3" +
                        ": bid=" + Utils.X_YYYYY.format(top3.m_bid) +
                        "; mkt=" + Utils.X_YYYYY.format(mkt3) +
                        "; ask=" + Utils.X_YYYYY.format(top3.m_ask) +
                        ": |  ratio1=" + Utils.X_YYYYY.format(ratio1) +
                        "; ratio2=" + Utils.X_YYYYY.format(ratio2) +
                        "; ratio3=" + Utils.X_YYYYY.format(ratio3) +
                        ": ratio=" + Utils.X_YYYYY.format(ratio)
                );

                if (m_triTrades.size() >= Triplet.NUMBER_OF_ACTIVE_TRIANGLES) {
                    log("do not create new brackets - NUMBER_OF_ACTIVE_TRIANGLES=" + Triplet.NUMBER_OF_ACTIVE_TRIANGLES + " reached");
                    break; // do not create more orders
                }
                if (iData.trianglesStarted() >= Triplet.START_TRIANGLES_PER_ITERATION) {
                    log("do not create much brackets in one iteration - reached " + Triplet.START_TRIANGLES_PER_ITERATION);
                    break; // do not start many trades in one iteration
                }
                TriTradeData newTriTrade = createNewOne(iData, tops, calcData, bracketPrice, false, true);
                if (newTriTrade != null) { // order placed
                    oneStarted = true;
                    m_triTrades.add(newTriTrade);
                    forkAndCheckFilledIfNeeded(iData, newTriTrade, newTriTrade.m_order, TriTradeState.PEG_JUST_FILLED);
                }
            }
        }
        return oneStarted;
    }

    private void checkBrackets(IterationData iData, TreeMap<Double, OnePegCalcData> bestMap, Map<Pair, TopData> tops, AccountData account, Pair pair, Direction direction) throws Exception {
        PairDirection pd1 = new PairDirection(pair, direction);
        Currency fromCurrency = pd1.currencyFrom();
        double available = getAvailable(fromCurrency);
        double allocated = account.allocated(fromCurrency);
        log("   pair:" + pd1 + "; from currency=" + fromCurrency + "; available=" + available + "; allocated=" + allocated);
        if (available > allocated) {
            OnePegCalcData bestPeg = findBestBracketPeg(bestMap, pd1);
            log("    bestPeg=" + bestPeg);
            double bracketPrice = bestPeg.m_need;
            TopData top = tops.get(pair);
            OrderSide side = (direction == Direction.FORWARD) ? OrderSide.BUY : OrderSide.SELL;
            double amount = side.isBuy() ? available / bracketPrice : available;
            log("    bracketPrice:" + bracketPrice + "; side=" + side + "; amount=" + amount + "; top=" + top);

            double minOrderToCreate = Btce.minOrderToCreate(pair);
            if (amount > minOrderToCreate) {
                iData.getNewTradesData(Exchange.BTCE, this); // make sure we have loaded all trades on this iteration
                OrderData order = new OrderData(pair, side, bracketPrice, amount);
                OrderData.OrderPlaceStatus ok = Triplet.placeOrder(m_account, order, OrderState.LIMIT_PLACED, iData);
                log("   place order = " + ok + ":  " + order);
                if (ok == OrderData.OrderPlaceStatus.OK) {
                    iData.noSleep();
                    TriTradeData ttData = new TriTradeData(order, bestPeg, false, TriTradeState.BRACKET_PLACED);
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

    private void checkOrdersToLive(TreeMap<Double, OnePegCalcData> bestMap, TopsData tops, IterationData iData) throws Exception {
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
                } else {
                    boolean isPeg = (triTrade.m_state == TriTradeState.PEG_PLACED);
                    if (isPeg || (triTrade.m_state == TriTradeState.BRACKET_PLACED)) {
                        boolean toLive = false;
                        if( Triplet.s_stopRequested ) {
                            triTrade.log("   " + (isPeg ? "peg" : "bracket") + " should be cancelled - stopRequested: " + triTrade);
                        } else {
                            OnePegCalcData tradePeg = triTrade.m_peg;
                            OnePegCalcData peg1 = findPeg(bestMap, tradePeg);
                            if (peg1 != null) {
                                toLive = isPeg
                                         ? checkPegToLive(tops, triTrade, tradePeg, peg1)
                                         : checkBracketToLive(tops, triTrade, tradePeg, peg1);
                            }
                        }
                        // todo: cancel also orders from the same Currency but to another available currency having bigger gain
                        (toLive ? triTradesToLive : triTradesToDie).add(triTrade);
                    } else { // we are not in init state
                        triTradesToLive.add(triTrade);
                        triTrade.log("  order to live (not init state): " + triTrade);
                    }
                }
            }
            m_triTrades = triTradesToLive;
            if (!triTradesToDie.isEmpty()) {
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

    private boolean checkBracketToLive(TopsData tops, TriTradeData triTrade, OnePegCalcData tradePeg, OnePegCalcData peg1) {
        boolean toLive = false;
        double distance = peg1.getBracketDistance(tops);
        if (distance < Triplet.BRACKET_DISTANCE_MAX) {
            double bracketPrice = Exchange.BTCE.roundPrice(peg1.m_bracketPrice, peg1.m_pair1.m_pair);
            double pegPrice = peg1.m_price1;
            OrderData order = triTrade.m_order;
            double orderPrice = order.m_price;
            toLive = order.m_side.isBuy()
                    ? (bracketPrice <= orderPrice) && (orderPrice <= pegPrice)
                    : (bracketPrice >= orderPrice) && (orderPrice >= pegPrice);
            triTrade.log("  bracket to live (" + peg1.name() + ")=" + toLive + "; distance=" + distance + "; bracketPrice=" + bracketPrice +
                    ", pegPrice=" + pegPrice + "; order=" + order);
        } else {
            triTrade.log("  bracket to die (" + peg1.name() + "): distance=" + distance);
        }
        return toLive;
    }

    private boolean checkPegToLive(TopsData tops, TriTradeData triTrade, OnePegCalcData tradePeg, OnePegCalcData peg1) {
        boolean toLive = false;
        boolean doMktOffset = triTrade.m_doMktOffset;
        double pegMax = doMktOffset ? peg1.m_max10 : peg1.m_max;
        String pegName = tradePeg.name();
        if (pegMax > Triplet.s_level) {
            OrderData order = triTrade.m_order;
            double orderPrice = order.m_price;
            double pegPrice = peg1.calcPegPrice(tops);
            double priceDif = Math.abs(pegPrice - orderPrice); // like 16.220010999999998 and 16.22001
            Pair pair = tradePeg.m_pair1.m_pair;
            double minOurPriceStep = Btce.minOurPriceStep(pair); // like 0.00001
            double minExchPriceStep = Btce.minExchPriceStep(pair); // like 0.00001
            double allowPriceDiff = minOurPriceStep * 1.1; //  do not jump over itself
            if (Triplet.ALLOW_ONE_PRICE_STEP_CONCURRENT_PEG) {
                allowPriceDiff += minExchPriceStep;
            }
            if (priceDif < allowPriceDiff) { // check if peg order needs to be moved - do not move if price change is too small
                double ratio = calcRatio(tops, tradePeg, triTrade, doMktOffset, "checkPegToLive");
                if (ratio < 1) {
                    log("warning: ratio < 1" + ratio);
                }
                toLive = true;
                triTrade.log("  peg to live (" + pegName + "): pegMax=" + pegMax + "; tradePegMax=" + tradePeg.m_max +
                        "; priceDif=" + order.roundPriceStr(Exchange.BTCE, priceDif) + ", pegPrice=" + pegPrice + "; order=" + order);
            } else {
                triTrade.log("   peg order should be moved (" + pegName + "). orderPrice=" + orderPrice +
                        ", pegPrice=" + pegPrice + "; priceDif=" + Utils.X_YYYYYYYY.format(priceDif) +
                        "; minOurPriceStep=" + Utils.X_YYYYYYYY.format(minOurPriceStep) +
                        "; minExchPriceStep=" + Utils.X_YYYYYYYY.format(minExchPriceStep)
                );
            }
        } else {
            triTrade.log("   peg order should be REmoved (" + pegName + "). max=" + pegMax +
                    ", old max=" + tradePeg.m_max + "; level=" + Triplet.s_level);
        }
        return toLive;
    }

    private double calcRatio(TopsData tops, OnePegCalcData tradePeg, TriTradeData triTrade, boolean doMktOffset, String header) {
        double ratio1 = tradePeg.pegRatio1(tops, m_account); // commission is applied to ratio
        return calcRatio(tops, tradePeg, triTrade, doMktOffset, header, ratio1);
    }

    private double calcRatio(TopsData tops, OnePegCalcData tradePeg, TriTradeData triTrade, boolean doMktOffset, String header, double ratio1) {
        double ratio2 = doMktOffset
                ? tradePeg.mktRatio2(tops, m_account, Triplet.MKT_OFFSET_PRICE_MINUS)
                : tradePeg.mktRatio2(tops, m_account); // commission is applied to ratio
        double ratio3 = doMktOffset
                ? tradePeg.mktRatio3(tops, m_account, Triplet.MKT_OFFSET_PRICE_MINUS)
                : tradePeg.mktRatio3(tops, m_account); // commission is applied to ratio
        double ratio = ratio1 * ratio2 * ratio3;
        if (header != null) {
            triTrade.log(header + "() ratio1=" + ratio1 + "; ratio2=" + ratio2 + "; ratio3=" + ratio3 + "; ratio=" + ratio);
        }
        return ratio;
    }

    private OnePegCalcData findPeg(TreeMap<Double, OnePegCalcData> bestMap, OnePegCalcData tradePeg) {
        for (OnePegCalcData peg1 : bestMap.values()) {
            if (peg1.equals(tradePeg)) {
                return peg1;
            }
        }
        return null;
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

    public boolean checkNew(IterationData iData, TreeMap<Double, OnePegCalcData> bestMap, TopsData tops) throws Exception {
        boolean oneStarted = false;
        if (Triplet.TRY_WITH_MKT_OFFSET) {
            oneStarted = checkNew(iData, mapWithBest10(bestMap), tops, true); // try max10 with prefer sort
        }
        if (!oneStarted) {
            TreeMap<Double, OnePegCalcData> map =
                    Triplet.PREFER_LIQUID_PAIRS
                            ? mapWithPref(bestMap) // resort by max with preferred
                            : Triplet.PREFER_EUR_CRYPT_PAIRS
                                ? mapWithEurCrypt(bestMap)
                                : bestMap;
            oneStarted = checkNew(iData, map, tops, false); // then try regular peg>level
            if (Triplet.TRY_WITH_MKT_OFFSET && !oneStarted) {
                oneStarted = checkNew(iData, bestMap, tops, true); // then try the rest max10
            }
        }
        return oneStarted;
    }

    private TreeMap<Double, OnePegCalcData> mapWithBest10(TreeMap<Double, OnePegCalcData> bestMap) {
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
        return best10Map;
    }

    private TreeMap<Double, OnePegCalcData> mapWithEurCrypt(TreeMap<Double, OnePegCalcData> bestMap) {
        // resort by max with EurCrypt preferred
        TreeMap<Double, OnePegCalcData> bestMapEurCrypt = new TreeMap<Double, OnePegCalcData>();
        for (OnePegCalcData opcd : bestMap.values()) {
            double key = 1 / opcd.m_max;
            Pair pair = opcd.m_pair1.m_pair;
            if((pair == Pair.BTC_EUR) || (pair == Pair.LTC_EUR)) {
                key /= 1.5; // prefer pairs with EurCrypt
            }
            bestMapEurCrypt.put(key, opcd);
        }
        return bestMapEurCrypt;
    }

    private TreeMap<Double, OnePegCalcData> mapWithPref(TreeMap<Double, OnePegCalcData> bestMap) {
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
        return bestMapPref;
    }

    private boolean checkNew(IterationData iData, TreeMap<Double, OnePegCalcData> bestMap, TopsData tops,
                             boolean doMktOffset) throws Exception {
        boolean oneStarted = false;
        for (Map.Entry<Double, OnePegCalcData> entry : bestMap.entrySet()) {
            long millis = iData.millisFromTopsLoad();
            if (millis > 1500) { // much time passed from tops loading to consider new triangles
                System.out.println(" !! much time passed from tops loading (" + millis + "ms) - no more new");
                break;
            }
            OnePegCalcData peg = entry.getValue();

            boolean levelReached = isLevelReached(peg, doMktOffset);
            if (levelReached) {
                if (m_triTrades.size() >= Triplet.NUMBER_OF_ACTIVE_TRIANGLES) {
                    log("do not create new order - NUMBER_OF_ACTIVE_TRIANGLES=" + Triplet.NUMBER_OF_ACTIVE_TRIANGLES + " reached");
                    break; // do not create more orders
                }
                if (iData.trianglesStarted() >= Triplet.START_TRIANGLES_PER_ITERATION) {
                    log("do not create much brackets in one iteration - reached " + Triplet.START_TRIANGLES_PER_ITERATION);
                    break; // do not start many trades in one iteration
                }
                // do not create multiple tri-trades for the pair-direction
                TriTradeData ttd = findTriTradeData(peg.m_pair1);
// stop brackets here if needed to start pegs
                if (ttd != null) {
                    log("  do not start multiple tri-trades on the same pair-direction. existing: " + ttd);
                    continue; // do not start multiple tri-trades
                }
                ttd = findTriTradeData(peg);
                if (ttd != null) {
                    log("do not start multiple tri-trades. existing: " + ttd);
                    continue; // do not start multiple tri-trades
                }
                double pegPrice = peg.calcPegPrice(tops);
                TriTradeData newTriTrade = createNewOne(iData, tops, peg, pegPrice, doMktOffset, false);
                if (newTriTrade != null) { // order placed
                    iData.oneMoreTriangleStarted();
                    oneStarted = true;
                    m_triTrades.add(newTriTrade);
                    forkAndCheckFilledIfNeeded(iData, newTriTrade, newTriTrade.m_order, TriTradeState.PEG_JUST_FILLED);
                }
            }
        }
        return oneStarted;
    }

    private boolean isLevelReached(OnePegCalcData peg, boolean doMktOffset) {
        double maxPeg = doMktOffset ? peg.m_max10 : peg.m_max;
        double level = Triplet.s_level;
        Pair startPair = peg.m_pair1.m_pair;
        if (Triplet.LOWER_LEVEL_FOR_LIQUIDITY_PAIRS) {
            if ((startPair == Pair.LTC_BTC) || (startPair == Pair.BTC_USD) || (startPair == Pair.LTC_USD)) {
                level -= Triplet.LIQUIDITY_PAIRS_LEVEL_DELTA; // reduce level for pairs with higher liquidity
            }
        }
        if (doMktOffset) {
            level += Triplet.MKT_OFFSET_LEVEL_DELTA; // increase level mkt offsets
        }
        if (maxPeg > (level - 0.1)) {
            log("BEST: max" + (doMktOffset ? "" : "*") + "=" + peg.m_max +
                    ", max10" + (doMktOffset ? "*" : "") + "=" + peg.m_max10 +
                    "; level=" + level + "; need=" + peg.m_need + ": " + peg.name());
        }
        return (maxPeg > level);
    }

    private TriTradeData findTriTradeData(OnePegCalcData peg) {
        for (TriTradeData ttd : m_triTrades) {
            if (ttd.m_peg.equals(peg)) {
                return ttd;
            }
        }
        return null;
    }

    private TriTradeData findTriTradeData(PairDirection pd) {
        for (TriTradeData ttd : m_triTrades) {
            if (ttd.m_peg.m_pair1.equals(pd)) {
                return ttd;
            }
        }
        return null;
    }

    private TriTradeData createNewOne(IterationData iData, TopsData tops,
                                      OnePegCalcData peg, double startPrice,
                                      boolean doMktOffset, boolean isBracket) throws Exception {
        double maxPeg = doMktOffset ? peg.m_max10 : peg.m_max;
        String name = peg.name();
        double mkt1Price = doMktOffset ? peg.calcMktPrice(tops, 0, Triplet.MKT_OFFSET_PRICE_MINUS) : peg.calcMktPrice(tops, 0);
        double mkt2Price = doMktOffset ? peg.calcMktPrice(tops, 1, Triplet.MKT_OFFSET_PRICE_MINUS) : peg.calcMktPrice(tops, 1);

        PairDirection pd1 = peg.m_pair1;
        Pair pair1 = pd1.m_pair;
        PairDirection pd2 = peg.m_pair2;
        Pair pair2 = pd2.m_pair;
        PairDirection pd3 = peg.m_pair3;
        Pair pair3 = pd3.m_pair;

        boolean direction = pd1.isForward();
        OrderSide side = pd1.getSide();
        TopData top = tops.get(pair1);
        TopData top2 = tops.get(pair2);
        TopData top3 = tops.get(pair3);

        Currency fromCurrency = pd1.currencyFrom();
        double available = getAvailable(fromCurrency);
        String availableStr = Exchange.BTCE.roundAmountStr(available, pair1);
        double amount = side.isBuy() ? available / startPrice : available;

        if (Triplet.USE_DEEP // adjust amount to pairs mkt availability
            && Triplet.ADJUST_AMOUNT_TO_MKT_AVAILABLE
            && !isBracket ) { // no adjust for bracket orders
            double ratio = adjustAmountToMktAvailable(tops, peg, doMktOffset, available);
            if (ratio < 1) {
                ratio *= Triplet.PLACE_MORE_THAN_MKT_AVAILABLE;
            }
            if (ratio < 1) {
                double amountIn = amount;
                amount = amountIn * ratio;
                Currency pairCurrency = pd1.m_pair.m_to;
                log("due to MKT orders availability in the book. PEG amount reduced from " + Utils.X_YYYYYYYY.format(amountIn) + " to " + Utils.X_YYYYYYYY.format(amount) + " " + pairCurrency);
                if (Triplet.ADJUST_TO_MIN_ORDER_SIZE) {
                    double minOrderToCreate = Btce.minOrderToCreate(pair1);
                    if (amount < minOrderToCreate) {
                        log(" amount increased to minOrderToCreate: from " + Utils.X_YYYYYYYY.format(amount) + " to " + Utils.X_YYYYYYYY.format(minOrderToCreate) + " " + pairCurrency);
                        amount = minOrderToCreate;
                    }
                }
            }
        }

        amount = Exchange.BTCE.roundAmount(amount, pair1);
        String amountStr = Exchange.BTCE.roundAmountStr(amount, pair1);
        double needPeg = peg.m_need;
        String needPegStr = Exchange.BTCE.roundPriceStr(needPeg, pair1);
        double orderPrice = Exchange.BTCE.roundPrice(startPrice, pair1);
        String orderPriceStr = Exchange.BTCE.roundPriceStr(orderPrice, pair1);

        log("#### " +
                (doMktOffset ? "mkt-offset" : "best") +
                ": " + Triplet.formatAndPad(maxPeg) + "; " + name + ", pair: " + pair1 + ", direction=" + direction +
                ", from=" + fromCurrency + "; available=" + availableStr + "; amount=" + amountStr + "; side=" + side +
                (isBracket ? "; bracket=" : "; peg=") +
                orderPriceStr + "; need=" + needPegStr + "; top: " + top.toString(Exchange.BTCE, pair1));

        double minOrderToCreate = Btce.minOrderToCreate(pair1);
        if (amount >= minOrderToCreate) {
            String mkt1PriceStr = Exchange.BTCE.roundPriceStr(mkt1Price, pair2);
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
            OrderData order = new OrderData(pair1, side, orderPrice, amount);
            TriTradeState initState = isBracket ? TriTradeState.BRACKET_PLACED : TriTradeState.PEG_PLACED;
            TriTradeData ttData = new TriTradeData(order, peg, doMktOffset, initState);
            String bid1Str = Exchange.BTCE.roundPriceStr(top.m_bid, pair1);
            String ask1Str = Exchange.BTCE.roundPriceStr(top.m_ask, pair1);
            String bid2Str = Exchange.BTCE.roundPriceStr(top2.m_bid, pair2);
            String ask2Str = Exchange.BTCE.roundPriceStr(top2.m_ask, pair2);
            String bid3Str = Exchange.BTCE.roundPriceStr(top3.m_bid, pair3);
            String ask3Str = Exchange.BTCE.roundPriceStr(top3.m_ask, pair3);
            ttData.log("   expected prices: [" + bid1Str + "; " + orderPriceStr + "; " + ask1Str  + "] -> " +
                                           "[" + bid2Str + "; " + mkt1PriceStr + "; " + ask2Str + "] -> " +
                                           "[" + bid3Str + "; " + mkt2PriceStr + "; " + ask3Str + "]");
            OrderData.OrderPlaceStatus ok = Triplet.placeOrder(m_account, order, OrderState.LIMIT_PLACED, iData);
            ttData.log("START:  place order = " + ok + ":  " + order.toString(Exchange.BTCE));
            if (ok == OrderData.OrderPlaceStatus.OK) {
                iData.noSleep();
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

    private double adjustAmountToMktAvailable(TopsData tops, OnePegCalcData peg, boolean doMktOffset, double amount) {
// TODO; remake here via Chain objects instead of OnePegCalcData
        double ret = 1;
        PairDirection pd1 = peg.m_pair1;
        Pair pair1 = pd1.m_pair;
        PairDirection pd2 = peg.m_pair2;
        Pair pair2 = pd2.m_pair;
        PairDirection pd3 = peg.m_pair3;
        Pair pair3 = pd3.m_pair;
        // EUR->LTC;LTC->USD;USD->EUR
        //Currency pd1to = pd1.currencyTo();
        //log("adjustAmountToMktAvailable("+peg.name()+")");
        //log(" we have input amount=" + amount + " " + pd1.currencyFrom());
        double ratio1 = peg.pegRatio1(tops, m_account); // commission is applied to ratio
        //log(" PEG(" + pd1.getName() + ") has ratio1="+ratio1);
        double amount2 = amount * ratio1;
        //log("  after PEG we will have amount=" + amount2 + " " + pd1to);
        amount2 = Exchange.BTCE.roundAmount(amount2, pair1);
        //log("   round=" + amount2);

        DeepsData.TopsDataAdapter adapter = (DeepsData.TopsDataAdapter) tops;
        DeepsData deeps = adapter.getDeeps();
        //log(" deeps="+deeps);

        OrderSide side2 = pd2.getSide();
        //log(" MKT1(" + pd2.getName() + ") pair=" + pair2 + "; side=" + side2);
        //DeepData deepData2 = deeps.get(pair2);
        //log("  deep=" + deepData2);
        double mktAmount2 = deeps.getMktAmount(pd2);
        double mktPrice2 = doMktOffset
                ? peg.calcMktPrice(tops, pd2, Triplet.MKT_OFFSET_PRICE_MINUS)
                : peg.calcMktPrice(tops, pd2);
        double ratio2 = doMktOffset
                ? peg.mktRatio2(tops, m_account, Triplet.MKT_OFFSET_PRICE_MINUS)
                : peg.mktRatio2(tops, m_account);
        //log("   mktAmount2=" + mktAmount2 + " " + pair2.m_to + "; mktPrice2="+mktPrice2+"; ratio2="+ratio2);
        double mktAmount2in, mktAmount2out;
        if( side2.isBuy() ) {
            // LTC_USD, buy: USD->LTC: mktPrice2=12.691896; mktAmount2=0.1002004 LTC
            mktAmount2in = mktAmount2 * mktPrice2; //~ 1.2 usd
            mktAmount2out = mktAmount2; // 0.1002004 LTC
        } else {
            // LTC_USD, sell: LTC->USD: mktPrice2=12.691896; mktAmount2=0.1002004 LTC
            mktAmount2in = mktAmount2; // 0.1002004 LTC
            mktAmount2out = mktAmount2 * mktPrice2; //~ 1.2 usd
        }
        //log("    mktAmount2in=" + mktAmount2in + " " + pd2.currencyFrom() + "; mktAmount2out=" + mktAmount2out + " " + pd2.currencyTo());
        double amount3;
        if (mktAmount2in < amount2) {
            ret = mktAmount2in / amount2;
            log("MKT1 " + pd2.getName() + "; pair=" + pair2 + "; side=" + side2 + ". book has only qty=" + Utils.X_YYYYYYYY.format(mktAmount2in) + " " + pd2.currencyFrom()+
                    ", need=" + amount2 + ": reducing amount at ratio="+ret);
            amount3 = mktAmount2out;
            //log("     adjustRatio=" + ret);
        } else {
            amount3 = amount2*ratio2;
        }
        //log("  after MKT1 we may have amount=" + amount3 + " " + pd2.currencyTo());
        amount3 = Exchange.BTCE.roundAmount(amount3, pair2);
        //log("   round=" + amount3);

        OrderSide side3 = pd3.getSide();
        //log(" MKT2(" + pd3.getName() + ") pair=" + pair3 + "; side=" + side3);
        //DeepData deepData3 = deeps.get(pair3);
        //log("  deep=" + deepData3);
        double mktAmount3 = deeps.getMktAmount(pd3);
        double mktPrice3 = doMktOffset
                ? peg.calcMktPrice(tops, pd3, Triplet.MKT_OFFSET_PRICE_MINUS)
                : peg.calcMktPrice(tops, pd3);
        //log("   mktAmount3=" + mktAmount3 + " " + pair3.m_to + "; mktPrice3="+mktPrice3);
        double mktAmount3in, mktAmount3out;
        if( side3.isBuy() ) {
            // LTC_USD, buy: USD->LTC: mktPrice2=12.691896; mktAmount2=0.1002004 LTC
            mktAmount3in = mktAmount3 * mktPrice3; //~ 1.2 usd
            mktAmount3out = mktAmount3; // 0.1002004 LTC
        } else {
            // LTC_USD, sell: LTC->USD: mktPrice2=12.691896; mktAmount2=0.1002004 LTC
            mktAmount3in = mktAmount3; // 0.1002004 LTC
            mktAmount3out = mktAmount3 * mktPrice3; //~ 1.2 usd
        }
        //log("    mktAmount3in=" + mktAmount3in + " " + pd3.currencyFrom() + "; mktAmount3out=" + mktAmount3out + " " + pd3.currencyTo());
        if(mktAmount3in < amount3) {
            double ratio = mktAmount3in / amount3;
            ret *= ratio;
            log("MKT2 " + pd3.getName() + "; pair=" + pair3 + "; side=" + side3 + ". book has only qty=" + Utils.X_YYYYYYYY.format(mktAmount3in) + " " + pd3.currencyFrom()+
                    ", need=" + amount3 + ": reducing amount at ratio=" + ret );
            //log("     adjustRatio=" + ret);
        }

        //        log(" exit ratio=" + ret);
        return ret;
    }

    private double getAvailable(Currency currency) {
        return m_account.available(currency) * Triplet.USE_ACCOUNT_FUNDS;
    }

    private static void log(String s) {
        Log.log(s);
    }

    private static class SortByBracketDistance implements Comparator<OnePegCalcData> {
        private final TopsData tops;

        public SortByBracketDistance(TopsData tops) {
            this.tops = tops;
        }

        @Override public int compare(OnePegCalcData o1, OnePegCalcData o2) {
            double distance1 = o1.getBracketDistance(tops);
            double distance2 = o2.getBracketDistance(tops);
            return (distance1 > distance2)
                    ? 1
                    : (distance1 == distance2) ? 0 : -1;
        }
    }
}
