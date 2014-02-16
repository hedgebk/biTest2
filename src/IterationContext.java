import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class IterationContext {
    public TopDatas m_top;
    public Map<Integer, LiveOrdersData> m_liveOrders;
    public long m_nextIterationDelay = 1000; // 1 sec by def
    private Map<Integer, TradesData> m_newTrades;
    public boolean m_acceptPriceSimulated;

    public TopDatas getTopsData(ExchangesData exchangesData) throws Exception {
        if( m_top == null ){
            m_top = requestTopsData(exchangesData);
        }
        return m_top;
    }

    public LiveOrdersData getLiveOrdersState(ExchangeData exchangeData) {
        int exchId = exchangeData.m_exch.m_databaseId;
        LiveOrdersData data;
        if(m_liveOrders == null) {
            m_liveOrders = new HashMap<Integer, LiveOrdersData>();
            data = null;
        } else {
            data = m_liveOrders.get(exchId);
        }
        if(data == null) {
            data = exchangeData.fetchLiveOrders();
            m_liveOrders.put(exchId, data);
        }
        return data;
    }

    public TradesData getNewTradesData(ExchangeData exchData) throws Exception {
        int exchId = exchData.exchId();
        TradesData data;
        if (m_newTrades == null) {
            m_newTrades = new HashMap<Integer, TradesData>();
            data = null;
        } else {
            data = m_newTrades.get(exchId);
        }
        if (data == null) {
            long millis0 = System.currentTimeMillis();
            TradesData trades = exchData.fetchTrades();
            if(trades == null) {
                System.out.println(" NO trades loaded for '" + exchData.exchName() + "' this time" );
                data = new TradesData(new ArrayList<TradesData.TradeData>()); // empty
            } else {
                data = exchData.filterOnlyNewTrades(trades); // this will update last processed trade time
                long millis1 = System.currentTimeMillis();
                System.out.println(" loaded " + trades.size() + " trades for '" + exchData.exchName() + "' " +
                                   "in " + (millis1 - millis0) + " ms; new " + data.size() + " trades: " + data);
            }
            m_newTrades.put(exchId, data);
        }
        return data;
    }

    private TopDatas requestTopsData(ExchangesData exchangesData) throws Exception {
        ExchangeData exch1data = exchangesData.m_exch1data;
        ExchangeData exch2data = exchangesData.m_exch2data;

        // load top mkt data
        long millis0 = System.currentTimeMillis();
        TopData top1 = exch1data.fetchTopOnce();
        long top1Millis = System.currentTimeMillis();
        TopData top2 = exch2data.fetchTopOnce();
        long top2Millis = System.currentTimeMillis();
        System.out.println("  loaded in " + (top1Millis - millis0) + " and " + (top2Millis - top1Millis) + " ms");
        Fetcher.log(exch1data.m_exch, top1);
        Fetcher.log(exch2data.m_exch, top2);
        System.out.println();

        TopDatas ret = new TopDatas(top1, top2);
        exchangesData.onTopsLoaded(ret);
        return ret;
    }

    public void delay(long millis) {
        m_nextIterationDelay = millis;
    }
} // IterationContext
