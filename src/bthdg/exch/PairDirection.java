package bthdg.exch;

import bthdg.Currency;
import bthdg.OrderSide;
import bthdg.triplet.Direction;

public class PairDirection {
    public final Pair m_pair;
    public final Direction m_direction;

    public Currency currencyFrom() { return m_pair.currencyFrom(m_direction); }
    public Currency currencyTo() { return m_pair.currencyFrom(m_direction.reverse()); }
    public boolean isForward() { return m_direction.m_forward; }

    public PairDirection(Pair pair, Direction direction) {
        m_pair = pair;
        m_direction = direction;
    }

    public PairDirection get(Direction direction) {
        if (direction == Direction.FORWARD) {
            return this;
        }
        return new PairDirection(m_pair, m_direction.reverse());
    }

    public String getName() {
        return m_pair.getName(m_direction);
    }

    @Override public String toString() {
        return "PairDirection{" +
                "pair=" + m_pair +
                ", " + ((m_direction== Direction.FORWARD) ? "forward" : "backward")+
                '}';
    }

    @Override public boolean equals(Object obj) {
        if (obj == this) { return true; }
        if (obj instanceof PairDirection) {
            PairDirection other = (PairDirection) obj;
            if (m_direction == other.m_direction) {
                return m_pair == other.m_pair;
            }
        }
        return false;
    }

    public static PairDirection get(Currency fromCurrency, Currency toCurrency) {
        for( Pair pair: Pair.values() ) {
            if((pair.m_from == fromCurrency) && (pair.m_to == toCurrency)) {
                return new PairDirection(pair, Direction.FORWARD);
            }
            if((pair.m_to == fromCurrency) && (pair.m_from == toCurrency)) {
                return new PairDirection(pair, Direction.BACKWARD);
            }
        }
        throw new RuntimeException("not supported pair: " + fromCurrency + "->" + toCurrency);
    }

    public OrderSide getSide() {
        return (m_direction == Direction.FORWARD) ? OrderSide.BUY : OrderSide.SELL;
    }
}
