package bthdg;

import java.io.IOException;

public class ForkData {
    long m_id;
    public PairExchangeData m_pairExData;
    private ExchangeData m_exch1data;
    private ExchangeData m_exch2data;
    public ForkState m_state = ForkState.NONE;
    // OPEN sides
    private ExchangeData m_openBuyExchange;
    private ExchangeData m_openSellExchange;
    private double m_earnThisRun;

    public double m_amount; // todo: serialize below
    /////////////////////////////////////////////////////////////////////////////////////////////
    // one of these crosses is active at the time
    public CrossData m_openCross;
    public CrossData m_closeCross;

    public ForkDirection m_direction;

    boolean checkAnyBracketExecuted() { return m_exch1data.hasOpenCloseBracketExecuted() || m_exch2data.hasOpenCloseBracketExecuted(); }
    boolean waitingForAllBracketsOpen() { return m_exch1data.waitingForOpenBrackets() && m_exch2data.waitingForOpenBrackets(); }
    private boolean hasBothBracketMarketExecuted() { return (m_exch1data.hasOpenCloseBracketExecuted() && m_exch2data.hasOpenCloseMktExecuted())
                                                   || (m_exch2data.hasOpenCloseBracketExecuted() && m_exch1data.hasOpenCloseMktExecuted()); }

    private static void log(String s) { Log.log(s); }

    @Override public String toString() {
        return "ForkData{" +
                "id=" + m_id +
                ", pairExData=" + m_pairExData.exchNames() +
                ", direction=" + m_direction +
                ", state=" + m_state +
                ", amount=" + m_amount +
                ", earnThisRun=" + m_earnThisRun +
                '}';
    }

    public ForkData(PairExchangeData pExData, ForkDirection direction, double amount) {
        this(System.currentTimeMillis(),
             null, // new ExchangeData(pExData.m_sharedExch1),
             null, // new ExchangeData(pExData.m_sharedExch2),
             amount);
        m_pairExData = pExData;
        if (direction != null) {
            startCross(direction);
        }
    }

    private ForkData(long id, ExchangeData e1, ExchangeData e2, double amount) {
        m_id = id;
//        m_exch1data = e1;
//        m_exch2data = e2;
        m_amount = amount;
    }

    public void checkState(IterationContext iContext) throws Exception {
        log("Fork.checkState() " + this);
//        m_exch1data.checkExchState(iContext);
//        m_exch2data.checkExchState(iContext);
        checkCross(iContext, m_openCross);
        checkCross(iContext, m_closeCross);
        m_state.checkState(iContext, this);
    }

    private void checkCross(IterationContext iContext, CrossData cross) throws Exception {
        if (cross != null) {
            cross.checkState(iContext, this);
            if(cross.stuckTooLong()) {
                setState(ForkState.STOP);
            }
        }
    }

    public void endThisRun() {
        log("@@@@@@@@@@@@@@@@@@@ END");
        log("****************************************************");
//        log(" execution log:");
//        log(m_executionTrace.toString());
        double commissionAmount = midCommissionAmount() * 2; // todo: later we need to calc commission based on real order size
        double income = m_earnThisRun - commissionAmount;
        log(" earnThisRun=" + m_earnThisRun + ", commissionAmount=" + commissionAmount + ", income=" + income);
        m_pairExData.addIncome(income);

        setState(ForkState.END);
    }

    public boolean waitingOtherSideAtMarket(IterationContext iContext) throws Exception {
        if (hasBothBracketMarketExecuted()) {
            log(" Bracket/market on both exchanges Executed !!!");
            if (verifyAndLogOpen(iContext)) {
                cleanOrders();
                return true;
            }
        }
        return false;
    }

    private void closeOtherSideAtMarket(ExchangeData toCoseExch, ExchangeData closedExch, TopData top) {
        OrderData closedBracketOrder = closedExch.getFilledBracketOrder();
        log("closeOtherSideAtMarket() closedExch='" + closedExch.exchName() + "', closedBracketOrder=" + closedBracketOrder);
        if (closedBracketOrder != null) {
            OrderSide side = closedBracketOrder.m_side;
            toCoseExch.closeOrders();
            boolean placed = toCoseExch.postOrderAtMarket(side.opposite(), top, ExchangeState.CLOSE_AT_MKT_PLACED);
            if (placed) {
                setState(ForkState.WAITING_CLOSE_OTHER_SIDE_AT_MKT);
            }
        } else {
            log("ERROR: no open orders found at " + closedExch);
            setState(ForkState.ERROR);
        }
    }

    void setState(ForkState state) {
        log("ForkData.setState() " + m_state + " -> " + state);
        m_state = state;
    }

    void moveBracketsIfNeeded(IterationContext iContext) throws Exception {
        log(" move open/close bracket orders if needed");
        m_pairExData.doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                TopData top1 = m_exch1data.m_shExchData.m_lastTop;
                TopData top2 = m_exch2data.m_shExchData.m_lastTop;
                double midDiffAverage = m_pairExData.m_diffAverageCounter.get();
                double commissionAmount = midCommissionAmount(); // todo: calc commission based on real order size
                double halfTargetDelta = (commissionAmount + Fetcher.EXPECTED_GAIN) / 2;
                log("  commissionAmount=" + format(commissionAmount) + ", halfTargetDelta=" + format(halfTargetDelta));
                boolean success = m_exch1data.moveBrackets(top1, top2, midDiffAverage, halfTargetDelta);
                if (success) {
                    success = m_exch2data.moveBrackets(top2, top1, -midDiffAverage, halfTargetDelta);
                    if (success) {
                        log("  on both exchanges open bracket orders are moved");
                    }
                }
                if (!success) {
                    log("ERROR: some exch moveBrackets failed");
                    setState(ForkState.ERROR);
                }
            }
        });
    }

    public void placeCloseBrackets(IterationContext iContext) throws Exception {
        log(" try place CloseBrackets");
        m_pairExData.doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                TopData top1 = m_exch1data.m_shExchData.m_lastTop;
                TopData top2 = m_exch2data.m_shExchData.m_lastTop;
                double midDiffAverage = m_pairExData.m_diffAverageCounter.get();

                double commissionAmount = midCommissionAmount();
                double halfTargetDelta = (commissionAmount + Fetcher.EXPECTED_GAIN) / 2;
                log("  commissionAmount=" + format(commissionAmount) + ", halfTargetDelta=" + format(halfTargetDelta));

                boolean exch1openSell = (m_openSellExchange == m_exch1data);
                boolean success = m_exch1data.placeCloseBracket(top1, top2, midDiffAverage,
                        (exch1openSell ? OrderSide.BUY : OrderSide.SELL), halfTargetDelta);
                if (success) {
                    success = m_exch2data.placeCloseBracket(top2, top1, -midDiffAverage,
                            (exch1openSell ? OrderSide.SELL : OrderSide.BUY), halfTargetDelta);
                }
                if (success) {
                    // i see the orders should be placed instantaneously
                    setState(ForkState.CLOSE_BRACKET_PLACED);
                } else {
                    log("ERROR: some exch placeCloseBracket failed");
                    setState(ForkState.ERROR);
                }
            }
        });
    }

    public void moveMarketsIfNeeded(IterationContext iContext) throws Exception {
        log(" move mkt orders if needed");
        m_pairExData.doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                boolean success = false;
                if(m_exch1data.moveMarketOrderIfNeeded()) {
                    if(m_exch2data.moveMarketOrderIfNeeded()) {
                        success = true;
                    }
                }
                if(!success) {
                    log("ERROR: in moveMarketsIfNeeded");
                    logState();
                    setState(ForkState.ERROR);
                }
            }
        });
    }

    void openOtherSideAtMarket(IterationContext iContext) throws Exception {
        TopDatas tops = iContext.getTopsData(m_pairExData); // make sure top data is loaded
        boolean hasBr1 = m_exch1data.hasOpenCloseBracketExecuted();
        boolean hasBr2 = m_exch2data.hasOpenCloseBracketExecuted();
        if (hasBr1 && hasBr2) {
            log("Both exchanges have OPEN bracket executed at this point - need check directions");
            OrderData openedBracketOrder1 = m_exch1data.getFilledBracketOrder();
            log(" Exch1='" + m_exch1data.exchName() + "', openedBracketOrder=" + openedBracketOrder1);
            OrderData openedBracketOrder2 = m_exch2data.getFilledBracketOrder();
            log(" Exch2='" + m_exch2data.exchName() + "', openedBracketOrder=" + openedBracketOrder2);
            // ok if at different sides, bad if at the same - complex
            if (openedBracketOrder1.m_side != openedBracketOrder2.m_side) {
                log(" OPEN brackets of different side was executed - fine - we are OPENED");
                if (verifyAndLogOpen(iContext)) {
                    cleanOrders();
                    setState(ForkState.BOTH_SIDES_OPENED);
                    placeCloseBrackets(iContext);
                }
            } else {
                log(" OPEN brackets of the same side was executed at once - not expecting this - closing all and restarting");
                setState(ForkState.RESTART);
            }
        } else if (hasBr1) {
            openOtherSideAtMarket(m_exch2data, m_exch1data, tops.m_top2);
        } else if (hasBr2) {
            openOtherSideAtMarket(m_exch1data, m_exch2data, tops.m_top1);
        } else {
            log("ERROR: no open orders found at both exch.");
            setState(ForkState.ERROR);
        }
    }

    public void closeOtherSideAtMarket(IterationContext iContext) throws Exception {
        TopDatas tops = iContext.getTopsData(m_pairExData); // make sure top data is loaded
        boolean hasBr1 = m_exch1data.hasOpenCloseBracketExecuted();
        boolean hasBr2 = m_exch2data.hasOpenCloseBracketExecuted();
        if (hasBr1 && hasBr2) {
            log("Both exchanges have CLOSE bracket executed at this point - need check directions");
            OrderData closedBracketOrder1 = m_exch1data.getFilledBracketOrder();
            log(" Exch1='" + m_exch1data.exchName() + "', closedBracketOrder=" + closedBracketOrder1);
            OrderData closedBracketOrder2 = m_exch2data.getFilledBracketOrder();
            log(" Exch2='" + m_exch2data.exchName() + "', closedBracketOrder=" + closedBracketOrder2);
            // ok if at different sides, bad if at the same - complex
            if (closedBracketOrder1.m_side != closedBracketOrder2.m_side) {
                log(" CLOSE brackets of different side was executed - fine - we are CLOSED");
                if (verifyAndLogOpen(iContext)) {
                    cleanOrders();
                    endThisRun();
                }
            } else {
                log(" CLOSE brackets of the same side was executed at once - not expecting this - closing all and restarting");
                setState(ForkState.RESTART);
            }
        } else if (hasBr1) {
            closeOtherSideAtMarket(m_exch2data, m_exch1data, tops.m_top2);
        } else if (hasBr2) {
            closeOtherSideAtMarket(m_exch1data, m_exch2data, tops.m_top1);
        } else {
            log("ERROR: no close orders found at both exch.");
            setState(ForkState.ERROR);
        }
    }

    private void openOtherSideAtMarket(ExchangeData toOpenExch, ExchangeData openedExch, TopData top) {
        OrderData openedBracketOrder = openedExch.getFilledBracketOrder();
        log("openOtherSideAtMarket() openedExch='" + openedExch.exchName() + "', openedBracketOrder=" + openedBracketOrder);
        if (openedBracketOrder != null) {
            OrderSide side = openedBracketOrder.m_side;
            toOpenExch.closeOrders();
            boolean placed = toOpenExch.postOrderAtMarket(side.opposite(), top, ExchangeState.OPEN_AT_MKT_PLACED);
            if (placed) {
                setState(ForkState.WAITING_OPEN_OTHER_SIDE_AT_MKT);
            }
        } else {
            log("ERROR: no open orders found at " + openedExch);
            setState(ForkState.ERROR);
        }
    }

    void placeOpenBrackets(IterationContext iContext) throws Exception {
        log(" try place OpenBrackets");
        m_pairExData.doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                TopData top1 = m_exch1data.m_shExchData.m_lastTop;
                TopData top2 = m_exch2data.m_shExchData.m_lastTop;
                double midDiffAverage = m_pairExData.m_diffAverageCounter.get();
                double commissionAmount = midCommissionAmount();
                double halfTargetDelta = (commissionAmount + Fetcher.EXPECTED_GAIN) / 2;
                log("  commissionAmount=" + format(commissionAmount) + ", halfTargetDelta=" + format(halfTargetDelta));
                boolean success = m_exch1data.placeBrackets(top1, top2, midDiffAverage, halfTargetDelta);
                if (success) {
                    success = m_exch2data.placeBrackets(top2, top1, -midDiffAverage, halfTargetDelta);
                    if (success) {
                        // i see the orders should be placed instantaneously
                        setState(ForkState.OPEN_BRACKETS_PLACED);
                    }
                }
                if (!success) {
                    log("ERROR: some exch placeOpenBrackets failed");
                    setState(ForkState.ERROR);
                }
            }
        });
    }

    public double midCommissionAmount() {
        return m_exch1data.midCommissionAmount() + m_exch2data.midCommissionAmount();
    }

    void queryAccountsData() throws Exception {
        m_pairExData.queryAccountData();
    }

    private void verifyAndLogSameExchange(IterationContext iContext, ExchangeData exch) throws Exception {
        iContext.getTopsData(m_pairExData); // make sure top data loaded
        logState();

        OrderData buyOrder = exch.m_buyOrder;
        OrderData sellOrder = exch.m_sellOrder;
        log("% BUY   on '" + exch.exchName() + "' @ " + buyOrder.priceStr());
        log("% SELL  on '" + exch.exchName() + "' @ " + sellOrder.priceStr());
        exch.logOrdersAndPrices(exch.m_shExchData.m_lastTop, null, null);

        double sellPrice = sellOrder.m_price;
        double buyPrice = buyOrder.m_price;
        double priceDiff = sellPrice - buyPrice;

        m_pairExData.logDiffAverageDelta();

        log("avg bidAskDiff:" + m_exch1data.exchName() + " " + format(m_exch1data.m_shExchData.m_bidAskDiffCalculator.getAverage()) + ",  " +
                m_exch2data.exchName() + " " + format(m_exch2data.m_shExchData.m_bidAskDiffCalculator.getAverage()));

        double commissionAmount = midCommissionAmount(); // todo: calc commission based on real order size
        double income = priceDiff - commissionAmount;
        log(" sellBuyPriceDiff=" + format(priceDiff) + ", commissionAmount=" + commissionAmount + ", income=" + income);
        m_earnThisRun += income;
        m_pairExData.addIncome(income);
    }

    private boolean verifyAndLogOpen(IterationContext iContext) throws Exception {
        iContext.getTopsData(m_pairExData); // make sure top data loaded
        logState();
        String err = null;
        ExchangeData buyExch = null;
        ExchangeData sellExch = null;
        if ((m_exch1data.m_buyOrder != null) && (m_exch2data.m_sellOrder != null)) {
            if (m_exch1data.m_sellOrder != null) {
                err = "gotBracket[exch1buy+exch2sell], unexpected sellOpenBracketOrder on exch1 " + m_exch1data.exchName();
            } else if (m_exch2data.m_buyOrder != null) {
                err = "gotBracket[exch1buy+exch2sell], unexpected buyOpenBracketOrder on exch2 " + m_exch2data.exchName();
            } else {
                buyExch = m_exch1data;
                sellExch = m_exch2data;
            }
        } else if ((m_exch2data.m_buyOrder != null) && (m_exch1data.m_sellOrder != null)) {
            if (m_exch2data.m_sellOrder != null) {
                err = "gotBracket[exch2buy+exch1sell], unexpected sellOpenBracketOrder on exch2 " + m_exch2data.exchName();
            } else if (m_exch1data.m_buyOrder != null) {
                err = "gotBracket[exch2buy+exch1sell], unexpected buyOpenBracketOrder on exch2 " + m_exch1data.exchName();
            } else {
                buyExch = m_exch2data;
                sellExch = m_exch1data;
            }
        } else {
            err = "no buy/sell OpenBracketOrders found";
        }
        boolean noErrors = (err == null);
        if (noErrors) {
            // save OPEN sides
            m_openBuyExchange = buyExch;
            m_openSellExchange = sellExch;
            OrderData openBuyOrder = buyExch.m_buyOrder;
            OrderData openSellOrder = sellExch.m_sellOrder;

            String str1 = "% BUY  on '" + buyExch.exchName() + "' @ " + openBuyOrder.priceStr();
            log(str1);
            m_openBuyExchange.logOrdersAndPrices(m_openBuyExchange.m_shExchData.m_lastTop, null, null);

            String str2 = "% SELL on '" + sellExch.exchName() + "' @ " + openSellOrder.priceStr();
            log(str2);
            m_openSellExchange.logOrdersAndPrices(m_openSellExchange.m_shExchData.m_lastTop, null, null);

            double midDiffAverage = m_pairExData.m_diffAverageCounter.get();
            double sellPrice = openSellOrder.m_price;
            double buyPrice = openBuyOrder.m_price;
            double priceDiff = sellPrice - buyPrice;

            m_pairExData.logDiffAverageDelta();

            double openEarn = priceDiff;
            if(m_openSellExchange == m_exch1data) { // sell on exch 1
                openEarn -= midDiffAverage;
            } else { // sell on exch 2
                openEarn += midDiffAverage;
            }
            m_earnThisRun += openEarn;
            log("%   >>>  priceDiff=" + format(priceDiff) + ",  openEarn=" + format(openEarn) + ", earnThisRun=" + m_earnThisRun);
            log("AVG:" + m_exch1data.exchName() + " " + format(m_exch1data.m_shExchData.m_bidAskDiffCalculator.getAverage()) + ",  " +
                    m_exch2data.exchName() + " " + format(m_exch2data.m_shExchData.m_bidAskDiffCalculator.getAverage()));
        } else {
            setState(ForkState.ERROR);
        }
        return noErrors;
    }

    private void logState() {
        log("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
        log("% '" + m_exch1data.exchName() + "' " + m_exch1data.m_state);
        log("%    buy:  " + m_exch1data.m_buyOrder);
        log("%    sell: " + m_exch1data.m_sellOrder);
        log("% '" + m_exch2data.exchName() + "' " + m_exch2data.m_state);
        log("%    buy:  " + m_exch2data.m_buyOrder);
        log("%    sell: " + m_exch2data.m_sellOrder);
    }

    public void cleanOrders() {
        m_exch1data.cleanOrders();
        m_exch2data.cleanOrders();
    }

    public void setAllAsError() {
        m_exch1data.setAllAsError();
        m_exch1data.setAllAsError();
    }

    public void stop() {
        m_exch1data.stop();
        m_exch2data.stop();
        if (m_openCross != null) {
            m_openCross.stop();
            if (m_closeCross != null) {
                m_closeCross.stop();
            }
        }
        setState(ForkState.STOP);
    }

    public boolean allStopped() {
        if( m_exch1data.isStopped() && m_exch2data.isStopped() ) {
            if ((m_openCross == null) || m_openCross.isStopped()) {
                if ((m_closeCross == null) || m_closeCross.isStopped()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void appendState(StringBuilder sb) {
        sb.append("{\"state\": \"")
          .append(m_state)
          .append("\", \"e1\": ");
        m_exch1data.appendState(sb);
        sb.append(", \"e2\": ");
        m_exch2data.appendState(sb);
        sb.append(", \"live\": \"");
        sb.append(getLiveTime());
        sb.append("\"}");
    }

    String getLiveTime() {
        return Utils.millisToDHMSStr(System.currentTimeMillis() - m_id);
    }

    public void serialize(StringBuilder sb) {
        sb.append("Fork[id=").append(m_id);
        sb.append("; e1=");
        m_exch1data.serialize(sb);
        sb.append("; e2=");
        m_exch2data.serialize(sb);
        sb.append("; state=").append(m_state.toString());
        sb.append("; openBuyExch=");
        if(m_openBuyExchange != null) {
            sb.append(m_openBuyExchange.m_exch);
        }
        sb.append("; openSellExch=");
        if(m_openSellExchange != null) {
            sb.append(m_openSellExchange.m_exch);
        }
        sb.append("; earn=").append(m_earnThisRun);
        sb.append("]");
    }

    public static ForkData deserialize(Deserializer deserializer) throws IOException {
        deserializer.readObjectStart("Fork");
        deserializer.readPropStart("id");
        String idStr = deserializer.readTill("; ");
        deserializer.readPropStart("e1");
        ExchangeData e1 = ExchangeData.deserialize(deserializer);
        deserializer.readStr("; ");
        deserializer.readPropStart("e2");
        ExchangeData e2 = ExchangeData.deserialize(deserializer);
        deserializer.readStr("; ");
        deserializer.readPropStart("state");
        String stateStr = deserializer.readTill("; ");
        deserializer.readPropStart("openBuyExch");
        String openBuyExchStr = deserializer.readTill("; ");
        deserializer.readPropStart("openSellExch");
        String openSellExchStr = deserializer.readTill("; ");
        deserializer.readPropStart("earn");
        String earn = deserializer.readTill("]");

        long id = Long.parseLong(idStr);
        ForkData res = new ForkData(id, e1, e2,
                0); // TODO: deserialize
        res.m_state = ForkState.valueOf(stateStr);
        res.m_earnThisRun = Double.parseDouble(earn);

        String e1name = e1.m_exch.name();
        String e2name = e2.m_exch.name();
        if (e1name.equals(openBuyExchStr)) {
            res.m_openBuyExchange = e1;
        } else if (e2name.equals(openBuyExchStr)) {
            res.m_openBuyExchange = e2;
        }
        if (e1name.equals(openSellExchStr)) {
            res.m_openSellExchange = e1;
        } else if (e2name.equals(openSellExchStr)) {
            res.m_openSellExchange = e2;
        }

        return res;
    }

    public void postDeserialize(PairExchangeData ret) {
        m_pairExData = ret;
        m_exch1data.postDeserialize(ret);
        m_exch2data.postDeserialize(ret);
    }

    public void compare(ForkData other) {
        if (m_id != other.m_id) {
            throw new RuntimeException("m_id");
        }
        if(Utils.compareAndNotNulls(m_exch1data, other.m_exch1data)) {
            m_exch1data.compare(other.m_exch1data);
        }
        if(Utils.compareAndNotNulls(m_exch2data, other.m_exch2data)) {
            m_exch2data.compare(other.m_exch2data);
        }
        if (m_state != other.m_state) {
            throw new RuntimeException("m_state");
        }
        if(Utils.compareAndNotNulls(m_openBuyExchange, other.m_openBuyExchange)) {
            if (m_openBuyExchange.m_exch != other.m_openBuyExchange.m_exch) {
                throw new RuntimeException("m_openBuyExchange.m_exch");
            }
        }
        if(Utils.compareAndNotNulls(m_openSellExchange, other.m_openSellExchange)) {
            if (m_openSellExchange.m_exch != other.m_openSellExchange.m_exch) {
                throw new RuntimeException("m_openSellExchange.m_exch");
            }
        }
        if (m_earnThisRun != other.m_earnThisRun) {
            throw new RuntimeException("m_earnThisRun");
        }
    }

    public boolean checkBothBracketsExecutedOnExch(IterationContext iContext) throws Exception {
        boolean ret = false;
        if (m_exch1data.hasBothBracketsExecuted()) {
            verifyAndLogSameExchange(iContext, m_exch1data);
            ret = true;
        }
        if (m_exch2data.hasBothBracketsExecuted()) {
            verifyAndLogSameExchange(iContext, m_exch2data);
            ret = true;
        }
        if (ret) {
            if (m_exch1data.hasOpenCloseBracketExecuted()) {
                m_exch1data.closeOrders();
            }
            if (m_exch2data.hasOpenCloseBracketExecuted()) {
                m_exch2data.closeOrders();
            }
        }
        return ret;
    }

    void placeOpenCrosses(IterationContext iContext) throws Exception {
        log(" try place OpenCrosses, direction=" + m_direction);
        m_pairExData.doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                SharedExchangeData buyExch = m_direction.buyExch(m_pairExData);
                SharedExchangeData sellExch = m_direction.sellExch(m_pairExData);

                m_openCross = new CrossData(buyExch, sellExch);
                m_openCross.init(ForkData.this, true);

                setState(ForkState.OPEN_BRACKETS_PLACED_NEW);
            }
        });
    }

    public void placeCloseCrosses(double amount) {
        log(" try place CloseCrosses, direction=" + m_direction);

        // note here is in reverse
        SharedExchangeData sellExch = m_direction.buyExch(m_pairExData);
        SharedExchangeData buyExch = m_direction.sellExch(m_pairExData);

        // update amount since account data can be changed
        m_amount = Math.min(amount, m_amount); // do not place close cross bigger than open - if possible, should be matched.

        m_closeCross = new CrossData(buyExch, sellExch);
        m_closeCross.init(ForkData.this, false);

        setState(ForkState.CLOSE_BRACKETS_PLACED_NEW);
    }

    private void startCross(ForkDirection direction) {
        m_direction = direction;
    }

    public ForkData fork(double qty) {
        if (m_openCross != null) {
            CrossData openCross = m_openCross.fork(qty);
            CrossData closeCross = (m_closeCross != null) ? m_closeCross.fork(qty) : null;
            double amount2 = m_amount - qty;
            m_amount = qty;

            ForkData ret = new ForkData(m_pairExData, null, amount2);
            ret.m_openCross = openCross;
            ret.m_closeCross = closeCross;
            ret.m_direction = m_direction;
            return ret;
        }
        return null;
    }

    public ForkData forkIfNeeded() {
        if (m_openCross != null) {
            if (m_openCross.isActive()) {
                double qty = m_openCross.needFork();
                if (qty != 0.0) {
                    return fork(qty);
                }
            } else {
                if (m_closeCross != null) {
                    if (m_closeCross.isActive()) {
                        double qty = m_closeCross.needFork();
                        if (qty != 0.0) {
                            return fork(qty);
                        }
                    }
                }
            }
        }
        return null;
    }

    public boolean isNotStarted() {
        return (m_openCross != null) && m_openCross.isNotStarted();
    }

    public boolean increaseOpenAmount(IterationContext iContext, final PairExchangeData pairExchangeData) {
        boolean ret = m_openCross.increaseOpenAmount(pairExchangeData, m_direction);
        if (ret) {
            m_amount = m_openCross.m_buyOrder.m_amount;
        }
        return ret;
    }

    public void evaluateGain() {
        SharedExchangeData shExch1 = m_closeCross.m_buyExch;
        double exch1Fee = shExch1.getFee();
        Exchange exch1 = shExch1.m_exchange;

        OrderData closeBuyOrder = m_closeCross.m_buyOrder;
        double closeBuyPrice = closeBuyOrder.m_price;
        double closeBuyQty = closeBuyOrder.m_amount;
        double closeBuyValue = closeBuyPrice*closeBuyQty;
        double closeBuyCommission = closeBuyValue* exch1Fee;

        OrderData openSellOrder = m_openCross.m_sellOrder;
        double openSellPrice = openSellOrder.m_price;
        double openSellQty = openSellOrder.m_amount;
        double openSellValue = openSellPrice * openSellQty;
        double openSellCommission = openSellValue * exch1Fee;

        double priceDiff1 = openSellPrice - closeBuyPrice;
        double gain1 = openSellValue - closeBuyValue - closeBuyCommission - openSellCommission;

        log("exch1: " + exch1 +
                "; fee " + format(exch1Fee) +
                "; buy " + closeBuyQty + " @ " + closeBuyPrice +
                "; sell " + openSellQty + " @ " + openSellPrice +
                "; priceDiff " + priceDiff1 );
        log(" buy value " + format(closeBuyValue) +
                "; commission " + format(closeBuyCommission));
        log(" sell value " + format(openSellValue) +
                "; commission " + format(openSellCommission));
        log(" gain1 " + gain1 );

        SharedExchangeData shExch2 = m_openCross.m_buyExch;
        double exch2Fee = shExch2.getFee();
        Exchange exch2 = shExch2.m_exchange;

        OrderData openBuyOrder = m_openCross.m_buyOrder;
        double openBuyPrice = openBuyOrder.m_price;
        double openBuyQty = openBuyOrder.m_amount;
        double openBuyValue = openBuyPrice * openBuyQty;
        double openBuyCommission = openBuyValue * exch2Fee;

        OrderData closeSellOrder = m_closeCross.m_sellOrder;
        double closeSellPrice = closeSellOrder.m_price;
        double closeSellQty = closeSellOrder.m_amount;
        double closeSellValue = closeSellPrice * closeSellQty;
        double closeSellCommission = closeSellValue * exch2Fee;

        double priceDiff2 = closeSellPrice - openBuyPrice;
        double gain2 = closeSellValue - openBuyValue - openBuyCommission - closeSellCommission;

        log("exch2: " + exch2 +
                "; fee " + format(exch2Fee) +
                "; buy " + openBuyQty + " @ " + openBuyPrice +
                "; sell " + closeSellQty + " @ " + closeSellPrice +
                "; priceDiff " + priceDiff2 );
        log(" buy value " + format(openBuyValue) +
                "; commission " + format(openBuyCommission));
        log(" sell value " + format(closeSellValue) +
                "; commission " + format(closeSellCommission));
        log(" gain2 " + gain2 );

        double gain = gain1 + gain2;
        double priceDiff = priceDiff1 + priceDiff2;
        m_pairExData.addGain( gain );
        log("priceDiff=" + priceDiff +
            "; gain=" + gain +
            "; totalRuns=" + m_pairExData.m_runs +
            "; totalEarn=" + m_pairExData.m_totalIncome);

        setState(ForkState.END);
    }

    private String format(double openBuyCommission) {
        return Fetcher.format(openBuyCommission);
    }
} // ForkData
