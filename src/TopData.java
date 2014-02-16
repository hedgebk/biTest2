public class TopData {
    public final double m_bid; // ASK > BID
    public final double m_ask;
    public final double m_last;
    public boolean m_live; // true if we got data from the last request, or false if obsolete

    public String bidStr() { return Utils.XX_YYYY.format(m_bid); }
    public String askStr() { return Utils.XX_YYYY.format(m_ask); }
    public String lastStr() { return Utils.XX_YYYY.format(m_last); }
    public double getMid() { return (m_ask + m_bid) / 2; }

    public void setObsolete() { m_live = true; }

    public static boolean isLive(TopData top) { return (top != null) && top.m_live; }

    public TopData(String bid, String ask, String last) {
        this(Double.parseDouble(bid), Double.parseDouble(ask), Double.parseDouble(last));
    }

    public TopData(double bid, double ask, double last) {
        m_bid = bid;
        m_ask = ask;
        m_last = last;
        m_live = true;
    }

    @Override public String toString() {
        return "TopData{" +
                toStringX() +
                '}';
    }

    String toStringX() {
        return (m_live ? "" : "OBSOLETE, ") +
                "bid=" + bidStr() +
                ", ask=" + askStr() +
                ", last=" + lastStr();
    }

    static TopDataEx calcDiff(TopData top1, TopData top2) {
        if( (top1 != null) && (top2 != null)) {
            return new TopDataEx(top1.m_bid - top2.m_bid, top1.m_ask - top2.m_ask, top1.m_last - top2.m_last,
                                       ((top1.m_bid + top1.m_ask) - (top2.m_bid + top2.m_ask))/2 );
        } else {
            return null;
        }
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
                    toStringX() +
                    ", mid=" + midStr() +
                    '}';
        }
    }
}
