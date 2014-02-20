package bthdg;

import java.io.IOException;

public class ForkData {
    private long m_id;
    private PairExchangeData m_pairExData;
    private ExchangeData m_exch1data;
    private ExchangeData m_exch2data;
    public ForkState m_state = ForkState.NONE;
    // OPEN sides
    private ExchangeData m_openBuyExchange;
    private ExchangeData m_openSellExchange;
//    private StringBuilder m_executionTrace = new StringBuilder();
    private double m_earnThisRun;

    boolean checkAnyBracketExecuted() { return m_exch1data.hasOpenCloseBracketExecuted() || m_exch2data.hasOpenCloseBracketExecuted(); }
    boolean waitingForAllBracketsOpen() { return m_exch1data.waitingForOpenBrackets() && m_exch2data.waitingForOpenBrackets(); }
    private boolean hasBothBracketMarketExecuted() { return (m_exch1data.hasOpenCloseBracketExecuted() && m_exch2data.hasOpenCloseMktExecuted())
                                                   || (m_exch2data.hasOpenCloseBracketExecuted() && m_exch1data.hasOpenCloseMktExecuted()); }

    @Override public String toString() {
        return "ForkData{" +
                "id=" + m_id +
                ", pairExData=" + m_pairExData.exchNames() +
                ", state=" + m_state +
                ", earnThisRun=" + m_earnThisRun +
                '}';
    }

    public ForkData(PairExchangeData pExData) {
        this(System.currentTimeMillis(),
             new ExchangeData(pExData.m_sharedExch1),
             new ExchangeData(pExData.m_sharedExch2));
        m_pairExData = pExData;
    }

    private ForkData(long id, ExchangeData e1, ExchangeData e2) {
        m_id = id;
        m_exch1data = e1;
        m_exch2data = e2;
    }

    public void checkState(IterationContext iContext) throws Exception {
        m_exch1data.checkExchState(iContext);
        m_exch2data.checkExchState(iContext);
        m_state.checkState(iContext, this);
    }

    public void endThisRun() {
        System.out.println("@@@@@@@@@@@@@@@@@@@ END");
        System.out.println("****************************************************");
//        System.out.println(" execution log:");
//        System.out.println(m_executionTrace.toString());
        double commissionAmount = getCommissionAmount(); // todo: calc commission based on real order size
        double income = m_earnThisRun - commissionAmount;
        System.out.println(" earnThisRun=" + m_earnThisRun + ", commissionAmount=" + commissionAmount + ", income=" + income);
        m_pairExData.addIncome(income);

        setState(ForkState.END);
    }

    public boolean waitingOtherSideAtMarket(IterationContext iContext) throws Exception {
        if (hasBothBracketMarketExecuted()) {
            System.out.println(" Bracket/market on both exchanges Executed !!!");
            if (verifyAndLogOpen(iContext)) {
                cleanOrders();
                return true;
            }
        }
        return false;
    }

    private void closeOtherSideAtMarket(ExchangeData toCoseExch, ExchangeData closedExch, TopData top) {
        OrderData closedBracketOrder = closedExch.getFilledBracketOrder();
        System.out.println("closeOtherSideAtMarket() closedExch='" + closedExch.exchName() + "', closedBracketOrder=" + closedBracketOrder);
        if (closedBracketOrder != null) {
            OrderSide side = closedBracketOrder.m_side;
            toCoseExch.closeOrders();
            boolean placed = toCoseExch.postOrderAtMarket(side.opposite(), top, ExchangeState.CLOSE_AT_MKT_PLACED);
            if (placed) {
                setState(ForkState.WAITING_CLOSE_OTHER_SIDE_AT_MKT);
            }
        } else {
            System.out.println("ERROR: no open orders found at " + closedExch);
            setState(ForkState.ERROR);
        }

    }

    void setState(ForkState state) {
        System.out.println("ForkData.setState() " + m_state + " -> " + state);
        m_state = state;
    }

    void moveBracketsIfNeeded(IterationContext iContext) throws Exception {
        System.out.println(" move open/close bracket orders if needed");
        doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                TopData top1 = m_exch1data.m_shExchData.m_lastTop;
                TopData top2 = m_exch2data.m_shExchData.m_lastTop;
                double midDiffAverage = m_pairExData.m_diffAverageCounter.get();
                double commissionAmount = getCommissionAmount(); // todo: calc commission based on real order size
                double halfTargetDelta = (commissionAmount + Fetcher.EXPECTED_GAIN) / 2;
                System.out.println("  commissionAmount=" + Fetcher.format(commissionAmount) + ", halfTargetDelta=" + Fetcher.format(halfTargetDelta));
                boolean success = m_exch1data.moveBrackets(top1, top2, midDiffAverage, halfTargetDelta);
                if (success) {
                    success = m_exch2data.moveBrackets(top2, top1, -midDiffAverage, halfTargetDelta);
                    if (success) {
                        System.out.println("  on both exchanges open bracket orders are moved");
                    }
                }
                if (!success) {
                    System.out.println("ERROR: some exch moveBrackets failed");
                    setState(ForkState.ERROR);
                }
            }
        });
    }

    private double getCommissionAmount() {
        return m_exch1data.commissionAmount() + m_exch2data.commissionAmount();
    }

    public void placeCloseBrackets(IterationContext iContext) throws Exception {
        System.out.println(" try place CloseBrackets");
        doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                TopData top1 = m_exch1data.m_shExchData.m_lastTop;
                TopData top2 = m_exch2data.m_shExchData.m_lastTop;
                double midDiffAverage = m_pairExData.m_diffAverageCounter.get();

                double commissionAmount = m_exch1data.commissionAmount() + m_exch2data.commissionAmount();
                double halfTargetDelta = (commissionAmount + Fetcher.EXPECTED_GAIN) / 2;
                System.out.println("  commissionAmount=" + Fetcher.format(commissionAmount) + ", halfTargetDelta=" + Fetcher.format(halfTargetDelta));

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
                    System.out.println("ERROR: some exch placeCloseBracket failed");
                    setState(ForkState.ERROR);
                }
            }
        });
    }

    public void moveMarketsIfNeeded(IterationContext iContext) throws Exception {
        System.out.println(" move mkt orders if needed");
        doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                boolean success = false;
                if(m_exch1data.moveMarketOrderIfNeeded()) {
                    if(m_exch2data.moveMarketOrderIfNeeded()) {
                        success = true;
                    }
                }
                if(!success) {
                    System.out.println("ERROR: in moveMarketsIfNeeded");
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
            System.out.println("Both exchanges have OPEN bracket executed at this point - need check directions");
            OrderData openedBracketOrder1 = m_exch1data.getFilledBracketOrder();
            System.out.println(" Exch1='" + m_exch1data.exchName() + "', openedBracketOrder=" + openedBracketOrder1);
            OrderData openedBracketOrder2 = m_exch2data.getFilledBracketOrder();
            System.out.println(" Exch2='" + m_exch2data.exchName() + "', openedBracketOrder=" + openedBracketOrder2);
            // ok if at different sides, bad if at the same - complex
            if (openedBracketOrder1.m_side != openedBracketOrder2.m_side) {
                System.out.println(" OPEN brackets of different side was executed - fine - we are OPENED");
                if (verifyAndLogOpen(iContext)) {
                    cleanOrders();
                    setState(ForkState.BOTH_SIDES_OPENED);
                    placeCloseBrackets(iContext);
                }
            } else {
                System.out.println(" OPEN brackets of the same side was executed at once - not expecting this - closing all and restarting");
                setState(ForkState.RESTART);
            }
        } else if (hasBr1) {
            openOtherSideAtMarket(m_exch2data, m_exch1data, tops.m_top2);
        } else if (hasBr2) {
            openOtherSideAtMarket(m_exch1data, m_exch2data, tops.m_top1);
        } else {
            System.out.println("ERROR: no open orders found at both exch.");
            setState(ForkState.ERROR);
        }
    }

    public void closeOtherSideAtMarket(IterationContext iContext) throws Exception {
        TopDatas tops = iContext.getTopsData(m_pairExData); // make sure top data is loaded
        boolean hasBr1 = m_exch1data.hasOpenCloseBracketExecuted();
        boolean hasBr2 = m_exch2data.hasOpenCloseBracketExecuted();
        if (hasBr1 && hasBr2) {
            System.out.println("Both exchanges have CLOSE bracket executed at this point - need check directions");
            OrderData closedBracketOrder1 = m_exch1data.getFilledBracketOrder();
            System.out.println(" Exch1='" + m_exch1data.exchName() + "', closedBracketOrder=" + closedBracketOrder1);
            OrderData closedBracketOrder2 = m_exch2data.getFilledBracketOrder();
            System.out.println(" Exch2='" + m_exch2data.exchName() + "', closedBracketOrder=" + closedBracketOrder2);
            // ok if at different sides, bad if at the same - complex
            if (closedBracketOrder1.m_side != closedBracketOrder2.m_side) {
                System.out.println(" CLOSE brackets of different side was executed - fine - we are CLOSED");
                if (verifyAndLogOpen(iContext)) {
                    cleanOrders();
                    endThisRun();
                }
            } else {
                System.out.println(" CLOSE brackets of the same side was executed at once - not expecting this - closing all and restarting");
                setState(ForkState.RESTART);
            }
        } else if (hasBr1) {
            closeOtherSideAtMarket(m_exch2data, m_exch1data, tops.m_top2);
        } else if (hasBr2) {
            closeOtherSideAtMarket(m_exch1data, m_exch2data, tops.m_top1);
        } else {
            System.out.println("ERROR: no close orders found at both exch.");
            setState(ForkState.ERROR);
        }
    }

    private void openOtherSideAtMarket(ExchangeData toOpenExch, ExchangeData openedExch, TopData top) {
        OrderData openedBracketOrder = openedExch.getFilledBracketOrder();
        System.out.println("openOtherSideAtMarket() openedExch='" + openedExch.exchName() + "', openedBracketOrder=" + openedBracketOrder);
        if (openedBracketOrder != null) {
            OrderSide side = openedBracketOrder.m_side;
            toOpenExch.closeOrders();
            boolean placed = toOpenExch.postOrderAtMarket(side.opposite(), top, ExchangeState.OPEN_AT_MKT_PLACED);
            if (placed) {
                setState(ForkState.WAITING_OPEN_OTHER_SIDE_AT_MKT);
            }
        } else {
            System.out.println("ERROR: no open orders found at " + openedExch);
            setState(ForkState.ERROR);
        }
    }

    void placeOpenBrackets(IterationContext iContext) throws Exception {
        System.out.println(" try place OpenBrackets");
        doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                TopData top1 = m_exch1data.m_shExchData.m_lastTop;
                TopData top2 = m_exch2data.m_shExchData.m_lastTop;
                double midDiffAverage = m_pairExData.m_diffAverageCounter.get();
                double commissionAmount = m_exch1data.commissionAmount() + m_exch2data.commissionAmount();
                double halfTargetDelta = (commissionAmount + Fetcher.EXPECTED_GAIN) / 2;
                System.out.println("  commissionAmount=" + Fetcher.format(commissionAmount) + ", halfTargetDelta=" + Fetcher.format(halfTargetDelta));
                boolean success = m_exch1data.placeBrackets(top1, top2, midDiffAverage, halfTargetDelta);
                if (success) {
                    success = m_exch2data.placeBrackets(top2, top1, -midDiffAverage, halfTargetDelta);
                    if (success) {
                        // i see the orders should be placed instantaneously
                        setState(ForkState.OPEN_BRACKETS_PLACED);
                    }
                }
                if (!success) {
                    System.out.println("ERROR: some exch placeOpenBrackets failed");
                    setState(ForkState.ERROR);
                }
            }
        });
    }

    void queryAccountsData() {
        m_exch1data.queryAccountData();
        m_exch2data.queryAccountData();
    }

    private void doWithFreshTopData(IterationContext iContext, Runnable run) throws Exception {
        TopDatas tops = iContext.getTopsData(m_pairExData);
        if (tops.bothFresh()) {
            logDiffAverageDelta();
            run.run();
            iContext.delay(5000);
        } else {
            System.out.println("some exchange top data is not fresh " +
                    "(fresh1=" + tops.top1fresh() + ", fresh2=" + tops.top2fresh() + ") - do nothing");
        }
    }

    private void logDiffAverageDelta() {
        double midDiffAverage = m_pairExData.m_diffAverageCounter.get();
        TopData.TopDataEx lastDiff = m_pairExData.m_lastDiff;
        double delta = lastDiff.m_mid - midDiffAverage;
        System.out.println("diff=" + lastDiff + ";  avg=" + Fetcher.format(midDiffAverage) + ";  delta=" + Fetcher.format(delta));
    }

    private void verifyAndLogSameExchange(IterationContext iContext, ExchangeData exch) throws Exception {
        iContext.getTopsData(m_pairExData); // make sure top data loaded
        logState();

        OrderData buyOrder = exch.m_buyOrder;
        OrderData sellOrder = exch.m_sellOrder;
        System.out.println("% BUY   on '" + exch.exchName() + "' @ " + buyOrder.priceStr());
        System.out.println("% SELL  on '" + exch.exchName() + "' @ " + sellOrder.priceStr());
        exch.logOrdersAndPrices(exch.m_shExchData.m_lastTop, null, null);

        double sellPrice = sellOrder.m_price;
        double buyPrice = buyOrder.m_price;
        double priceDiff = sellPrice - buyPrice;

        logDiffAverageDelta();

        System.out.println("avd bidAskDiff:" + m_exch1data.exchName() + " " + Fetcher.format(m_exch1data.m_shExchData.m_bidAskDiffCalculator.getAverage()) + ",  " +
                                               m_exch2data.exchName() + " " + Fetcher.format(m_exch2data.m_shExchData.m_bidAskDiffCalculator.getAverage()));

        double commissionAmount = getCommissionAmount(); // todo: calc commission based on real order size
        double income = priceDiff - commissionAmount;
        System.out.println(" sellBuyPriceDiff=" + Fetcher.format(priceDiff) + ", commissionAmount="+commissionAmount+", income=" + income);
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
            System.out.println(str1);
//            m_executionTrace.append(str1).append("\n");
            m_openBuyExchange.logOrdersAndPrices(m_openBuyExchange.m_shExchData.m_lastTop, null, null);

            String str2 = "% SELL on '" + sellExch.exchName() + "' @ " + openSellOrder.priceStr();
            System.out.println(str2);
//            m_executionTrace.append(str2).append("\n");
            m_openSellExchange.logOrdersAndPrices(m_openSellExchange.m_shExchData.m_lastTop, null, null);

            double midDiffAverage = m_pairExData.m_diffAverageCounter.get();
            double sellPrice = openSellOrder.m_price;
            double buyPrice = openBuyOrder.m_price;
            double priceDiff = sellPrice - buyPrice;

            logDiffAverageDelta();

            double openEarn = priceDiff;
            if(m_openSellExchange == m_exch1data) { // sell on exch 1
                openEarn -= midDiffAverage;
            } else { // sell on exch 2
                openEarn += midDiffAverage;
            }
            m_earnThisRun += openEarn;
            System.out.println("%   >>>  priceDiff=" + Fetcher.format(priceDiff) + ",  openEarn=" + Fetcher.format(openEarn) + ", earnThisRun=" + m_earnThisRun);
            System.out.println("AVG:" + m_exch1data.exchName() + " " + Fetcher.format(m_exch1data.m_shExchData.m_bidAskDiffCalculator.getAverage()) + ",  " +
                                        m_exch2data.exchName() + " " + Fetcher.format(m_exch2data.m_shExchData.m_bidAskDiffCalculator.getAverage()));
        } else {
            setState(ForkState.ERROR);
        }
        return noErrors;
    }

    private void logState() {
        System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
        System.out.println("% '" + m_exch1data.exchName() + "' " + m_exch1data.m_state);
        System.out.println("%    buy:  " + m_exch1data.m_buyOrder);
        System.out.println("%    sell: " + m_exch1data.m_sellOrder);
        System.out.println("% '" + m_exch2data.exchName() + "' " + m_exch2data.m_state);
        System.out.println("%    buy:  " + m_exch2data.m_buyOrder);
        System.out.println("%    sell: " + m_exch2data.m_sellOrder);
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
    }

    public boolean allStopped() {
        return m_exch1data.isStopped() && m_exch2data.isStopped();
    }

    public void appendState(StringBuilder sb) {
        sb.append( "{\"state\": \""+m_state+"\"}");
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
        ForkData res = new ForkData(id, e1, e2);
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
} // ForkData
