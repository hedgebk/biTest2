package bthdg.exch;

// execution report
public class Exec {
    public final String m_orderId;
    public final OrderStatus m_orderStatus;
    public final double m_averagePrice;

    public Exec(String orderId, OrderStatus orderStatus, double averagePrice) {
        m_orderId = orderId;
        m_orderStatus = orderStatus;
        m_averagePrice = averagePrice;
    }

    @Override public String toString() {
        return "Exec[orderId=" + m_orderId + "; orderStatus=" + m_orderStatus + ", averagePrice=" + m_averagePrice + "]";
    }
}
