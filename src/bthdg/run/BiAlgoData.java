package bthdg.run;

import bthdg.IIterationContext;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.util.Sync;
import bthdg.util.Utils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class BiAlgoData {
    private static final int ID_CHARS_NUM = 6;

    public final String m_id;
    public final Pair m_pair;
    public final boolean m_up;
    private final ExchangesPair m_exchangesPair;
    public BiAlgoState m_state = BiAlgoState.NEW;
    public final BiAlgo.BiPoint m_openPoint;
    private BiAlgo.BiPoint m_closePoint;
    public final OrderDataExchange m_openOde1;
    public final OrderDataExchange m_openOde2;
    OrderDataExchange m_closeOde1;
    OrderDataExchange m_closeOde2;
    public double m_amount;
    public double m_open;
    public double m_close;

    void log(String s) { Log.log(m_id + " " + s); }
    private void err(String s, Exception e) { Log.err(m_id + " " + s, e); }

    public BiAlgoData(ExchangesPair exchangesPair, boolean up, Pair pair, BiAlgo.BiPoint mktPoint, double amount,
                      OrderDataExchange ode1, OrderDataExchange ode2) {
        this(null, exchangesPair, up, pair, mktPoint, amount, ode1, ode2);
    }

    public BiAlgoData(String parentId, ExchangesPair exchangesPair, boolean up, Pair pair, BiAlgo.BiPoint mktPoint, double amount,
                      OrderDataExchange ode1, OrderDataExchange ode2) {
        m_id = generateId(parentId);
        m_exchangesPair = exchangesPair;
        m_up = up;
        m_pair = pair;
        m_openPoint = mktPoint;
        m_amount = amount;
        m_openOde1 = ode1;
        m_openOde2 = ode2;
    }

    @Override public String toString() {
        return "BiAlgoData{" +
                "id='" + m_id + '\'' +
                ", up=" + m_up +
                ", state=" + m_state +
                ", amount=" + m_amount +
                ((m_open > 0) ? ", open=" + m_open : "") +
                ((m_close > 0) ? ", close=" + m_close : "") +
                '}';
    }

    private String generateId(String parentId) {
        return Utils.generateId(parentId, ID_CHARS_NUM);
    }

    public void placeOpenOrders(final IterationHolder ih) throws InterruptedException {
        OrderDataExchange[] ode = new OrderDataExchange[]{m_openOde1, m_openOde2};
        placeOrders(ih, ode, BiAlgoState.OPEN_PLACED);
    }

    private void placeOrders(IterationHolder ih, OrderDataExchange[] ode, BiAlgoState okState) throws InterruptedException {
        List<OrderData.OrderPlaceStatus> placeRes = Utils.runAndSync(ode, new Utils.IRunnable<OrderDataExchange, OrderData.OrderPlaceStatus>() {
            @Override public OrderData.OrderPlaceStatus run(OrderDataExchange ode) {
                try {
                    log("placeOrder: " + ode);
                    OrderData.OrderPlaceStatus orderPlaceStatus = BiAlgo.placeOrder(ode.m_account, ode.m_orderData, OrderState.LIMIT_PLACED);
                    log("placeOrder res: " + orderPlaceStatus);
                    return orderPlaceStatus;
                } catch (Exception e) {
                    String msg = "placeOrder error: " + e;
                    err(msg, e);
                    return OrderData.OrderPlaceStatus.ERROR;
                }
            }
        });
        boolean noErrors = true;
        for (int i = 0, placeResSize = placeRes.size(); i < placeResSize; i++) {
            OrderData.OrderPlaceStatus pod = placeRes.get(i);
            if (pod == OrderData.OrderPlaceStatus.ERROR) {
                handlePlaceOrderError();
                noErrors = false;
                setState(BiAlgoState.ERROR);
            } else if (pod == OrderData.OrderPlaceStatus.OK) {
                ih.lockDeep(ode[i]);
            } else {
                log("ERROR: not supported OrderPlaceStatus: " + pod);
            }
        }
        if(noErrors) {
            log("all order placed: " + placeRes);
            setState(okState);
        } else {
            log("some ERROR detected: order place res: " + placeRes);
        }
    }

    void setState(BiAlgoState state) {
        if(m_state != state) {
            log("setState " + m_state + "->" + state);
            m_state = state;
        }
    }

    private void handlePlaceOrderError(/*PlaceOrderData pod*/) {
        log("ERROR: implement handlePlaceOrderError: " /*+ pod*/);
    }

    public void placeCloseOrders(IterationHolder ih, BiAlgo.BiPoint mktPoint, OrderDataExchange ode1, OrderDataExchange ode2) throws InterruptedException {
        m_closePoint = mktPoint;
        m_closeOde1 = ode1;
        m_closeOde2 = ode2;
        OrderDataExchange[] ode = new OrderDataExchange[]{m_closeOde1, m_closeOde2};
        placeOrders(ih, ode, BiAlgoState.CLOSE_PLACED);
    }

    public AtomicBoolean checkLiveOrderState(IterationHolder ih, BiAlgo biAlgo) {
        Boolean isOpen = m_state.isOpen();
        log("checkLiveOrderState (isOpen=" + isOpen + ") on " + this);
        List<AtomicBoolean> ret = null;

        OrderDataExchange[] odes = getOrderDataExchanges();
        for(OrderDataExchange ode: odes) {
            if(ode != null) {
                AtomicBoolean stat = checkLiveOrderState(ih, ode, biAlgo);
                ret = Sync.addSync(ret, stat);
            }
        }
        if (ret != null) {
            final AtomicBoolean sync = new AtomicBoolean(false);
            Sync.waitInThreadIfNeeded(ret, new Runnable() {
                @Override public void run() {
                    m_state.checkState(BiAlgoData.this);
                    Sync.setAndNotify(sync);
                }
            });
            return sync;
        }
        return null;
    }

    private AtomicBoolean checkLiveOrderState(final IterationHolder ih, OrderDataExchange ode, final BiAlgo biAlgo) {
        AtomicBoolean ret = null;
        final OrderData orderData = ode.m_orderData;
        OrderStatus status = orderData.m_status;
        if (status.isActive()) {
            log("checkLiveOrderState: " + ode);
            final Exchange exchange = ode.m_exchange;
            log(" queryLiveOrders: " + exchange + ", " + m_pair);
            ret = new AtomicBoolean(false);
            final AtomicBoolean finalRet = ret;
            ih.queryLiveOrders(exchange, m_pair, new LiveOrdersMgr.ILiveOrdersCallback() {
                @Override public void onLiveOrders(final OrdersData ordersData) {
                    log("onLiveOrders: " + exchange + ", " + m_pair + ": " + ordersData);
                    String orderId = orderData.m_orderId;
                    log(" orderId: " + orderId);
                    OrdersData.OrdData ordData = ordersData.m_ords.get(orderId);
                    log("  live order state: " + ordData);
                    AccountData accountData = biAlgo.m_accountMap.get(exchange);
                    try {
                        orderData.checkState(new IIterationContext.BaseIterationContext() {
                            @Override public OrdersData getLiveOrders(Exchange exchange) throws Exception {
                                return ordersData;
                            }
                        }, exchange, accountData, null, null);
                    } catch (Exception e) {
                        err("orderData.checkState error: " + e, e);
                    }
                    Sync.setAndNotify(finalRet);
                }
            });
        }
        return ret;
    }

    public void logIterationEnd() {
        log(" " + this);
        log("  open1: " + m_openOde1);
        log("  open2: " + m_openOde2);
        if (m_closeOde1 != null) {
            log("   close1: " + m_closeOde1);
        }
        if (m_closeOde2 != null) {
            log("   close2: " + m_closeOde2);
        }
    }

    public boolean cancel() throws Exception {
        log("cancel data: " + this);
        boolean ok = true;

        OrderDataExchange[] odes = getOrderDataExchanges();
        for( OrderDataExchange ode: odes ) {
            if (ode != null) {
                OrderData od = ode.m_orderData;
                if (od.m_status.isActive()) {
                    ok &= ode.cancelOrder();
                }
            }
        }

        setState(ok ? BiAlgoState.CANCEL : BiAlgoState.CANCELING);
        return ok;
    }

    public boolean checkTheSamePending(OrderDataExchange ode) {
        Exchange exch = ode.m_exchange;
        OrderData oData = ode.m_orderData;
        OrderSide side = oData.m_side;
        double price = oData.m_price;

        Boolean isOpen = m_state.isOpen();
        if(isOpen != null) {
            OrderDataExchange[] orderDataExchanges = getOrderDataExchanges(isOpen);
            for(OrderDataExchange o: orderDataExchanges) {
                Exchange exchange = o.m_exchange;
                if(exch == exchange) {
                    OrderData orderData = o.m_orderData;
                    if( orderData.m_side == side) {
                        if( orderData.m_price == price) {
                            OrderStatus status = orderData.m_status;
                            if(status.isActive()) {
                                log( " got matched live active on " + this);
                                log( "  active " + o);
                                log( "  candidate for new " + ode);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public OrderData getFirstActiveLive() {
        Boolean isOpen = m_state.isOpen();
        if(isOpen != null) {
            OrderDataExchange[] orderDataExchanges = getOrderDataExchanges(isOpen);
            for(OrderDataExchange ode: orderDataExchanges) {
                OrderData od = ode.m_orderData;
                if(od.isActive()) {
                    return od;
                }
            }
        }
        return null;
    }

    OrderDataExchange[] getOrderDataExchanges() {
        return new OrderDataExchange[] {m_openOde1, m_closeOde1, m_openOde2, m_closeOde2};
    }

    OrderDataExchange[] getOrderDataExchanges(Boolean isOpen) {
        OrderDataExchange ode1 = isOpen ? m_openOde1 : m_closeOde1;
        OrderDataExchange ode2 = isOpen ? m_openOde2 : m_closeOde2;
        return new OrderDataExchange[]{ode1, ode2};
    }

    public boolean hasLiveExchOrder() {
        OrderDataExchange[] odes = getOrderDataExchanges();
        for (OrderDataExchange ode : odes) {
            if (ode != null) {
                if (ode.m_orderData.isActive()) {
                    return true;
                }
            }
        }
        return false;
    }

    public BiAlgoData split(double splitAmount) {
        OrderDataExchange ode1 = OrderDataExchange.split(m_openOde1, splitAmount);
        OrderDataExchange ode2 = OrderDataExchange.split(m_openOde2, splitAmount);
        BiAlgoData ret = new BiAlgoData(m_id, m_exchangesPair, m_up, m_pair, m_openPoint, m_amount, ode1, ode2);
        BiAlgoState state = m_state;
        if (state == BiAlgoState.SOME_OPEN) {
            state = BiAlgoState.OPEN;
        } else if (state == BiAlgoState.SOME_CLOSE) {
            state = BiAlgoState.CLOSE;
        }
        ret.m_state = state;
        OrderDataExchange ode3 = OrderDataExchange.split(m_closeOde1, splitAmount);
        OrderDataExchange ode4 = OrderDataExchange.split(m_closeOde2, splitAmount);
        ret.m_closeOde1 = ode3;
        ret.m_closeOde2 = ode4;
        return ret;
    }

}
