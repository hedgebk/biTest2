package bthdg;

public enum Pair {
    BTC_USD(Currency.BTC, Currency.USD, 0.001),
    LTC_USD(Currency.LTC, Currency.USD, 0.0001),
    LTC_BTC(Currency.LTC, Currency.BTC, 0.0001),
    BTC_EUR(Currency.BTC, Currency.EUR, 0.0001),
    LTC_EUR(Currency.LTC, Currency.EUR, 0.0001),
    EUR_USD(Currency.EUR, Currency.USD, 0.0001);

    private static final double DEF_MIN_PRICE_STEP = 0.01;

    public final Currency m_from;
    public final Currency m_to;
    public final double m_minPriceStep;

    Pair(Currency to, Currency from) {
        this(to, from, DEF_MIN_PRICE_STEP);
    }

    Pair(Currency to, Currency from, double minPriceStep) {
        m_from = from;
        m_to = to;
        m_minPriceStep = minPriceStep;
    }

    public String getName(boolean forward) {
        return forward ? m_from + "->" + m_to : m_to + "->" + m_from;
    }

    public Currency currency(boolean from) {
        return from ? m_from : m_to;
    }
}
