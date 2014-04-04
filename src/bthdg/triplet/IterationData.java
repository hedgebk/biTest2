package bthdg.triplet;

import bthdg.*;
import bthdg.exch.OrdersData;
import bthdg.exch.TopData;
import bthdg.exch.TopsData;
import bthdg.exch.TradesData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class IterationData implements IIterationContext {
    private TopsData m_tops;
    private Map<Pair, TradesData> m_trades;
    private OrdersData m_liveOrders;
    public NewTradesAggregator m_newTrades = new NewTradesAggregator();
    private boolean m_acceptPriceSimulated;
    public TradesAggregator m_tradesAgg;

    public IterationData(TradesAggregator tAgg) {
         m_tradesAgg = tAgg;
    }

    @Override public boolean acceptPriceSimulated() { return m_acceptPriceSimulated; }
    @Override public void acceptPriceSimulated(boolean b) { m_acceptPriceSimulated = b; }
    @Override public Map<Pair, TradesData> fetchTrades(Exchange exchange) throws Exception { return getTrades(); }
    @Override public TopData getTop(Exchange exchange, Pair pair) throws Exception { return getTops().get(pair); }

    public TopsData getTops() throws Exception {
        if (m_tops == null) {
            return loadTops();
        }
        return m_tops;
    }

    public TopsData loadTops() throws Exception {
        m_tops = Fetcher.fetchTops(Exchange.BTCE, Triplet.PAIRS);
        log(" loaded tops: " + m_tops.toString(Exchange.BTCE));
        return m_tops;
    }

    public Map<Pair, TradesData> getTrades() throws Exception {
        if (m_trades == null) {
            m_trades = Fetcher.fetchTrades(Exchange.BTCE, Triplet.PAIRS);
        }
        return m_trades;
    }

    public OrdersData getLiveOrders(Exchange exchange) throws Exception {
        if (m_liveOrders == null) {
            m_liveOrders = Fetcher.fetchOrders(exchange, null);
            log("liveOrders loaded: " + m_liveOrders);
        }
        return m_liveOrders;
    }

    public void resetLiveOrders() {
//        log("  resetLiveOrders");
        m_liveOrders = null;
    }

    @Override public Map<Pair, TradesData> getNewTradesData(Exchange exchange, final TradesData.ILastTradeTimeHolder holder) throws Exception {
        final AtomicBoolean bool = new AtomicBoolean(false);
        TradesData.ILastTradeTimeHolder proxy = new TradesData.ILastTradeTimeHolder() {
            @Override public long lastProcessedTradesTime() { return holder.lastProcessedTradesTime(); }
            @Override public void lastProcessedTradesTime(long lastProcessedTradesTime) {
                holder.lastProcessedTradesTime(lastProcessedTradesTime);
                bool.set(true);
            }
        };
        Map<Pair, TradesData> ret = m_newTrades.getNewTradesData(this, exchange, proxy);
        if (bool.get() && notFirst(ret)) {
            m_tradesAgg.update(ret);
        }
        return ret;
    }

    private boolean notFirst(Map<Pair, TradesData> ret) {
        for(TradesData td : ret.values()) {
            List<TradeData> trades = td.m_trades;
            if( trades.size() != Triplet.LOAD_TRADES_NUM ) {
                return true;
            }
        }
        return false;
    }

    private static void log(String s) {
        Log.log(s);
    }
}
