package bthdg.exch;

import bthdg.Currency;
import bthdg.triplet.Direction;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public enum Pair {
    LTC_USD(1, Currency.LTC, Currency.USD),
    LTC_BTC(2, Currency.LTC, Currency.BTC),
    BTC_USD(3, Currency.BTC, Currency.USD),
    LTC_EUR(4, Currency.LTC, Currency.EUR),
    BTC_EUR(5, Currency.BTC, Currency.EUR),
    EUR_USD(6, Currency.EUR, Currency.USD);

    public final int m_id;
    public final Currency m_from;
    public final Currency m_to;

    Pair(int id, Currency to, Currency from) {
        m_id = id;
        m_from = from;
        m_to = to;
    }

    public String getName(Direction direction) {
        return (direction == Direction.FORWARD) ? m_from + "->" + m_to : m_to + "->" + m_from;
    }

    public Currency currencyFrom(boolean from) {
        return from ? m_from : m_to;
    }

    public Currency currencyFrom(Direction direction) {
        return (direction == Direction.FORWARD) ? m_from : m_to;
    }

    public static Pair getById(int pairId) {
        for (Pair pair : values()) {
            if (pair.m_id == pairId) {
                return pair;
            }
        }
        throw new RuntimeException("no pair with id=" + pairId);
    }
}
