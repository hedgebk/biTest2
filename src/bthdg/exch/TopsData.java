package bthdg.exch;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class TopsData {
    public HashMap<Pair,TopData> m_map = new HashMap<Pair,TopData>(Pair.values().length);

    public TopsData() {}

    public TopsData(Pair pair, TopData topData) {
        put(pair, topData);
    }

    public TopsData copy() {
        TopsData ret = new TopsData();
        ret.m_map.putAll(m_map);
        return ret;
    }

    public TopData get(Pair pair) {
        TopData topData = getInt(pair);
        if (topData == null) {
            throw new RuntimeException("no top data for pair " + pair);
        }
        return topData;
    }

    public TopData getInt(Pair pair) {
        return m_map.get(pair);
    }

    public void put(Pair pair, TopData top) { m_map.put(pair, top); }
    public Set<Map.Entry<Pair, TopData>> entrySet() { return m_map.entrySet(); }

    public double convert(Currency inCurrency, Currency outCurrency, double all, Exchange exchange) {
        double rate = rate(exchange, inCurrency, outCurrency);
        double converted = all / rate;
        return converted;
    }

    public Double rate(Exchange exchange, Currency from, Currency to) {
        Double rate;
        if (from == to) {
            rate = 1d;
        } else {
            boolean support = exchange.supportPair(from, to);
            if(support) {
                rate = rate(from, to);
            } else {
                Currency baseCurrency = exchange.baseCurrency();
                rate = rate(from, baseCurrency) * rate(baseCurrency, to);
            }
        }
        return rate;
    }

    public Double rate(Currency inCurrency, Currency outCurrency) {
        PairDirection pd = PairDirection.get(inCurrency, outCurrency);
        Pair pair = pd.m_pair;
        TopData top = getInt(pair);
        if(top == null) {
            return null;
        }
        double rate = top.getMid();
        if (!pd.isForward()) {
            rate = 1 / rate;
        }
        return rate;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TopsData[");
        for(Map.Entry<Pair, TopData> entry: m_map.entrySet()) {
            Pair pair = entry.getKey();
            TopData top = entry.getValue();
            sb.append(pair);
            sb.append("=");
            sb.append(top);
            sb.append("; ");
        }
        sb.append("}");
        return sb.toString();
    }

    public String toString(Exchange exchange) {
        Iterator<Map.Entry<Pair,TopData>> i = entrySet().iterator();
        if (!i.hasNext()){ return "{}"; }

        int counter = 0;
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (;;) {
            Map.Entry<Pair,TopData> e = i.next();
            Pair pair = e.getKey();
            TopData top = e.getValue();
            sb.append(pair);
            sb.append('=');
            sb.append(top.toString(exchange, pair));
            if (! i.hasNext()) {
                return sb.append('}').toString();
            }
            sb.append(',').append(' ');
            counter++;
            if (counter % 3 == 0) {
                sb.append("\n    ");
            }
        }
    }
}
