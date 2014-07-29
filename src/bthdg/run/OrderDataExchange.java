package bthdg.run;

import bthdg.Log;
import bthdg.exch.AccountData;
import bthdg.exch.Exchange;
import bthdg.exch.OrderData;
import bthdg.exch.OrderStatus;

public class OrderDataExchange {
    public final OrderData m_orderData;
    public final Exchange m_exchange;
    public final AccountData m_account;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

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

    public static OrderDataExchange split(OrderDataExchange ode, double splitAmount) {
        if( ode == null ) {
            return null;
        }
        OrderData orderData = ode.m_orderData.split(splitAmount);
        orderData.m_status = OrderStatus.FILLED;
        return new OrderDataExchange(orderData, ode.m_exchange, ode.m_account);
    }

    public boolean cancelOrder() throws Exception {
        log("cancel order: " + this);
        String error = m_account.cancelOrder(m_orderData);
        if (error == null) {
            return true;
        } else {
            log("error in cancel order: " + error + "; " + m_orderData);
            return false;
        }
    }
}
