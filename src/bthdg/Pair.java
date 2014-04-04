package bthdg;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public enum Pair {
    //                                  freq         // minOrderSize
    LTC_USD(Currency.LTC, Currency.USD, 13.33333333),
    LTC_BTC(Currency.LTC, Currency.BTC, 20),
    BTC_USD(Currency.BTC, Currency.USD, 3.636363636),
    LTC_EUR(Currency.LTC, Currency.EUR, 0.357142857),
    BTC_EUR(Currency.BTC, Currency.EUR, 0.285714286),
    EUR_USD(Currency.EUR, Currency.USD, 0.273972603);

    public final Currency m_from;
    public final Currency m_to;
    public final double m_freq; // btce trade frequency / minute   todo: move to BTCE class

    Pair(Currency to, Currency from, double freq) {
        m_from = from;
        m_to = to;
        m_freq = freq;
    }

    public String getName(boolean forward) {
        return forward ? m_from + "->" + m_to : m_to + "->" + m_from;
    }

    public Currency currencyFrom(boolean from) {
        return from ? m_from : m_to;
    }
}
