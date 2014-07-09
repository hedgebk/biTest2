package bthdg.run;

import bthdg.exch.Exchange;
import bthdg.exch.OrderData;
import bthdg.exch.OrderSide;
import bthdg.exch.Pair;

import java.util.HashMap;
import java.util.Map;

public class IterationHolder {
    private final int m_count;
    private LiveOrdersMgr m_liveOrdersMgr;
    private Map<String, Double> m_lockedMap;
    private Map<BiAlgo.ExchangesPair, Integer> m_tradesCountMap;

    public IterationHolder(int count) {
        m_count = count;
    }

    public void queryLiveOrders( Exchange exchange, Pair pair, LiveOrdersMgr.ILiveOrdersCallback callback ) {
        LiveOrdersMgr liveOrdersMgr;
        synchronized (this) {
            if( m_liveOrdersMgr == null ) {
                m_liveOrdersMgr = new LiveOrdersMgr();
            }
            liveOrdersMgr = m_liveOrdersMgr;
        }
        liveOrdersMgr.queryLiveOrders( exchange, pair, callback );
    }

    public void lockDeep(OrderDataExchange ode) {
        Exchange exchange = ode.m_exchange;
        OrderData od = ode.m_orderData;
        OrderSide side = od.m_side;
        String key = deepLockKey(exchange, side);
        Double locked;
        if(m_lockedMap == null) {
            m_lockedMap = new HashMap<String, Double>();
            locked = 0d;
        } else {
            locked = m_lockedMap.get(key);
            if (locked == null) {
                locked = 0d;
            }
        }
        double amount = od.m_amount;
        locked += amount;
        m_lockedMap.put(key, locked);
    }

    public double getDeepLocked(Exchange exchange, OrderSide side) {
        if(m_lockedMap != null) {
            String key = deepLockKey(exchange, side);
            Double locked = m_lockedMap.get(key);
            if(locked != null) {
                return locked;
            }
        }
        return 0d;
    }

    private String deepLockKey(Exchange exchange, OrderSide side) {
        return exchange + ":" + side;
    }

    public void addTradePlaced(BiAlgo.ExchangesPair exchangesPair) {
        Integer count;
        if(m_tradesCountMap == null) {
            m_tradesCountMap = new HashMap<BiAlgo.ExchangesPair, Integer>();
            count = 0;
        } else {
            count = m_tradesCountMap.get(exchangesPair);
        }
        count ++;
        m_tradesCountMap.put(exchangesPair, count);
    }

    public int getTradePlaced(BiAlgo.ExchangesPair exchangesPair) {
        if(m_tradesCountMap != null) {
            Integer count = m_tradesCountMap.get(exchangesPair);
            if (count != null) {
                return count;
            }
        }
        return 0;
    }
}
