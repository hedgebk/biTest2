public class ExchangesData {
    public final ExchangeData m_exch1data;
    public final ExchangeData m_exch2data;
    public TopData.TopDataEx m_lastDiff;
    public ExchangesState m_state = ExchangesState.NONE;
    public final Utils.AverageCounter m_diffAverageCounter = new Utils.AverageCounter(Fetcher.MOVING_AVERAGE);
    // OPEN sides
    private ExchangeData m_openBuyExchange;
    private ExchangeData m_openSellExchange;
    private OrderData m_openBuyOrder;
    private OrderData m_openSellOrder;
    private StringBuilder m_executionTrace = new StringBuilder();
    private double m_earnThisRun;

    boolean waitingForAllBracketsOpen() { return m_exch1data.waitingForOpenBrackets() && m_exch2data.waitingForOpenBrackets(); }
    boolean hasAnyBracketExecuted() { return m_exch1data.hasOpenCloseBracketExecuted() || m_exch2data.hasOpenCloseBracketExecuted(); }
    private boolean hasBothBracketMarketExecuted() { return (m_exch1data.hasOpenCloseBracketExecuted() && m_exch2data.hasOpenCloseMktExecuted())
                                                   || (m_exch2data.hasOpenCloseBracketExecuted() && m_exch1data.hasOpenCloseMktExecuted()); }

    public ExchangesData(ExchangeData exch1data, ExchangeData exch2data) {
        m_exch1data = exch1data;
        m_exch2data = exch2data;
    }

    public boolean checkState(IterationContext iContext) throws Exception {
        System.out.println("Exchanges state: " + m_state);
        if(m_state.preCheckState(iContext, this) ) {
            return true;
        }

        if(m_state.queryTrades()) {
            iContext.getNewTradesData(m_exch1data);
            iContext.getNewTradesData(m_exch2data);
        }

        m_exch1data.checkExchState(iContext);
        m_exch2data.checkExchState(iContext);
        m_state.checkState(iContext, this);
        return false;
    }

    public void onTopsLoaded(TopDatas topDatas) {
        m_lastDiff = topDatas.calculateDiff(); // top1 - top2
        if (m_lastDiff != null) {
            m_diffAverageCounter.justAdd(System.currentTimeMillis(), m_lastDiff.m_mid);
        }
        m_exch1data.m_lastTop = topDatas.m_top1;
        m_exch2data.m_lastTop = topDatas.m_top2;
    }

    void setState(ExchangesState state) {
        System.out.println("Exchanges state " + m_state + " -> " + state);
        m_state = state;
    }

    void queryAccountsData() {
        m_exch1data.queryAccountData();
        m_exch2data.queryAccountData();
    }

    private void doWithFreshTopData(IterationContext iContext, Runnable run) throws Exception {
        TopDatas tops = iContext.getTopsData(this);
        if (tops.bothFresh()) {
            logDiffAverageDelta();

            run.run();

            double waitDistance = Fetcher.TARGET_DELTA;
            System.out.println("waitDistance="+waitDistance);
            long delay = (long) (Fetcher.MIN_DELAY + Fetcher.MIN_DELAY * 4 * Math.min(1, Math.abs(waitDistance) / Fetcher.HALF_TARGET_DELTA));
            delay = Math.max(delay,1000);
            iContext.delay(delay);
        } else {
            System.out.println("some exchange top data is not fresh " +
                    "(fresh1=" + tops.top1fresh() + ", fresh2=" + tops.top2fresh() + ") - do nothing");
        }
    }

    private void logDiffAverageDelta() {
        double midDiffAverage = m_diffAverageCounter.get();
        double delta = m_lastDiff.m_mid - midDiffAverage;
        System.out.println("diff=" + m_lastDiff + ";  avg=" + Fetcher.format(midDiffAverage) + ";  delta=" + Fetcher.format(delta));
    }

    void moveBracketsIfNeeded(IterationContext iContext) throws Exception {
        System.out.println(" move open bracket orders if needed");
        doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                TopData top1 = m_exch1data.m_lastTop;
                TopData top2 = m_exch2data.m_lastTop;
                double midDiffAverage = m_diffAverageCounter.get();
                boolean success = m_exch1data.moveBrackets(top1, top2, midDiffAverage);
                if (success) {
                    success = m_exch2data.moveBrackets(top2, top1, -midDiffAverage);
                    if (success) {
                        System.out.println("  on both exchanges open bracket orders are moved");
                    }
                }
                if (!success) {
                    System.out.println("ERROR: some exch moveBrackets failed");
                    setState(ExchangesState.ERROR);
                }
            }
        });
    }

    void placeOpenBrackets(IterationContext iContext) throws Exception {
        System.out.println(" try place OpenBrackets");
        doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                TopData top1 = m_exch1data.m_lastTop;
                TopData top2 = m_exch2data.m_lastTop;
                double midDiffAverage = m_diffAverageCounter.get();
                boolean success = m_exch1data.placeBrackets(top1, top2, midDiffAverage);
                if (success) {
                    success = m_exch2data.placeBrackets(top2, top1, -midDiffAverage);
                    if (success) {
                        // i see the orders should be placed instantaneously
                        setState(ExchangesState.OPEN_BRACKETS_PLACED);
                    }
                }
                if (!success) {
                    System.out.println("ERROR: some exch placeOpenBrackets failed");
                    setState(ExchangesState.ERROR);
                }
            }
        });
    }

    public void placeCloseBrackets(IterationContext iContext) throws Exception {
        System.out.println(" try place CloseBrackets");
        doWithFreshTopData(iContext, new Runnable() {
            public void run() {
                TopData top1 = m_exch1data.m_lastTop;
                TopData top2 = m_exch2data.m_lastTop;
                double midDiffAverage = m_diffAverageCounter.get();

                boolean exch1openSell = (m_openSellExchange == m_exch1data);
                boolean success = m_exch1data.placeCloseBracket(top1, top2, midDiffAverage,
                        (exch1openSell ? OrderSide.BUY : OrderSide.SELL));
                if (success) {
                    success = m_exch2data.placeCloseBracket(top2, top1, -midDiffAverage,
                            (exch1openSell ? OrderSide.SELL : OrderSide.BUY));
                }
                if (success) {
                    // i see the orders should be placed instantaneously
                    setState(ExchangesState.CLOSE_BRACKET_PLACED);
                } else {
                    System.out.println("ERROR: some exch placeCloseBracket failed");
                    setState(ExchangesState.ERROR);
                }
            }
        });
    }

    void openOtherSideAtMarket(IterationContext iContext) throws Exception {
        TopDatas tops = iContext.getTopsData(this); // make sure top data is loaded
        boolean hasBr1 = m_exch1data.hasOpenCloseBracketExecuted();
        boolean hasBr2 = m_exch2data.hasOpenCloseBracketExecuted();
        if (hasBr1 && hasBr2) { // todo: ok if at different sides, bad if at the same - complex. this need to be handled here
            System.out.println("ERROR: both exchanges have bracket executed at this point.");
            setState(ExchangesState.ERROR);
        } else if (hasBr1) {
            openOtherSideAtMarket(m_exch2data, m_exch1data, tops.m_top2);
        } else if (hasBr2) {
            openOtherSideAtMarket(m_exch1data, m_exch2data, tops.m_top1);
        } else {
            System.out.println("ERROR: no open orders found at both exch.");
            setState(ExchangesState.ERROR);
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
                setState(ExchangesState.WAITING_OPEN_OTHER_SIDE_AT_MKT);
            }
        } else {
            System.out.println("ERROR: no open orders found at " + openedExch);
            setState(ExchangesState.ERROR);
        }
    }

    private boolean verifyAndLogOpen(IterationContext iContext) throws Exception {
        iContext.getTopsData(this);
        System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
        System.out.println("% '" + m_exch1data.exchName() + "' " + m_exch1data.m_state);
        System.out.println("%    buy:  " + m_exch1data.m_buyOpenBracketOrder);
        System.out.println("%    sell: " + m_exch1data.m_sellOpenBracketOrder);
        System.out.println("% '" + m_exch2data.exchName() + "' " + m_exch2data.m_state);
        System.out.println("%    buy:  " + m_exch2data.m_buyOpenBracketOrder);
        System.out.println("%    sell: " + m_exch2data.m_sellOpenBracketOrder);
        String err = null;
        ExchangeData buyExch = null;
        ExchangeData sellExch = null;
        if ((m_exch1data.m_buyOpenBracketOrder != null) && (m_exch2data.m_sellOpenBracketOrder != null)) {
            if (m_exch1data.m_sellOpenBracketOrder != null) {
                err = "unexpected sellOpenBracketOrder on " + m_exch1data.exchName();
            } else if (m_exch2data.m_buyOpenBracketOrder != null) {
                err = "unexpected buyOpenBracketOrder on " + m_exch2data.exchName();
            } else {
                buyExch = m_exch1data;
                sellExch = m_exch2data;
            }
        } else if ((m_exch2data.m_buyOpenBracketOrder != null) && (m_exch1data.m_sellOpenBracketOrder != null)) {
            if (m_exch2data.m_sellOpenBracketOrder != null) {
                err = "unexpected sellOpenBracketOrder on " + m_exch2data.exchName();
            } else if (m_exch1data.m_buyOpenBracketOrder != null) {
                err = "unexpected buyOpenBracketOrder on " + m_exch1data.exchName();
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
            m_openBuyOrder = buyExch.m_buyOpenBracketOrder;
            m_openSellOrder = sellExch.m_sellOpenBracketOrder;

            String str1 = "% BUY  on '" + buyExch.exchName() + "' @ " + m_openBuyOrder.priceStr();
            System.out.println(str1);
            m_executionTrace.append(str1).append("\n");
            m_openBuyExchange.logOrdersAndPrices(m_openBuyExchange.m_lastTop, null, null);

            String str2 = "% SELL on '" + sellExch.exchName() + "' @ " + m_openSellOrder.priceStr();
            System.out.println(str2);
            m_executionTrace.append(str2).append("\n");
            m_openSellExchange.logOrdersAndPrices(m_openSellExchange.m_lastTop, null, null);

            double midDiffAverage = m_diffAverageCounter.get();
            double sellPrice = m_openSellOrder.m_price;
            double buyPrice = m_openBuyOrder.m_price;
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
        } else {
            setState(ExchangesState.ERROR);
        }
        return noErrors;
    }

    public void cleanOrders() {
        m_exch1data.cleanOrders();
        m_exch2data.cleanOrders();
    }

    public void closeOtherSideAtMarket(IterationContext iContext) throws Exception {
        TopDatas tops = iContext.getTopsData(this); // make sure top data is loaded
        boolean hasBr1 = m_exch1data.hasOpenCloseBracketExecuted();
        boolean hasBr2 = m_exch2data.hasOpenCloseBracketExecuted();
        if (hasBr1 && hasBr2) { // todo: ok since at different sides - just cache out
            System.out.println("ERROR: both exchanges have bracket executed at this point.");
            setState(ExchangesState.ERROR);
        } else if (hasBr1) {
            closeOtherSideAtMarket(m_exch2data, m_exch1data, tops.m_top2);
        } else if (hasBr2) {
            closeOtherSideAtMarket(m_exch1data, m_exch2data, tops.m_top1);
        } else {
            System.out.println("ERROR: no open orders found at both exch.");
            setState(ExchangesState.ERROR);
        }
    }

    private void closeOtherSideAtMarket(ExchangeData toCoseExch, ExchangeData closedExch, TopData top) {
        OrderData closedBracketOrder = closedExch.getFilledBracketOrder();
        System.out.println("closeOtherSideAtMarket() closedExch='" + closedExch.exchName() + "', closedBracketOrder=" + closedBracketOrder);
        if (closedBracketOrder != null) {
            OrderSide side = closedBracketOrder.m_side;
            toCoseExch.closeOrders();
            boolean placed = toCoseExch.postOrderAtMarket(side.opposite(), top, ExchangeState.CLOSE_AT_MKT_PLACED);
            if (placed) {
                setState(ExchangesState.WAITING_CLOSE_OTHER_SIDE_AT_MKT);
            }
        } else {
            System.out.println("ERROR: no open orders found at " + closedExch);
            setState(ExchangesState.ERROR);
        }

    }

    public void setAllAsError() {
        m_exch1data.setAllAsError();
        m_exch2data.setAllAsError();
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

    public void endThisRun() {
        System.out.println("@@@@@@@@@@@@@@@@@@@ END");
        setState(ExchangesState.END);
        System.out.println("****************************************************");
        System.out.println(" execution log:");
        System.out.println(m_executionTrace.toString());
        System.out.println(" earnThisRun=" + m_earnThisRun);
    }
} // ExchangesData
