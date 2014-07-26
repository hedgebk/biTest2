package bthdg.run;

import bthdg.exch.*;

import java.util.HashMap;
import java.util.Map;

public class IterationHolder {
    private final int m_count;
    private LiveOrdersMgr m_liveOrdersMgr;
    private Map<String, Double> m_lockedMap;
    private Map<ExchangesPair, Integer> m_tradesCountMap;
    private Map<Exchange, Map<Currency,Double>> m_lockedFundsMap;

    public IterationHolder(int count) { m_count = count; }

    public void queryLiveOrders( Exchange exchange, Pair pair, LiveOrdersMgr.ILiveOrdersCallback callback ) {
        LiveOrdersMgr liveOrdersMgr;
        synchronized (this) {
            if( m_liveOrdersMgr == null ) {
                m_liveOrdersMgr = new LiveOrdersMgr();
            }
            liveOrdersMgr = m_liveOrdersMgr;
        }
        liveOrdersMgr.queryLiveOrders(exchange, pair, callback);
    }

    public synchronized void lockDeep(OrderDataExchange ode) {
        Exchange exchange = ode.m_exchange;
        OrderData od = ode.m_orderData;
        OrderSide side = od.m_side;
        String key = deepLockKey(exchange, side);
        Double locked;
        if (m_lockedMap == null) {
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

    public synchronized double getDeepLocked(Exchange exchange, OrderSide side) {
        if (m_lockedMap != null) {
            String key = deepLockKey(exchange, side);
            Double locked = m_lockedMap.get(key);
            if (locked != null) {
                return locked;
            }
        }
        return 0d;
    }

    private String deepLockKey(Exchange exchange, OrderSide side) {
        return exchange + ":" + side;
    }

    public synchronized void addTradePlaced(ExchangesPair exchangesPair) {
        Integer count;
        if(m_tradesCountMap == null) {
            m_tradesCountMap = new HashMap<ExchangesPair, Integer>();
            count = 0;
        } else {
            count = m_tradesCountMap.get(exchangesPair);
        }
        count ++;
        m_tradesCountMap.put(exchangesPair, count);
    }

    public int getTradePlaced(ExchangesPair exchangesPair) {
        if(m_tradesCountMap != null) {
            Integer count = m_tradesCountMap.get(exchangesPair);
            if (count != null) {
                return count;
            }
        }
        return 0;
    }

    public synchronized boolean lockAmount(AccountData account, double lockAmount, Currency currency) {
        if (m_lockedFundsMap == null) {
            m_lockedFundsMap = new HashMap<Exchange, Map<Currency,Double>>();
        }
        Exchange exchange = account.m_exchange;
        Map<Currency,Double> exchLockedFundsMap = m_lockedFundsMap.get(exchange);
        if(exchLockedFundsMap == null) {
            exchLockedFundsMap = new HashMap<Currency, Double>();
            m_lockedFundsMap.put(exchange, exchLockedFundsMap);
        }
        Double locked = exchLockedFundsMap.get(currency);
        if(locked == null) {
            locked = 0d;
        }
        Double allowedToLock = account.available(currency) - locked;
        if(allowedToLock >= lockAmount) {
            locked += lockAmount;
            exchLockedFundsMap.put(currency, locked);
            return true;
        }
        return false;
    }

    public synchronized boolean unlockAmount(AccountData account, double unlockAmount, Currency currency) {
        if (m_lockedFundsMap != null) {
            Exchange exchange = account.m_exchange;
            Map<Currency,Double> exchLockedFundsMap = m_lockedFundsMap.get(exchange);
            if(exchLockedFundsMap != null) {
                Double locked = exchLockedFundsMap.get(currency);
                if(locked != null) {
                    locked -= unlockAmount;
                    if(locked != 0) {
                        exchLockedFundsMap.put(currency, locked);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public synchronized Double getLocked(Exchange exchange, Currency currency) {
        if (m_lockedFundsMap != null) {
            Map<Currency,Double> exchLockedFundsMap = m_lockedFundsMap.get(exchange);
            if(exchLockedFundsMap != null) {
                Double locked = exchLockedFundsMap.get(currency);
                return locked;
            }
        }
        return null;
    }
}
