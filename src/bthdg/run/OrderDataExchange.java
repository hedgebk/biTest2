package bthdg.run;

import bthdg.exch.AccountData;
import bthdg.exch.Exchange;
import bthdg.exch.OrderData;

public class OrderDataExchange {
    public final OrderData m_orderData;
    public final Exchange m_exchange;
    public final AccountData m_account;

    public double getBalance() { return m_orderData.getBalance(); }

    OrderDataExchange(OrderData orderData, Exchange exchange, AccountData account) {
        m_orderData = orderData;
        m_exchange = exchange;
        m_account = account;
    }

    @Override public String toString() {
        return "OrderDataExchange{" +
                "orderData=" + m_orderData +
                ", exchange=" + m_exchange +
                '}';
    }
}
