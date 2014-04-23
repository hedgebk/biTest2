package bthdg.duplet;

import bthdg.*;
import bthdg.exch.OrdersData;
import bthdg.exch.Pair;
import bthdg.exch.TopData;
import bthdg.exch.TradesData;

import java.util.HashMap;
import java.util.Map;

public class IterationContext implements IIterationContext {
    private IRecorder m_recorder;
    public TopDatas m_top;
    public Map<Integer, OrdersData> m_liveOrders;
    public long m_nextIterationDelay = 1000; // 1 sec by def
    public boolean m_acceptPriceSimulated;
    public boolean m_accountRequested;
    public NewTradesAggregator m_newTrades = new NewTradesAggregator() {
        @Override protected void onNewTrades(Exchange exchange, Map<Pair, TradesData> data) {
            IterationContext.this.onNewTrades(exchange, data);
        }
    };
    private PairExchangeData m_pairExchange;

    public boolean acceptPriceSimulated() { return m_acceptPriceSimulated; }
    public void acceptPriceSimulated(boolean b) { m_acceptPriceSimulated = b; }

    private static void log(String s) { Log.log(s); }

    public IterationContext(PairExchangeData data, IRecorder recorder) {
        m_pairExchange = data;
        m_recorder = recorder;
    }

    @Override public TopData getTop(Exchange exchange, Pair pair) throws Exception {
        TopDatas topsData = getTopsData(m_pairExchange);
        return (exchange == m_pairExchange.m_sharedExch1.m_exchange) ? topsData.m_top1 : topsData.m_top2;
    }

    public TopDatas getTopsData(PairExchangeData pairExchangeData) throws Exception {
        if( m_top == null ){
            m_top = requestTopsData(pairExchangeData);
        }
        return m_top;
    }

    public OrdersData getLiveOrdersState(SharedExchangeData shExchData) throws Exception {
        Exchange exchange = shExchData.m_exchange;
        return getLiveOrders(exchange);
    }

    public OrdersData getLiveOrders(Exchange exchange) throws Exception {
        int exchId = exchange.m_databaseId;
        OrdersData data;
        if(m_liveOrders == null) {
            m_liveOrders = new HashMap<Integer, OrdersData>();
            data = null;
        } else {
            data = m_liveOrders.get(exchId);
        }
        if(data == null) {
            data = Fetcher.fetchOrders(exchange, null);
            m_liveOrders.put(exchId, data);
        }
        return data;
    }

    private TopDatas requestTopsData(PairExchangeData pairExchangeData) throws Exception {
        SharedExchangeData sharedExch1data = pairExchangeData.m_sharedExch1;
        SharedExchangeData sharedExch2data = pairExchangeData.m_sharedExch2;

        // load top mkt data
        long millis0 = System.currentTimeMillis();
        TopData top1 = sharedExch1data.fetchTopOnce();
        long top1Millis = System.currentTimeMillis();
        TopData top2 = sharedExch2data.fetchTopOnce();
        long top2Millis = System.currentTimeMillis();
//        log("  loaded in " + (top1Millis - millis0) + " and " + (top2Millis - top1Millis) + " ms");
        logTop(sharedExch1data.m_exchange, top1);
        logTop(sharedExch2data.m_exchange, top2);

        TopDatas ret = new TopDatas(top1, top2);
        pairExchangeData.onTopsLoaded(ret);
        return ret;
    }

    public void delay(long millis) {
        m_nextIterationDelay = millis;
    }

    public void queryAccountsData(ForkData forkData) throws Exception {
        if (!m_accountRequested) {
            forkData.queryAccountsData();
            m_accountRequested = true;
        }
    }

    public void onOrderFilled(Exchange exchange, OrderData orderData, CrossData crossData) {
        if (m_recorder != null) {
            m_recorder.recordOrderFilled(exchange, orderData, crossData);
        }
    }

    private void onNewTrades(Exchange exchange, Map<Pair, TradesData> data) {
        if (m_recorder != null) {
            m_recorder.recordTrades(exchange, data);
        }
    }

    @Override public Map<Pair, TradesData> fetchTrades(Exchange exchange) {
        Map<Pair, TradesData> ret = new HashMap<Pair, TradesData>();
        TradesData trades = Fetcher.fetchTradesOnce(exchange);
        ret.put(Pair.BTC_EUR, trades);
        return ret;
    }

    public Map<Pair, TradesData> getNewTradesData(Exchange exchange, TradesData.ILastTradeTimeHolder holder) throws Exception {
        return m_newTrades.getNewTradesData(this, exchange, holder);
    }

    static void logTop(Exchange exch, TopData top) {
        if (top == null) {
            log("NO top data for '" + exch.m_name + "'\t\t");
        } else {
//            log(exch.m_name +
//                    ": bid: " + format(top.m_bid) +
//                    ", ask: " + format(top.m_ask) +
//                    ", last: " + format(top.m_last) +
//                    ", bid_ask_dif: " + format(top.m_ask - top.m_bid) +
//                    "\t\t");
        }
    }

    public interface IRecorder {
        void recordOrderFilled(Exchange exchange, OrderData orderData, CrossData crossData);
        void recordTrades(Exchange exchange, Map<Pair, TradesData> data);
    }
} // IterationContext
