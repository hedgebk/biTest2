package bthdg.exch;

import bthdg.Exchange;
import bthdg.util.Utils;
import org.json.simple.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Deep book data */
public class DeepData {
    public final double JOIN_SMALL_QUOTES_THRESHOLD = 0.8;
    public final List<Deep> m_bids;
    public final List<Deep> m_asks;

    public DeepData(List<Deep> bids, List<Deep> asks) {
        m_bids = bids;
        m_asks = asks;
    }

    @Override public String toString() {
        return "DeepData{" +
                "bids=" + m_bids +
                ", asks=" + m_asks +
                '}';
    }

    public static DeepData create(JSONArray bids, JSONArray asks) {
        List<DeepData.Deep> bids_ = DeepData.Deep.parse(bids);
        List<DeepData.Deep> asks_ = DeepData.Deep.parse(asks);
        return new DeepData(bids_, asks_);
    }

    public TopDataAdapter getTopDataAdapter() {
        return new TopDataAdapter();
    }

    public Deep getAsk() {
        return m_asks.get(0);
    }

    public Deep getBid() {
        return m_bids.get(0);
    }

    public void joinSmallQuotes(Exchange exchange, Pair pair) {
        double minOrderSize = exchange.minOrderToCreate(pair) * JOIN_SMALL_QUOTES_THRESHOLD;
        joinSmallQuotes(minOrderSize, m_bids, exchange, pair, "bid");
        joinSmallQuotes(minOrderSize, m_asks, exchange, pair, "ask");
    }

    private void joinSmallQuotes(double minOrderSize, List<Deep> deeps, Exchange exchange, Pair pair, String side) {
        for (int i = 1; i < deeps.size(); i++) {
            Deep deep0 = deeps.get(0);
            double quote0size = deep0.m_size;
            if (quote0size < minOrderSize) {
                Deep deep1 = deeps.get(1);
                double quote1size = deep1.m_size;
                double quoteSize = quote0size + quote1size;
                deep1.m_size = quoteSize;
                System.out.println("joined small deep quote: " + side + " " + exchange + " " + pair +
                        " min=" + format8(minOrderSize) + ": 0-size=" + format8(quote0size) + "@" + deep0.m_price +
                        ", 1-size=" + format8(quote1size) + "@" + deep1.m_price +
                        " => size=" + format8(quoteSize));
                deeps.remove(0);
                i--;
            } else {
                break;
            }
        }
    }

    private String format8(double minOrderSize) {
        return Utils.X_YYYYYYYY.format(minOrderSize);
    }

    public static class Deep {
        public final double m_price;
        public double m_size;

        public Deep(double price, double size) {
            m_price = price;
            m_size = size;
        }

        public static List<Deep> parse(JSONArray array) {
            int len = array.size();
            List<Deep> ret = new ArrayList<Deep>(len);
            for (int i = 0; i < len; i++) {
                JSONArray arr = (JSONArray) array.get(i);
                double price = Utils.getDouble(arr.get(0));
                double size = Utils.getDouble(arr.get(1));
                Deep deep = new Deep(price, size);
                ret.add(deep);
            }
            return ret;  //To change body of created methods use File | Settings | File Templates.
        }

        @Override public String toString() {
            return "Deep{" +
                    "price=" + m_price +
                    ", size=" + m_size +
                    '}';
        }
    }

    public class TopDataAdapter extends TopData {
        public TopDataAdapter() {
            super(getBid().m_price, getAsk().m_price, 0);
        }
    }
}
