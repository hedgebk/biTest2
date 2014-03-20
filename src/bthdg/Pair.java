package bthdg;

public enum Pair {
    BTC_USD(Currency.BTC, Currency.USD),
    LTC_USD(Currency.LTC, Currency.USD),
    LTC_BTC(Currency.LTC, Currency.BTC),
    BTC_EUR(Currency.BTC, Currency.EUR),
    LTC_EUR(Currency.LTC, Currency.EUR),
    EUR_USD(Currency.EUR, Currency.USD);

    public final Currency m_from;
    public final Currency m_to;

    Pair(Currency to, Currency from) {
        m_from = from;
        m_to = to;
    }

    public String getName(boolean forward) {
        return forward ? m_from + "->" + m_to : m_to + "->" + m_from;
    }
}
