package bthdg.run;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.Exchange;
import bthdg.exch.OrdersData;
import bthdg.exch.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LiveOrdersMgr {
    public Map<Exchange,Map<Pair,OrdersDataWrapper>> m_ordersMap; // = new HashMap<Exchange,Map<Pair,OrdersData>>();

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public void queryLiveOrders( Exchange exchange, Pair pair, ILiveOrdersCallback callback ) {
        OrdersDataWrapper odw = getOrdersDataWrapper(exchange, pair);
        odw.query(callback);
    }

    private OrdersDataWrapper getOrdersDataWrapper(Exchange exchange, Pair pair) {
        boolean perPair = exchange.requirePairForOrders();
        if (pair == Pair.ALL) {
            if (perPair) {
                throw new RuntimeException("exchange " + exchange + " do not supports ALL orders request - use per-pair");
            }
            throw new RuntimeException("ALL-pairs orders request not implemented yet");
        }
        Map<Pair, OrdersDataWrapper> map = getOrdersMap(exchange);
        synchronized (map) {
            OrdersDataWrapper odw = map.get(pair);
            if (odw == null) {
                odw = new OrdersDataWrapper(exchange, pair);
                map.put(pair, odw);
            }
            return odw;
        }
    }

    private synchronized Map<Pair, OrdersDataWrapper> getOrdersMap(Exchange exchange) {
        if(m_ordersMap == null) {
            m_ordersMap = new HashMap<Exchange, Map<Pair, OrdersDataWrapper>>();
        }
        Map<Pair, OrdersDataWrapper> ret = m_ordersMap.get(exchange);
        if(ret == null) {
            ret = new HashMap<Pair, OrdersDataWrapper>();
            m_ordersMap.put(exchange, ret);
        }
        return ret;
    }

    private static class OrdersDataWrapper {
        private final Exchange m_exchange;
        private final Pair m_pair;
        private List<ILiveOrdersCallback> m_callbacks;
        private Thread m_fetcher;
        private OrdersData m_ordersData;

        public OrdersDataWrapper(Exchange exchange, Pair pair) {
            m_exchange = exchange;
            m_pair = pair;
        }

        private synchronized void notifyCallbacks() {
            log("notifyCallbacks...");
            for (ILiveOrdersCallback callback : m_callbacks) {
                callback.onLiveOrders(m_ordersData);
            }
            m_callbacks.clear();
            m_callbacks = null;
        }

        public void query(ILiveOrdersCallback callback) {
            log("LiveOrdersMgr.query");
            OrdersData ordersData = null;
            synchronized(this) {
                if( m_ordersData != null ) { // already queried
                    ordersData = m_ordersData;
                    log("already queried: " + m_ordersData);
                    callback.onLiveOrders(ordersData);
                    log(" notify callback immediately");
                } else {
                    runFetcher(callback);
                }
            }
            log("LiveOrdersMgr.query finished");
        }

        private void runFetcher(ILiveOrdersCallback callback) {
            if( m_callbacks == null ) {
                m_callbacks = new ArrayList<ILiveOrdersCallback>();
            }
            m_callbacks.add(callback);
            if(m_fetcher == null) {
                m_fetcher = new Thread() {
                    @Override public void run() {
                        OrdersData ordersData;
                        try {
                            log("fetchOrders() m_exchange="+m_exchange + ", m_pair="+m_pair);
                            ordersData = Fetcher.fetchOrders(m_exchange, m_pair);
                            log(" ordersData="+ordersData);
                        } catch (Exception e) {
                            ordersData = new OrdersData("fetchOrders error: " + e);
                            log(" error ordersData="+ordersData);
                            e.printStackTrace();
                        }
                        synchronized(this) {
                            m_ordersData = ordersData;
                        }
                        notifyCallbacks();
                    }
                };
                m_fetcher.start();
                log(" callback stored, started fetchOrders thread");
            }
        }
    }


    public interface ILiveOrdersCallback {
        void onLiveOrders(OrdersData ordersData);
    }

}

