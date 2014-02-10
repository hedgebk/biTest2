import java.util.ArrayList;
import java.util.List;

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

    public TradesData newTrades(Long lastProcessedTradesTime1) {
        if( lastProcessedTradesTime1 == null ) {
            return this;
        }
        List<TradeData> newTrades = new ArrayList<TradeData>();
        for(TradeData trade: m_trades) {
            long timestamp = trade.m_timestamp;
            if(timestamp > lastProcessedTradesTime1) {
                newTrades.add(trade);
            }
        }

        return new TradesData(newTrades);
    }

    public static class TradeData {
        public final double m_amount;
        public final double m_price;
        public final long m_timestamp;
        public final long m_tid;
        public final TradeType m_type;

        public TradeData(double amount, double price, long timestamp, long tid, TradeType type) {
            m_amount = amount;
            m_price = price;
            m_timestamp = timestamp;
            m_tid = tid;
            m_type= type;
        }

        @Override public String toString() {
            return "TradeData{" +
                    "amount=" + m_amount +
                    ", price=" + m_price +
                    ", timestamp=" + m_timestamp +
                    ", tid=" + m_tid +
                    ", type=" + m_type +
                    '}';
        }
    }

    public enum TradeType {
        BID("bid"),
        ASK("ask");

        private final String m_typeStr;

        TradeType(String typeStr) {
            m_typeStr = typeStr;
        }

        public static TradeType get(String typeStr) {
            for (TradeType tradeType : values()) {
                if(tradeType.m_typeStr.equals(typeStr)) {
                    return tradeType;
                }
            }
            throw new RuntimeException("non supported trade type '"+typeStr+"'");
        }
    }
}
