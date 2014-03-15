package bthdg;

public enum OrderSide {
    BUY("B") {
        @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice >= mktPrice; }
        @Override public OrderSide opposite() { return SELL; }
        @Override public double mktPrice(TopData top) { return top.m_ask; }
        @Override public double pegPrice(TopData top) { return top.m_bid + MIN_PRICE_PRECISION; }
        @Override public boolean allocate(AccountData accountData, OrderData orderData) { return accountData.allocateUsd(orderData); }
        @Override public void release(AccountData accountData, OrderData orderData) { accountData.releaseUsd(orderData); }
        @Override public void releaseTrade(AccountData accountData, double price, double btcAmount) {accountData.releaseUsd(price, btcAmount);}
        @Override public boolean isBuy() { return true; }
    },
    SELL("S") {
        @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice <= mktPrice; }
        @Override public OrderSide opposite() { return BUY; }
        @Override public double mktPrice(TopData top) { return top.m_bid; }
        @Override public double pegPrice(TopData top) { return top.m_ask - MIN_PRICE_PRECISION; }
        @Override public boolean allocate(AccountData accountData, OrderData orderData) { return accountData.allocateBtc(orderData); }
        @Override public void release(AccountData accountData, OrderData orderData) { accountData.releaseBtc(orderData); }
        @Override public void releaseTrade(AccountData accountData, double price, double btcAmount) {accountData.releaseBtc(price, btcAmount);}
        @Override public boolean isBuy() { return false; }
    };

    public static final double MIN_PRICE_PRECISION = 0.01;

    public final String m_char;

    OrderSide(String s) {
        m_char = s;
    }

    public boolean acceptPrice(double orderPrice, double mktPrice) { return false; }
    public OrderSide opposite() { return null; }
    public double mktPrice(TopData top) { return 0; } // ASK > BID
    public double pegPrice(TopData top) { return 0; }
    public boolean allocate(AccountData accountData, OrderData orderData) { return false; }
    public void release(AccountData accountData, OrderData orderData) {}
    public void releaseTrade(AccountData accountData, double price, double btcAmount) {}
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
