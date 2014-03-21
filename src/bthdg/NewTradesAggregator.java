package bthdg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class NewTradesAggregator {
    private Map<Integer, Map<Pair, TradesData>> m_newTrades; // exchangeId -> <Piar -> TradesData>

    private static void log(String s) { Log.log(s); }
    protected void onNewTrades(Exchange exchange, Map<Pair, TradesData> data) { /*noop*/ }

    public Map<Pair, TradesData> getNewTradesData(IIterationContext iContext, Exchange exchange,
                                                  TradesData.ILastTradeTimeHolder holder) throws Exception {
        int exchId = exchange.m_databaseId;
        Map<Pair, TradesData> data;
        if (m_newTrades == null) {
            m_newTrades = new HashMap<Integer, Map<Pair, TradesData>>();
            data = null;
        } else {
            data = m_newTrades.get(exchId);
        }
        if (data == null) {
            Map<Pair, TradesData> tradesMap = iContext.fetchTrades(exchange);
            String exchName = exchange.m_name;
            if(tradesMap == null) {
                log(" NO trades loaded for '" + exchName + "' this time");
                data = new HashMap<Pair, TradesData>(); // empty
            } else {
                data = filterOnlyNewTrades(tradesMap, holder); // this will update last processed trade time

                logTradesLoaded("loaded trades num for '" + exchName + "': ", tradesMap);
                logTradesLoaded(" new trades :", data);

                onNewTrades(exchange, data);
            }
            m_newTrades.put(exchId, data);
        }
        return data;
    }

    private void logTradesLoaded(String prefix, Map<Pair, TradesData> tradesMap) {
        StringBuffer sb = new StringBuffer(prefix);
        for (Map.Entry<Pair, TradesData> entry : tradesMap.entrySet()) {
            sb.append(entry.getKey());
            sb.append(":");
            sb.append(entry.getValue().size());
            sb.append("; ");
        }
        log(sb.toString());
    }

    public static Map<Pair, TradesData> filterOnlyNewTrades(Map<Pair, TradesData> tradesMap, TradesData.ILastTradeTimeHolder holder) {
        long lastProcessedTradesTime = holder.lastProcessedTradesTime();
        Map<Pair, TradesData> ret = new HashMap<Pair, TradesData>();
        for (Map.Entry<Pair, TradesData> entry : tradesMap.entrySet()) {
            Pair pair = entry.getKey();
            TradesData trades = entry.getValue();
            TradesData newTrades = trades.newTrades(lastProcessedTradesTime);
            if (!newTrades.m_trades.isEmpty()) {
                for (TradeData trade : newTrades.m_trades) {
                    long timestamp = trade.m_timestamp;
                    if (timestamp > lastProcessedTradesTime) {
                        lastProcessedTradesTime = timestamp;
                    }
                }
            }
            ret.put(pair, newTrades);
        }
        holder.lastProcessedTradesTime(lastProcessedTradesTime);
        return ret;
    }
}
