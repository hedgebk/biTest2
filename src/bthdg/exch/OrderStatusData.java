package bthdg.exch;

import java.util.HashMap;

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

    public OrdersData toOrdersData() {
        if (m_error == null) {
            HashMap<String, OrdersData.OrdData> ords = new HashMap<String, OrdersData.OrdData>();
            ords.put(m_ordData.m_orderId, m_ordData);
            return new OrdersData(ords);
        }
        return new OrdersData(m_error);
    }
}
