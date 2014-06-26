package bthdg.triplet;

import bthdg.exch.*;
import bthdg.util.Utils;

public enum TriTradeState {
    PEG_PLACED {
        @Override public void checkState(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {
            OrderData order = triTradeData.m_order;
            triTradeData.log("PEG_PLACED(" + triTradeData.m_peg.name() + ") - check order " + order + " ...");
            order.checkState(iData, Triplet.s_exchange, triTradesData.m_account, null, triTradesData);
            triTradesData.forkAndCheckFilledIfNeeded(iData, triTradeData, order, PEG_FILLED);
            triTradeData.log("PEG_PLACED(" + triTradeData.m_peg.name() + ") END");
        }
    },
    BRACKET_PLACED {
        @Override public void checkState(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {
            OrderData order = triTradeData.m_order;
            triTradeData.log("BRACKET_PLACED(" + triTradeData.m_peg.name() + ") - check order " + order + " ...");
            order.checkState(iData, Triplet.s_exchange, triTradesData.m_account, null, triTradesData);
            triTradesData.forkAndCheckFilledIfNeeded(iData, triTradeData, order, PEG_FILLED);
            triTradeData.log("BRACKET_PLACED(" + triTradeData.m_peg.name() + ") END");
        }
    },
    PEG_JUST_FILLED {
        @Override public void checkState(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("PEG_JUST_FILLED(" + triTradeData.m_peg.name() + ") - run 1st MKT order...");
            triTradeData.setState(PEG_FILLED);
            triTradeData.startMktOrder(iData, triTradesData, 1, MKT1_EXECUTED, true); // try to place mkt without re-questing tops - blind trade
            triTradeData.log("PEG_JUST_FILLED(" + triTradeData.m_peg.name() + ") END");
        }
    },
    PEG_FILLED {
        @Override public void checkState(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("PEG_FILLED(" + triTradeData.m_peg.name() + ") - run 1st MKT order...");
            triTradeData.startMktOrder(iData, triTradesData, 1, MKT1_EXECUTED); // will fork and check inside if needed
            triTradeData.log("PEG_FILLED(" + triTradeData.m_peg.name() + ") END");
        }
    },
    MKT1_PLACED {
        @Override public void checkState(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {
            checkMktPlaced(iData, triTradesData, triTradeData, 1, MKT1_EXECUTED);
        }
    },
    MKT1_EXECUTED{
        @Override public void checkState(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("MKT1_EXECUTED(" + triTradeData.m_peg.name() + ") - run 2nd MKT order...");
            triTradeData.startMktOrder(iData, triTradesData, 2, MKT2_EXECUTED); // will fork and check inside if needed
            triTradeData.log("MKT1_EXECUTED(" + triTradeData.m_peg.name() + ") END");
        }
    },
    MKT2_PLACED {
        @Override public void checkState(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {
            checkMktPlaced(iData, triTradesData, triTradeData, 2, MKT2_EXECUTED);
        }
    },
    MKT2_EXECUTED {
        @Override public void checkState(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("MKT2_EXECUTED(" + triTradeData.m_peg.name() + ") - we are done");
            allExecuted(iData, triTradesData, triTradeData);
            triTradeData.log("MKT2_EXECUTED(" + triTradeData.m_peg.name() + ") END");
        }
    },
    DONE {
        @Override public void checkState(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("DONE state on " + this);
        }
        @Override public boolean isInactive() { return true; }
    },
    CANCELED {
        @Override public void checkState(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("CANCELED state on " + this);
        }
        @Override public boolean isInactive() { return true; }
    },
    ERROR {
        @Override public void checkState(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("ERROR state on " + this);
        }
        @Override public boolean isInactive() { return true; }
    },
    ;

    private static void allExecuted(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {
        OnePegCalcData peg = triTradeData.m_peg;
        boolean doMktOffset = triTradeData.m_doMktOffset;
        int startIndx = peg.m_indx;

        AccountData account = triTradesData.m_account;

        OrderData order1 = triTradeData.m_order;
        OrderData order2 = triTradeData.getMktOrder(0);
        OrderData order3 = triTradeData.getMktOrder(1);

        double price1 = peg.m_price1;
        double price2 = doMktOffset ? peg.m_price2minus : peg.m_price2;
        double price3 = doMktOffset ? peg.m_price3minus : peg.m_price3;

        String prefix = triTradeData.m_id;

        order1.logOrderEnds(prefix, 1, price1);
        order2.logOrderEnds(prefix, 2, price2);
        order3.logOrderEnds(prefix, 3, price3);

        double[] ends1 = order1.calcOrderEnds(account, price1);
        double[] ends2 = order2.calcOrderEnds(account, price2);
        double[] ends3 = order3.calcOrderEnds(account, price3);

        Currency startCurrency = order1.startCurrency();
        Currency endCurrency = order3.endCurrency();
        double amount1 = order1.startAmount();
        double amount3 = order3.endAmount(account);
        triTradeData.log(
                "  START " + Utils.format8(amount1) + " " + startCurrency +
                        " -> END " + Utils.format8(amount3) + " " + endCurrency +
                        "  |||  start " + Utils.format8(amount1) + " " + startCurrency +
                        "  end " + Utils.format8(order1.endAmount(account)) + " " + order1.endCurrency() +
                        " | start " + Utils.format8(order2.startAmount()) + " " + order2.startCurrency() +
                        "  end " + Utils.format8(order2.endAmount(account)) + " " + order2.endCurrency() +
                        " | start " + Utils.format8(order3.startAmount()) + " " + order3.startCurrency() +
                        "  end " + Utils.format8(amount3) + " " + endCurrency
        );

        double in = ends1[0];
        double out = ends3[1];
        double plus = out - in;
        double gain = out / in;

        TopsData tops = iData.getTops();
        double acctEval = account.evaluate(tops, startCurrency, Triplet.s_exchange);

        Triplet.s_totalRatio *= (1+plus/acctEval);
        Triplet.s_counter++;

        double ratio1 = ends1[1]/ends1[0];
        double ratio2 = ends2[1]/ends2[0];
        double ratio3 = ends3[1]/ends3[0];
        double ratio = ratio1 * ratio2 * ratio3;

        double eurRate = 0;
        if (Triplet.s_exchange.supportsCurrency(Currency.EUR)) {
            double valuateEur = account.evaluateEur(tops, Triplet.s_exchange);
            eurRate = valuateEur / Triplet.s_startEur;
        }
        double usdRate = 0;
        if (Triplet.s_exchange.supportsCurrency(Currency.USD)) {
            double valuateUsd = account.evaluateUsd(tops, Triplet.s_exchange);
            usdRate = valuateUsd / Triplet.s_startUsd;
        }
        double btcRate = 0;
        if (Triplet.s_exchange.supportsCurrency(Currency.BTC)) {
            double valuateBtc = account.evaluate(tops, Currency.BTC, Triplet.s_exchange);
            btcRate = valuateBtc / Triplet.s_startBtc;
        }
        double cnhRate = 0;
        if (Triplet.s_exchange.supportsCurrency(Currency.CNH)) {
            double valuateCnh = account.evaluate(tops, Currency.CNH, Triplet.s_exchange);
            cnhRate = valuateCnh / Triplet.s_startCnh;
        }

        double midMul = account.midMul(Triplet.s_startAccount);

        triTradeData.log(" @@@@@@   ratio1=" + format5(ratio1) + ";  ratio2=" + format5(ratio2) +
                ";  ratio3=" + format5(ratio3) + ";    ratio=" + format5(ratio) +
                "; executed in " + Utils.millisToDHMSStr(System.currentTimeMillis() - triTradeData.m_startTime) +
                "; iterations=" + triTradeData.m_iterationsNum );
        triTradeData.log(" @@@@@@   in=" + format5(in) + ";  out=" + format5(out) +
                "; out-in=" + format5(plus) + " " + startCurrency + ";  gain=" + format5(gain) +
                "; level=" + Triplet.s_level +
                ";  totalRatio=" + format5(Triplet.s_totalRatio) +
                "; millis=" + System.currentTimeMillis() +
                ((usdRate != 0) ? "; valuateUsd=" + format5(usdRate) : "") +
                ((eurRate != 0) ? "; valuateEur=" + format5(eurRate) : "") +
                ((btcRate != 0) ? "; valuateBtc=" + format5(btcRate) : "") +
                ((cnhRate != 0) ? "; valuateCnh=" + format5(cnhRate) : "") +
                "; midMul=" + format5(midMul) +
                "; count=" + Triplet.s_counter);
        triTradeData.log(" @@@@@@    peg: " +
                (peg.mktCrossLvl() ? "!MKT! " : "") +
                "max" + (doMktOffset ? "" : "*") + "=" + format5(peg.m_max) +
                "; max10" + (doMktOffset ? "*" : "") + "=" + format5(peg.m_max10) + "; startIndx=" + startIndx +
                "; need=" + format5(peg.m_need) +
                "; price1=" + Triplet.roundPrice(price1, peg.m_pair1.m_pair) + "; p1=" + peg.m_pair1 +
                "; price2=" + Triplet.roundPrice(price2, peg.m_pair2.m_pair) + "; p2=" + peg.m_pair2 +
                "; price3=" + Triplet.roundPrice(price3, peg.m_pair3.m_pair) + "; p3=" + peg.m_pair3
        );

        if (gain > 1) {
            if (Triplet.s_level > Triplet.LVL2) {
                double level = Triplet.s_level;
                double newLevel = (level - Triplet.LVL) / Triplet.LVL_DECREASE_RATE + Triplet.LVL;
                newLevel = Math.max(newLevel, Triplet.LVL2);
                Triplet.s_level = newLevel;
                triTradeData.log(" LEVEL decreased from " + format5(level) + " to " + format5(Triplet.s_level));
            }
        } else {
            double level = Triplet.s_level;
            Triplet.s_level = (level - Triplet.LVL) * Triplet.LVL_INCREASE_RATE + Triplet.LVL;
            triTradeData.log(" LEVEL increased from " + format5(level) + " to " + format5(Triplet.s_level));
        }
        triTradeData.setState(DONE);
    }

    private static String format5(double ratio1) {
        return Utils.X_YYYYY.format(ratio1);
    }

    private static void checkMktPlaced(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData, int num/*1 or 2*/, TriTradeState stateForFilled) throws Exception {
        boolean isFirst = (num == 1);
        int mktOrderIndx = num - 1;
        OrderData order = triTradeData.getMktOrder(mktOrderIndx);
        String orderStr = (order != null) ? order.toString(Triplet.s_exchange) : null;
        String name = triTradeData.m_peg.name();
        triTradeData.log("TriTradeState.MKT" + num + "_PLACED(" + name + ") - check order " + orderStr + " ...");
        if (order == null) { // move mkt order can be unsuccessful - cancel fine, but placing failed - just start mkt again
            triTradeData.log(" no MKT order " + num + " - placing new " + (isFirst ? "1st" : "2nd") + " MKT order...");
            triTradeData.startMktOrder(iData, triTradesData, num, stateForFilled);
        } else {
            order.checkState(iData, Triplet.s_exchange, triTradesData.m_account, null, triTradesData);
            triTradesData.forkAndCheckFilledIfNeeded(iData, triTradeData, order, stateForFilled);
            if (!order.isFilled()) {
                orderStr = order.toString(Triplet.s_exchange);
                Pair pair = order.m_pair;
                TopData top = iData.getTop(Triplet.s_exchange, pair);
                String topStr = top.toString(Triplet.s_exchange, pair);
                double mktPrice = order.m_side.mktPrice(top);
                double orderPrice = order.m_price;
                String orderPriceStr = Triplet.roundPriceStr(orderPrice, pair);
                String bidPriceStr = Triplet.roundPriceStr(top.m_bid, pair);
                String askPriceStr = Triplet.roundPriceStr(top.m_ask, pair);
                double absPriceDif = Triplet.roundPrice(Math.abs(orderPrice - mktPrice), pair);
                double minPriceStep = Triplet.s_exchange.minExchPriceStep(pair);
                if (absPriceDif >= minPriceStep) {
                    triTradeData.log("MKT order " + num + " run out of market: [" + bidPriceStr + "; " + orderPriceStr + "; " + askPriceStr + "]: " + orderStr);
                    if (triTradesData.cancelOrder(order, iData)) {
                        triTradeData.m_mktOrders[mktOrderIndx] = null;
                        triTradeData.log("placing new " + (isFirst ? "1st" : "2nd") + " MKT order...");
                        triTradeData.startMktOrder(iData, triTradesData, num, stateForFilled);
                    } else {
                        triTradeData.log("cancel order failed: " + orderStr);
                    }
                } else {
                    triTradeData.log("MKT order " + num + " is on market bound: [" + bidPriceStr + "; " + orderPriceStr + "; " + askPriceStr + "]: " +
                            "absPriceDif=" + Utils.format8(absPriceDif) + "; " + orderStr + ";  top=" + topStr);
                }
            }
        }
        triTradeData.log("TriTradeState.MKT" + num + "_PLACED(" + name + ") END");
    }

    public void checkState(IterationData iData, TriTradesData triTradesData, TriTradeData triTradeData) throws Exception {}
    public boolean isInactive() { return false; }
}
