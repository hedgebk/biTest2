package bthdg.run;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.Exchange;
import bthdg.exch.OrdersData;
import bthdg.exch.Pair;
import bthdg.exch.PlaceOrderData;
import bthdg.util.Utils;

import java.util.List;

public class BiAlgoData {
    private static final int ID_CHARS_NUM = 6;

    public final String m_id;
    public final Pair m_pair;
    public final boolean m_up;
    public BiAlgoState m_state;
    public final BiAlgo.BiPoint m_startPoint;
    public final OrderDataExchange m_ode1;
    public final OrderDataExchange m_ode2;
    public double m_amount;

    private void log(String s) { Log.log(m_id + " " + s); }
    private void err(String s, Exception e) { Log.err(m_id + " " + s, e); }

    public BiAlgoData(boolean up, Pair pair, BiAlgo.BiPoint mktPoint, double amount, OrderDataExchange ode1, OrderDataExchange ode2) {
        m_id = generateId(null);
        m_up = up;
        m_pair = pair;
        m_startPoint = mktPoint;
        m_amount = amount;
        m_ode1 = ode1;
        m_ode2 = ode2;
    }

    @Override public String toString() {
        return "BiAlgoData{" +
                "id='" + m_id + '\'' +
                ", up=" + m_up +
                ", state=" + m_state +
                '}';
    }

    private String generateId(String parentId) {
        return Utils.generateId(parentId, ID_CHARS_NUM);
    }

    public void placeOpenOrders(final IterationHolder ih) throws InterruptedException {
        OrderDataExchange[] ode = new OrderDataExchange[]{m_ode1, m_ode2};

        List<PlaceOrderData> placeRes = Utils.runAndSync(ode, new Utils.IRunnable<OrderDataExchange, PlaceOrderData>() {
            @Override public PlaceOrderData run(OrderDataExchange ode) {
                try {
                    log("placeOder: " + ode);
                    PlaceOrderData placeOrderData = Fetcher.placeOrder(ode.m_orderData, ode.m_exchange);
                    log("placeOder res: " + placeOrderData);
                    return placeOrderData;
                } catch (Exception e) {
                    String msg = "placeOrder error: " + e;
                    err(msg, e);
                    return new PlaceOrderData(msg);
                }
            }
        });
        log("all order placed: " + placeRes);

        boolean noErrors = true;
        for (PlaceOrderData pod : placeRes) {
            if (pod.m_error != null) {
                handlePlaceOrderError(pod);
                noErrors = false;
            }
        }

        if(noErrors) {
            // need to check execution status
            List<OrdersData> checkRes = Utils.runAndSync(ode, new Utils.IRunnable<OrderDataExchange, OrdersData>() {
                @Override public OrdersData run(OrderDataExchange ode) {
                    final OrdersData ret[] = new OrdersData[1];
                    synchronized (ret) {
                        final Exchange exchange = ode.m_exchange;
                        log("queryLiveOrders: " + exchange + ", " + m_pair);
                        ih.queryLiveOrders(exchange, m_pair, new LiveOrdersMgr.ILiveOrdersCallback() {
                            @Override public void onLiveOrders(OrdersData ordersData) {
                                log("onLiveOrders: " + exchange + ", " + m_pair + ": " + ordersData);
                                synchronized (ret) {
                                    ret[0] = ordersData;
                                }
                                ordersData.notify();
                            }
                        });
                        if(ret[0]==null) {
                            try {
                                ret.wait();
                            } catch (InterruptedException e) {}
                        }
                    }
                    return ret[0];
                }
            });
            log("check execution status: " + checkRes);
        }

    }

    private void handlePlaceOrderError(PlaceOrderData pod) {
        log("TODO: implement handlePlaceOrderError: " + pod);
    }

    public void placeCloseOrders(IterationHolder ih, OrderDataExchange ode1, OrderDataExchange ode2) {
        //To change body of created methods use File | Settings | File Templates.
    }

    public enum BiAlgoState {
    }

}
