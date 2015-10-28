package bthdg.exch;

public class OrderStatusData {
    private OrdersData.OrdData m_ordData;
    public String m_error;

    public OrderStatusData(String error) {
        m_error = error;
    }

    public OrderStatusData(OrdersData.OrdData ord) {
        m_ordData = ord;
    }

    @Override public String toString() {
        return "OrderStatusData{" +
                ( m_error != null
                    ? "error=" + m_error
                    : "ordData=" + m_ordData
                ) +
                '}';
    }
}
