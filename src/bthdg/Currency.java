package bthdg;

public enum Currency {
    USD("usd"),
    BTC("btc"),
    LTC("ltc"),
    EUR("eur");

    public final String m_name;

    Currency(String name) {
        m_name = name;
    }

    public static Currency getByName(String str) {
        if (str == null) {
            return null;
        }
        for (Currency curr : values()) {
            if (curr.m_name.equals(str)) {
                return curr;
            }
        }
        throw new RuntimeException("non supported Currency '" + str + "'");
    }

}