public class TopData {
    public final double m_bid; // ASK > BID
    public final double m_ask;
    public final double m_last;

    public String bidStr() { return Utils.XX_YYYY.format(m_bid); }
    public String askStr() { return Utils.XX_YYYY.format(m_ask); }
    public String lastStr() { return Utils.XX_YYYY.format(m_last); }

    public TopData(String bid, String ask, String last) {
        this(Double.parseDouble(bid), Double.parseDouble(ask), Double.parseDouble(last));
    }

    public TopData(double bid, double ask, double last) {
        m_bid = bid;
        m_ask = ask;
        m_last = last;
    }

    @Override public String toString() {
        return "TopData{" +
                "bid=" + bidStr() +
                ", ask=" + askStr() +
                ", last=" + m_last +
                '}';
    }

    public static class TopDataEx extends TopData {
        public final double m_mid;

        public String midStr() { return Utils.XX_YYYY.format(m_mid); }

        public TopDataEx(double bid, double ask, double last, double mid) {
            super(bid, ask, last);
            m_mid = mid;
        }

        @Override public String toString() {
            return "TopData{" +
                    "bid=" + bidStr() +
                    ", ask=" + askStr() +
                    ", last=" + lastStr() +
                    ", mid=" + midStr() +
                    '}';
        }
    }
}
