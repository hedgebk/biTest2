package bthdg.exch;

import bthdg.Exchange;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class TopsData {
    public HashMap<Pair,TopData> m_map = new HashMap<Pair,TopData>(Pair.values().length);

    public TopData get(Pair pair) { return m_map.get(pair); }
    public void put(Pair pair, TopData top) { m_map.put(pair, top); }
    public Set<Map.Entry<Pair, TopData>> entrySet() { return m_map.entrySet(); }

    public double convert(Currency inCurrency, Currency outCurrency, double all) {
        double rate;
        if(PairDirection.support(inCurrency, outCurrency)) {
            rate = rate(inCurrency, outCurrency);
        } else {
            rate = rate(inCurrency, Currency.BTC) * rate(Currency.BTC, outCurrency);
        }
        double converted = all / rate;
        return converted;
    }

    private double rate(Currency inCurrency, Currency outCurrency) {
        double rate;PairDirection pd = PairDirection.get(inCurrency, outCurrency);
        Pair pair = pd.m_pair;
        TopData top = get(pair);
        rate = top.getMid();
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
