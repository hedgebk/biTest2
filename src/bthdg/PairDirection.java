package bthdg;

public class PairDirection {
    public final Pair m_pair;
    public final boolean m_forward;

    public Currency currencyFrom() { return m_pair.currencyFrom(m_forward); }

    public PairDirection(Pair pair, boolean forward) {
        m_pair = pair;
        m_forward = forward;
    }

    public PairDirection get(boolean forward) {
        if (forward) {
            return this;
        }
        return new PairDirection(m_pair, !m_forward);
    }

    public String getName() {
        return m_pair.getName(m_forward);
    }

    @Override public String toString() {
        return "PairDirection{" +
                "pair=" + m_pair +
                ", " + (m_forward ? "forward" : "backward")+
                '}';
    }

    @Override public boolean equals(Object obj) {
        if (obj == this) { return true; }
        if (obj instanceof PairDirection) {
            PairDirection other = (PairDirection) obj;
            if (m_forward == other.m_forward) {
                return m_pair == other.m_pair;
            }
        }
        return false;
    }

    public static PairDirection get(Currency fromCurrency, Currency toCurrency) {
        for( Pair pair: Pair.values() ) {
            if((pair.m_from == fromCurrency) && (pair.m_to == toCurrency)) {
                return new PairDirection(pair, true);
            }
            if((pair.m_to == fromCurrency) && (pair.m_from == toCurrency)) {
                return new PairDirection(pair, false);
            }
        }
        throw new RuntimeException("not supported pair: " + fromCurrency + "->" + toCurrency);
    }

    public OrderSide getSide() {
        return m_forward ? OrderSide.BUY : OrderSide.SELL;
    }
}
