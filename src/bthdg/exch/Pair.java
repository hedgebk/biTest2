package bthdg.exch;

import bthdg.Exchange;
import bthdg.triplet.Direction;

public enum Pair {
    LTC_USD(1, Currency.LTC, Currency.USD),
    LTC_BTC(2, Currency.LTC, Currency.BTC),
    BTC_USD(3, Currency.BTC, Currency.USD),
    LTC_EUR(4, Currency.LTC, Currency.EUR),
    BTC_EUR(5, Currency.BTC, Currency.EUR),
    EUR_USD(6, Currency.EUR, Currency.USD),
    PPC_USD(7, Currency.PPC, Currency.USD),
    PPC_BTC(8, Currency.PPC, Currency.BTC),
    NMC_USD(9,  Currency.NMC, Currency.USD),
    NMC_BTC(10, Currency.NMC, Currency.BTC),
    NVC_USD(11, Currency.NVC, Currency.USD),
    NVC_BTC(12, Currency.NVC, Currency.BTC),

    BTC_RUR(13, Currency.BTC, Currency.RUR),
    LTC_RUR(14, Currency.LTC, Currency.RUR),
    USD_RUR(15, Currency.USD, Currency.RUR),
    EUR_RUR(16, Currency.EUR, Currency.RUR),

    BTC_GBP(17, Currency.BTC, Currency.GBP),
    LTC_GBP(18, Currency.LTC, Currency.GBP),
    GBP_USD(19, Currency.GBP, Currency.USD),

    BTC_CNH(20, Currency.BTC, Currency.CNH),
    LTC_CNH(21, Currency.LTC, Currency.CNH),
    USD_CNH(22, Currency.USD, Currency.CNH),
    ;

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

    public static Pair getByName(String name) {
        for (Pair pair : values()) {
            if (pair.name().equals(name)) {
                return pair;
            }
        }
        throw new RuntimeException("no pair with name=" + name);
    }

    public static Pair resolvePair(String pairName, Exchange exchange) {
        Pair[] pairs = exchange.supportedPairs();
        for (Pair pair : pairs) {
            if (pair.name().equalsIgnoreCase(pairName)) {
                return pair;
            }
        }
        return null;
    }
}
