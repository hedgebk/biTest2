package bthdg.exch;

import java.util.Map;

public class OrdersData {
    public String m_error;
    public Map<String,OrdersData.OrdData> m_ords;

    public OrdersData(String error) {
        m_error = error;
    }

    public OrdersData() {
    }

    public OrdersData(Map<String,OrdData> ords) {
        m_ords = ords;
    }

    @Override public String toString() {
        return "OrdersData{" +
                (m_error != null
                    ? "error='" + m_error
                    : "ords=" + m_ords
                ) +
                '}';
    }

    public OrdData getOrderData(String orderId) {
        return m_ords.get(orderId);
    }

    public static class OrdData {
        public String m_orderId;
        public double m_orderAmount; // not all exchanges supports
        public double m_remainedAmount;
        public double m_rate;
        public long m_createTime; // not all exchanges supports
        public String m_status;
        public Pair m_pair;
        public OrderSide m_orderSide;

        public OrdData(String orderId, double orderAmount, double remainedAmount, double rate, long createTime, String status, Pair pair, OrderSide orderSide) {
            m_orderId = orderId;
            m_orderAmount = orderAmount;
            m_remainedAmount = remainedAmount;
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
                    ", orderAmount=" + m_orderAmount +
                    ", remainedAmount=" + m_remainedAmount +
                    ", rate=" + m_rate +
                    ", pair=" + m_pair +
                    ", createTime=" + m_createTime +
                    ", status=" + m_status +
                    '}';
        }

        public OrderData toOrderData() {
            OrderData orderData = new OrderData(m_pair, m_orderSide, m_rate, m_orderAmount);
            orderData.m_orderId = m_orderId;
            return orderData;
        }
    }
}
