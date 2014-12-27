package bthdg.exch;

// maker/taker
public enum TradeType {
    BID("bid"),
    ASK("ask");

    private final String m_typeStr;

    TradeType(String typeStr) {
        m_typeStr = typeStr;
    }

    public static TradeType get(String typeStr) {
        for (TradeType tradeType : values()) {
            if (tradeType.m_typeStr.equals(typeStr)) {
                return tradeType;
            }
        }
        throw new RuntimeException("non supported trade type '" + typeStr + "'");
    }
}
