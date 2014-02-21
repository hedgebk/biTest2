package bthdg;

import bthdg.exch.Bitstamp;
import bthdg.exch.Btce;

public enum Exchange {
    BITSTAMP("bitstamp", "bitstampUSD", 1, 0.002,
             Bitstamp.topTestStr(), "https://www.bitstamp.net/api/ticker/",
             null, "https://www.bitstamp.net/api/order_book/",
             Bitstamp.tradesTestStr(), "https://www.bitstamp.net/api/transactions/?time=minute") {
        @Override public TopData parseTop(Object jObj) { return Bitstamp.parseTop(jObj); }
        @Override public DeepData parseDeep(Object jObj) { return Bitstamp.parseDeep(jObj); }
        @Override public TradesData parseTrades(Object jObj) { return Bitstamp.parseTrades(jObj); }
        @Override public String deepTestStr() { return Bitstamp.deepTestStr(); }
    },
    BTCE("btce", "btceUSD", 2, 0.002,
          Btce.topTestStr(), "https://btc-e.com/api/3/ticker/btc_usd", // "https://btc-e.com/api/2/btc_usd/ticker"
          Btce.deepTestStr(), "https://btc-e.com/api/3/depth/btc_usd", // GET-parameter "limit" - how much trades to return def_value = 150; max_value=2000
          Btce.tradesTestStr(), "https://btc-e.com/api/3/trades/btc_usd" // GET-parameter "limit" - how much trades to return def_value = 150; max_value=2000
    ) {
        @Override public TopData parseTop(Object jObj) { return Btce.parseTop(jObj); }
        @Override public DeepData parseDeep(Object jObj) { return Btce.parseDeep(jObj); }
        @Override public TradesData parseTrades(Object jObj) { return Btce.parseTrades(jObj); }
    },
    MTGOX("mtgox", "mtgoxUSD", 3, 0.0025,
          null, null, null, null, null, null),
    CAMPBX("CampBX", "cbxUSD", 4, 0.0055 /*Volume discounts available*/,
           campBxTopTestStr(), "http://CampBX.com/api/xticker.php",
           null, null,
           "", "");
    // ? https://www.bitfinex.com/
    // ? www.itbit.com
    //    http://docs.itbit.apiary.io/
    //    https://www.itbit.com/api/feeds/ticker/XBTUSD
    // ? bitcoin-central.net
    // crypto-trade.com ?
    // huobi ? https://github.com/xiaojay/huobi/blob/master/huobi.py
    //  http://www.huobi.com/help/index.php?a=api_help
    // btcchina ? https://vip.btcchina.com/?lang=en

    public final int m_databaseId;
    public final String m_name;
    public final String m_bitcoinchartsSymbol;

    public final String m_apiTopEndpoint;
    public final String m_topTestStr;

    public final String m_apiDeepEndpoint;
    private final String m_deepTestStr;

    public final String m_apiTradesEndpoint;
    public final String m_tradesTestStr;
    public final double m_fee;

    public String deepTestStr() { return m_deepTestStr; }

    Exchange(String name, String bitcoinchartsSymbol, int databaseId, double fee,
             String topTestStr, String apiTopEndpoint,
             String deepTestStr, String apiDeepEndpoint,
             String tradesTestStr, String apiTradesEndpoint ) {
        m_name = name;
        m_bitcoinchartsSymbol = bitcoinchartsSymbol;
        m_databaseId = databaseId;
        m_fee = fee;

        m_apiTopEndpoint = apiTopEndpoint;
        m_topTestStr = topTestStr;

        m_apiDeepEndpoint = apiDeepEndpoint;
        m_deepTestStr = deepTestStr;

        m_apiTradesEndpoint = apiTradesEndpoint;
        m_tradesTestStr = tradesTestStr;
    }

    public TopData parseTop(Object jObj) { return null; }
    public DeepData parseDeep(Object jObj) { return null; }
    public TradesData parseTrades(Object jObj) { return null; }

    private static String campBxTopTestStr() { return "{\"Last Trade\":\"717.58\",\"Best Bid\":\"715.00\",\"Best Ask\":\"720.00\"}"; }
}
