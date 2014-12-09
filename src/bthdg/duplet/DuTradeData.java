package bthdg.duplet;

import bthdg.exch.OrderSide;
import bthdg.exch.TradeData;
import bthdg.exch.TradesData;
import bthdg.util.Utils;

public class DuTradeData extends TradeData {
    public final long m_crossId; // TODO: eliminate - should reside in some wrapper
    public final long m_forkId;  // TODO: eliminate - should reside in some wrapper

    public DuTradeData(double amount, double price, long timestamp, long tid, TradesData.TradeType type, OrderSide orderSide,
                     int exchId, long crossId, long forkId) {
        super( amount, price, timestamp, tid, type, orderSide, exchId );
        m_crossId = crossId;
        m_forkId = forkId;
    }

    @Override public String toString() {
        return "TradeData{" +
                "amount=" + Utils.format5(m_amount) +
                ", price=" + Utils.format5(m_price) +
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
