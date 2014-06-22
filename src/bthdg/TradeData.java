package bthdg;

import bthdg.exch.OrderSide;
import bthdg.exch.TradesData;
import bthdg.util.Utils;

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
                "amount=" + Utils.X_YYYYY.format(m_amount) +
                ", price=" + Utils.X_YYYYY.format(m_price) +
                ", time=" + m_timestamp +
                ", tid=" + m_tid +
                ", type=" + m_type +
                ((m_orderSide != null) ? ", orderSide=" + m_orderSide : "") +
                ((m_exchId != 0) ? ", exchId=" + m_exchId : "") +
                ((m_crossId != 0) ? ", crossId=" + m_crossId : "") +
                ((m_forkId != 0) ? ", forkId=" + m_forkId : "") +
                '}';
    }
}
