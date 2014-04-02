package bthdg.triplet;

import bthdg.*;
import bthdg.exch.TopData;
import bthdg.exch.TopsData;

public enum TriTradeState {
    PEG_PLACED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            OrderData order = triTradeData.m_order;
            triTradeData.log("TriTradeState.PEG_PLACED(" + triTradeData.m_peg.name() + ") - check order " + order + " ...");
            order.checkState(iData, Exchange.BTCE, triangleData.m_account, null, triangleData);
            if (order.isFilled()) {
                triTradeData.setState(TriTradeState.PEG_FILLED);
            } // else : move to PEG price if needed
        }
    },
    PEG_FILLED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("TriTradeState.PEG_FILLED(" + triTradeData.m_peg.name() + ") - run 1st MKT order...");
            startMktOrder(iData, triangleData, triTradeData, 1);
        }
    },
    MKT1_PLACED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            checkMktPlaced(iData, triangleData, triTradeData, 1);
        }
    },
    MKT1_EXECUTED{
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("TriTradeState.MKT1_EXECUTED(" + triTradeData.m_peg.name() + ") - run 2nd MKT order...");
            startMktOrder(iData, triangleData, triTradeData, 2);
        }
    },
    MKT2_PLACED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            checkMktPlaced(iData, triangleData, triTradeData, 2);
        }
    },
    MKT2_EXECUTED {
        @Override public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {
            triTradeData.log("MKT2_EXECUTED(" + triTradeData.m_peg.name() + ") - we are done");

            OnePegCalcData peg = triTradeData.m_peg;
            TriangleRotationCalcData rotationData = peg.m_parent;
            Triangle triangle = rotationData.m_triangle;
//            String name = triangle.name();
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
                    ";  ratio3=" + Utils.X_YYYYY.format(ratio3) + ";    ratio=" + Utils.X_YYYYY.format(ratio));
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

    private static void checkMktPlaced(IterationData iData, TriangleData triangleData, TriTradeData triTradeData, int num/*1 or 2*/) throws Exception {
        boolean isFirst = (num == 1);
        int indx = num - 1;
        OrderData order = triTradeData.m_mktOrders[indx];
        triTradeData.log("TriTradeState.MKT" + num + "_PLACED(" + triTradeData.m_peg.name() + ") - check order " + order + " ...");
        if (order == null) { // move mkt order can be unsuccess - cancel fine, but placing failed - just start mkt again
            triTradeData.log(" no mkt order - placing new " + (isFirst ? "1st" : "1nd") + " MKT order...");
            startMktOrder(iData, triangleData, triTradeData, num);
        } else {
            order.checkState(iData, Exchange.BTCE, triangleData.m_account, null, triangleData);
            if (order.isFilled()) {
                triTradeData.setState(isFirst ? TriTradeState.MKT1_EXECUTED : TriTradeState.MKT2_EXECUTED);
            } else if (order.isPartiallyFilled(Exchange.BTCE)) {
                triTradeData.log("MKT order " + num + " is partially filled - will split");
            } else {
                triTradeData.log("MKT order " + num + " run out of market: " + order + ";  top=" + iData.getTop(Exchange.BTCE, order.m_pair));
                if (triangleData.cancelOrder(order, iData)) {
                    triTradeData.m_mktOrders[indx] = null;
                    triTradeData.log("placing new " + (isFirst ? "1st" : "1nd") + " MKT order...");
                    startMktOrder(iData, triangleData, triTradeData, num);
                } else {
                    triTradeData.log("cancel order failed: " + order);
                }
            }
        }
    }

    private static void startMktOrder(IterationData iData, TriangleData triangleData, TriTradeData triTradeData, int num /*1 or 2*/) throws Exception {
        triTradeData.log("startMktOrder(" + num + ") waitStep=" + triTradeData.m_waitMktOrderStep);

        AccountData account = triangleData.m_account;
        OnePegCalcData peg = triTradeData.m_peg;
        TopsData tops = iData.getTops();

        PairDirection pd = (num == 1) ? peg.m_pair2 : peg.m_pair3;
        Pair pair = pd.m_pair;
        boolean forward = pd.m_forward;
        TopData topData = tops.get(pair);
        OrderSide side = forward ? OrderSide.BUY : OrderSide.SELL;
        double tryPrice = side.mktPrice(topData);
        boolean useLmtPrice = false;

        // evaluate current mkt prices
        double ratio1 = triTradeData.m_order.ratio(account); // commission is applied to ratio
        double ratio2 = (num == 1) ? peg.mktRatio2(tops, account) : triTradeData.m_mktOrders[0].ratio(account); // commission is applied to ratio
        double ratio3 = peg.mktRatio3(tops, account); // commission is applied to ratio
        double ratio = ratio1 * ratio2 * ratio3;
        triTradeData.log(" ratio1=" + ratio1 + "; ratio2=" + ratio2 + "; ratio3=" + ratio3 + "; ratio=" + ratio);
        if (ratio < 1) {
            double zeroProfitRatio = (num == 1) ? (1.0 / ratio1 / ratio3) : (1.0 / ratio1 / ratio2);
            double zeroProfitPrice = zeroProfitRatio / (1 - account.m_fee); // add commission
            if (side.isBuy()) {
                zeroProfitPrice = 1 / zeroProfitPrice;
            }
            double mid = topData.getMid();

            triTradeData.log("  MKT conditions do not allow profit on MKT orders close. pair=" + pair + "; forward=" + forward +
                    "; side=" + side + "; zeroProfitPrice=" + zeroProfitPrice + "; top=" + topData);
            if( (topData.m_ask > zeroProfitPrice) && (zeroProfitPrice > topData.m_bid)) {
                triTradeData.log("   ! zero_Profit_Price is between mkt edges; mid=" + mid);
            }

            if (triTradeData.m_waitMktOrderStep++ < Triplet.WAIT_MKT_ORDER_STEPS) {
                double mktPrice = tryPrice;
                double priceAdd = (tryPrice - mid) * (triTradeData.m_waitMktOrderStep - 1) / Triplet.WAIT_MKT_ORDER_STEPS;
                tryPrice = mid + priceAdd;
                triTradeData.log("     wait some time - try with mid->mkt orders first: mkt=" + mktPrice + "; mid=" + mid +
                        "; step=" + triTradeData.m_waitMktOrderStep + "; priceAdd=" + priceAdd + ", lmtPrice=" + tryPrice);
                useLmtPrice = true;
            } else {
                triTradeData.log("   we run out of waitMktOrder attempts. placing MKT (" + num + ")");
// todo: nothing to do - place non mkt order, but still profitable / zero / loss decreased
                triTradeData.m_waitMktOrderStep = 0;
            }
        } else {
            // todo - try e.g. mkt-10 first  to get better profit
            triTradeData.m_waitMktOrderStep = 0;
        }

        OrderData prevOrder = (num == 1) ? triTradeData.m_order : triTradeData.m_mktOrders[0];
        OrderSide prevSide = prevOrder.m_side;
        Currency prevEndCurrency = prevOrder.endCurrency();
        double prevEndAmount = (prevSide.isBuy() ? prevOrder.m_amount : prevOrder.m_amount * prevOrder.m_price) * (1 - account.m_fee); // deduct commissions
        triTradeData.log(" prev order " + prevOrder + "; exit amount " + prevEndAmount + " " + prevEndCurrency);

        Currency fromCurrency = pair.currencyFrom(forward);
        if (prevEndCurrency != fromCurrency) {
            triTradeData.log("ERROR: currencies are not matched");
        }
        double available = account.available(fromCurrency);
        if (prevEndAmount > available) {
            triTradeData.log(" try cancel PEG orders for " + fromCurrency + " - not enough available funds to place MKT: available=" + available + "; needed=" + prevEndAmount);

            tryCancelPegOrders(iData, triangleData, triTradeData, account, prevEndAmount, fromCurrency);

            available = account.available(fromCurrency);
            if (prevEndAmount > available) {
                triTradeData.log("ERROR: still not enough available funds to place MKT: available=" + available);
                triTradeData.setState(CANCELED);
            } else {
                triTradeData.log(" fine - released enough funds to place MKT(" + num + "): available=" + available + "; needed=" + prevEndAmount);
                tops = iData.loadTops(); // re-request tops since time passed - mktPrice can run out
            }
        }

        double amount = side.isBuy() ? (prevEndAmount / tryPrice) : prevEndAmount;

        triTradeData.log("order(" + num + "):" + peg.name() + ", pair: " + pair + ", forward=" + forward + ", from=" + fromCurrency +
                "; available=" + available + "; amount=" + amount + "; side=" + side +
                "; mktPrice=" + Triplet.format5(tryPrice) + "; top: " + topData);

        // todo: check for physical min order size like 0.01
        OrderData order = new OrderData(pair, side, tryPrice, amount);
        OrderState ordState = useLmtPrice ? OrderState.LIMIT_PLACED : OrderState.MARKET_PLACED;
        OrderData.OrderPlaceStatus ok = Triplet.placeOrder(account, order, ordState, iData);
        triTradeData.log("   place order = " + ok);
        if (ok == OrderData.OrderPlaceStatus.OK) {
            triTradeData.setMktOrder(order, num - 1);
        } else if (ok == OrderData.OrderPlaceStatus.CAN_REPEAT) {
            triTradeData.log("    some problem, but we can repeat place order on next iteraction");
        } else {
            triTradeData.log("    error placing order - setting error in triTrade");
            triTradeData.setState(ERROR);
        }
    }

    private static void tryCancelPegOrders(IterationData iData, TriangleData triangleData, TriTradeData triTradeData, AccountData account, double prevEndAmount, Currency fromCurrency) throws Exception {
        double available;
        for (TriTradeData nextTriTrade : triangleData.m_triTrades) {
            if (nextTriTrade.m_state == PEG_PLACED) {
                OrderData order = nextTriTrade.m_order;
                Currency startCurrency = order.startCurrency();
                if (startCurrency == fromCurrency) {
                    triTradeData.log("  found PEG order for " + fromCurrency + " " + nextTriTrade.m_peg.name() + " " + order);
                    boolean canceled = triangleData.cancelOrder(order, iData);
                    if (canceled) {
                        nextTriTrade.setState(CANCELED);
                        available = account.available(fromCurrency);
                        if (prevEndAmount < available) {
                            break;
                        }
                    } else {
                        triTradeData.log("   cancel peg order error for " + order);
                    }
                }
            }
        }
    }

    public void checkState(IterationData iData, TriangleData triangleData, TriTradeData triTradeData) throws Exception {}

//    private static void log(String s) {
//        Log.log(s);
//    }
}
