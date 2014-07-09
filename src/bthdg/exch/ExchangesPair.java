package bthdg.exch;

/** the pair of exchanges */
public class ExchangesPair {
    public final Exchange m_exchange1;
    public final Exchange m_exchange2;

    public ExchangesPair(Exchange exchange1, Exchange exchange2) {
        m_exchange1 = exchange1;
        m_exchange2 = exchange2;
    }

    public String name() {
        return m_exchange1.name() + "_" + m_exchange2.name();
    }

    public Exchange[] toArray() {
        return new Exchange[]{m_exchange1, m_exchange2};
    }
}
