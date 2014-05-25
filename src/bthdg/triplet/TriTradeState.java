package bthdg.triplet;

import bthdg.*;
import bthdg.exch.*;

public enum TriTradeState {
    PEG_PLACED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            OrderData order = triTradeData.m_order;
            triTradeData.log("PEG_PLACED(" + triTradeData.m_peg.name() + ") - check order " + order + " ...");
            order.checkState(iData, Exchange.BTCE, triangleData.m_account, null, triangleData);
            triangleData.forkAndCheckFilledIfNeeded(iData, triTradeData, order, PEG_FILLED);
            triTradeData.log("PEG_PLACED(" + triTradeData.m_peg.name() + ") END");
        }
    },
    BRACKET_PLACED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            OrderData order = triTradeData.m_order;
            triTradeData.log("BRACKET_PLACED(" + triTradeData.m_peg.name() + ") - check order " + order + " ...");
            order.checkState(iData, Exchange.BTCE, triangleData.m_account, null, triangleData);
            triangleData.forkAndCheckFilledIfNeeded(iData, triTradeData, order, PEG_FILLED);
            triTradeData.log("BRACKET_PLACED(" + triTradeData.m_peg.name() + ") END");
        }
    },
    PEG_JUST_FILLED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("PEG_JUST_FILLED(" + triTradeData.m_peg.name() + ") - run 1st MKT order...");
            triTradeData.setState(PEG_FILLED);
            triTradeData.startMktOrder(iData, triangleData, 1, MKT1_EXECUTED, true); // try to place mkt without re-questing tops - blind trade
            triTradeData.log("PEG_JUST_FILLED(" + triTradeData.m_peg.name() + ") END");
        }
    },
    PEG_FILLED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("PEG_FILLED(" + triTradeData.m_peg.name() + ") - run 1st MKT order...");
            triTradeData.startMktOrder(iData, triangleData, 1, MKT1_EXECUTED); // will fork and check inside if needed
            triTradeData.log("PEG_FILLED(" + triTradeData.m_peg.name() + ") END");
        }
    },
    MKT1_PLACED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            checkMktPlaced(iData, triangleData, triTradeData, 1, MKT1_EXECUTED);
        }
    },
    MKT1_EXECUTED{
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("MKT1_EXECUTED(" + triTradeData.m_peg.name() + ") - run 2nd MKT order...");
            triTradeData.startMktOrder(iData, triangleData, 2, MKT2_EXECUTED); // will fork and check inside if needed
            triTradeData.log("MKT1_EXECUTED(" + triTradeData.m_peg.name() + ") END");
        }
    },
    MKT2_PLACED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            checkMktPlaced(iData, triangleData, triTradeData, 2, MKT2_EXECUTED);
        }
    },
    MKT2_EXECUTED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("MKT2_EXECUTED(" + triTradeData.m_peg.name() + ") - we are done");
            allExecuted(iData, triangleData, triTradeData);
            triTradeData.log("MKT2_EXECUTED(" + triTradeData.m_peg.name() + ") END");
        }
    },
    DONE {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("DONE state on " + this);
        }
    },
    CANCELED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("CANCELED state on " + this);
        }
    },
    ERROR {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("ERROR state on " + this);
        }
    },
    ;

    private static void allExecuted(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
        OnePegCalcData peg = triTradeData.m_peg;
        boolean doMktOffset = triTradeData.m_doMktOffset;
        int startIndx = peg.m_indx;

        AccountData account = triangleData.m_account;

        OrderData order1 = triTradeData.m_order;
        OrderData order2 = triTradeData.m_mktOrders[0];
        OrderData order3 = triTradeData.m_mktOrders[1];

        double price1 = peg.m_price1;
        double price2 = doMktOffset ? peg.m_price2minus : peg.m_price2;
        double price3 = doMktOffset ? peg.m_price3minus : peg.m_price3;

        double[] ends1 = order1.logOrderEnds(account, 1, price1);
        double[] ends2 = order2.logOrderEnds(account, 2, price2);
        double[] ends3 = order3.logOrderEnds(account, 3, price3);
        Currency startCurrency = order1.startCurrency();
        Currency endCurrency = order3.endCurrency();
        double amount1 = order1.startAmount();
        double amount3 = order3.endAmount(account);
        triTradeData.log(
                "  START " + Utils.X_YYYYYYYY.format(amount1) + " " + startCurrency +
                        " -> END " + Utils.X_YYYYYYYY.format(amount3) + " " + endCurrency +
                        "  |||  start " + Utils.X_YYYYYYYY.format(amount1) + " " + startCurrency +
                        "  end " + Utils.X_YYYYYYYY.format(order1.endAmount(account)) + " " + order1.endCurrency() +
                        " | start " + Utils.X_YYYYYYYY.format(order2.startAmount()) + " " + order2.startCurrency() +
                        "  end " + Utils.X_YYYYYYYY.format(order2.endAmount(account)) + " " + order2.endCurrency() +
                        " | start " + Utils.X_YYYYYYYY.format(order3.startAmount()) + " " + order3.startCurrency() +
                        "  end " + Utils.X_YYYYYYYY.format(amount3) + " " + endCurrency
        );

        double in = ends1[0];
        double out = ends3[1];
        double plus = out - in;
        double gain = out / in;

        TopsData tops = iData.getTops();
        double acctEval = account.evaluate(tops, startCurrency);

        Triplet.s_totalRatio *= (1+plus/acctEval);
        Triplet.s_counter++;

        double ratio1 = ends1[1]/ends1[0];
        double ratio2 = ends2[1]/ends2[0];
        double ratio3 = ends3[1]/ends3[0];
        double ratio = ratio1 * ratio2 * ratio3;

        double valuateEur = account.evaluateEur(tops);
        double eurRate = valuateEur / Triplet.s_startEur;
        double valuateUsd = account.evaluateUsd(tops);
        double usdRate = valuateUsd / Triplet.s_startUsd;

        double midMul = account.midMul(Triplet.s_startAccount);

        triTradeData.log(" @@@@@@   ratio1=" + format5(ratio1) + ";  ratio2=" + format5(ratio2) +
                ";  ratio3=" + format5(ratio3) + ";    ratio=" + format5(ratio) +
                "; executed in " + Utils.millisToDHMSStr(System.currentTimeMillis() - triTradeData.m_startTime) +
                "; iterations=" + triTradeData.m_iterationsNum );
        triTradeData.log(" @@@@@@   in=" + format5(in) + ";  out=" + format5(out) +
                "; out-in=" + format5(plus) + " " + startCurrency + ";  gain=" + format5(gain) +
                "; level=" + Triplet.s_level + ";  totalRatio=" + format5(Triplet.s_totalRatio) +
                "; millis=" + System.currentTimeMillis() + "; valuateUsd=" + format5(usdRate) +
                "; valuateEur=" + format5(eurRate) + "; midMul=" + format5(midMul) +
                "; count=" + Triplet.s_counter);
        triTradeData.log(" @@@@@@    peg: max"+(doMktOffset?"":"*")+"=" + format5(peg.m_max) +
                "; max10"+(doMktOffset?"*":"")+"=" + format5(peg.m_max10) + "; startIndx=" + startIndx +
                "; need=" + format5(peg.m_need) +
                "; price1=" + Exchange.BTCE.roundPrice(price1, peg.m_pair1.m_pair) + "; p1=" + peg.m_pair1 +
                "; price2=" + Exchange.BTCE.roundPrice(price2, peg.m_pair2.m_pair) + "; p2=" + peg.m_pair2 +
                "; price3=" + Exchange.BTCE.roundPrice(price3, peg.m_pair3.m_pair) + "; p3=" + peg.m_pair3
        );

        if (gain > 1) {
            if (Triplet.s_level > Triplet.LVL2) {
                double level = Triplet.s_level;
                Triplet.s_level = (Triplet.s_level - Triplet.LVL) / 1.2 + Triplet.LVL;
                Triplet.s_level = Math.max(Triplet.s_level, Triplet.LVL2);
                triTradeData.log(" LEVEL decreased from " + format5(level) + " to " + format5(Triplet.s_level));
            }
        } else {
            double level = Triplet.s_level;
            Triplet.s_level = (Triplet.s_level - Triplet.LVL) * 1.4 + Triplet.LVL;
            triTradeData.log(" LEVEL increased from " + format5(level) + " to " + format5(Triplet.s_level));
        }
        triTradeData.setState(DONE);
    }

    private static String format5(double ratio1) {
        return Utils.X_YYYYY.format(ratio1);
    }

    private static void checkMktPlaced(IterationData iData, TriangleData triangleData, TriTradeData triTradeData, int num/*1 or 2*/, TriTradeState stateForFilled) throws Exception {
        boolean isFirst = (num == 1);
        int indx = num - 1;
        OrderData order = triTradeData.m_mktOrders[indx];
        String orderStr = (order != null) ? order.toString(Exchange.BTCE) : null;
        String name = triTradeData.m_peg.name();
        triTradeData.log("TriTradeState.MKT" + num + "_PLACED(" + name + ") - check order " + orderStr + " ...");
        if (order == null) { // move mkt order can be unsuccessful - cancel fine, but placing failed - just start mkt again
            triTradeData.log(" no MKT order " + num + " - placing new " + (isFirst ? "1st" : "2nd") + " MKT order...");
            triTradeData.startMktOrder(iData, triangleData, num, stateForFilled);
        } else {
            order.checkState(iData, Exchange.BTCE, triangleData.m_account, null, triangleData);
            triangleData.forkAndCheckFilledIfNeeded(iData, triTradeData, order, stateForFilled);
            if (!order.isFilled()) {
                orderStr = order.toString(Exchange.BTCE);
                Pair pair = order.m_pair;
                TopData top = iData.getTop(Exchange.BTCE, pair);
                String topStr = top.toString(Exchange.BTCE, pair);
                double mktPrice = order.m_side.mktPrice(top);
                double orderPrice = order.m_price;
                String orderPriceStr = Exchange.BTCE.roundPriceStr(orderPrice, pair);
                String bidPriceStr = Exchange.BTCE.roundPriceStr(top.m_bid, pair);
                String askPriceStr = Exchange.BTCE.roundPriceStr(top.m_ask, pair);
                double absPriceDif = Exchange.BTCE.roundPrice(Math.abs(orderPrice - mktPrice), pair);
                double minPriceStep = Btce.minExchPriceStep(pair);
                if (absPriceDif >= minPriceStep) {
                    triTradeData.log("MKT order " + num + " run out of market: [" + bidPriceStr + "; " + orderPriceStr + "; " + askPriceStr + "]: " + orderStr);
                    if (triangleData.cancelOrder(order, iData)) {
                        triTradeData.m_mktOrders[indx] = null;
                        triTradeData.log("placing new " + (isFirst ? "1st" : "2nd") + " MKT order...");
                        triTradeData.startMktOrder(iData, triangleData, num, stateForFilled);
                    } else {
                        triTradeData.log("cancel order failed: " + orderStr);
                    }
                } else {
                    triTradeData.log("MKT order " + num + " is on market bound: [" + bidPriceStr + "; " + orderPriceStr + "; " + askPriceStr + "]: " +
                            "absPriceDif=" + Utils.X_YYYYYYYY.format(absPriceDif) + "; " + orderStr + ";  top=" + topStr);
                }
            }
        }
        triTradeData.log("TriTradeState.MKT" + num + "_PLACED(" + name + ") END");
    }

    public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {}
}
