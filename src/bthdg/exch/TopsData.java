package bthdg.exch;

import bthdg.Currency;
import bthdg.Pair;
import bthdg.PairDirection;

import java.util.HashMap;

public class TopsData extends HashMap<Pair,TopData> {
    public double convert(Currency inCurrency, Currency outCurrency, double all) {
        PairDirection pd = PairDirection.get(inCurrency, outCurrency);
        Pair pair = pd.m_pair;
        TopData top = get(pair);
        double mid = top.getMid();
        if (!pd.m_forward) {
            mid = 1 / mid;
        }
        double converted = all / mid;
        return converted;
    }
}
