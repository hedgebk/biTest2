public enum OrderSide {
    BUY {
        @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice >= mktPrice; }
        @Override public OrderSide opposite() { return SELL; }
        @Override public double mktPrice(TopData top) { return top.m_ask; }
    },
    SELL {
        @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice <= mktPrice; }
        @Override public OrderSide opposite() { return BUY; }
        @Override public double mktPrice(TopData top) { return top.m_bid; }
    };

    public boolean acceptPrice(double orderPrice, double mktPrice) { return false; }
    public OrderSide opposite() { return null; }
    public double mktPrice(TopData top) { return 0; } // ASK > BID
} // OrderSide
