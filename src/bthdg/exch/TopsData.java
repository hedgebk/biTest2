package bthdg.exch;

import bthdg.Currency;
import bthdg.Exchange;
import bthdg.Pair;
import bthdg.PairDirection;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class TopsData {
    private HashMap<Pair,TopData> m_map = new HashMap<Pair,TopData>();

    public TopData get(Pair pair) { return m_map.get(pair); }
    public void put(Pair pair, TopData top) { m_map.put(pair, top); }
    public Set<Map.Entry<Pair, TopData>> entrySet() { return m_map.entrySet(); }

    public double convert(Currency inCurrency, Currency outCurrency, double all) {
        PairDirection pd = PairDirection.get(inCurrency, outCurrency);
        Pair pair = pd.m_pair;
        TopData top = get(pair);
        double mid = top.getMid();
        if (!pd.isForward()) {
            mid = 1 / mid;
        }
        double converted = all / mid;
        return converted;
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
