package bthdg.exch;

import java.math.RoundingMode;

public enum OrderSide {
    BUY("B", "buy") {
        @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice >= mktPrice; }
        @Override public OrderSide opposite() { return SELL; }
        @Override public double mktPrice(TopData top) { return top.m_ask; }
        @Override public double pegPrice(TopData top, Double step, Double minStep) {
            double pegStep = minPriceStep(step);
            double pegPrice = top.m_bid + pegStep;
            if (pegPrice >= top.m_ask) {
                double exchStep = minPriceStep(minStep);
                pegPrice = top.m_bid + exchStep;
                if (pegPrice >= top.m_ask) {
                    pegPrice = top.m_bid;
                }
            }
            return pegPrice;
        }
        @Override public boolean isBuy() { return true; }
        @Override public RoundingMode getPegRoundMode() { return RoundingMode.FLOOR; }
        @Override public RoundingMode getMktRoundMode() { return RoundingMode.CEILING; }
        @Override public DeepData.Deep getDeep(DeepData deeps) { return deeps.getAsk(); }
    },
    SELL("S", "sell") {
        @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice <= mktPrice; }
        @Override public OrderSide opposite() { return BUY; }
        @Override public double mktPrice(TopData top) { return top.m_bid; }
        @Override public double pegPrice(TopData top, Double step, Double minStep) {
            double pegStep = minPriceStep(step);
            double pegPrice = top.m_ask - pegStep;
            if (pegPrice <= top.m_bid) {
                double exchStep = minPriceStep(minStep);
                pegPrice = top.m_ask - exchStep;
                if (pegPrice <= top.m_bid) {
                    pegPrice = top.m_ask;
                }
            }
            return pegPrice;
        }
        @Override public boolean isBuy() { return false; }
        @Override public RoundingMode getPegRoundMode() { return RoundingMode.CEILING; }
        @Override public RoundingMode getMktRoundMode() { return RoundingMode.FLOOR; }
        @Override public DeepData.Deep getDeep(DeepData deeps) { return deeps.getBid(); }
    };

    public static final double MIN_PRICE_PRECISION = 0.01;

    private static double minPriceStep(Double step) { return (step == null) ? MIN_PRICE_PRECISION : step; }

    public final String m_char;
    public final String m_name;

    OrderSide(String s, String name) {
        m_char = s;
        m_name = name;
    }

    public boolean acceptPrice(double orderPrice, double mktPrice) { return false; }
    public OrderSide opposite() { return null; }
    public double mktPrice(TopData top) { return 0; } // ASK > BID
    public double pegPrice(TopData top, Double step, Double minStep) { return 0; }
    public boolean isBuy() { return false; }
    public DeepData.Deep getDeep(DeepData deeps) { return null; }
    public RoundingMode getPegRoundMode() { return null; }
    public RoundingMode getMktRoundMode() { return null; }

    public static OrderSide getByCode(String str) {
        if (str == null) {
            return null;
        }
        for (OrderSide orderSide : values()) {
            if (orderSide.m_char.equals(str)) {
                return orderSide;
            }
        }
        throw new RuntimeException("non supported OrderSide '" + str + "'");
    }

    public static OrderSide getByName(String str) {
        if (str == null) {
            return null;
        }
        for (OrderSide orderSide : values()) {
            if (orderSide.m_name.equals(str)) {
                return orderSide;
            }
        }
        throw new RuntimeException("non supported OrderSide '" + str + "'");
    }
} // OrderSide
