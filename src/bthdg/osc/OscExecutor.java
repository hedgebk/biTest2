package bthdg.osc;

import bthdg.Fetcher;
import bthdg.IIterationContext;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.util.Utils;
import bthdg.ws.ITopListener;
import bthdg.ws.IWs;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

class OscExecutor implements Runnable{
    public static final int MIN_REPROCESS_DIRECTION_TIME = 5000;
    public static final double OPPOSITE_DIRECTION_FACTOR = 0.85;
    public static final int AVG_PRICE_PERIOD_RATIO1 = 3;
    public static final int AVG_PRICE_PERIOD_RATIO2 = 5;

    private final IWs m_ws;
    private final long m_startMillis;
    private int m_direction;
    private boolean m_run = true;
    private boolean m_changed;
    private boolean m_initialized;
    private OscOrderWatcher m_orderWatcher;

    private TopsData m_initTops;
    private TopsData m_topsData;
    private AccountData m_initAccount;
    private AccountData m_account;
    private State m_state = State.NONE;
    private double m_buy;
    private double m_sell;
    private OrderData m_order;
    private List<CloseOrderWrapper> m_closeOrders = new ArrayList<CloseOrderWrapper>();
    private boolean m_maySyncAccount = false;
    private final Utils.AverageCounter m_avgPriceCounter1;
    private final Utils.AverageCounter m_avgPriceCounter2;
    final TrendCounter m_priceTrendCounter;
    private final Booster m_booster;
    private final AvgStochDirectionAdjuster m_avgStochDirectionAdjuster;
    private final AvgPriceDirectionAdjuster m_avgPriceDirectionAdjuster;
    private long m_lastProcessDirectionTime;
    private boolean m_feeding;

    private static void log(String s) { Log.log(s); }

    public OscExecutor(IWs ws) {
        m_avgStochDirectionAdjuster = new AvgStochDirectionAdjuster();
        m_avgPriceDirectionAdjuster = new AvgPriceDirectionAdjuster();
        m_avgPriceCounter1 = new Utils.FadingAverageCounter(Osc.AVG_BAR_SIZE * AVG_PRICE_PERIOD_RATIO1);
        m_avgPriceCounter2 = new Utils.FadingAverageCounter(Osc.AVG_BAR_SIZE * AVG_PRICE_PERIOD_RATIO2);
        m_priceTrendCounter = new TrendCounter(Osc.AVG_BAR_SIZE);
        m_booster = new Booster();
        m_startMillis = System.currentTimeMillis();
        m_ws = ws;
        Thread thread = new Thread(this);
        thread.setName("OscExecutor");
        thread.start();
    }

    public void update(int delta) {
        synchronized (this) {
            int direction = m_direction;
            m_direction += delta;
            log("update(delta=" + delta + ") direction updated from " + direction + " -> " + m_direction);
            m_changed = true;
            notify();
        }
    }

    @Override public void run() {
        while (m_run) {
            try {
                boolean changed;
                synchronized (this) {
                    if (!m_changed) {
                        log("waiting for updated osc direction");
                        wait();
                    }
                    changed = m_changed;
                    m_changed = false;
                }
                if (changed) {
                    log("process updated osc direction=" + m_direction);
                    postRecheckDirection();
                }
            } catch (Exception e) {
                log("error in OscExecutor");
                e.printStackTrace();
            }
        }
    }

    void init() {
        if (!m_initialized) {
            log("not initialized - added InitTask to queue");
            addTask(new InitTask());
            m_initialized = true;
        }
    }

    private void initImpl() throws Exception {
        log("OscExecutor.initImpl()................");

        Exchange exchange = m_ws.exchange();
        m_topsData = Fetcher.fetchTops(exchange, Osc.PAIR);
        log(" topsData=" + m_topsData);

        initAccount();
        m_initAccount = m_account.copy();
        m_initTops = m_topsData.copy();

        log("initImpl() continue: subscribeTrades()");
        m_ws.subscribeTop(Osc.PAIR, new ITopListener() {
            @Override public void onTop(long timestamp, double buy, double sell) {
//                    log("onTop() timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
                if (buy > sell) {
                    log("ERROR: ignored invalid top data. buy > sell: timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
                    return;
                }
                m_buy = buy;
                m_sell = sell;

                TopData topData = new TopData(buy, sell);
                m_topsData.put(Osc.PAIR, topData);
                log(" topsData'=" + m_topsData);

                addTask(new TopTask());
            }
        });
    }

    private void initAccount() throws Exception {
        Exchange exchange = m_ws.exchange();
        AccountData account = m_account;
        m_account = Fetcher.fetchAccount(exchange);
        if (m_account != null) {
            log(" account=" + m_account);
            double valuateBtc = m_account.evaluate(m_topsData, Currency.BTC, exchange);
            log("  valuateBtc=" + valuateBtc + " BTC");
            if (account!= null) {
                account.compareFunds(m_account);
            }
            m_maySyncAccount = false;
        } else {
            log("account request error");
        }
    }

    private void setState(State state) {
        if ((state != null) && (m_state != state)) {
            log("STATE changed from " + m_state + " to " + state);
            m_state = state;
        }
    }

    private void recheckDirection() throws Exception {
        log("OscExecutor.recheckDirection() m_direction=" + m_direction);
        setState(m_state.onDirection(this));
    }

    private IIterationContext.BaseIterationContext checkLiveOrders() throws Exception {
        log("checkLiveOrders()");
        IIterationContext.BaseIterationContext iContext = null;
        if (!m_closeOrders.isEmpty()) {
            iContext = checkCloseOrdersState(null);
        }
        if (m_order != null) {
            iContext = (iContext == null) ? getLiveOrdersContext() : iContext;
            setState(checkOrderState(iContext));
        }
        return iContext;
    }

    private void gotTop() throws Exception {
//            log("OscExecutor.gotTop() buy=" + buy + "; sell=" + sell);
//            log(" topsData =" + m_topsData);
        m_state.onTop(this);
    }

    private void gotTrade(TradeData tData) throws Exception {
        log("OscExecutor.gotTrade() tData=" + tData);

        double avg1 = m_avgPriceCounter1.get();
        double avg2 = m_avgPriceCounter2.get();
        Double last = m_priceTrendCounter.getLast();
        Double oldest = m_priceTrendCounter.getOldest();
        double trend = last - oldest;
        log(" avg1=" + avg1 + " avg2=" + avg2 + "; last=" + last + "; oldest=" + oldest + "; trend=" + trend);

        IIterationContext.BaseIterationContext iContext = checkCloseOrdersStateIfNeeded(tData, null);
        setState(m_state.onTrade(this, tData, iContext));

        if (m_feeding) {
            long passed = System.currentTimeMillis() - m_lastProcessDirectionTime;
            if (passed > Math.min(Osc.AVG_BAR_SIZE, MIN_REPROCESS_DIRECTION_TIME)) {
                log(" no check direction for long time " + passed + "ms - postRecheckDirection");
                postRecheckDirection();
            }
        }
    }

    private IIterationContext.BaseIterationContext checkCloseOrdersStateIfNeeded(TradeData tData, IIterationContext.BaseIterationContext inContext) throws Exception {
        IIterationContext.BaseIterationContext iContext = inContext;
        if (!m_closeOrders.isEmpty()) {
            double tradePrice = tData.m_price;
            boolean gotMatchedPrice = false;
            for (CloseOrderWrapper closeOrderWrapper : m_closeOrders) {
                OrderData closeOrder = closeOrderWrapper.m_closeOrder;
                double closeOrderPrice = closeOrder.m_price;
                if (closeOrderPrice == tradePrice) {
                    log("trade price " + tData + " matched with close order " + closeOrder);
                    gotMatchedPrice = true;
                }
            }
            if (gotMatchedPrice) {
                iContext = checkCloseOrdersState(iContext);
            }
        }
        return iContext;
    }

    private IIterationContext.BaseIterationContext checkCloseOrdersState(IIterationContext.BaseIterationContext inContext) throws Exception {
        IIterationContext.BaseIterationContext iContext = (inContext == null) ? getLiveOrdersContext() : inContext;
        Exchange exchange = m_ws.exchange();
        log(" checking close orders state...");
        boolean closed = false;
        for( ListIterator<CloseOrderWrapper> it = m_closeOrders.listIterator(); it.hasNext(); ) {
            CloseOrderWrapper closeOrderWrapper = it.next();
            OrderData closeOrder = closeOrderWrapper.m_closeOrder;
            closeOrder.checkState(iContext, exchange, m_account,
                    null, // TODO - implement exec listener, add partial support - to fire partial close orders
                    null);
            log("  closeOrder state checked: " + closeOrder);
            if (closeOrder.isFilled()) {
                OrderData openOrder = closeOrderWrapper.m_order;
                log("$$$$$$   closeOrder FILLED: " + closeOrder + "; open order: " + openOrder);
                it.remove();
                closed = true;
            }
        }
        if(closed) {
            log("some orders closed - post recheck direction");
            postRecheckDirection();
            logValuate(exchange);
        }
        return iContext;
    }

    private void logValuate(Exchange exchange) {
        log("{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{");
        double valuateBtcInit = m_initAccount.evaluate(m_initTops, Currency.BTC, exchange);
        double valuateCnhInit = m_initAccount.evaluate(m_initTops, Currency.CNH, exchange);
        log("  INIT:  valuateBtc=" + valuateBtcInit + " BTC; valuateCnh=" + valuateCnhInit + " CNH");
        double valuateBtcNow = m_account.evaluate(m_topsData, Currency.BTC, exchange);
        double valuateCnhNow = m_account.evaluate(m_topsData, Currency.CNH, exchange);
        log("  NOW:   valuateBtc=" + valuateBtcNow + " BTC; valuateCnh=" + valuateCnhNow + " CNH");
        double valuateBtcSleep = m_initAccount.evaluate(m_topsData, Currency.BTC, exchange);
        double valuateCnhSleep = m_initAccount.evaluate(m_topsData, Currency.CNH, exchange);
        log("  SLEEP: valuateBtc=" + valuateBtcSleep + " BTC; valuateCnh=" + valuateCnhSleep + " CNH");
        double gainBtc = valuateBtcNow / valuateBtcInit;
        double gainCnh = valuateCnhNow / valuateCnhInit;
        double gainAvg = (gainBtc + gainCnh) / 2;
        long takesMillis = System.currentTimeMillis() - m_startMillis;
        double pow = ((double) Utils.ONE_DAY_IN_MILLIS) / takesMillis;
        double projected = Math.pow(gainAvg, pow);
        log("  GAIN: Btc=" + gainBtc + "; Cnh=" + gainCnh + " CNH; avg=" + gainAvg + "; projected=" + projected + "; takes: " + Utils.millisToDHMSStr(takesMillis));
        log("}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}");
    }

    private OscOrderWatcher getOrderWatcher() {
        if (m_orderWatcher == null) {
            m_orderWatcher = new OscOrderWatcher();
        }
        return m_orderWatcher;
    }

    private void addTask(IOrderTask task) {
        getOrderWatcher().addTask(task);
    }

    private void postRecheckDirection() {
        addTask(new RecheckDirectionTask());
    }

    public void onTrade(TradeData tData) {
        long timestamp = tData.m_timestamp;
        m_avgPriceCounter1.justAdd(timestamp, tData.m_price);
        m_avgPriceCounter2.justAdd(timestamp, tData.m_price);
        m_priceTrendCounter.addPoint(timestamp, tData.m_price);
        addTask(new TradeTask(tData));
    }

    private State processDirection() throws Exception {
        m_lastProcessDirectionTime = System.currentTimeMillis();
        double directionAdjustedIn = ((double)m_direction) / Osc.PHASES / Osc.BAR_SIZES.length; // directionAdjusted  [-1 ... 1]
        log("processDirection() direction=" + m_direction + ") directionAdjustedIn=" + directionAdjustedIn);

        if ((directionAdjustedIn < -1) || (directionAdjustedIn > 1)) {
            log("ERROR: invalid directionAdjusted=" + directionAdjustedIn);
            if (directionAdjustedIn < -1) {
                directionAdjustedIn = -1;
            }
            if (directionAdjustedIn > 1) {
                directionAdjustedIn = 1;
            }
        }

//        if (Osc.RATIO_POWER != 1) {
//            boolean negative = (directionAdjusted < 0);
//            double boosted = Math.pow(Math.abs(directionAdjusted), 1 / Osc.RATIO_POWER);
//            if (negative) {
//                boosted = -boosted;
//            }
//            log("  boost direction from " + directionAdjusted + " to " + boosted);
//            directionAdjusted = boosted;
//        }

//        directionAdjusted = m_booster.boost(directionAdjusted);

        double directionAdjusted = m_avgStochDirectionAdjuster.adjustDirection(directionAdjustedIn);

        directionAdjusted = m_avgPriceDirectionAdjuster.adjustDirection(directionAdjustedIn, directionAdjusted);

        Exchange exchange = m_ws.exchange();

        double valuateBtc = m_account.evaluate(m_topsData, Currency.BTC, exchange);
        double valuateCnh = m_account.evaluate(m_topsData, Currency.CNH, exchange);
        log("  valuateBtc=" + valuateBtc + " BTC; valuateCnh=" + valuateCnh + " CNH");

        double haveBtc = m_account.getAllValue(Currency.BTC);
        double haveCnh = m_account.getAllValue(Currency.CNH);
        log("  haveBtc=" + Utils.format8(haveBtc) + " BTC; haveCnh=" + Utils.format8(haveCnh) + " CNH; on account=" + m_account);

        double needBtc = (directionAdjusted + 1) / 2 * valuateBtc;
        double needCnh = -(directionAdjusted - 1) / 2 * valuateCnh;
        log("  needBtc=" + Utils.format8(needBtc) + " BTC; needCnh=" + Utils.format8(needCnh) + " CNH");

        double needBuyBtc = needBtc - haveBtc;
        double needSellCnh = haveCnh - needCnh;
        log("  directionAdjusted=" + directionAdjusted + "; needBuyBtc=" + Utils.format8(needBuyBtc) + "; needSellCnh=" + Utils.format8(needSellCnh));

        double orderSize = Math.abs(needBuyBtc);
        OrderSide needOrderSide = (needBuyBtc == 0) ? null : (needBuyBtc > 0) ? OrderSide.BUY : OrderSide.SELL;
        log("   needOrderSide=" + needOrderSide + "; orderSize=" + orderSize);

        double placeOrderSize = 0;
        if (orderSize != 0) {
            double cumCancelOrdersSize = getCumCancelOrdersSize(needOrderSide);
            double orderSizeAdjusted = orderSize - cumCancelOrdersSize;
            log("    orderSize=" + Utils.format8(orderSize) + "; cumCancelOrdersSize=" + cumCancelOrdersSize + "; orderSizeAdjusted=" + Utils.format8(orderSizeAdjusted));

            if (orderSizeAdjusted > 0) {
                orderSizeAdjusted = (needOrderSide == OrderSide.BUY) ? orderSizeAdjusted : -orderSizeAdjusted;
                log("     signed orderSizeAdjusted=" + Utils.format8(orderSizeAdjusted));

                orderSizeAdjusted = adjustSizeToAvailable(needBuyBtc, exchange);
                log("      available adjusted orderSize=" + Utils.format8(orderSizeAdjusted));

                orderSizeAdjusted *= Osc.USE_FUNDS_FROM_AVAILABLE; // do not use ALL available funds
                log("       fund ratio adjusted orderSize=" + Utils.format8(orderSizeAdjusted));

                double orderSizeRound = exchange.roundAmount(orderSizeAdjusted, Osc.PAIR);
                placeOrderSize = Math.abs(orderSizeRound);
                log("        orderSizeAdjusted=" + Utils.format8(orderSizeAdjusted) + "; orderSizeRound=" + orderSizeRound + "; placeOrderSize=" + Utils.format8(placeOrderSize));
            }
        }

        double minOrderToCreate = exchange.minOrderToCreate(Osc.PAIR);
        if (placeOrderSize >= minOrderToCreate) {
            boolean cancelAttempted = cancelOrderIfDirectionDiffers(needOrderSide, placeOrderSize);
            if (!cancelAttempted) { // cancel attempt was not performed
                if (m_order == null) { // we should not have open order at this place
                    cancelAttempted = cancelSameDirectionCloseOrders(needOrderSide);
                    if (!cancelAttempted) { // cancel attempt was not performed
                        Currency currency = Osc.PAIR.currencyFrom(needOrderSide.isBuy());
                        log("  currency=" + currency + "; PAIR=" + Osc.PAIR);
                        double orderPrice = calcOrderPrice(exchange, directionAdjusted, needOrderSide);
                        m_order = new OrderData(Osc.PAIR, needOrderSide, orderPrice, placeOrderSize);
                        log("   orderData=" + m_order);

                        if (placeOrderToExchange(exchange, m_order)) {
                            return State.ORDER;
                        } else {
                            log("order place error - switch to ERROR state");
                            return State.ERROR;
                        }
                    } else {
                        log("some orders maybe closed - post recheck direction");
                        postRecheckDirection();
                    }
                } else {
                    log("warning: order still exist - do nothing: " + m_order);
                }
            } else {
                log("order cancel was attempted. time passed. posting recheck direction");
                postRecheckDirection();
            }
        } else {
            log("warning: small order to create: placeOrderSize=" + placeOrderSize + "; minOrderToCreate=" + minOrderToCreate);
            if (m_maySyncAccount) {
                log("no orders - we may re-check account");
                initAccount();
            }
            return State.NONE;
        }
        return null;
    }

    private double calcOrderPrice(Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
        // directionAdjusted [-1 ... 1]
        log("  buy=" + m_buy + "; sell=" + m_sell + "; directionAdjusted=" + directionAdjusted + "; needOrderSide=" + needOrderSide);
        double midPrice = (m_buy + m_sell) / 2;
        double halfBidAskDiff = (m_sell - m_buy) / 2;
        double directionEffect = (directionAdjusted + (needOrderSide.isBuy() ? -3 : 3)) / 4;
        double adjustedPrice = midPrice + directionEffect * halfBidAskDiff;
        log("   midPrice=" + midPrice + "; halfBidAskDiff=" + halfBidAskDiff + "; directionEffect=" + directionEffect + "; adjustedPrice=" + adjustedPrice);
        RoundingMode roundMode = needOrderSide.getPegRoundMode();
        double orderPrice = exchange.roundPrice(adjustedPrice, Osc.PAIR, roundMode);
        log("   roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
        return orderPrice;
    }

    private double adjustSizeToAvailable(double needBuyBtc, Exchange exchange) {
        log(" account=" + m_account);
        double haveBtc = m_account.available(Currency.BTC);
        double haveCnh = m_account.available(Currency.CNH);
        log(" haveBtc=" + Utils.format8(haveBtc) + "; haveCnh=" + Utils.format8(haveCnh));
        double buyBtc;
        if (needBuyBtc > 0) {
            log("  will buy Btc:");
            double needSellCnh = m_topsData.convert(Currency.BTC, Currency.CNH, needBuyBtc, exchange);
            double canSellCnh = Math.min(needSellCnh, haveCnh);
            double canBuyBtc = m_topsData.convert(Currency.CNH, Currency.BTC, canSellCnh, exchange);
            log("   need to sell " + needSellCnh + " CNH; can Sell " + canSellCnh + " CNH; this will buy " + canBuyBtc + " BTC");
            buyBtc = canBuyBtc;
        } else if (needBuyBtc < 0) {
            log("  will sell Btc:");
            double needSellBtc = -needBuyBtc;
            double canSellBtc = Math.min(needSellBtc, haveBtc);
            double canBuyCnh = m_topsData.convert(Currency.BTC, Currency.CNH, canSellBtc, exchange);
            log("   need to sell " + needSellBtc + " BTC; can Sell " + canSellBtc + " BTC; this will buy " + canBuyCnh + " CNH");
            buyBtc = -canSellBtc;
        } else {
            log("  do not buy/sell anything");
            buyBtc = 0;
        }
        return buyBtc;
    }

    private boolean cancelSameDirectionCloseOrders(OrderSide needOrderSide) throws Exception {
        log("  cancelSameDirectionCloseOrders() size=" + m_closeOrders.size());
        boolean cancelAttempted = false;
        if (!m_closeOrders.isEmpty() && (needOrderSide != null)) {
            boolean hadError = false;
            for (ListIterator<CloseOrderWrapper> iterator = m_closeOrders.listIterator(); iterator.hasNext(); ) {
                CloseOrderWrapper next = iterator.next();
                OrderData closeOrder = next.m_closeOrder;
                OrderSide closeOrderSide = closeOrder.m_side;
                log("   next closeOrder " + closeOrder);
                if (closeOrderSide == needOrderSide) {
                    log("    need cancel existing close order: " + closeOrder);
                    String error = cancelOrder(closeOrder);
                    if (error == null) {
                        iterator.remove();
                    } else {
                        // basically we may have no such order error here - when executed concurrently
                        log("ERROR canceling close order: " + error + "; " + m_order);
                        hadError = true;
                    }
                    cancelAttempted = true;
                }
            }
            if (hadError) {
                log("we had problems canceling close orders - re-check close orders state...");
                checkCloseOrdersState(null);
            }
        }
        return cancelAttempted;
    }

    private boolean cancelOrderIfDirectionDiffers(OrderSide needOrderSide, double needOrderSize) throws Exception {
        log("cancelOrderIfDirectionDiffers() needOrderSide=" + needOrderSide + "; needOrderSize=" + needOrderSize + "; order=" + m_order);
        boolean cancelAttempted = false;
        if (m_order != null) {
            boolean cancelOrder = true;
            OrderSide haveOrderSide = m_order.m_side;
            log("  we have existing order: " + m_order);
            log("   needOrderSide=" + needOrderSide + "; haveOrderSide=" + haveOrderSide);
            if (needOrderSide == haveOrderSide) {
                double remained = m_order.remained();
                log("   same order sides: we have order: needOrderSize=" + needOrderSize + "; remainedOrderSize=" + remained);
                double orderSizeRatio = remained / needOrderSize;
                log("    orderSizeRatio=" + orderSizeRatio);
                if ((orderSizeRatio < (1 + Osc.ORDER_SIZE_TOLERANCE)) && (orderSizeRatio > (1 - Osc.ORDER_SIZE_TOLERANCE))) {
                    log("     order Sizes are very close - do not cancel existing order (tolerance=" + Osc.ORDER_SIZE_TOLERANCE + ")");
                    cancelOrder = false;
                }
            } else {
                log("   OrderSides are different - definitely canceling (needOrderSide=" + needOrderSide + ", haveOrderSide=" + haveOrderSide + ")");
            }
            if (cancelOrder) {
                log("  need cancel existing order: " + m_order);
                String error = cancelOrder(m_order);
                cancelAttempted = true;
                if (error == null) {
                    log("   order cancelled OK");
                    m_order = null;
                } else {
                    log("ERROR in cancel order: " + error + "; " + m_order);
                    // the can be already executed (partially)
                    checkLiveOrders();
                }
                initAccount();
            }
        }
        return cancelAttempted;
    }

    private double getCumCancelOrdersSize(OrderSide haveOrderSide) {
        double cumCancelOrdersSize = 0;
        for (CloseOrderWrapper close : m_closeOrders) {
            OrderData closeOrder = close.m_closeOrder;
            OrderSide cancelOrderSide = closeOrder.m_side;
            if (haveOrderSide != cancelOrderSide) {
                double size = closeOrder.remained();
                cumCancelOrdersSize += size;
            }
        }
        return cumCancelOrdersSize;
    }

    private boolean placeOrderToExchange(Exchange exchange, OrderData order) throws Exception {
        m_maySyncAccount = true;
        if (m_account.allocateOrder(order)) {
            OrderData.OrderPlaceStatus ops = placeOrderToExchange(exchange, order, OrderState.LIMIT_PLACED);
            if (ops == OrderData.OrderPlaceStatus.OK) {
                log(" placeOrderToExchange successful: " + exchange + ", " + order + ", account: " + m_account);
                return true;
            } else {
                m_account.releaseOrder(order, exchange);
            }
        } else {
            log("ERROR: account allocateOrder unsuccessful: " + exchange + ", " + order + ", account: " + m_account);
        }
        return false;
    }

    private OrderData.OrderPlaceStatus placeOrderToExchange(Exchange exchange, OrderData order, OrderState state) throws Exception {
        OrderData.OrderPlaceStatus ret;
        PlaceOrderData poData = Fetcher.placeOrder(order, exchange);
        log(" PlaceOrderData: " + poData.toString(exchange, order.m_pair));
        String error = poData.m_error;
        if (error == null) {
            order.m_status = OrderStatus.SUBMITTED;
            double amount = poData.m_received;
            if (amount != 0) { // not implemented - makes sense for btce
                // see Triplet.placeOrderToExchange()
                String amountStr = order.roundAmountStr(exchange, amount);
                String orderAmountStr = order.roundAmountStr(exchange);
                throw new RuntimeException("  some part of order (" + amountStr + " from " + orderAmountStr + ") is executed at the time of placing ");
            }
            order.m_state = state;
            ret = OrderData.OrderPlaceStatus.OK;
        } else {
            order.m_status = OrderStatus.ERROR;
            order.m_state = OrderState.NONE;
            if (error.contains("SocketTimeoutException")) {
                ret = OrderData.OrderPlaceStatus.CAN_REPEAT;
                // actually order can be placed, but we will not control it - we will go to ERROR state
            } else if (error.contains("It is not enough") || // It is not enough BTC in the account for sale
                    (error.contains("Insufficient") && error.contains("balance"))) { // Insufficient CNY balance
                ret = OrderData.OrderPlaceStatus.ERROR;
                log("  NotEnoughFunds detected - need sync account");
                initAccount();
            } else {
                ret = OrderData.OrderPlaceStatus.ERROR;
            }
        }
        return ret;
    }

    private State processTrade(TradeData tData, IIterationContext.BaseIterationContext inContext) throws Exception {
        log("processTrade(tData=" + tData + ") m_order=" + m_order);
        if (m_order != null) {
            double ordPrice = m_order.m_price;
            double tradePrice = tData.m_price;
            log(" tradePrice=" + tradePrice + ", orderPrice=" + ordPrice);
            if (tradePrice == ordPrice) {
                log("  same price - MAYBE SOME PART OF OUR ORDER EXECUTED ?");
                double orderRemained = m_order.remained();
                double tradeSize = tData.m_amount;
                log("   orderRemained=" + orderRemained + "; tradeSize=" + tradeSize);

                IIterationContext.BaseIterationContext iContext = (inContext == null) ? getLiveOrdersContext() : inContext;
                return checkOrderState(iContext); //check orders state
            } else {
                if (tradePrice < m_buy) {
                    double buy = (m_buy * 2 + tradePrice) / 3;
                    log("  buy is adapted to trade price: " + m_buy + " -> " + buy);
                    m_buy = buy;
                }
                if (tradePrice > m_sell) {
                    double sell = (m_sell * 2 + tradePrice) / 3;
                    log("  sell is adapted to trade price: " + m_sell + " -> " + sell);
                    m_sell = sell;
                }
                if ((m_order != null) && !m_order.isFilled()) {
                    checkOrderOutOfMarket();
                }
            }
        }
        return null; // no change
    }

    private void checkOrderOutOfMarket() throws Exception {
        boolean isBuy = m_order.m_side.isBuy();
        double orderPrice = m_order.m_price;

        /// ask  110 will sell
        /// bid  100 will buy
        // order 95 buy
        double threshold = (m_sell - m_buy) / 2;
        if ((isBuy && (m_buy - threshold > orderPrice)) || (!isBuy && (m_sell + threshold < orderPrice))) {
            log("  order " + m_order.m_side + "@" + orderPrice + " is FAR out of MKT [buy=" + m_buy + "; sell=" + m_sell + "] - cancel order...");
            OrderData order = m_order;
            String error = cancelOrder(order);
            if (error == null) {
                m_order = null;
            } else {
                log("ERROR in cancel order: " + error + "; " + m_order);
                log("looks the order was already executed - need check live orders");
                checkLiveOrders();
            }
            log("need Recheck Direction");
            postRecheckDirection();
        } else {
            // order 101 buy
            /// ask  100 will sell
            /// bid   90 will buy
            if ((isBuy && (m_sell <= orderPrice)) || (!isBuy && (m_buy >= orderPrice))) {
                log("  MKT price [buy=" + m_buy + "; sell=" + m_sell + "] is crossed the order " + m_order.m_side + "@" + orderPrice + " - starting CheckLiveOrdersTask");
                addTask(new CheckLiveOrdersTask());
            }
        }
    }

    private String cancelOrder(OrderData order) throws Exception {
        log("cancelOrder() " + order);
        String error = m_account.cancelOrder(order);
        m_maySyncAccount = true;
        return error;
    }

    private State checkOrderState(IIterationContext.BaseIterationContext iContext) throws Exception {
        Exchange exchange = m_ws.exchange();
        m_order.checkState(iContext, exchange, m_account,
                null, // TODO - implement exec listener, add partial support - to fire partial close orders
                null);
        log("  order state checked: " + m_order);

        if (m_order.isFilled()) {
            log("$$$$$$   order FILLED: " + m_order);
            if(Osc.DO_CLOSE_ORDERS) {
                OrderSide orderSide = m_order.m_side;
                OrderSide closeSide = orderSide.opposite();
                double closePrice = m_order.m_price + (orderSide.isBuy() ? Osc.CLOSE_PRICE_DIFF : -Osc.CLOSE_PRICE_DIFF);
                double closeSize = m_order.m_amount;

                OrderData closeOrder = new OrderData(Osc.PAIR, closeSide, closePrice, closeSize);
                log("  placing closeOrder=" + closeOrder);

                boolean placed = placeOrderToExchange(exchange, closeOrder);
                if (placed) {
                    m_closeOrders.add(new CloseOrderWrapper(closeOrder, m_order));
                    m_order = null;
                    return State.NONE;
                } else {
                    m_order = null;
                    log("ERROR placing closeOrder=" + closeOrder);
                    return State.ERROR;
                }
            } else {
                logValuate(exchange);
                m_order = null;
                return State.NONE;
            }
        } else {
            log("   order not yet FILLED: " + m_order);
        }
        return null; // no change
    }

    private void processTop() throws Exception {
        log("processTop(buy=" + m_buy + ", sell=" + m_sell + ")");
        if ((m_order != null) && !m_order.isFilled()) {
            checkOrderOutOfMarket();
        }
    }

    private IIterationContext.BaseIterationContext getLiveOrdersContext() throws Exception {
        final OrdersData ordersData = Fetcher.fetchOrders(m_ws.exchange(), Osc.PAIR);
        log(" liveOrders loaded " + ordersData);
        return new IIterationContext.BaseIterationContext() {
            @Override public OrdersData getLiveOrders(Exchange exchange) throws Exception { return ordersData; }
        };
    }

    private void onError() throws Exception {
        log("onError() resetting...  -------------------------- ");
        IIterationContext.BaseIterationContext iContext = checkLiveOrders();
        OrdersData liveOrders = iContext.getLiveOrders(m_ws.exchange());
        if (liveOrders != null) {
            log(" liveOrders " + liveOrders);
            // we may have offline order, try to link
            linkOfflineOrder(m_order, liveOrders);
            for (CloseOrderWrapper wrapper : m_closeOrders) {
                OrderData closeOrder = wrapper.m_closeOrder;
                linkOfflineOrder(closeOrder, liveOrders);
            }
        }
        cancelAllOrders();
        initAccount();
    }

    private void linkOfflineOrder(OrderData order, OrdersData liveOrders) {
        if ((order != null) && (order.m_orderId == null)) {
            log("we have offline order - try to match " + order);
            for (OrdersData.OrdData nextOrder : liveOrders.m_ords.values()) {
                if (nextOrder.m_orderAmount == order.m_amount) {
                    log("  found liveOrder with matched amount: " + nextOrder);
                    order.m_orderId = nextOrder.m_orderId;
                    break;
                }
            }
        }
    }

    private void cancelAllOrders() throws Exception {
        if (m_order != null) {
            log("  we have existing order, will cancel: " + m_order);
            String error = cancelOrder(m_order);
            if (error == null) {
                m_order = null;
            } else {
                log("error in cancel order: " + error + "; " + m_order);
                m_order = null;
            }
        }
        if (!m_closeOrders.isEmpty()) {
            log("  we have existing close orders, will cancel");
            for (ListIterator<CloseOrderWrapper> iterator = m_closeOrders.listIterator(); iterator.hasNext(); ) {
                CloseOrderWrapper next = iterator.next();
                OrderData closeOrder = next.m_closeOrder;
                log("  need cancel existing close order: " + closeOrder);
                String error = cancelOrder(closeOrder);
                if (error != null) {
                    log("error canceling close order: " + error + "; " + m_order);
                }
                iterator.remove();
            }
        }
    }

    public void stop() throws Exception {
        m_ws.stop();
        synchronized (this) {
            m_run = false;
            notify();
        }
        addTask(new StopTaskTask());
        m_orderWatcher.stop();
        log("stop() finished");
    }

    public void onAvgStoch(double avgStoch) {
        m_avgStochDirectionAdjuster.updateAvgStoch(avgStoch);
        m_feeding = true; // preheat finished, got some bars
    }

    private static class OscOrderWatcher implements Runnable {
        private final LinkedList<IOrderTask> m_tasksQueue = new LinkedList<IOrderTask>();
        private Thread m_thread;
        private boolean m_run = true;

        public void addTask(IOrderTask task) {
            synchronized (m_tasksQueue) {
                for (ListIterator<IOrderTask> listIterator = m_tasksQueue.listIterator(); listIterator.hasNext(); ) {
                    IOrderTask nextTask = listIterator.next();
                    if (task.isDuplicate(nextTask)) {
                        listIterator.remove();
                    }
                }
                m_tasksQueue.addLast(task);
                m_tasksQueue.notify();
                if (m_thread == null) {
                    m_thread = new Thread(this);
                    m_thread.setName("OscOrderWatcher");
                    m_thread.start();
                }
            }
        }

        @Override public void run() {
            log("OscOrderWatcher.queue: started thread");
            while (m_run) {
                IOrderTask task = null;
                try {
                    synchronized (m_tasksQueue) {
                        if (m_tasksQueue.isEmpty()) {
                            m_tasksQueue.wait();
                        }
                        task = m_tasksQueue.pollFirst();
                    }
                    if (task != null) {
                        task.process();
                    }
                } catch (Exception e) {
                    log("error in OscOrderWatcher: " + e + "; for task " + task);
                    e.printStackTrace();
                }
            }
            log("OscOrderWatcher.queue: thread finished");
        }

        public void stop() {
            m_run = false;
        }
    }

    private interface IOrderTask {
        void process() throws Exception;
        boolean isDuplicate(IOrderTask other);
    }

    public static class TrendCounter extends Utils.AverageCounter {
        private final Utils.FadingAverageCounter m_avgPriceCounter;

        public TrendCounter(long size) {
            super(size);
            m_avgPriceCounter = new Utils.FadingAverageCounter(size);
        }

        public boolean isTrendUp() {
            double trend = getTrend();
            return (trend > 0);
        }

        public double getTrend() {
            Double last = getLast();
            Double oldest = getOldest();
            return last - oldest;
        }

        public void addPoint(long timestamp, double value) {
            double avg = m_avgPriceCounter.add(timestamp, value);
            justAdd(timestamp, avg);
        }
    }

    private class StopTaskTask extends BaseOrderTask {
        public StopTaskTask() {}

        @Override public boolean isDuplicate(IOrderTask other) {
            log("stopping. removed task " + other);
            return true;
        }

        @Override public void process() throws Exception {
            cancelAllOrders();
            m_orderWatcher.stop();
        }
    }

    private abstract class BaseOrderTask implements IOrderTask {
        @Override public boolean isDuplicate(IOrderTask other) {
            // single presence in queue task
            return getClass().equals(other.getClass());
        }
    }

    private class RecheckDirectionTask extends BaseOrderTask {
        public RecheckDirectionTask() {}

        @Override public boolean isDuplicate(IOrderTask other) {
            boolean duplicate = super.isDuplicate(other);
            if (duplicate) {
                log(" skipped RecheckDirectionTask duplicate");
            }
            return duplicate;
        }

        @Override public void process() throws Exception {
            recheckDirection();
        }
    }

    private class CheckLiveOrdersTask extends BaseOrderTask {
        public CheckLiveOrdersTask() {}

        @Override public boolean isDuplicate(IOrderTask other) {
            boolean duplicate = super.isDuplicate(other);
            if (duplicate) {
                log(" skipped CheckLiveOrdersTask duplicate");
            }
            return duplicate;
        }

        @Override public void process() throws Exception {
            log("CheckLiveOrdersTask.process()");
            checkLiveOrders();
        }
    }

    private class TradeTask implements IOrderTask {
        private final TradeData m_tData;

        public TradeTask(TradeData tData) {
            m_tData = tData;
        }

        @Override public boolean isDuplicate(IOrderTask other) {
            if (other instanceof TradeTask) {
                TradeTask tradeTask = (TradeTask) other;
                double price = m_tData.m_price;
                if (tradeTask.m_tData.m_price == price) { // skip same price TradeTask
                    return true;
                }
            }
            return false;
        }

        @Override public void process() throws Exception {
            gotTrade(m_tData);
        }

        @Override public String toString() {
            return "TradeTask[tData=" + m_tData + "]";
        }
    }

    private class TopTask extends BaseOrderTask {
        public TopTask() {}

        @Override public void process() throws Exception {
            gotTop();
        }
    }

    private class InitTask implements IOrderTask {
        public InitTask() {}

        @Override public boolean isDuplicate(IOrderTask other) { return false; }
        @Override public void process() throws Exception {
            log("InitTask.process()");
            initImpl();
        }
    }

    private static enum State {
        NONE { // no order placed
            @Override public State onDirection(OscExecutor executor) throws Exception {
                log("State.NONE.onDirection(direction=" + executor.m_direction + ") on " + this + " *********************************************");
                return executor.processDirection();
            }
        },
        ORDER { // order placed - waiting
            @Override public State onDirection(OscExecutor executor) throws Exception {
                log("State.ORDER.onDirection(direction=" + executor.m_direction + ") on " + this + " *********************************************");
                return executor.processDirection();
            }
            @Override public State onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
                log("State.ORDER.onTrade(tData=" + tData + ") on " + this + " *********************************************");
                return executor.processTrade(tData, iContext);
            }
            @Override public void onTop(OscExecutor executor) throws Exception {
                log("State.ORDER.onTop(buy=" + executor.m_buy + ", sell=" + executor.m_sell + ") on " + this + " *********************************************");
                executor.processTop();
            }
        },
        ERROR {
            public State onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
                log("State.ERROR.onTrade(tData=" + tData + ") on " + this + " *********************************************");
                executor.onError();
                return NONE;
            }
        };

        public void onTop(OscExecutor executor) throws Exception {
            log("State.onTop(buy=" + executor.m_buy + ", sell=" + executor.m_sell + ") on " + this + " *********************************************");
        }

        public State onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
            log("State.onTrade(tData=" + tData + ") on " + this + " *********************************************");
            return this;
        }

        public State onDirection(OscExecutor executor) throws Exception {
            log("State.onDirection(direction=" + executor.m_direction + ") on " + this + " *********************************************");
            return this;
        }
    }

    private static class CloseOrderWrapper {
        private final OrderData m_closeOrder;
        private final OrderData m_order;

        public CloseOrderWrapper(OrderData closeOrder, OrderData order) {
            m_closeOrder = closeOrder;
            m_order = order;
        }
    }

    private static class Booster {
        private Double m_lastValue = null;
        private Boolean m_boostUp = null;
        private double m_boostStart;
        private double m_boostRange;

        public double boost(double value) {
            double ret;
            if(m_lastValue != null) {
                double diff = value - m_lastValue;
                if ((m_boostUp == null) || (m_boostUp && (diff < 0)) || (!m_boostUp && (diff > 0))) {
                    m_boostUp = (diff > 0);
                    m_boostStart = m_lastValue;
                    m_boostRange = m_boostUp ? (1 - m_boostStart) : (m_boostStart + 1);
log("boost direction changed: diff=" + diff + "; boostUp=" + m_boostUp + "; boostStart=" + m_boostStart + "; boostRange=" + m_boostRange);
                }
                double boostDistance = m_boostUp ? value - m_boostStart : m_boostStart - value;
                double boostRatio = boostDistance / m_boostRange;
                double boosted = Math.pow(boostRatio, 0.5);
                double boostedDistance = boosted * m_boostRange;
                ret = m_boostUp ? m_boostStart + boostedDistance : m_boostStart - boostedDistance;
                log("boosted from " + value + " to " + ret + ": boostDistance=" + boostDistance + "; boostRatio=" + boostRatio + "; boosted=" + boosted + "; boostedDistance=" + boostedDistance);
            } else {
                ret = value;
            }
            m_lastValue = value;
            return ret;
        }
    }

    private class AvgStochDirectionAdjuster {
        public static final double SMALL_DIFFS_THRESHOLD = 0.01;

//        private Utils.FadingAverageCounter m_avgCounter = new Utils.FadingAverageCounter(Osc.AVG_BAR_SIZE * 4 / 3);
        private Utils.ArrayAverageCounter m_avgCounter = new Utils.ArrayAverageCounter(8);
        private Double m_prevBlend;
        private OscLogProcessor.TrendWatcher m_avgStochTrendWatcher = new OscLogProcessor.TrendWatcher(SMALL_DIFFS_THRESHOLD);

        public void updateAvgStoch(double avgStoch) {
            double blend = m_avgCounter.add(avgStoch);
log("updateAvgStoch(avgStoch=" + avgStoch + ") blend=" + blend + "; full=" + m_avgCounter.m_full);
            if (m_avgCounter.m_full) {
                Direction prevDirection = m_avgStochTrendWatcher.m_direction;
                m_avgStochTrendWatcher.update(blend);
                Direction newDirection = m_avgStochTrendWatcher.m_direction;
log(" peak=" + m_avgStochTrendWatcher.m_peak + "; direction=" + newDirection);
                if ((prevDirection != null) && (newDirection != null) && (prevDirection != newDirection)) {
log("  direction trend changed from " + prevDirection + "to " + newDirection);
                }
                m_prevBlend = blend;
            }
        }

        private static final double LEVEL1_TO = 0.7;
        private static final double LEVEL2_TO = 0.6;
        private static final double LEVEL1 = 0.03; // [0 ... LEVEL1]      -> [0.8 ... 1]x    [LEVEL1_TO ... 1]
        private static final double LEVEL2 = 0.10; // [LEVEL1 ... LEVEL2] -> +[0 ... 0.6]    +[0 ... LEVEL2_TO]
                                                   // [LEVEL2 ... 1]      -> +[0.6 ... 1]    +[LEVEL2_TO ... 1]

        public double adjustDirection(double directionAdjusted) { // directionAdjusted  [-1 ... 1]
log("  AvgStochDirectionAdjuster.adjustDirection() adjustDirection=" + directionAdjusted);
            double ret = directionAdjusted;
            Double peakAvgStoch = m_avgStochTrendWatcher.m_peak;
            if (peakAvgStoch != null) {
                Direction avgStochDirection = m_avgStochTrendWatcher.m_direction;

                if (avgStochDirection == Direction.FORWARD) {
                    if (ret < 0) {
log("  Direction.FORWARD with negative directionAdjusted=" + directionAdjusted + ". zeroed");
                        ret = 0;
                    }
                } else if (avgStochDirection == Direction.BACKWARD) {
                    if (ret > 0) {
log("  Direction.BACKWARD with positive directionAdjusted=" + directionAdjusted + ". zeroed");
                        ret = 0;
                    }
                }

                double distanceFromPeak = avgStochDirection.applyDirection(m_prevBlend - peakAvgStoch);
log("boost?: distanceFromPeak=" + distanceFromPeak + "; direction=" + avgStochDirection + "; peakAvgStoch=" + peakAvgStoch + "; m_prevBlend=" + m_prevBlend);
                // distanceFromPeak  [0 ... 1]
                double boosted;
                if (distanceFromPeak < LEVEL1) {          //[0   ... 0.1]
//                    double q = LEVEL1 - distanceFromPeak; //[0.1 ... 0  ]
//                    double r = q / LEVEL1;                //[1   ... 0  ]
//                    double w = r * (1 - LEVEL1_TO);       //[0.2 ... 0  ]
//                    double e = 1 - w;                     //[0.8 ... 1  ]
//log("  first range: e=" + e);
//                    ret = directionAdjusted * e;
log("  first range: no change");
                    return ret;
                } else if (distanceFromPeak < LEVEL2) {   //[0.1 ... 0.2]
                    double q = distanceFromPeak - LEVEL1; //[0   ... 0.1]
                    double r = q / (LEVEL2 - LEVEL1);     //[0   ... 1  ]
                    double w = r * LEVEL2_TO;             //[0   ... 0.6]
log("  second range: w=" + w);
                    boosted = boostDirection(ret, w, avgStochDirection);
                } else {                                  //[0.2 ... 1]
                    double q = distanceFromPeak - LEVEL2; //[0 ... 0.8]
                    double r  = q / (1 - LEVEL2);         //[0 ... 1  ]
                    double w = r * (1 - LEVEL2_TO);       //[0 ... 0.4]
                    double e = LEVEL2_TO + w;             //[0.6 ... 1]
log("  third range: e=" + e);
                    boosted = boostDirection(ret, e, avgStochDirection);
                }
                log(" boosted from " + ret + " to " + boosted);
                ret = boosted;
            }
            return ret;
        }

        private double boostDirection(double directionAdjusted, double rate, Direction avgStochDirection) {
            Direction directionAdjustedDirection = Direction.get(directionAdjusted);
            double directionAbs = Math.abs(directionAdjusted);
            double newDirectionAbs;
            if (directionAdjustedDirection == avgStochDirection) { // same directions
                double distanceToOne = 1 - directionAbs;
                double plus = distanceToOne * rate;
                newDirectionAbs = directionAbs + plus;
log("   boostDirection() same directions: distanceToOne=" + distanceToOne + "; plus=" + plus);
            } else { // opposite directions
                double distanceToOne = 1 + directionAbs;
                double minus = distanceToOne * rate;
                newDirectionAbs = directionAbs - minus;
log("   boostDirection() opposite directions: distanceToOne=" + distanceToOne + "; minus=" + minus);
            }
            double ret = directionAdjustedDirection.applyDirection(newDirectionAbs);
log("    ret=" + ret);
            return ret;
        }
    }

    private class AvgPriceDirectionAdjuster {
        private static final double CENTER_TOLERANCE_RATIO = 3.5;

        private OscLogProcessor.TrendWatcher m_avgPriceTrendWatcher1 = new OscLogProcessor.TrendWatcher(0.02);
        private OscLogProcessor.TrendWatcher m_avgPriceTrendWatcher2 = new OscLogProcessor.TrendWatcher(0.02);
        private OscLogProcessor.TrendWatcher m_directionAdjustedTrendWatcher = new OscLogProcessor.TrendWatcher(0.01) {
            @Override protected double getTolerance(double value) { // [-1 ... 0 ... 1]
                double absValue = Math.abs(value); // [1 ... 0 ... 1]
                double distanceToEdge = 1 - absValue; // [0 ... 1 ... 0]
                return m_tolerance * (1 + distanceToEdge * (CENTER_TOLERANCE_RATIO - 1));
            }
        };

        public double adjustDirection(double directionAdjustedIn, double directionAdjusted) { // directionAdjusted  [-1 ... 1]
            double ret = directionAdjusted;

            m_directionAdjustedTrendWatcher.update(directionAdjustedIn);
            Direction directionAdjustedDirection = m_directionAdjustedTrendWatcher.m_direction;

            double avgPrice1 = m_avgPriceCounter1.get();
            m_avgPriceTrendWatcher1.update(avgPrice1);

            Direction avgPriceDirection1 = m_avgPriceTrendWatcher1.m_direction;
            log(" avgPriceDirection1=" + avgPriceDirection1 + "; directionAdjustedDirection=" + directionAdjustedDirection);

            if ((avgPriceDirection1 != null) && (directionAdjustedDirection != null) && (avgPriceDirection1 != directionAdjustedDirection)) {
log("  opposite directions. avgPrice1=" + avgPrice1 + "; avgPricePeak1=" + m_avgPriceTrendWatcher1.m_peak + "; directionAdjusted=" + directionAdjustedIn + "; directionAdjustedPeak=" + m_directionAdjustedTrendWatcher.m_peak);
                double directionChilled = ret * OPPOSITE_DIRECTION_FACTOR;
                log("  direction chilled1 from " + ret + " to " + directionChilled);
                ret = directionChilled;
            }

            double avgPrice2 = m_avgPriceCounter2.get();
            m_avgPriceTrendWatcher2.update(avgPrice2);

            Direction avgPriceDirection2 = m_avgPriceTrendWatcher2.m_direction;
            log(" avgPriceDirection2=" + avgPriceDirection2 + "; directionAdjustedDirection=" + directionAdjustedDirection);

            if ((avgPriceDirection2 != null) && (directionAdjustedDirection != null) && (avgPriceDirection2 != directionAdjustedDirection)) {
log("  opposite directions. avgPrice2=" + avgPrice2 + "; avgPricePeak2=" + m_avgPriceTrendWatcher2.m_peak + "; directionAdjusted=" + directionAdjustedIn + "; directionAdjustedPeak=" + m_directionAdjustedTrendWatcher.m_peak);
                double directionChilled = ret * OPPOSITE_DIRECTION_FACTOR;
                log("  direction chilled2 from " + ret + " to " + directionChilled);
                ret = directionChilled;
            }

            return ret;
        }
    }
}
