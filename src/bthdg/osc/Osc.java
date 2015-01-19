package bthdg.osc;

import bthdg.Fetcher;
import bthdg.IIterationContext;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.exch.Currency;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;
import bthdg.ws.*;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.*;

public class Osc {
    public static long BAR_SIZE = Utils.toMillis("1d");
    public static int LEN1 = 14;
    public static int LEN2 = 14;
    public static int K = 3;
    public static int D = 3;
    public static int PHASES = 1;
    public static double START_LEVEL = 0.005;
    public static double STOP_LEVEL = 0.005;
    public static int START_STOP_LEVEL_MULTIPLY = 3;
    private static boolean STICK_TOP_BOTTOM = false;

    public static int PREHEAT_BARS_NUM = calcPreheatBarsNum();

    public static final int INIT_BARS_BEFORE = 4;
    public static final Pair PAIR = Pair.BTC_CNH; // TODO: BTC is hardcoded below
    private static final int MAX_PLACE_ORDER_REPEAT = 2;
    public static final double CLOSE_PRICE_DIFF = 1.25;
    private static final double ORDER_SIZE_TOLERANCE = 0.3;
    public static final double USE_FUNDS_FROM_AVAILABLE = 0.95; // 95%
    private static final boolean DO_CLOSE_ORDERS = false;

    private static Osc s_osc; // instance

    private final String m_e;
    private Processor m_processor;

    public Osc(String[] args) {
        Log.s_impl = new Log.TimestampLog();

        if(args.length > 0) {
            m_e = args[0];
        } else {
            throw new RuntimeException("no arg params");
        }
    }

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }
    private static int calcPreheatBarsNum() { return LEN1 + LEN2 + (K - 1) + (D - 1); }

    public static void main(String[] args) {
        try {
            s_osc = new Osc(args);
            s_osc.run();

            new ConsoleReader() {
                @Override protected void beforeLine() {
                    System.out.print(">");
                }
                @Override protected boolean processLine(String line) throws Exception {
                    return onConsoleLine(line);
                }
            }.run();

            Thread thread = Thread.currentThread();
            synchronized (thread) {
                thread.wait();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean onConsoleLine(String line) throws Exception {
        log("onConsoleLine: " + line);
        if (line.equals("stop")) {
            s_osc.onStop();
            return true;
        }
        return false;
    }

    private void onStop() throws Exception {
        log("stop()-----------------------------------------------------");
        m_processor.stop();
    }

    private void run() throws IOException {
        Properties keys = BaseExch.loadKeys();
//        Btcn.init(keys);

        init(keys);

        IWs ws;
        Exchange exchange = Exchange.getExchange(m_e);
        switch (exchange) {
            case HUOBI:
                ws = HuobiWs.create(keys);
                break;
            case OKCOIN:
                ws = OkCoinWs.create(keys);
                break;
            default:
                throw new RuntimeException("not supported exchange: " + exchange);
        }

//        BitstampWs.main(args);

        m_processor = new Processor(ws);

        try {
            ws.subscribeTrades(PAIR, new ITradesListener() {
                @Override public void onTrade(TradeData tdata) {
                    m_processor.onTrade(tdata);
                }
            });
        } catch (Exception e) {
            err("subscribeTrades error" + e, e);
        }
    }

    private void init(Properties properties) {
        BAR_SIZE = Utils.toMillis(properties.getProperty("osc.bar_size"));
        log("BAR_SIZE=" + BAR_SIZE);
        LEN1 = Integer.parseInt(properties.getProperty("osc.len1"));
        log("LEN1=" + LEN1);
        LEN2 = Integer.parseInt(properties.getProperty("osc.len2"));
        log("LEN2=" + LEN2);
        K = Integer.parseInt(properties.getProperty("osc.k"));
        log("K=" + K);
        D = Integer.parseInt(properties.getProperty("osc.d"));
        log("D=" + D);
        PHASES = Integer.parseInt(properties.getProperty("osc.phases"));
        log("PHASES=" + PHASES);
        START_LEVEL = Double.parseDouble(properties.getProperty("osc.start_level"));
        log("START_LEVEL=" + START_LEVEL);
        STOP_LEVEL = Double.parseDouble(properties.getProperty("osc.stop_level"));
        log("STOP_LEVEL=" + STOP_LEVEL);
        START_STOP_LEVEL_MULTIPLY = Integer.parseInt(properties.getProperty("osc.start_stop_level_multiply"));
        log("START_STOP_LEVEL_MULTIPLY=" + START_STOP_LEVEL_MULTIPLY);
        String str = properties.getProperty("osc.stickTopBottom");
        STICK_TOP_BOTTOM = Boolean.parseBoolean(str);
        log("STICK_TOP_BOTTOM=" + STICK_TOP_BOTTOM);

        PREHEAT_BARS_NUM = calcPreheatBarsNum();
    }

    private static class Processor implements Runnable {
        private final OscExecutor m_executor;
        private final OscCalculator[] m_calcs = new OscCalculator[PHASES];
        private final Thread m_thread;
        private final LinkedList<TradeData> m_queue = new LinkedList<TradeData>();
        private boolean m_run = true;

        public Processor(IWs ws) {
            m_executor = new OscExecutor(ws);
            for (int i = 0; i < PHASES; i++) {
                m_calcs[i] = new PhasedOscCalculator(i, m_executor, STICK_TOP_BOTTOM);
            }
            m_thread = new Thread(this);
            m_thread.setName("Processor");
            m_thread.start();
        }

        public void onTrade(TradeData tdata) {
            synchronized (m_queue) {
                m_queue.addLast(tdata);
                m_queue.notify();
            }
        }

        @Override public void run() {
            while (m_run) {
                TradeData tData = null;
                try {
                    synchronized (m_queue) {
                        tData = m_queue.pollFirst();
                        if (tData == null) {
                            m_queue.wait();
                            tData = m_queue.pollFirst();
                        }
                    }
                    if (tData != null) {
                        process(tData);
                    }
                } catch (Exception e) {
                    log("error processing tData=" + tData);
                    e.printStackTrace();
                }
            }
            log("Processor thread finished");
        }

        private void process(TradeData tData) {
//            log("Processor.process() tData=" + tData);
            m_executor.onTrade(tData);
            for (int i = 0; i < PHASES; i++) {
                m_calcs[i].update(tData.m_timestamp, tData.m_price);
            }
        }

        public void stop() throws Exception {
            m_executor.stop();
            synchronized (m_queue) {
                m_run = false;
                m_queue.notify();
            }
        }
    }

    static class OscExecutor implements Runnable{
        private final IWs m_ws;
        private final Thread m_thread;
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

        public OscExecutor(IWs ws) {
            m_startMillis = System.currentTimeMillis();
            m_ws = ws;
            m_thread = new Thread(this);
            m_thread.setName("OscExecutor");
            m_thread.start();
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
            m_topsData = Fetcher.fetchTops(exchange, PAIR);
            log(" topsData=" + m_topsData);

            initAccount();
            m_initAccount = m_account.copy();
            m_initTops = m_topsData.copy();

            log("initImpl() continue: subscribeTrades()");
            m_ws.subscribeTop(PAIR, new ITopListener() {
                @Override public void onTop(long timestamp, double buy, double sell) {
//                    log("onTop() timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
                    m_buy = buy;
                    m_sell = sell;

                    TopData topData = new TopData(buy, sell);
                    m_topsData.put(PAIR, topData);
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

        private void checkLiveOrders() throws Exception {
            log("checkLiveOrders()");
            IIterationContext.BaseIterationContext iContext = null;
            if (!m_closeOrders.isEmpty()) {
                iContext = checkCloseOrdersState(null);
            }
            setState(checkOrderState(iContext));
        }

        private void gotTop() throws Exception {
//            log("OscExecutor.gotTop() buy=" + buy + "; sell=" + sell);
//            log(" topsData =" + m_topsData);
            m_state.onTop(this);
        }

        private void gotTrade(TradeData tData) throws Exception {
//            log("OscExecutor.gotTrade() tData=" + tData);
            IIterationContext.BaseIterationContext iContext = checkCloseOrdersStateIfNeeded(tData, null);
            setState(m_state.onTrade(this, tData, iContext));
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
            log("  GAIN: Btc=" + gainBtc + "; Cnh=" + gainCnh + " CNH; avg=" + gainAvg + "; projected=" + projected);
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
            addTask(new TradeTask(tData));
        }

        private State processDirection() throws Exception {
            log("processDirection() direction=" + m_direction + ")");

            double directionAdjusted = ((double)m_direction) / PHASES;        // directionAdjusted  [-1 ... 1]
            if ((directionAdjusted < -1) || (directionAdjusted > 1)) {
                log("ERROR: invalid directionAdjusted=" + directionAdjusted);
                if (directionAdjusted < -1) {
                    directionAdjusted = -1;
                }
                if (directionAdjusted > 1) {
                    directionAdjusted = 1;
                }
            }

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

                    orderSizeAdjusted *= USE_FUNDS_FROM_AVAILABLE; // do not use ALL available funds
                    log("       fund ratio adjusted orderSize=" + Utils.format8(orderSizeAdjusted));

                    double orderSizeRound = exchange.roundAmount(orderSizeAdjusted, PAIR);
                    placeOrderSize = Math.abs(orderSizeRound);
                    log("        orderSizeAdjusted=" + Utils.format8(orderSizeAdjusted) + "; orderSizeRound=" + orderSizeRound + "; placeOrderSize=" + Utils.format8(placeOrderSize));
                }
            }

            boolean cancelAttempted = cancelOrderIfDirectionDiffers(needOrderSide, placeOrderSize);
            if (!cancelAttempted) { // cancel attempt was not performed
                if (m_order == null) { // we should not have open order at this place
                    cancelAttempted = cancelSameDirectionCloseOrders(needOrderSide);
                    if (!cancelAttempted) { // cancel attempt was not performed
                        double minOrderToCreate = exchange.minOrderToCreate(PAIR);
                        if (placeOrderSize >= minOrderToCreate) {
                            Currency currency = PAIR.currencyFrom(needOrderSide.isBuy());
                            log("  currency=" + currency + "; PAIR=" + PAIR);
                            double orderPrice = calcOrderPrice(exchange, directionAdjusted, needOrderSide);
                            m_order = new OrderData(PAIR, needOrderSide, orderPrice, placeOrderSize);
                            log("   orderData=" + m_order);

                            if (placeOrderToExchange(exchange, m_order)) {
                                return State.ORDER;
                            } else {
                                return State.ERROR;
                            }
                        } else {
                            log("warning: small order to create: placeOrderSize=" + placeOrderSize + "; minOrderToCreate=" + minOrderToCreate);
                            if (m_maySyncAccount) {
                                log("no orders - we may re-check account");
                                initAccount();
                            }
                            return State.NONE;
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
            return null;
        }

        private double calcOrderPrice(Exchange exchange, double directionAdjusted, OrderSide needOrderSide) {
            // directionAdjusted [-1 ... 1]
            double midPrice = (m_buy + m_sell) / 2;
            double adjustedPrice = midPrice + directionAdjusted * (m_sell - m_buy) / 2;
            log("  buy=" + m_buy + "; sell=" + m_sell + "; midPrice=" + midPrice + "; directionAdjusted=" + directionAdjusted + "; adjustedPrice=" + adjustedPrice);
            RoundingMode roundMode = needOrderSide.getMktRoundMode();
            double orderPrice = exchange.roundPrice(adjustedPrice, PAIR, roundMode);
            log("  roundMode=" + roundMode + "; rounded orderPrice=" + orderPrice);
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
                    log("   next closeOrder "+closeOrder);
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
                    if ((orderSizeRatio < (1 + ORDER_SIZE_TOLERANCE)) && (orderSizeRatio > (1 - ORDER_SIZE_TOLERANCE))) {
                        log("     order Sizes are very close - do not cancel existing order (tolerance=" + ORDER_SIZE_TOLERANCE + ")");
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
                if (ops != OrderData.OrderPlaceStatus.OK) {
                    m_account.releaseOrder(order, exchange);
                } else {
                    log(" placeOrderToExchange successful: " + exchange + ", " + order + ", account: " + m_account);
                    return true;
                }
            } else {
                log("ERROR: account allocateOrder unsuccessful: " + exchange + ", " + order + ", account: " + m_account);
            }
            return false;
        }

        private OrderData.OrderPlaceStatus placeOrderToExchange(Exchange exchange, OrderData order, OrderState state) throws Exception {
            int repeatCounter = MAX_PLACE_ORDER_REPEAT;
            while (true) {
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
                        if (repeatCounter-- > 0) {
                            log(" repeat place order, count=" + repeatCounter);
                            continue;
                        }
                        ret = OrderData.OrderPlaceStatus.CAN_REPEAT;
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

                    return checkOrderState(inContext); //check orders state
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

        private State checkOrderState(IIterationContext.BaseIterationContext inContext) throws Exception {
            if (m_order != null) {
                IIterationContext.BaseIterationContext iContext = (inContext == null) ? getLiveOrdersContext() : inContext;

                Exchange exchange = m_ws.exchange();
                m_order.checkState(iContext, exchange, m_account,
                        null, // TODO - implement exec listener, add partial support - to fire partial close orders
                        null);
                log("  order state checked: " + m_order);

                if (m_order.isFilled()) {
                    log("$$$$$$   order FILLED: " + m_order);
                    if(DO_CLOSE_ORDERS) {
                        OrderSide orderSide = m_order.m_side;
                        OrderSide closeSide = orderSide.opposite();
                        double closePrice = m_order.m_price + (orderSide.isBuy() ? CLOSE_PRICE_DIFF : -CLOSE_PRICE_DIFF);
                        double closeSize = m_order.m_amount;

                        OrderData closeOrder = new OrderData(PAIR, closeSide, closePrice, closeSize);
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
            final OrdersData ordersData = Fetcher.fetchOrders(m_ws.exchange(), PAIR);
            log(" liveOrders loaded " + ordersData);
            return new IIterationContext.BaseIterationContext() {
                @Override public OrdersData getLiveOrders(Exchange exchange) throws Exception { return ordersData; }
            };
        }

        private void onError() throws Exception {
            log("onError() resetting...  -------------------------- ");
            cancelAllOrders();
            checkLiveOrders();
            initAccount();
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
            cancelAllOrders();
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
//                                log("OscOrderWatcher.queue: found existing task to remove of class " + toRemove);
                            listIterator.remove();
                        }
                    }

                    m_tasksQueue.addLast(task);
//log("OscOrderWatcher.queue: task added " + task + "; notify...");
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

        private class StopTaskTask extends BaseOrderTask {
            public StopTaskTask() {}

            @Override public boolean isDuplicate(IOrderTask other) {
                log("stopping. removed task " + other);
                return true;
            }

            @Override public void process() throws Exception {
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
                    if (tradeTask.m_tData.m_price == price) {
//                        log("skip same price TradeTask. price="+price);
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
                @Override public OscExecutor.State onDirection(OscExecutor executor) throws Exception {
                    log("State.NONE.onDirection(direction=" + executor.m_direction + ") on " + this+" *********************************************");
                    return executor.processDirection();
                }
            },
            ORDER { // order placed - waiting
                @Override public OscExecutor.State onDirection(OscExecutor executor) throws Exception {
                    log("State.ORDER.onDirection(direction=" + executor.m_direction + ") on " + this+" *********************************************");
                    return executor.processDirection();
                }
                @Override public OscExecutor.State onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
                    log("State.ORDER.onTrade(tData=" + tData + ") on " + this+" *********************************************");
                    return executor.processTrade(tData, iContext);
                }
                @Override public void onTop(OscExecutor executor) throws Exception {
                    log("State.ORDER.onTop(buy=" + executor.m_buy + ", sell=" + executor.m_sell + ") on " + this+" *********************************************");
                    executor.processTop();
                }
            },
            ERROR {
                public OscExecutor.State onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
                    log("State.ERROR.onTrade(tData=" + tData + ") on " + this+" *********************************************");
                    executor.onError();
                    return NONE;
                }
            };

            public void onTop(OscExecutor executor) throws Exception {
                log("State.onTop(buy=" + executor.m_buy + ", sell=" + executor.m_sell + ") on " + this+" *********************************************");
            }

            public State onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
                log("State.onTrade(tData=" + tData + ") on " + this+" *********************************************");
                return this;
            }

            public State onDirection(OscExecutor executor) throws Exception {
                log("State.onDirection(direction=" + executor.m_direction + ") on " + this+" *********************************************");
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
    }
}
