package bthdg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class IterationContext {
    private IRecorder m_recorder;
    public TopDatas m_top;
    public Map<Integer, LiveOrdersData> m_liveOrders;
    public long m_nextIterationDelay = 1000; // 1 sec by def
    private Map<Integer, TradesData> m_newTrades;
    public boolean m_acceptPriceSimulated;
    public boolean m_accountRequested;

    private static void log(String s) { Log.log(s); }

    public IterationContext(IRecorder recorder) {
        m_recorder = recorder;
    }

    public TopDatas getTopsData(PairExchangeData pairExchangeData) throws Exception {
        if( m_top == null ){
            m_top = requestTopsData(pairExchangeData);
        }
        return m_top;
    }

    public LiveOrdersData getLiveOrdersState(SharedExchangeData shExchData) {
        int exchId = shExchData.m_exchange.m_databaseId;
        LiveOrdersData data;
        if(m_liveOrders == null) {
            m_liveOrders = new HashMap<Integer, LiveOrdersData>();
            data = null;
        } else {
            data = m_liveOrders.get(exchId);
        }
        if(data == null) {
            data = shExchData.fetchLiveOrders();
            m_liveOrders.put(exchId, data);
        }
        return data;
    }

    public TradesData getNewTradesData(SharedExchangeData shExchData) {
        Exchange exch = shExchData.m_exchange;
        int exchId = exch.m_databaseId;
        TradesData data;
        if (m_newTrades == null) {
            m_newTrades = new HashMap<Integer, TradesData>();
            data = null;
        } else {
            data = m_newTrades.get(exchId);
        }
        if (data == null) {
            long millis0 = System.currentTimeMillis();
            TradesData trades = Fetcher.fetchTradesOnce(exch);
            String exchName = exch.m_name;
            if(trades == null) {
                log(" NO trades loaded for '" + exchName + "' this time");
                data = new TradesData(new ArrayList<TradeData>()); // empty
            } else {
                data = shExchData.filterOnlyNewTrades(trades); // this will update last processed trade time
                long millis1 = System.currentTimeMillis();
                int size = trades.size();
                log(" loaded " + size + " trades for '" + exchName + "' " +
                        "in " + (millis1 - millis0) + " ms; new " + data.size() + " trades: " + data);
                if (size > 0) {
                    onNewTrades(shExchData, data);
                }
            }
            m_newTrades.put(exchId, data);
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
        Fetcher.logTop(sharedExch1data.m_exchange, top1);
        Fetcher.logTop(sharedExch2data.m_exchange, top2);

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

    public void onOrderFilled(SharedExchangeData shExchData, OrderData orderData, CrossData crossData) {
        if (m_recorder != null) {
            m_recorder.recordOrderFilled(shExchData, orderData, crossData);
        }
    }

    private void onNewTrades(SharedExchangeData shExchData, TradesData data) {
        if (m_recorder != null) {
            m_recorder.recordTrades(shExchData, data);
        }
    }


    public interface IRecorder {
        void recordOrderFilled(SharedExchangeData shExchData, OrderData orderData, CrossData crossData);
        void recordTrades(SharedExchangeData shExchData, TradesData data);
    }
} // IterationContext
