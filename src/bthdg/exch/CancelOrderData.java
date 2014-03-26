package bthdg.exch;

import bthdg.AccountData;

public class CancelOrderData {
    public String m_error;
    public long m_orderId;
    public AccountData m_funds;

    public CancelOrderData(String error) {
        m_error = error;
    }

    public CancelOrderData(long orderId, AccountData funds) {
        m_orderId = orderId;
        m_funds = funds;
    }

    @Override public String toString() {
        return "CancelOrderData{" +
                ( m_error != null
                    ? "error=" + m_error
                    : "orderId=" + m_orderId +
                      ", funds=" + m_funds
                ) +
                '}';
    }
}
