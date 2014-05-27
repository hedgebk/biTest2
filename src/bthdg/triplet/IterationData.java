package bthdg.triplet;

import bthdg.*;
import bthdg.exch.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class IterationData implements IIterationContext {
    static long s_topLoadTakes; // time takes all Tops loading  | deep avg 1088ms    top avg 991ms
    static int s_topLoadCount;  // tops loading count           |      count 464         count 842

    TopsData m_tops;
    private Map<Pair, TradesData> m_trades;
    private OrdersData m_liveOrders;
    public NewTradesAggregator m_newTrades = new NewTradesAggregator();
    private boolean m_acceptPriceSimulated;
    public TradesAggregator m_tradesAgg;
    private long m_startMillis;
    private long m_topsLoadTime;
    private TopsData m_prevTops;
    private DeepsData m_deeps;
    private boolean m_noSleep;

    public IterationData(TradesAggregator tAgg, TopsData tops) {
        m_tradesAgg = tAgg;
        m_startMillis = System.currentTimeMillis();
        m_prevTops = tops;
    }

    @Override public boolean acceptPriceSimulated() { return m_acceptPriceSimulated; }
    @Override public void acceptPriceSimulated(boolean b) { m_acceptPriceSimulated = b; }
    @Override public Map<Pair, TradesData> fetchTrades(Exchange exchange) throws Exception { return getTrades(); }
    @Override public TopData getTop(Exchange exchange, Pair pair) throws Exception { return getTops().get(pair); }
    public void noSleep() { m_noSleep = true; }
    public boolean isNoSleep() { return m_noSleep; }
    public long millisFromStart() { return System.currentTimeMillis() - m_startMillis; }
    public long millisFromTopsLoad() {
        return (m_topsLoadTime == 0) ? 0 : System.currentTimeMillis() - m_topsLoadTime;
    }

    public TopsData getAnyTops() throws Exception {
        if (m_tops != null) {
            log(" USING CURRENT ITERATION tops (do not load fresh) " + millisFromStart() + "ms: " + m_tops.toString(Exchange.BTCE));
            return m_tops;
        }
        if(m_prevTops != null) {
            log(" USING PREV ITERATION tops (blind trade) " + millisFromStart() + "ms: " + m_tops.toString(Exchange.BTCE));
            return m_prevTops;
        }
        return getTops();
    }

    public TopsData getTops() throws Exception {
        if (m_tops == null) {
            return loadTops();
        }
        return m_tops;
    }

    public TopsData loadTops() throws Exception {
        long start = System.currentTimeMillis();
        if (Triplet.USE_DEEP) {
            m_deeps = Fetcher.fetchDeeps(Exchange.BTCE, Triplet.PAIRS);
            m_tops = m_deeps.getTopsDataAdapter();
        } else {
            m_tops = Fetcher.fetchTops(Exchange.BTCE, Triplet.PAIRS);
        }
        long end = System.currentTimeMillis();
        long takes = end - start;
        s_topLoadTakes += takes;
        s_topLoadCount++;
        long average = s_topLoadTakes/s_topLoadCount;
        log(" loaded tops" + (Triplet.USE_DEEP ? "*" : "") + " " + millisFromStart() + "ms; take " + takes + "ms; avg " + average + "ms: " + m_tops.toString(Exchange.BTCE));
        m_topsLoadTime = System.currentTimeMillis();
        if(Triplet.USE_RALLY) {
            checkLtcBtc();
        }
        return m_tops;
    }

    private void checkLtcBtc() {
        TopData b = m_tops.get(Pair.BTC_USD);
        TopData l = m_tops.get(Pair.LTC_USD);
        TopData lb = m_tops.get(Pair.LTC_BTC);
        double midB = b.getMid();   // 475.100
        double midL = l.getMid();   // 11.884000
        double midLb = lb.getMid(); // 0.02514
        double midLLb = midL/midLb;
        double ratio = midB/midLLb;
        log(" LLB ratio\t" + ratio + "\t" + System.currentTimeMillis());
    }

    public void resetTops() {
        log("  resetTops");
        m_tops = null;
        m_topsLoadTime = 0;
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
            log(" liveOrders loaded " + millisFromStart() + "ms: " + m_liveOrders);
        }
        return m_liveOrders;
    }

    public void resetLiveOrders() {
        log("  resetLiveOrders");
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
