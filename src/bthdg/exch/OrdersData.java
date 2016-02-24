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
        public double m_filledAmount;
        public double m_remainedAmount;
        public double m_rate;
        public long m_createTime; // not all exchanges supports
        public String m_status;
        public Pair m_pair;
        public OrderSide m_orderSide;
        public OrderType m_orderType;

        public OrderStatus m_orderStatus;
        public double m_avgPrice;

        public OrdData(String orderId, double orderAmount, double filledAmount, double remainedAmount, double rate,
                       long createTime, String status, Pair pair, OrderSide orderSide) {
            this(orderId, orderAmount, filledAmount, remainedAmount, rate, createTime, status, pair, orderSide, OrderType.LIMIT);
        }

        public OrdData(String orderId, double orderAmount, double filledAmount, double remainedAmount, double rate,
                       long createTime, String status, Pair pair, OrderSide orderSide, OrderType orderType) {
            m_orderId = orderId;
            m_orderAmount = orderAmount;
            m_filledAmount = filledAmount;
            m_remainedAmount = remainedAmount;
            m_rate = rate;
            m_createTime = createTime;
            m_status = status;
            m_pair = pair;
            m_orderSide = orderSide;
            m_orderType = orderType;
        }

        @Override public String toString() {
            return "OrdData{" +
                    "orderId='" + m_orderId + '\'' +
                    ", status=" + m_status +
                    ", pair=" + m_pair +
                    ", orderSide=" + m_orderSide +
                    ", orderType=" + m_orderType +
                    ", orderAmount=" + m_orderAmount +
                    ", rate=" + m_rate +
                    ", filledAmount=" + m_filledAmount +
                    ", remainedAmount=" + m_remainedAmount +
                    ", createTime=" + m_createTime +
                    ((m_orderStatus == null) ? "" : " " + m_orderStatus) +
                    ", avgPrice=" + m_avgPrice +
                    '}';
        }

        public OrderData toOrderData() {
            OrderData orderData = new OrderData(m_pair, m_orderType, m_orderSide, m_rate, m_orderAmount);
            orderData.m_orderId = m_orderId;
            return orderData;
        }
    }
}
