package bthdg;

public class TradeData {
    public double m_amount;
    public final double m_price;
    public final long m_timestamp;
    public final long m_tid;
    public final TradesData.TradeType m_type;
    public final OrderSide m_orderSide;
    public final int m_exchId;
    public final long m_crossId;
    public final long m_forkId;

    public TradeData(double amount, double price, long timestamp, long tid, TradesData.TradeType type) {
        this(amount, price, timestamp, tid, type, null, 0, 0, 0);
    }

    public TradeData(double amount, double price, long timestamp, long tid, TradesData.TradeType type, OrderSide orderSide,
                     int exchId, long crossId, long forkId) {
        m_amount = amount;
        m_price = price;
        m_timestamp = timestamp;
        m_tid = tid;
        m_type= type;
        m_orderSide = orderSide;
        m_exchId = exchId;
        m_crossId = crossId;
        m_forkId = forkId;
    }

    @Override public String toString() {
        return "TradeData{" +
                "amount=" + m_amount +
                ", price=" + m_price +
                ", timestamp=" + m_timestamp +
                ", tid=" + m_tid +
                ", type=" + m_type +
                ", orderSide=" + m_orderSide +
                ", exchId=" + m_exchId +
                ", crossId=" + m_crossId +
                ", forkId=" + m_forkId +
                '}';
    }
}
