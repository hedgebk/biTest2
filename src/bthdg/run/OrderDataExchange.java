package bthdg.run;

import bthdg.exch.Exchange;
import bthdg.exch.OrderData;

public class OrderDataExchange {
    public final OrderData m_orderData;
    public final Exchange m_exchange;

    OrderDataExchange(OrderData orderData, Exchange exchange) {
        m_orderData = orderData;
        m_exchange = exchange;
    }

    @Override public String toString() {
        return "OrderDataExchange{" +
                "orderData=" + m_orderData +
                ", exchange=" + m_exchange +
                '}';
    }
}
