package bthdg.exch;

import java.util.ArrayList;
import java.util.List;

// todo: make it extends List<TradeData>
public class TradesData {
    public final List<TradeData> m_trades;

    public TradesData(List<TradeData> trades) {
        m_trades = trades;
    }

    @Override public String toString() {
        return "TradesData{" +
                "trades=" + m_trades +
                '}';
    }

    public int size() { return m_trades.size(); }

    public TradesData newTrades(Long lastProcessedTradesTime) {
        if (lastProcessedTradesTime == null) {
            return this;
        }
        List<TradeData> newTrades = new ArrayList<TradeData>();
        for (TradeData trade : m_trades) {
            long timestamp = trade.m_timestamp;
            if (timestamp > lastProcessedTradesTime) {
                newTrades.add(trade);
            }
        }
        return new TradesData(newTrades);
    }

    public interface ILastTradeTimeHolder {
        long lastProcessedTradesTime();
        void lastProcessedTradesTime(long lastProcessedTradesTime);
    }
}
