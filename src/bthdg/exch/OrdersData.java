package bthdg.exch;

import bthdg.OrderSide;
import bthdg.Pair;

import java.util.List;

public class OrdersData {
    public String m_erorr;
    public List<OrdData> m_ords;

    public OrdersData(String error) {
        m_erorr = error;
    }

    public OrdersData() {
    }

    public OrdersData(List<OrdData> ords) {
        m_ords = ords;
    }

    @Override public String toString() {
        return "OrdersData{" +
                (m_erorr != null
                    ? "erorr='" + m_erorr
                    : "ords=" + m_ords
                ) +
                '}';
    }

    public static class OrdData {
        public String m_orderId;
        public double m_amount;
        public double m_rate;
        public long m_createTime;
        public long m_status;
        public Pair m_pair;
        public OrderSide m_orderSide;

        public OrdData(String orderId, double amount, double rate, long createTime, long status, Pair pair, OrderSide orderSide) {
            m_orderId = orderId;
            m_amount = amount;
            m_rate = rate;
            m_createTime = createTime;
            m_status = status;
            m_pair = pair;
            m_orderSide = orderSide;
        }

        @Override public String toString() {
            return "OrdData{" +
                    "orderId='" + m_orderId + '\'' +
                    ", orderSide=" + m_orderSide +
                    ", amount=" + m_amount +
                    ", rate=" + m_rate +
                    ", pair=" + m_pair +
                    ", createTime=" + m_createTime +
                    ", status=" + m_status +
                    '}';
        }
    }
}
