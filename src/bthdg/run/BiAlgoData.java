package bthdg.run;

import bthdg.Fetcher;
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

    public BiAlgoData(boolean up, Pair pair, BiAlgo.BiPoint mktPoint, double amount, OrderDataExchange ode1, OrderDataExchange ode2) {
        m_id = generateId(null);
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
                    OrderData.OrderPlaceStatus orderPlaceStatus = BiAlgo.placeOrder(ode.m_account, ode.m_exchange, ode.m_orderData, OrderState.LIMIT_PLACED);
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
        }
    }

    private void setState(BiAlgoState state) {
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
        boolean isOpen = m_state.isOpen();
        log("checkLiveOrderState (isOpen=" + isOpen + ") on " + this);
        List<AtomicBoolean> ret = null;
        OrderDataExchange ode1 = isOpen ? m_openOde1 : m_closeOde1;
        log(" ode1: " + ode1);
        OrderDataExchange ode2 = isOpen ? m_openOde2 : m_closeOde2;
        log(" ode2: " + ode2);
        AtomicBoolean stat1 = checkLiveOrderState(ih, ode1, biAlgo);
        ret = Sync.addSync(ret, stat1);
        AtomicBoolean stat2 = checkLiveOrderState(ih, ode2, biAlgo);
        ret = Sync.addSync(ret, stat2);

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
        log("checkLiveOrderState: " + ode);
        final OrderData orderData = ode.m_orderData;
        OrderStatus status = orderData.m_status;
        log(" status: " + status);
        if (status.isActive()) {
            final Exchange exchange = ode.m_exchange;
            log("queryLiveOrders: " + exchange + ", " + m_pair);
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
        OrderData odo1 = m_openOde1.m_orderData;
        if (odo1.m_status.isActive()) {
            ok &= cancelOrder(m_openOde1);
        }
        OrderData odo2 = m_openOde2.m_orderData;
        if (odo2.m_status.isActive()) {
            ok &= cancelOrder(m_openOde2);
        }
        if (m_closeOde1 != null) {
            OrderData odc1 = m_closeOde1.m_orderData;
            if (odc1.m_status.isActive()) {
                ok &= cancelOrder(m_closeOde1);
            }
        }
        if (m_closeOde2 != null) {
            OrderData odc2 = m_closeOde2.m_orderData;
            if (odc2.m_status.isActive()) {
                ok &= cancelOrder(m_closeOde2);
            }
        }
        if(ok) {
            setState(BiAlgoState.CANCEL);
        }
        return ok;
    }

    private boolean cancelOrder(OrderDataExchange orderData) throws Exception {
        log("cancel order: " + orderData);
        Exchange exchange = orderData.m_exchange;
        OrderData od = orderData.m_orderData;
        String orderId = od.m_orderId;
        Pair pair = od.m_pair;
        CancelOrderData coData = Fetcher.cancelOrder(exchange, orderId, pair);
        String error = coData.m_error;
        if (error == null) {
            od.cancel();
            AccountData account = orderData.m_account;
            account.releaseOrder(od, exchange);
            return true;
        } else {
            log("error in cancel order: " + error + "; " + od);
            return false;
        }
    }

    public boolean checkTheSamePending(OrderDataExchange ode) {
        Exchange exch = ode.m_exchange;
        OrderData oData = ode.m_orderData;
        OrderSide side = oData.m_side;
        double price = oData.m_price;

        Boolean isOpen = m_state.isOpen();
        if(isOpen != null) {
            OrderDataExchange ode1 = isOpen ? m_openOde1 : m_closeOde1;
            OrderDataExchange ode2 = isOpen ? m_openOde2 : m_closeOde2;
            for(OrderDataExchange o: new OrderDataExchange[]{ode1, ode2}) {
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

    public enum BiAlgoState {
        NEW,
        OPEN_PLACED {
            @Override public boolean isPending() { return true; }
            @Override public Boolean isOpen() { return true; }
            @Override public void checkState(BiAlgoData biAlgoData) {
                checkPlacedState(biAlgoData, OPEN, SOME_OPEN, true);
            }
        },
        SOME_OPEN {
            @Override public boolean isPending() { return true; }
            @Override public Boolean isOpen() { return true; }
            @Override public void checkState(BiAlgoData biAlgoData) {
                checkSomeDone(biAlgoData, OPEN, true);
            }
        },
        OPEN,
        CLOSE_PLACED {
            @Override public boolean isPending() { return true; }
            @Override public Boolean isOpen() { return false; }
            @Override public void checkState(BiAlgoData biAlgoData) {
                checkPlacedState(biAlgoData, CLOSE, SOME_CLOSE, false);
            }
        },
        SOME_CLOSE {
            @Override public boolean isPending() { return true; }
            @Override public Boolean isOpen() { return false; }
            @Override public void checkState(BiAlgoData biAlgoData) {
                checkSomeDone(biAlgoData, CLOSE, false);
            }
        },
        CLOSE,
        CANCEL,
        ERROR;

        private static void checkSomeDone(BiAlgoData biAlgoData, BiAlgoState filledState, boolean opening) {
            OrderDataExchange ode1 = opening ? biAlgoData.m_openOde1 : biAlgoData.m_closeOde1;
            OrderDataExchange ode2 = opening ? biAlgoData.m_openOde2 : biAlgoData.m_closeOde2;
            OrderData od1 = ode1.m_orderData;
            OrderData od2 = ode2.m_orderData;
            OrderStatus status1 = od1.m_status;
            OrderStatus status2 = od2.m_status;
            String side = (opening ? "open" : "close");
            if ((status1 == OrderStatus.FILLED) && (status2 == OrderStatus.FILLED)) { // both filled
                double filled1 = od1.m_filled;
                double filled2 = od2.m_filled;
                double done = Math.min(filled1, filled2);
                biAlgoData.log("both executed: filled1=" + filled1 + "; filled2=" + filled2 + ";  " +
                        side + "=" + done);
                if (opening) {
                    biAlgoData.m_open = done;
                } else {
                    biAlgoData.m_close = done;
                }
                biAlgoData.setState(filledState);
            } else if (status1.partialOrFilled() || status2.partialOrFilled()) {
                double filled1 = od1.m_filled;
                double filled2 = od2.m_filled;
                double newDone = Math.min(filled1, filled2);
                double oldDone = biAlgoData.m_open;
                if (newDone > oldDone) {
                    biAlgoData.log("some more executed: " +
                            "old " + side + "=" + oldDone +
                            "; new " + side + "=" + newDone +
                            "; filled1=" + filled1 + "; filled2=" + filled2);
                    if (opening) {
                        biAlgoData.m_open = newDone;
                    } else {
                        biAlgoData.m_close = newDone;
                    }
                } else {
                    biAlgoData.log("no more executed: filled1=" + filled1 + "; filled2=" + filled2);
                }
            } else {
                biAlgoData.log("ERROR: NOT supported state: status1=" + status1 + ", status2=" + status2);
            }
        }

        private static void checkPlacedState(BiAlgoData biAlgoData, BiAlgoState filledState, BiAlgoState partialState, boolean opening) {
            OrderDataExchange ode1 = opening ? biAlgoData.m_openOde1 : biAlgoData.m_closeOde1;
            OrderDataExchange ode2 = opening ? biAlgoData.m_openOde2 : biAlgoData.m_closeOde2;
            OrderData od1 = ode1.m_orderData;
            OrderData od2 = ode2.m_orderData;
            OrderStatus status1 = od1.m_status;
            OrderStatus status2 = od2.m_status;
            if ((status1 == OrderStatus.FILLED) && (status2 == OrderStatus.FILLED)) { // both filled
                double filled1 = od1.m_filled;
                double filled2 = od2.m_filled;
                double done = Math.min(filled1, filled2);
                if (opening) {
                    biAlgoData.m_open = done;
                } else {
                    biAlgoData.m_close = done;
                }
                biAlgoData.log("both executed: filled1=" + filled1 + "; filled2=" + filled2 + ";  " +
                        (opening ? "open" : "close") + "=" + done);
                biAlgoData.setState(filledState);
            } else if (status1.partialOrFilled() || status2.partialOrFilled()) {
                double filled1 = od1.m_filled;
                double filled2 = od2.m_filled;
                double done = Math.min(filled1, filled2);
                if (opening) {
                    biAlgoData.m_open = done;
                } else {
                    biAlgoData.m_close = done;
                }
                biAlgoData.log("some executed: filled1=" + filled1 + "; filled2=" + filled2 + ";  " +
                        (opening ? "open" : "close") + "=" + done);
                biAlgoData.setState(partialState);
            } else if ((status1 == OrderStatus.SUBMITTED) && (status2 == OrderStatus.SUBMITTED)) {
                biAlgoData.log("nothing yet executed");
            } else {
                biAlgoData.log("on supported state: status1=" + status1 + ", status2=" + status2);
            }
        }

        public boolean isPending() { return false; }
        public void checkState(BiAlgoData biAlgoData) { throw new RuntimeException("checkState() not implemented yet for " + this); }
        public Boolean isOpen() { return null; }
    }

}
