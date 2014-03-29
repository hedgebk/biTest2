package bthdg;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public enum Pair {
    //                                  minAmountStep freq         // minOrderSize
    LTC_USD(Currency.LTC, Currency.USD, 0.00001,      13.33333333, 0.18),
    LTC_BTC(Currency.LTC, Currency.BTC, 0.00001,      20,          0.18),
    BTC_USD(Currency.BTC, Currency.USD, 0.00001,      3.636363636, 0.01),
    LTC_EUR(Currency.LTC, Currency.EUR, 0.00001,      0.357142857, 0.18),
    BTC_EUR(Currency.BTC, Currency.EUR, 0.00001,      0.285714286, 0.01),
    EUR_USD(Currency.EUR, Currency.USD, 0.00001,      0.273972603, 3);

    public final Currency m_from;
    public final Currency m_to;
    public final double m_minAmountStep;
    public final double m_freq; // btce trade frequency / minute   todo: move to BTCE class
    public final double m_minOrderSize; // minOrderSize for our trades

    Pair(Currency to, Currency from, double minAmountStep, double freq, double minOrderSize) {
        m_from = from;
        m_to = to;
        m_minAmountStep = minAmountStep;
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
