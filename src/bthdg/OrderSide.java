package bthdg;

import bthdg.exch.TopData;

public enum OrderSide {
    BUY("B", "buy") {
        @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice >= mktPrice; }
        @Override public OrderSide opposite() { return SELL; }
        @Override public double mktPrice(TopData top) { return top.m_ask; }
        @Override public double pegPrice(TopData top, Double step) {
            double pegPrice = top.m_bid + minPriceStep(step); // bid and ask can be VERY close - peg will run out of mkt bid/ask bounds - adjust
            return (pegPrice >= top.m_ask) ? top.getMid() : pegPrice;
        }
        @Override public boolean isBuy() { return true; }
    },
    SELL("S", "sell") {
        @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice <= mktPrice; }
        @Override public OrderSide opposite() { return BUY; }
        @Override public double mktPrice(TopData top) { return top.m_bid; }
        @Override public double pegPrice(TopData top, Double step) {
            double pegPrice = top.m_ask - minPriceStep(step); // bid and ask can be VERY close - peg will run out of mkt bid/ask bounds - adjust
            return (pegPrice <= top.m_bid) ? top.getMid() : pegPrice;
        }
        @Override public boolean isBuy() { return false; }
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
    public double pegPrice(TopData top, Double step) { return 0; }
    public boolean isBuy() { return false; }

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
