package bthdg.exch;

import bthdg.AccountData;

public class PlaceOrderData {
    public String m_error;
    public long m_orderId;
    public double m_remains;
    public double m_received;
    public AccountData m_accountData;

    public PlaceOrderData(long orderId, double remains, double received, AccountData accountData) {
        m_orderId = orderId;
        m_remains = remains;
        m_received = received;
        m_accountData = accountData;
    }

    public PlaceOrderData(String error) {
        m_error = error;
    }

    @Override public String toString() {
        return "PlaceOrderData{" +
                (m_error != null
                        ? "error='" + m_error
                        : ", orderId=" + m_orderId +
                          ", remains=" + m_remains +
                          ", received=" + m_received +
                          ", accountData=" + m_accountData
                ) +
                '}';
    }
}
