package bthdg.exch;

import bthdg.Deserializer;
import bthdg.util.Utils;

import java.io.IOException;

public class TopData {
    public final double m_bid; // ASK > BID
    public final double m_ask;
    public final double m_last;
    public boolean m_live; // true if we got data from the last request, or false if obsolete

    public String bidStr() { return Utils.XX_YYYY.format(m_bid); }
    public String askStr() { return Utils.XX_YYYY.format(m_ask); }
    public String lastStr() { return Utils.XX_YYYY.format(m_last); }
    public double getMid() { return (m_ask + m_bid) / 2; }
    public double getBidAskDiff() { return m_ask - m_bid; } // ASK > BID

    public void setObsolete() { m_live = true; }

    public static boolean isLive(TopData top) { return (top != null) && top.m_live; }

    public TopData(double bid, double ask) {
        this(bid, ask, 0, true);
    }

    public TopData(double bid, double ask, double last) {
        this(bid, ask, last, true);
    }

    public TopData(String bid, String ask, String last) {
        this(Double.parseDouble(bid), Double.parseDouble(ask), Double.parseDouble(last));
    }

    private TopData(double bid, double ask, double last, boolean live) {
        m_bid = bid;
        m_ask = ask;
        m_last = last;
        m_live = live;
    }

    public String toString(Exchange exchange, Pair pair) {
        return "Top{" + toStringX(exchange, pair) + '}';
    }

    @Override public String toString() {
        return "Top{" + toStringX() + '}';
    }

    String toStringX(Exchange exchange, Pair pair) {
        return (m_live ? "" : "OBSOLETE, ") +
                "bid=" + exchange.roundPriceStr(m_bid, pair) +
                ", ask=" + exchange.roundPriceStr(m_ask, pair) +
                ", last=" + exchange.roundPriceStr(m_last, pair);
    }

    String toStringX() {
        return (m_live ? "" : "OBSOLETE, ") +
                "bid=" + bidStr() +
                ", ask=" + askStr() +
                ", last=" + lastStr();
    }

    public static TopDataEx calcDiff(TopData top1, TopData top2) {
        if( (top1 != null) && (top2 != null)) {
            return new TopDataEx(top1.m_bid - top2.m_bid, top1.m_ask - top2.m_ask, top1.m_last - top2.m_last,
                                       ((top1.m_bid + top1.m_ask) - (top2.m_bid + top2.m_ask))/2 );
        } else {
            return null;
        }
    }

    public void serialize(StringBuilder sb) {
        sb.append("Top[bid=").append(m_bid);
        sb.append("; ask=").append(m_ask);
        sb.append("; last=").append(m_last);
        sb.append("; live=").append(m_live);
        sb.append("; ]");
    }

    public static TopData deserialize(Deserializer deserializer) throws IOException {
        if( deserializer.readIf("; ")) {
            return null;
        }
        deserializer.readObjectStart("Top");
        double bid = readBid(deserializer);
        double ask = readAsk(deserializer);
        double last = readLast(deserializer);
        boolean live = readLive(deserializer);
        deserializer.readObjectEnd();
        deserializer.readStr("; ");

        return new TopData(bid, ask, last, live);
    }

    private static boolean readLive(Deserializer deserializer) throws IOException {
        deserializer.readPropStart("live");
        String liveStr = deserializer.readTill("; ");
        return Boolean.parseBoolean(liveStr);
    }

    private static double readLast(Deserializer deserializer) throws IOException {
        return readDouble(deserializer, "last");
    }

    private static double readAsk(Deserializer deserializer) throws IOException {
        return readDouble(deserializer, "ask");
    }

    private static double readBid(Deserializer deserializer) throws IOException {
        return readDouble(deserializer, "bid");
    }

    private static double readDouble(Deserializer deserializer, String key) throws IOException {
        deserializer.readPropStart(key);
        String value = deserializer.readTill("; ");
        return Double.parseDouble(value);
    }

    public void compare(TopData other) {
        if (m_bid != other.m_bid) {
            throw new RuntimeException("m_bid");
        }
        if (m_ask != other.m_ask) {
            throw new RuntimeException("m_ask");
        }
        if (m_last != other.m_last) {
            throw new RuntimeException("m_last");
        }
        if (m_live != other.m_live) {
            throw new RuntimeException("m_live");
        }
    }

    public static class TopDataEx extends TopData {
        public final double m_mid;

        public String midStr() { return Utils.XX_YYYY.format(m_mid); }

        public TopDataEx(double bid, double ask, double last, double mid) {
            this(bid, ask, last, true, mid);
        }

        public TopDataEx(double bid, double ask, double last, boolean live, double mid) {
            super(bid, ask, last, live);
            m_mid = mid;
        }

        @Override public String toString() {
            return "TopData{" +
                    toStringX() +
                    ", mid=" + midStr() +
                    '}';
        }

        public void serialize(StringBuilder sb) {
            sb.append("TopEx[bid=").append(m_bid);
            sb.append("; ask=").append(m_ask);
            sb.append("; last=").append(m_last);
            sb.append("; live=").append(m_live);
            sb.append("; mid=").append(m_mid);
            sb.append("; ]");
        }

        public static TopDataEx deserialize(Deserializer deserializer) throws IOException {
            if( deserializer.readIf("; ")) {
                return null;
            }
            deserializer.readObjectStart("TopEx");
            double bid = readBid(deserializer);
            double ask = readAsk(deserializer);
            double last = readLast(deserializer);
            boolean live = readLive(deserializer);
            double mid = readMid(deserializer);
            deserializer.readObjectEnd();
            deserializer.readStr("; ");

            return new TopDataEx(bid, ask, last, live, mid);
        }

        private static double readMid(Deserializer deserializer) throws IOException {
            return readDouble(deserializer, "mid");
        }

        public void compare(TopDataEx other) {
            super.compare(other);
            if (m_mid != other.m_mid) {
                throw new RuntimeException("m_mid");
            }
        }
    }
}
