package bthdg.exch;

import bthdg.Utils;
import org.json.simple.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class DeepData {
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

    public static class Deep {
        public final double m_price;
        public final double m_size;

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
