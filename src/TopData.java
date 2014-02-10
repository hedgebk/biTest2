public class TopData {
    public final double m_bid; // ASK > BID
    public final double m_ask;
    public final double m_last;

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
                "bid=" + m_bid +
                ", ask=" + m_ask +
                ", last=" + m_last +
                '}';
    }

    public static class TopDataEx extends TopData {
        public final double m_mid;

        public TopDataEx(double bid, double ask, double last, double mid) {
            super(bid, ask, last);
            m_mid = mid;
        }

        @Override public String toString() {
            return "TopData{" +
                    "bid=" + m_bid +
                    ", ask=" + m_ask +
                    ", last=" + m_last +
                    ", mid=" + m_mid +
                    '}';
        }
    }
}
