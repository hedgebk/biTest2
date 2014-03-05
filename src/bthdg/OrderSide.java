package bthdg;

public enum OrderSide {
    BUY {
        @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice >= mktPrice; }
        @Override public OrderSide opposite() { return SELL; }
        @Override public double mktPrice(TopData top) { return top.m_ask; }
        @Override public boolean allocate(AccountData accountData, OrderData orderData) { return accountData.allocateUsd(orderData); }
        @Override public void release(AccountData accountData, OrderData orderData) { accountData.releaseUsd(orderData); }
        @Override public void releaseTrade(AccountData accountData, double price, double amount) {accountData.releaseUsd(price, amount);}
    },
    SELL {
        @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice <= mktPrice; }
        @Override public OrderSide opposite() { return BUY; }
        @Override public double mktPrice(TopData top) { return top.m_bid; }
        @Override public boolean allocate(AccountData accountData, OrderData orderData) { return accountData.allocateBtc(orderData); }
        @Override public void release(AccountData accountData, OrderData orderData) { accountData.releaseBtc(orderData); }
        @Override public void releaseTrade(AccountData accountData, double price, double amount) {accountData.releaseBtc(price, amount);}
    };

    public boolean acceptPrice(double orderPrice, double mktPrice) { return false; }
    public OrderSide opposite() { return null; }
    public double mktPrice(TopData top) { return 0; } // ASK > BID
    public boolean allocate(AccountData accountData, OrderData orderData) { return false; }
    public void release(AccountData accountData, OrderData orderData) {}
    public void releaseTrade(AccountData accountData, double price, double amount) {}
} // OrderSide
