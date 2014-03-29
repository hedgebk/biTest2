package bthdg.triplet;

import bthdg.*;
import bthdg.exch.TopData;

import java.util.Map;

public enum TriTradeState {
    PEG_PLACED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            OrderData order = triTradeData.m_order;
            log("TriTradeState.PEG_PLACED(" + triTradeData.m_peg.name() + ") - check order " + order + " ...");
            order.checkState(iData, Exchange.BTCE, triangleData.m_account, null, triangleData);
            if (order.isFilled()) {
                triTradeData.setState(TriTradeState.PEG_FILLED);
            } // else : move to PEG price if needed
        }
    },
    PEG_FILLED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            log("TriTradeState.PEG_FILLED(" + triTradeData.m_peg.name() + ") - run 1st MKT order...");
            startMktOrder(iData, triangleData, triTradeData, 1);
        }
    },
    MKT1_PLACED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            OrderData order = triTradeData.m_mktOrders[0];
            log("TriTradeState.MKT1_PLACED(" + triTradeData.m_peg.name() + ") - check order " + order + " ...");
            order.checkState(iData, Exchange.BTCE, triangleData.m_account, null, triangleData);
            if (order.isFilled()) {
                triTradeData.setState(TriTradeState.MKT1_EXECUTED);
            } else {
                log("1st MKT order run out of market " + order + ";  top=" + iData.getTop(Exchange.BTCE, order.m_pair));
                if (triangleData.cancelOrder(order, iData)) {
                    triTradeData.m_mktOrders[0] = null;
                    log("placing new 1st MKT order...");
                    startMktOrder(iData, triangleData, triTradeData, 1);
                } else {
                    log("cancel order failed: " + order);
                }
            }
        }
    },
    MKT1_EXECUTED{
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            log("TriTradeState.MKT1_EXECUTED(" + triTradeData.m_peg.name() + ") - run 2nd MKT order...");
            startMktOrder(iData, triangleData, triTradeData, 2);
        }
    },
    MKT2_PLACED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            OrderData order = triTradeData.m_mktOrders[1];
            log("TriTradeState.MKT2_PLACED(" + triTradeData.m_peg.name() + ") - check order " + order + " ...");
            order.checkState(iData, Exchange.BTCE, triangleData.m_account, null, triangleData);
            if (order.isFilled()) {
                triTradeData.setState(TriTradeState.MKT2_EXECUTED);
            } else {
                log("2nd MKT order run out of market " + order + ";  top=" + iData.getTop(Exchange.BTCE, order.m_pair));
                if (triangleData.cancelOrder(order, iData)) {
                    triTradeData.m_mktOrders[1] = null;
                    log("placing new 2nd MKT order...");
                    startMktOrder(iData, triangleData, triTradeData, 2);
                } else {
                    log("cancel order failed: " + order);
                }
            }
        }
    },
    MKT2_EXECUTED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            log("MKT2_EXECUTED(" + triTradeData.m_peg.name() + ") - we are done");

            OnePegCalcData peg = triTradeData.m_peg;
            TriangleRotationCalcData rotationData = peg.m_parent;
            Triangle triangle = rotationData.m_triangle;
            String name = triangle.name();
            int startIndx = peg.m_indx;
            boolean rotationDirection = rotationData.m_forward;
//            log(" " + name + "; start=" + startIndx + "; direction=" + rotationDirection);

            AccountData account = triangleData.m_account;

            OrderData order1 = triTradeData.m_order;
            OrderData order2 = triTradeData.m_mktOrders[0];
            OrderData order3 = triTradeData.m_mktOrders[1];

            double[] ends1 = order1.logOrderEnds(account, 1, peg.m_price1);
            double[] ends2 = order2.logOrderEnds(account, 2, peg.m_price2);
            double[] ends3 = order3.logOrderEnds(account, 3, peg.m_price3);
            Currency currency = order1.startCurrency();
            log(
                "  start " + Utils.X_YYYYY.format(order1.startAmount()) + " " + currency +
                "  end " + Utils.X_YYYYY.format(order1.endAmount(account)) + " " + order1.endCurrency() +
                " | start " + Utils.X_YYYYY.format(order2.startAmount()) + " " + order2.startCurrency() +
                "  end " + Utils.X_YYYYY.format(order2.endAmount(account)) + " " + order2.endCurrency() +
                " | start " + Utils.X_YYYYY.format(order3.startAmount()) + " " + order3.startCurrency() +
                "  end " + Utils.X_YYYYY.format(order3.endAmount(account)) + " " + order3.endCurrency()
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

            Map<Pair, TopData> tops = iData.getTops();
            double valuateEur = account.evaluateEur(tops);
            double eurRate = valuateEur / Triplet.s_startEur;
            double valuateUsd = account.evaluateUsd(tops);
            double usdRate = valuateUsd / Triplet.s_startUsd;

            double midMul = account.midMul(Triplet.s_startAccount);

            log(" @@@@@@   ratio1=" + Utils.X_YYYYY.format(ratio1) + ";  ratio2=" + Utils.X_YYYYY.format(ratio2) +
                    ";  ratio3=" + Utils.X_YYYYY.format(ratio3) + ";    ratio=" + Utils.X_YYYYY.format(ratio));
            log(" @@@@@@   in=" + Utils.X_YYYYY.format(in) + ";  out=" + Utils.X_YYYYY.format(out) +
                    "; out-in=" + Utils.X_YYYYY.format(out - in) + " " + currency + ";  gain=" + Utils.X_YYYYY.format(gain) +
                    "; level=" + Triplet.s_level + ";  totalRatio=" + Utils.X_YYYYY.format(Triplet.s_totalRatio) +
                    "; millis=" + System.currentTimeMillis() + "; valuateUsd=" + Utils.X_YYYYY.format(usdRate) +
                    "; valuateEur=" + Utils.X_YYYYY.format(eurRate) + "; midMul=" + Utils.X_YYYYY.format(midMul) +
                    "; count=" + Triplet.s_counter);
            log(" @@@@@@    peg: max=" + peg.m_max + "; startIndx=" + startIndx + "; need=" + peg.m_need +
                    "; price1=" + peg.m_price1 + "; p1=" + peg.m_pair1 +
                    "; price2=" + peg.m_price2 + "; p2=" + peg.m_pair2 +
                    "; price3=" + peg.m_price3 + "; p3=" + peg.m_pair3);

            if (gain > 1) {
                if (Triplet.s_level > Triplet.LVL2) {
                    double level = Triplet.s_level;
                    Triplet.s_level = (Triplet.s_level - Triplet.LVL) / 1.2 + Triplet.LVL;
                    Triplet.s_level = Math.max(Triplet.s_level, Triplet.LVL2);
                    log(" LEVEL decreased from " + Utils.X_YYYYY.format(level) + " to " + Utils.X_YYYYY.format(Triplet.s_level));
                }
            } else {
                double level = Triplet.s_level;
                Triplet.s_level = (Triplet.s_level - Triplet.LVL) * 1.3 + Triplet.LVL;
                log(" LEVEL increased from " + Utils.X_YYYYY.format(level) + " to " + Utils.X_YYYYY.format(Triplet.s_level));
            }
            triTradeData.setState(DONE);
        }
    },
    DONE {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            log("DONE state on " + this);
        }
    },
    CANCELED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            log("CANCELED state on " + this);
        }
    },
    ERROR {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            log("ERROR state on " + this);
        }
    },
    ;

    private static boolean startMktOrder(IterationData iData, TriangleData triangleData, TriTradeData triTradeData, int num /*1 or 2*/) throws Exception {
        log("startMktOrder(" + num + ")");

        AccountData account = triangleData.m_account;
        OnePegCalcData peg = triTradeData.m_peg;
        Map<Pair, TopData> tops = iData.getTops();

        PairDirection pd = (num == 1) ? peg.m_pair2 : peg.m_pair3;
        Pair pair = pd.m_pair;
        boolean forward = pd.m_forward;
        TopData topData = tops.get(pair);
        OrderSide side = forward ? OrderSide.BUY : OrderSide.SELL;

        // evaluate current mkt prices
        double ratio1 = triTradeData.m_order.ratio(account);
        double ratio2 = (num == 1) ? peg.mktRatio2(tops, account) : triTradeData.m_mktOrders[0].ratio(account);
        double ratio3 = peg.mktRatio3(tops, account);
        double ratio = ratio1 * ratio2 * ratio3;
        log(" ratio1=" + ratio1 + "; ratio2=" + ratio2 + "; ratio3=" + ratio3 + "; ratio=" + ratio);
        if (ratio < 1) {
            double zeroProfitPrice = (num == 1) ? (1.0 / ratio1 / ratio3) : (1.0 / ratio1 / ratio2);

            log("  MKT conditions do not allow profit on MKT orders close. pair=" + pair + "; forward=" + forward +
                    "; side=" + side + "; zeroProfitPrice=" + zeroProfitPrice + "; top=" + topData);
            if( (topData.m_ask > zeroProfitPrice) && (zeroProfitPrice > topData.m_bid)) {
                log("   ! zeroProfitPrice is between mkt edges" );
            }

            if (triTradeData.m_waitMktOrder++ < Triplet.WAIT_MKT_ORDER_STEPS) {
                log("   wait some time. waitMktOrder counter=" + triTradeData.m_waitMktOrder);
                triTradeData.setState((num == 1) ? TriTradeState.PEG_FILLED : TriTradeState.MKT1_EXECUTED);
                return false;
            } else {
                log("   we run out of waitMktOrder attempts. placing MKT (" + num + ")");
// todo: nothing to do - place non mkt order, but still profitable / zero / loss decreased
            }
        }
        triTradeData.m_waitMktOrder = 0;

        OrderData prevOrder = (num == 1) ? triTradeData.m_order : triTradeData.m_mktOrders[0];
        OrderSide prevSide = prevOrder.m_side;
        Currency prevEndCurrency = prevOrder.endCurrency();
        double prevEndAmount = (prevSide.isBuy() ? prevOrder.m_amount : prevOrder.m_amount * prevOrder.m_price) * (1 - account.m_fee); // deduct commissions
        log(" prev order " + prevOrder + "; exit amount " + prevEndAmount + " " + prevEndCurrency);

        String name = peg.name();
        double mktPrice = side.mktPrice(topData);

        Currency fromCurrency = pair.currencyFrom(forward);
        if (prevEndCurrency != fromCurrency) {
            log("ERROR: currencies are not matched");
        }
        double available = account.available(fromCurrency);
        if (prevEndAmount > available) {
            log(" try cancel PEG orders for " + fromCurrency + " - not enough available funds to place MKT: available=" + available + "; needed=" + prevEndAmount);

            for (TriTradeData nextTriTrade : triangleData.m_triTrades) {
                if (nextTriTrade.m_state == PEG_PLACED) {
                    OrderData order = nextTriTrade.m_order;
                    Currency startCurrency = order.startCurrency();
                    if (startCurrency == fromCurrency) {
                        log("  found PEG order for " + fromCurrency + " " + nextTriTrade.m_peg.name() + " " + order);
                        boolean canceled = triangleData.cancelOrder(order, iData);
                        if (canceled) {
                            nextTriTrade.setState(CANCELED);
                            available = account.available(fromCurrency);
                            if (prevEndAmount < available) {
                                break;
                            }
                        } else {
                            log("   cancel order error for " + order);
                        }
                    }
                }
            }

            available = account.available(fromCurrency);
            if (prevEndAmount > available) {
                log("ERROR: still not enough available funds to place MKT: available=" + available);
                triTradeData.setState(CANCELED);
                return false;
            } else {
                log(" fine - released enough funds to place MKT(" + num + "): available=" + available + "; needed=" + prevEndAmount);
                tops = iData.loadTops(); // re-request tops since time passed - mktPrice can run out
            }
        }

        double amount = side.isBuy() ? prevEndAmount / mktPrice : prevEndAmount;

        log("order(" + num + "):" + name + ", pair: " + pair + ", forward=" + forward + ", from=" + fromCurrency +
                "; available=" + available + "; amount=" + amount + "; side=" + side +
                "; mktPrice=" + Triplet.format4(mktPrice) + "; top: " + topData);

        // todo: check for physical min order size like 0.01
        OrderData order = new OrderData(pair, side, mktPrice, amount);
        boolean ok = Triplet.placeOrder(account, order, OrderState.MARKET_PLACED, iData);
        log("   place order = " + ok);
        if (ok) {
            triTradeData.setMktOrder(order, num - 1);
            return true;
        } else {
            triTradeData.setState(ERROR);
            return false;
        }
    }

    public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {}

    private static void log(String s) {
        Log.log(s);
    }
}
