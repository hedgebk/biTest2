package bthdg;

public enum Pair {
    LTC_USD(Currency.LTC, Currency.USD, 0.00001, 13.33333333, 0.36),
    LTC_BTC(Currency.LTC, Currency.BTC, 0.00002, 20,          0.36),
    BTC_USD(Currency.BTC, Currency.USD, 0.001,   3.636363636, 0.01),
    LTC_EUR(Currency.LTC, Currency.EUR, 0.0001,  0.357142857, 0.36),
    BTC_EUR(Currency.BTC, Currency.EUR, 0.0001,  0.285714286, 0.01),
    EUR_USD(Currency.EUR, Currency.USD, 0.0001,  0.273972603, 6);

    private static final double DEF_MIN_PRICE_STEP = 0.01;

    public final Currency m_from;
    public final Currency m_to;
    public final double m_minPriceStep;
    public final double m_freq; // btce trade frequency / minute
    public final double m_minOrderSize;

    Pair(Currency to, Currency from, double minPriceStep, double freq, double minOrderSize) {
        m_from = from;
        m_to = to;
        m_minPriceStep = minPriceStep;
        m_freq = freq;
        m_minOrderSize = minOrderSize;
    }

    public String getName(boolean forward) {
        return forward ? m_from + "->" + m_to : m_to + "->" + m_from;
    }

    public Currency currencyFrom(boolean from) {
        return from ? m_from : m_to;
    }
}
