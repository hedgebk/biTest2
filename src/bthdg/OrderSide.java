package bthdg;

import bthdg.exch.TopData;

public enum OrderSide {
    BUY("B") {
        @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice >= mktPrice; }
        @Override public OrderSide opposite() { return SELL; }
        @Override public double mktPrice(TopData top) { return top.m_ask; }
        @Override public double pegPrice(TopData top, Pair pair) { return top.m_bid + minPriceStep(pair); }
        @Override public boolean isBuy() { return true; }
    },
    SELL("S") {
        @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice <= mktPrice; }
        @Override public OrderSide opposite() { return BUY; }
        @Override public double mktPrice(TopData top) { return top.m_bid; }
        @Override public double pegPrice(TopData top, Pair pair) { return top.m_ask - minPriceStep(pair); }
        @Override public boolean isBuy() { return false; }
    };

    public static final double MIN_PRICE_PRECISION = 0.01;

    private static double minPriceStep(Pair pair) { return (pair == null) ? MIN_PRICE_PRECISION : pair.m_minPriceStep; }

    public final String m_char;

    OrderSide(String s) {
        m_char = s;
    }

    public boolean acceptPrice(double orderPrice, double mktPrice) { return false; }
    public OrderSide opposite() { return null; }
    public double mktPrice(TopData top) { return 0; } // ASK > BID
    public double pegPrice(TopData top, Pair pair) { return 0; }
    public boolean isBuy() { return false; }

    public static OrderSide get(String str) {
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
} // OrderSide
