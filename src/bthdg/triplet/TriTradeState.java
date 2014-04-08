package bthdg.triplet;

import bthdg.*;
import bthdg.exch.TopData;
import bthdg.exch.TopsData;

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
//        TriangleRotationCalcData rotationData = peg.m_parent;
//        Triangle triangle = rotationData.m_triangle;
//            String name = triangle.name();
        int startIndx = peg.m_indx;
//        boolean rotationDirection = rotationData.m_forward;
//            log(" " + name + "; start=" + startIndx + "; direction=" + rotationDirection);

        AccountData account = triangleData.m_account;

        OrderData order1 = triTradeData.m_order;
        OrderData order2 = triTradeData.m_mktOrders[0];
        OrderData order3 = triTradeData.m_mktOrders[1];

        double[] ends1 = order1.logOrderEnds(account, 1, peg.m_price1);
        double[] ends2 = order2.logOrderEnds(account, 2, peg.m_price2);
        double[] ends3 = order3.logOrderEnds(account, 3, peg.m_price3);
        Currency currency = order1.startCurrency();
        Currency endCurrency = order3.endCurrency();
        double amount1 = order1.startAmount();
        double amount3 = order3.endAmount(account);
        triTradeData.log(
                "  START " + Utils.X_YYYYYYYY.format(amount1) + " " + currency +
                        " -> END " + Utils.X_YYYYYYYY.format(amount3) + " " + endCurrency +
                        "  |||  start " + Utils.X_YYYYYYYY.format(amount1) + " " + currency +
                        "  end " + Utils.X_YYYYYYYY.format(order1.endAmount(account)) + " " + order1.endCurrency() +
                        " | start " + Utils.X_YYYYYYYY.format(order2.startAmount()) + " " + order2.startCurrency() +
                        "  end " + Utils.X_YYYYYYYY.format(order2.endAmount(account)) + " " + order2.endCurrency() +
                        " | start " + Utils.X_YYYYYYYY.format(order3.startAmount()) + " " + order3.startCurrency() +
                        "  end " + Utils.X_YYYYYYYY.format(amount3) + " " + endCurrency
        );

        double in = ends1[0];
        double out = ends3[1];
        double gain = out / in;
        Triplet.s_totalRatio *= ((gain - 1) / 4 + 1);
        Triplet.s_counter++;

        double ratio1 = ends1[1]/ends1[0];
        double ratio2 = ends2[1]/ends2[0];
        double ratio3 = ends3[1]/ends3[0];
        double ratio = ratio1 * ratio2 * ratio3;

        TopsData tops = iData.getTops();
        double valuateEur = account.evaluateEur(tops);
        double eurRate = valuateEur / Triplet.s_startEur;
        double valuateUsd = account.evaluateUsd(tops);
        double usdRate = valuateUsd / Triplet.s_startUsd;

        double midMul = account.midMul(Triplet.s_startAccount);

        triTradeData.log(" @@@@@@   ratio1=" + Utils.X_YYYYY.format(ratio1) + ";  ratio2=" + Utils.X_YYYYY.format(ratio2) +
                ";  ratio3=" + Utils.X_YYYYY.format(ratio3) + ";    ratio=" + Utils.X_YYYYY.format(ratio) +
                "; executed in " + Utils.millisToDHMSStr(System.currentTimeMillis() - triTradeData.m_startTime));
        triTradeData.log(" @@@@@@   in=" + Utils.X_YYYYY.format(in) + ";  out=" + Utils.X_YYYYY.format(out) +
                "; out-in=" + Utils.X_YYYYY.format(out - in) + " " + currency + ";  gain=" + Utils.X_YYYYY.format(gain) +
                "; level=" + Triplet.s_level + ";  totalRatio=" + Utils.X_YYYYY.format(Triplet.s_totalRatio) +
                "; millis=" + System.currentTimeMillis() + "; valuateUsd=" + Utils.X_YYYYY.format(usdRate) +
                "; valuateEur=" + Utils.X_YYYYY.format(eurRate) + "; midMul=" + Utils.X_YYYYY.format(midMul) +
                "; count=" + Triplet.s_counter);
        triTradeData.log(" @@@@@@    peg: max=" + peg.m_max + "; startIndx=" + startIndx + "; need=" + peg.m_need +
                "; price1=" + peg.m_price1 + "; p1=" + peg.m_pair1 +
                "; price2=" + peg.m_price2 + "; p2=" + peg.m_pair2 +
                "; price3=" + peg.m_price3 + "; p3=" + peg.m_pair3);

        if (gain > 1) {
            if (Triplet.s_level > Triplet.LVL2) {
                double level = Triplet.s_level;
                Triplet.s_level = (Triplet.s_level - Triplet.LVL) / 1.2 + Triplet.LVL;
                Triplet.s_level = Math.max(Triplet.s_level, Triplet.LVL2);
                triTradeData.log(" LEVEL decreased from " + Utils.X_YYYYY.format(level) + " to " + Utils.X_YYYYY.format(Triplet.s_level));
            }
        } else {
            double level = Triplet.s_level;
            Triplet.s_level = (Triplet.s_level - Triplet.LVL) * 1.3 + Triplet.LVL;
            triTradeData.log(" LEVEL increased from " + Utils.X_YYYYY.format(level) + " to " + Utils.X_YYYYY.format(Triplet.s_level));
        }
        triTradeData.setState(DONE);
    }

    private static void checkMktPlaced(IterationData iData, TriangleData triangleData, TriTradeData triTradeData, int num/*1 or 2*/, TriTradeState stateForFilled) throws Exception {
        boolean isFirst = (num == 1);
        int indx = num - 1;
        OrderData order = triTradeData.m_mktOrders[indx];
        String orderStr = (order != null) ? order.toString(Exchange.BTCE) : null;
        triTradeData.log("TriTradeState.MKT" + num + "_PLACED(" + triTradeData.m_peg.name() + ") - check order " + orderStr + " ...");
        if (order == null) { // move mkt order can be unsuccessful - cancel fine, but placing failed - just start mkt again
            triTradeData.log(" no MKT order " + num + " - placing new " + (isFirst ? "1st" : "2nd") + " MKT order...");
            triTradeData.startMktOrder(iData, triangleData, num, stateForFilled);
        } else {
            order.checkState(iData, Exchange.BTCE, triangleData.m_account, null, triangleData);
            triangleData.forkAndCheckFilledIfNeeded(iData, triTradeData, order, stateForFilled);
            if(!order.isFilled()) {
                orderStr = order.toString(Exchange.BTCE);
                Pair pair = order.m_pair;
                TopData top = iData.getTop(Exchange.BTCE, pair);
                String topStr = top.toString(Exchange.BTCE, pair);
                double mktPrice = order.m_side.mktPrice(top);
                double orderPrice = order.m_price;
                double absPriceDif = Math.abs(orderPrice - mktPrice);
                double minPriceStep = Exchange.BTCE.minPriceStep(pair);
                if(absPriceDif > minPriceStep) {
                    triTradeData.log("MKT order " + num + " run out of market: " + orderStr + ";  top=" + topStr);
                    if (triangleData.cancelOrder(order, iData)) {
                        triTradeData.m_mktOrders[indx] = null;
                        triTradeData.log("placing new " + (isFirst ? "1st" : "1nd") + " MKT order...");
                        triTradeData.startMktOrder(iData, triangleData, num, stateForFilled);
                    } else {
                        triTradeData.log("cancel order failed: " + orderStr);
                    }
                } else {
                    triTradeData.log("MKT order " + num + " is on market bound - wait some more: " + orderStr + ";  top=" + topStr);
                }
            }
        }
        triTradeData.log("TriTradeState.MKT" + num + "_PLACED(" + triTradeData.m_peg.name() + ") END");
    }

    public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {}
}
