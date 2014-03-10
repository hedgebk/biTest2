package bthdg;

import bthdg.exch.BaseExch;
import bthdg.exch.Bitstamp;
import bthdg.exch.Btce;

// to support others ?
// ? https://www.bitfinex.com/
// ? www.itbit.com
//    http://docs.itbit.apiary.io/
//    https://www.itbit.com/api/feeds/ticker/XBTUSD
// ? bitcoin-central.net
// crypto-trade.com ?
// huobi ? https://github.com/xiaojay/huobi/blob/master/huobi.py
//  http://www.huobi.com/help/index.php?a=api_help
// btcchina ? https://vip.btcchina.com/?lang=en

public enum Exchange {
    BITSTAMP("bitstamp", new Bitstamp(), "bitstampUSD", 1, 0.002, true,
             Bitstamp.topTestStr(), "https://www.bitstamp.net/api/ticker/",
             null, "https://www.bitstamp.net/api/order_book/",
             Bitstamp.tradesTestStr(), "https://www.bitstamp.net/api/transactions/?time=minute",
             Bitstamp.accountTestStr(), new UrlDef("https://www.bitstamp.net/api/balance/")
    ) {
        @Override public TopData parseTop(Object jObj) { return Bitstamp.parseTop(jObj); }
        @Override public DeepData parseDeep(Object jObj) { return Bitstamp.parseDeep(jObj); }
        @Override public TradesData parseTrades(Object jObj) { return Bitstamp.parseTrades(jObj); }
        @Override public AccountData parseAccount(Object jObj) { return Bitstamp.parseAccount(jObj); }
        @Override public String deepTestStr() { return Bitstamp.deepTestStr(); }
    },
    BTCE("btce", new Btce(), "btceUSD", 2, 0.002, true,
          Btce.topTestStr(), "https://btc-e.com/api/3/ticker/btc_usd", // "https://btc-e.com/api/2/btc_usd/ticker"
          Btce.deepTestStr(), "https://btc-e.com/api/3/depth/btc_usd", // GET-parameter "limit" - how much trades to return def_value = 150; max_value=2000
          Btce.tradesTestStr(), "https://btc-e.com/api/3/trades/btc_usd", // GET-parameter "limit" - how much trades to return def_value = 150; max_value=2000
          Btce.accountTestStr(), new UrlDef("https://btc-e.com/tapi", "method", "getInfo")
    ) {
        @Override public TopData parseTop(Object jObj) { return Btce.parseTop(jObj); }
        @Override public DeepData parseDeep(Object jObj) { return Btce.parseDeep(jObj); }
        @Override public TradesData parseTrades(Object jObj) { return Btce.parseTrades(jObj); }
        @Override public AccountData parseAccount(Object jObj) { return Btce.parseAccount(jObj); }
    },
    MTGOX("mtgox", null, "mtgoxUSD", 3, 0.0025, false,
          null, null, null, null, null, null, null, null),
    CAMPBX("CampBX", null, "cbxUSD", 4, 0.0055 /*Volume discounts available*/, true,
           campBxTopTestStr(), "http://CampBX.com/api/xticker.php",
           null, null, "", "", null, null);

    public final String m_name;
    public BaseExch m_baseExch;
    public final String m_bitcoinchartsSymbol;
    public final int m_databaseId;
    public final double m_baseFee;

    public final UrlDef m_apiTopEndpoint;
    public final String m_topTestStr;

    public final UrlDef m_apiDeepEndpoint;
    private final String m_deepTestStr;

    public final UrlDef m_apiTradesEndpoint;
    public final String m_tradesTestStr;

    public final UrlDef m_accountEndpoint;
    public final String m_accountTestStr;
    public final boolean m_doWebUpdate;

    public String deepTestStr() { return m_deepTestStr; }

    Exchange(String name, BaseExch baseExch, String bitcoinchartsSymbol, int databaseId, double baseFee, boolean doWebUpdate,
             String topTestStr, String apiTopEndpoint,
             String deepTestStr, String apiDeepEndpoint,
             String tradesTestStr, String apiTradesEndpoint,
             String accountTestStr, UrlDef accountEndpoint
    ) {
        m_name = name;
        m_baseExch = baseExch;
        m_bitcoinchartsSymbol = bitcoinchartsSymbol;
        m_databaseId = databaseId;
        m_baseFee = baseFee;
        m_doWebUpdate = doWebUpdate;

        m_apiTopEndpoint = new UrlDef(apiTopEndpoint);
        m_topTestStr = topTestStr;

        m_apiDeepEndpoint = new UrlDef(apiDeepEndpoint);
        m_deepTestStr = deepTestStr;

        m_apiTradesEndpoint = new UrlDef(apiTradesEndpoint);
        m_tradesTestStr = tradesTestStr;

        m_accountEndpoint = accountEndpoint;
        m_accountTestStr = accountTestStr;
    }

    public TopData parseTop(Object jObj) { return null; }
    public DeepData parseDeep(Object jObj) { return null; }
    public TradesData parseTrades(Object jObj) { return null; }
    public AccountData parseAccount(Object jObj) { return null; }

    private static String campBxTopTestStr() { return "{\"Last Trade\":\"717.58\",\"Best Bid\":\"715.00\",\"Best Ask\":\"720.00\"}"; }


    public static class UrlDef {
        public final String m_location;
        public final String m_paramName;
        public final String m_paramValue;

        public UrlDef(String location) {
            this(location, null, null);
        }

        public UrlDef(String location, String paramName, String paramValue) {
            m_location = location;
            m_paramName = paramName;
            m_paramValue = paramValue;
        }

        @Override public String toString() {
            return "UrlDef{" +
                    "location='" + m_location + '\'' +
                    ((m_paramName != null) ? ", paramName='" + m_paramName + '\'' : "") +
                    ((m_paramValue != null) ? ", paramValue='" + m_paramValue + '\'' : "") +
                    '}';
        }
    }
}
