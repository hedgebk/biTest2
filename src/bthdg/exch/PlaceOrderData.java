package bthdg.exch;

import bthdg.util.Utils;

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

    public PlaceOrderData(long orderId) {
        m_orderId = orderId;
    }

    public PlaceOrderData(String error) {
        m_error = error;
    }

    @Override public String toString() {
        return "PlaceOrderData{" +
                (m_error != null
                        ? "error='" + m_error + "'"
                        : "orderId=" + m_orderId +
                          ", remains=" + Utils.format8(m_remains) +
                          ", received=" + Utils.format8(m_received) +
                          ((m_accountData == null)
                                  ? ""
                                  : ", accountData=" + m_accountData)
                ) +
                '}';
    }

    public String toString(Exchange exchange, Pair m_pair) {
        return "PlaceOrderData{" +
                (m_error != null
                        ? "error='" + m_error + "'"
                        : "orderId=" + m_orderId +
                          ", remains=" + exchange.roundAmountStr(m_remains, m_pair) +
                          ", received=" + exchange.roundAmountStr(m_received, m_pair) +
                          ((m_accountData == null)
                                  ? ""
                                  : ", accountData=" + m_accountData)
                ) +
                '}';
    }
}
