package bthdg.osc;

import bthdg.Fetcher;
import bthdg.IIterationContext;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.exch.Currency;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;
import bthdg.ws.ITopListener;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;
import bthdg.ws.OkCoinWs;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.*;

public class Osc {
    private static final long BAR_SIZE = Utils.toMillis("20s");
    public static final int LEN1 = 14;
    public static final int LEN2 = 14;
    public static final int K = 3;
    public static final int D = 3;
    public static final int PREHEAT_BARS_NUM = LEN1 + LEN2 + (K - 1) + (D - 1);
    public static final int INIT_BARS_BEFORE = 3;
    public static final int PHASES = 1;
    public static final double START_LEVEL = 0.01;
    public static final double STOP_LEVEL = 0.005;
    public static final Pair PAIR = Pair.BTC_CNH; // TODO: BTC is hardcoded below
    private static final int MAX_PLACE_ORDER_REPEAT = 2;
    public static final double CLOSE_PRICE_DIFF = 1.5;
    private static final double ORDER_SIZE_TOLERANCE = 0.1;
    public static final double USE_FUNDS_FROM_AVAILABLE = 0.95; // 95%

    private static int s_notEnoughFundsCounter;

    private Processor m_processor;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public static void main(String[] args) {
        try {
            new Osc().run();

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

    private static boolean onConsoleLine(String line) {
        log("onConsoleLine: " + line);
        return false;
    }

    private void run() throws IOException {
        Properties keys = BaseExch.loadKeys();
//        Btcn.init(keys);

        IWs ws = OkCoinWs.create(keys);
//        IWs ws = HuobiWs.create(keys);
//        BtcnWs.main(args);
//        BitstampWs.main(args);

        m_processor = new Processor(ws);

        ws.subscribeTrades(PAIR, new ITradesListener() {
            @Override public void onTrade(TradeData tdata) {
                m_processor.onTrade(tdata);
            }
        });
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
                m_calcs[i] = new PhasedOscCalculator(i, m_executor);
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
        }

        private void process(TradeData tData) {
//            log("Processor.process() tData=" + tData);
            m_executor.onTrade(tData);
            for (int i = 0; i < PHASES; i++) {
                m_calcs[i].update(tData.m_timestamp, tData.m_price);
            }
        }
    }

    private static class OscExecutor implements Runnable{
        private final IWs m_ws;
        private final Thread m_thread;
        private int m_direction;
        private boolean m_run = true;
        private boolean m_changed;
        private boolean m_initialized;
        private OscOrderWatcher m_orderWatcher;

        private TopsData m_topsData;
        private AccountData m_account;
        private State m_state = State.NONE;
        private double m_buy;
        private double m_sell;
        private OrderData m_order;
        private List<CloseOrderWrapper> m_closeOrders = new ArrayList<CloseOrderWrapper>();

        public OscExecutor(IWs ws) {
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
                    int direction;
                    synchronized (this) {
                        if (!m_changed) {
                            log("waiting for updated osc direction");
                            wait();
                        }
                        changed = m_changed;
                        direction = m_direction;
                        m_changed = false;
                    }
                    if (changed) {
                        log("process updated osc direction=" + direction);
                        onDirection(direction);
                    }
                } catch (Exception e) {
                    log("error in OscExecutor");
                    e.printStackTrace();
                }
            }
        }

        private void init() {
            if (!m_initialized) {
                log("not initialized - added InitTask to queue");
                getOrderWatcher().addTask(new InitTask());
                m_initialized = true;
            }
        }

        private void initImpl() throws Exception {
            log("OscExecutor.initImpl()................");

            Exchange exchange = m_ws.exchange();
            m_topsData = Fetcher.fetchTops(exchange, PAIR);
            log(" topsData=" + m_topsData);

            initAccount();

            log("initImpl() continue: subscribeTrades()");
            m_ws.subscribeTop(PAIR, new ITopListener() {
                @Override public void onTop(long timestamp, double buy, double sell) {
                    log("onTop() timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
//                    log(" queue.add TopTask");
                    m_buy = buy;
                    m_sell = sell;
                    getOrderWatcher().addTask(new TopTask(buy, sell), TopTask.class);
                }
            });
        }

        private void initAccount() throws Exception {
            Exchange exchange = m_ws.exchange();
            m_account = Fetcher.fetchAccount(exchange);
            if (m_account != null) {
                log(" account=" + m_account);
                double valuateBtc = m_account.evaluate(m_topsData, Currency.BTC, exchange);
                log("  valuateBtc=" + valuateBtc + " BTC");
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

        private void gotDirection(int direction) throws Exception {
            log("OscExecutor.gotDirection() direction=" + direction);
            setState(m_state.onDirection(this, direction));
        }

        private void recheckDirection() throws Exception {
            log("OscExecutor.recheckDirection() m_direction=" + m_direction);
            setState(m_state.onDirection(this, m_direction));
        }

        private void checkLiveOrders() throws Exception {
            log("checkLiveOrders()");
            IIterationContext.BaseIterationContext iContext = null;
            if (!m_closeOrders.isEmpty()) {
                iContext = checkCloseOrdersState(null);
            }
            setState(checkOrderState(iContext));
        }

        private void gotTop(double buy, double sell) {
//            log("OscExecutor.gotTop() buy=" + buy + "; sell=" + sell);

//            log(" topsData =" + m_topsData);
            TopData topData = new TopData(buy, sell);
            m_topsData.put(PAIR, topData);
            log(" topsData'=" + m_topsData);

            m_state.onTop(this, buy, sell);
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
            }
            return iContext;
        }

        private void postRecheckDirection() {
            getOrderWatcher().addTask(new RecheckDirectionTask(), RecheckDirectionTask.class);
        }

        private OscOrderWatcher getOrderWatcher() {
            if (m_orderWatcher == null) {
                m_orderWatcher = new OscOrderWatcher();
            }
            return m_orderWatcher;
        }

        private void onDirection(int direction) {
            getOrderWatcher().addTask(new DirectionTask(direction));
        }

        public void onTrade(TradeData tData) {
            getOrderWatcher().addTask(new TradeTask(tData));
        }

        private State processDirection(int direction) throws Exception {
            log("processDirection(direction=" + direction + ")");
            m_direction = direction;

            Exchange exchange = m_ws.exchange();
            double buyBtc = calcOrderSizeFromDirection(direction, exchange);

            OrderSide needOrderSide = (buyBtc == 0) ? null : (buyBtc > 0) ? OrderSide.BUY : OrderSide.SELL;
            buyBtc *= USE_FUNDS_FROM_AVAILABLE; // do not use ALL available funds
            log(" buyBtc'=" + buyBtc + "; needOrderSide=" + needOrderSide);

            double orderSize = Math.abs(buyBtc);
            double orderSizeRound = exchange.roundAmount(orderSize, PAIR);
            double minOrderToCreate = exchange.minOrderToCreate(PAIR);
            log(" orderSize=" + orderSize + "; orderSizeRound=" + orderSizeRound + "; minOrderToCreate=" + minOrderToCreate);

            boolean cancelAttempted = cancelOrderIfDirectionDiffers(needOrderSide, orderSizeRound);
            if (!cancelAttempted) { // cancel attempt was not performed
                cancelAttempted = cancelSameDirectionCloseOrders(needOrderSide);
                if (!cancelAttempted) { // cancel attempt was not performed
                    if (orderSizeRound >= minOrderToCreate) {
                        Currency currency = PAIR.currencyFrom(needOrderSide.isBuy());
                        log("  currency=" + currency + "; PAIR=" + PAIR);
                        RoundingMode roundMode = needOrderSide.getMktRoundMode();
                        log("  roundMode=" + roundMode);
                        double midPrice = (m_buy + m_sell) / 2;
                        log("  buy=" + m_buy + "; sell=" + m_sell + "; midPrice=" + midPrice);
                        double orderPrice = exchange.roundPrice(midPrice, PAIR, roundMode);
                        log("  rounded orderPrice=" + orderPrice);
                        m_order = new OrderData(PAIR, needOrderSide, orderPrice, orderSizeRound);
                        log("  orderData=" + m_order);

                        if (placeOrderToExchange(exchange, m_order)) {
                            return State.ORDER;
                        } else {
                            return State.ERROR;
                        }
                    } else {
                        log("warning: small order to create: orderSizeRound=" + orderSizeRound + "; minOrderToCreate=" + minOrderToCreate);
                    }
                } else {
                    log("some orders maybe closed - post recheck direction");
                    postRecheckDirection();
                }
            } else {
                log("order cancel was attempted. time passed. posting recheck direction");
                postRecheckDirection();
            }
            return null;
        }

        private double calcOrderSizeFromDirection(int direction, Exchange exchange) {
            double valuateBtc = m_account.evaluate(m_topsData, Currency.BTC, exchange);
            double valuateCnh = m_account.evaluate(m_topsData, Currency.CNH, exchange);
            log("  valuateBtc=" + valuateBtc + " BTC; valuateCnh=" + valuateCnh + " CNH");

            int directionAdjusted = direction / PHASES;        // directionAdjusted  [-1 ... 1]
            double needBuyBtc = directionAdjusted * valuateBtc;
            double needSellCnh = directionAdjusted * valuateCnh;
            log("  directionAdjusted=" + directionAdjusted + "; needBuyBtc=" + needBuyBtc + "; needSellCnh=" + needSellCnh);

            log(" account=" + m_account);
            double haveBtc = m_account.available(Currency.BTC);
            double haveCnh = m_account.available(Currency.CNH);
            log(" haveBtc=" + haveBtc + "; haveCnh=" + haveCnh);
            double buyBtc;
            if (needBuyBtc > 0) {
                log("  will buy Btc:");
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
            boolean reprocess = false;
            if (!m_closeOrders.isEmpty()) {
                log("  cancelSameDirectionCloseOrders...");
                boolean hadError = false;
                for (ListIterator<CloseOrderWrapper> iterator = m_closeOrders.listIterator(); iterator.hasNext(); ) {
                    CloseOrderWrapper next = iterator.next();
                    OrderData closeOrder = next.m_closeOrder;
                    OrderSide closeOrderSide = closeOrder.m_side;
                    log("  next closeOrder "+closeOrder);
                    if (closeOrderSide == needOrderSide) {
                        log("   need cancel existing close order: " + closeOrder);
                        String error = m_account.cancelOrder(closeOrder);
                        if (error == null) {
                            iterator.remove();
                        } else {
                            // basically we may have no such order error here - when executed concurrently
                            log("ERROR canceling close order: " + error + "; " + m_order);
                            hadError = true;
                        }
                        reprocess = true;
                    }
                }
                if (hadError) {
                    checkCloseOrdersState(null);
                }
            }
            return reprocess;
        }

        private boolean cancelOrderIfDirectionDiffers(OrderSide needOrderSide, double orderSizeRound) throws Exception {
            boolean toCancel = false;
            if (m_order != null) {
                OrderSide haveOrderSide = m_order.m_side;
                log("  we have existing order: needOrderSide=" + needOrderSide + "; haveOrderSide=" + haveOrderSide);
                toCancel = true;
                if (needOrderSide == haveOrderSide) {
                    double remained = m_order.remained();
                    log("   we have order: needOrderSize=" + orderSizeRound + "; haveOrderSize=" + remained);
                    if (orderSizeRound != 0) {
                        double orderSizeRatio = remained / orderSizeRound;
                        log("    orderSizeRatio=" + orderSizeRatio);
                        if ((orderSizeRatio < (1 + ORDER_SIZE_TOLERANCE)) && (orderSizeRatio > (1 - ORDER_SIZE_TOLERANCE))) {
                            log("     order Sizes are very close - do not cancel existing order");
                            toCancel = false;
                        }
                    }
                } else {
                    log("   OrderSides are different - definitely canceling (needOrderSide=" + needOrderSide + ", haveOrderSide=" + haveOrderSide + ")");
                }
                if (toCancel) {
                    log("  need cancel existing order: " + m_order);
                    String error = m_account.cancelOrder(m_order);
                    if (error == null) {
                        m_order = null;
                    } else {
                        log("ERROR in cancel order: " + error + "; " + m_order);
                        // todo: orders/need account sync
                        if (error.contains("Order does not exist")) {
                            log("looks the order was already executed - need check live orders");
                            checkLiveOrders();
                        }
                    }
                }
            }
            return toCancel;
        }

        private boolean placeOrderToExchange(Exchange exchange, OrderData order) throws Exception {
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

        private static OrderData.OrderPlaceStatus placeOrderToExchange(Exchange exchange, OrderData order, OrderState state) throws Exception {
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
                        s_notEnoughFundsCounter++;
                        ret = OrderData.OrderPlaceStatus.ERROR;
                        log("  NotEnoughFunds detected - increased account sync counter to " + s_notEnoughFundsCounter);
                    } else {
                        ret = OrderData.OrderPlaceStatus.ERROR;
                    }
                }
                return ret;
            }
        }

        private State processTrade(TradeData tData, IIterationContext.BaseIterationContext inContext) throws Exception {
            log("processTrade(tData=" + tData + ")");
            double tradePrice = tData.m_price;
            double ordPrice = m_order.m_price;
            log(" tradePrice=" + tradePrice + ", orderPrice=" + ordPrice);
            if (tradePrice == ordPrice) {
                log("  same price - MAYBE SOME PART OF OUR ORDER EXECUTED ?");
                double orderRemained = m_order.remained();
                double tradeSize = tData.m_amount;
                log("   orderRemained=" + orderRemained + "; tradeSize=" + tradeSize);

                //check orders state
                return checkOrderState(inContext);
            } else {
                if ((m_order != null) && !m_order.isFilled()) {
                    startCheckLiveOrdersTaskIfNeeded(m_buy, m_sell);
                }
            }
            return State.ORDER;
        }

        private void startCheckLiveOrdersTaskIfNeeded(double buy, double sell) {
            boolean isBuy = m_order.m_side.isBuy();
            double orderPrice = m_order.m_price;
            if ((isBuy && (buy < orderPrice)) || (!isBuy && (sell > orderPrice))) {
                log("  order price " + orderPrice + " is out of MKT [buy=" + buy + "; sell=" + sell + "] starting CheckLiveOrdersTask");
                getOrderWatcher().addTask(new CheckLiveOrdersTask(), CheckLiveOrdersTask.class);
            }
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
                    log("   order not yet FILLED: " + m_order);
                }
            }
            return null; // no change
        }

        private void processTop(double buy, double sell) {
            log("processTop(buy=" + buy + ", sell=" + sell + ")");
            if ((m_order != null) && !m_order.isFilled()) {
                startCheckLiveOrdersTaskIfNeeded(buy, sell);
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
            if (m_order != null) {
                log("  we have existing order, will cancel: " + m_order);
                String error = m_account.cancelOrder(m_order);
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
                    String error = m_account.cancelOrder(closeOrder);
                    if (error != null) {
                        log("error canceling close order: " + error + "; " + m_order);
                    }
                    iterator.remove();
                }
            }
            initAccount();
        }

        private static class OscOrderWatcher implements Runnable {
            private final LinkedList<IOrderTask> m_tasksQueue = new LinkedList<IOrderTask>();
            private Thread m_thread;
            private boolean m_run = true;

            public void addTask(IOrderTask task) {
                addTask(task, null);
            }

            public void addTask(IOrderTask task, Class toRemove) {
                synchronized (m_tasksQueue) {
                    if (toRemove != null) {
                        for (ListIterator<IOrderTask> listIterator = m_tasksQueue.listIterator(); listIterator.hasNext(); ) {
                            IOrderTask nextTask = listIterator.next();
                            if (toRemove.isInstance(nextTask)) {
                                log("OscOrderWatcher.queue: found existing task to remove of class " + toRemove);
                                listIterator.remove();
                            }
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
                    try {
                        IOrderTask task;
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
                        log("error in OscOrderWatcher");
                        e.printStackTrace();
                    }
                }
            }
        }

        private interface IOrderTask {
            void process() throws Exception;
        }

        private class DirectionTask implements IOrderTask {
            private final int m_direction;

            public DirectionTask(int direction) {
                m_direction = direction;
            }

            @Override public void process() throws Exception {
                log("DirectionTask.process() direction=" + m_direction);
                gotDirection(m_direction);
            }
        }

        private class RecheckDirectionTask implements IOrderTask {
            public RecheckDirectionTask() {}

            @Override public void process() throws Exception {
                log("RecheckDirectionTask.process()");
                recheckDirection();
            }
        }

        private class CheckLiveOrdersTask implements IOrderTask {
            public CheckLiveOrdersTask() {}

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

            @Override public void process() throws Exception {
                gotTrade(m_tData);
            }
        }

        private class TopTask implements IOrderTask {
            private final double m_buy;
            private final double m_sell;

            public TopTask(double buy, double sell) {
                m_buy = buy;
                m_sell = sell;
            }

            @Override public void process() {
                gotTop(m_buy, m_sell);
            }
        }

        private class InitTask implements IOrderTask {
            public InitTask() {
            }

            @Override public void process() throws Exception {
                log("InitTask.process()");
                initImpl();
            }
        }

        private static enum State {
            NONE { // no order placed
                @Override public OscExecutor.State onDirection(OscExecutor executor, int direction) throws Exception {
                    log("State.NONE.onDirection(direction=" + direction + ") on " + this);
                    return executor.processDirection(direction);
                }
            },
            ORDER { // order placed - waiting
                @Override public OscExecutor.State onDirection(OscExecutor executor, int direction) throws Exception {
                    log("State.ORDER.onDirection(direction=" + direction + ") on " + this);
                    return executor.processDirection(direction);
                }
                @Override public OscExecutor.State onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
//                    log("State.ORDER.onTrade(tData=" + tData + ") on " + this);
                    return executor.processTrade(tData, iContext);
                }
                @Override public void onTop(OscExecutor executor, double buy, double sell) {
//                    log("State.ORDER.onTop(buy=" + buy + ", sell=" + sell + ") on " + this);
                    executor.processTop(buy, sell);
                }
            },
            ERROR {
                public OscExecutor.State onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
                    log("State.ERROR.onTrade(tData=" + tData + ") on " + this);
                    executor.onError();
                    return NONE;
                }
            };

            public void onTop(OscExecutor executor, double buy, double sell) {
                log("State.NONE.onTop(buy=" + buy + ", sell=" + sell + ") on " + this);
            }

            public State onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
                log("State.NONE.onTrade(tData=" + tData + ") on " + this);
                return this;
            }

            public State onDirection(OscExecutor executor, int direction) throws Exception {
                log("State.NONE.onDirection(direction=" + direction + ") on " + this);
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

    private static class PhasedOscCalculator extends OscCalculator {
        private final OscExecutor m_executor;
        private final int m_index;
        private State m_state = State.NONE;
        private int m_barNum = 0;

        public PhasedOscCalculator(int index, OscExecutor executor) {
            super(Osc.LEN1, Osc.LEN2, Osc.K, Osc.D, Osc.BAR_SIZE, getOffset(index));
            m_executor = executor;
            m_index = index;
        }

        private static long getOffset(int index) {
            return BAR_SIZE / PHASES * index;
        }

        @Override protected void update(long stamp, boolean finishBar) {
            super.update(stamp, finishBar);
            if(finishBar) {
                log(" bar " + m_barNum + "; PREHEAT_BARS_NUM=" + PREHEAT_BARS_NUM);
                if (m_barNum++ == PREHEAT_BARS_NUM - INIT_BARS_BEFORE) {
                    m_executor.init();
                }
            }
        }

        @Override public void fine(long stamp, double stoch1, double stoch2) {
//log(" fine " + stamp + ": " + stoch1 + "; " + stoch2);
        }

        @Override public void bar(long barStart, double stoch1, double stoch2) {
            log(" ------------ [" + m_index + "] bar\t" + barStart + "\t" + stoch1 + "\t " + stoch2);
            m_state = m_state.process(this, stoch1, stoch2);
        }

        public void start(OrderSide orderSide) {
            log("start() bar " + m_barNum + "; orderSide=" + orderSide);
            m_executor.update((orderSide == OrderSide.BUY) ? 1 : -1);
        }

        public void stop(OrderSide orderSide) {
            log("stop() bar " + m_barNum + "; orderSide=" + orderSide);
            m_executor.update((orderSide == OrderSide.BUY) ? 1 : -1);
        }
    }

    private enum State {
        NONE {
            @Override public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
                double stochDiff = stoch2 - stoch1;
                if (stochDiff > startLevel(stoch1, stoch2)) {
                    log("start level reached for SELL; stochDiff="+stochDiff);
                    calc.start(OrderSide.SELL);
                    return DOWN;
                }
                if (-stochDiff > startLevel(stoch1, stoch2)) {
                    log("start level reached for BUY; stochDiff="+stochDiff);
                    calc.start(OrderSide.BUY);
                    return UP;
                }
                return this;
            }
        },
        UP {
            @Override public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
                double stochDiff = stoch2 - stoch1;
                boolean reverseDiff = stochDiff > stopLevel(stoch1, stoch2);
                if (reverseDiff) {
                    calc.stop(OrderSide.SELL);
                    return NONE;
                }
                return this;
            }
        },
        DOWN {
            @Override public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
                double stochDiff = stoch2 - stoch1;
                boolean reverseDiff = -stochDiff > stopLevel(stoch1, stoch2);
                if (reverseDiff) {
                    calc.stop(OrderSide.BUY);
                    return NONE;
                }
                return this;
            }
        };

        public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
            throw new RuntimeException("must be overridden");
        }

        private static double startLevel(double stoch1, double stoch2) {
            return START_LEVEL;
        }

        private static double stopLevel(double stoch1, double stoch2) {
            return STOP_LEVEL;
        }
    }
}
