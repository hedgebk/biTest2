package bthdg;

import bthdg.exch.*;

import java.util.Map;

// to support others ?
// https://www.kraken.com
// https://vircurex.com
// ? www.itbit.com
//    http://docs.itbit.apiary.io/
//    https://www.itbit.com/api/feeds/ticker/XBTUSD
// ? bitcoin-central.net
// crypto-trade.com ?
// huobi ? https://github.com/xiaojay/huobi/blob/master/huobi.py
//  http://www.huobi.com/help/index.php?a=api_help
// btcchina ? https://vip.btcchina.com/?lang=en

public enum Exchange {
    BITSTAMP("bitstamp", new Bitstamp(), "bitstampUSD", 1, 0.002, true, 2,
             Bitstamp.topTestStr(), "https://www.bitstamp.net/api/ticker/",
             null, "https://www.bitstamp.net/api/order_book/",
             Bitstamp.tradesTestStr(), "https://www.bitstamp.net/api/transactions/?time=minute",
             Bitstamp.accountTestStr(), new UrlDef("https://www.bitstamp.net/api/balance/"),
             null, null, null
    ) {
        @Override public TopData parseTop(Object jObj, Pair pair) { return Bitstamp.parseTop(jObj); }
        @Override public DeepData parseDeep(Object jObj) { return Bitstamp.parseDeep(jObj); }
        @Override public TradesData parseTrades(Object jObj, Pair pair) { return Bitstamp.parseTrades(jObj); }
        @Override public AccountData parseAccount(Object jObj) { return Bitstamp.parseAccount(jObj); }
        @Override public String deepTestStr() { return Bitstamp.deepTestStr(); }
    },
    BTCE("btce", new Btce(), "btceUSD", 2, 0.002, true, 3,
          Btce.topTestStr(), "https://btc-e.com/api/3/ticker/XXXX", // XXXX like "btc_usd-ltc_btc" // old? : "https://btc-e.com/api/2/btc_usd/ticker"
          Btce.deepTestStr(), Btce.apiDeepEndpoint(), // GET-parameter "limit" - how much trades to return def_value = 150; max_value=2000
          Btce.tradesTestStr(), Btce.apiTradesEndpoint(), // XXXX like "btc_usd-ltc_btc"; GET-parameter "limit" - how much trades to return def_value = 150; max_value=2000
          Btce.accountTestStr(), new UrlDef("https://btc-e.com/tapi", "method", "getInfo"),
          new UrlDef("https://btc-e.com/tapi", "method", "Trade"),
          new UrlDef("https://btc-e.com/tapi", "method", "ActiveOrders"),
          new UrlDef("https://btc-e.com/tapi", "method", "CancelOrder")
    ) {
        @Override public TopData parseTop(Object jObj, Pair pair) { return Btce.parseTop(jObj, pair); }
        @Override public TopsData parseTops(Object jObj, Pair[] pairs) { return Btce.parseTops(jObj, pairs); }
        @Override public DeepData parseDeep(Object jObj) { return Btce.parseDeep(jObj); }
        @Override public DeepsData parseDeeps(Object jObj, Pair[] pairs) { return Btce.parseDeeps(jObj, pairs); }
        @Override public TradesData parseTrades(Object jObj, Pair pair) { return Btce.parseTrades(jObj, pair); }
        @Override public Map<Pair, TradesData> parseTrades(Object jObj, Pair[] pairs) { return Btce.parseTrades(jObj, pairs); }
        @Override public AccountData parseAccount(Object jObj) { return Btce.parseAccount(jObj); }
        @Override public PlaceOrderData parseOrder(Object jObj) { return Btce.parseOrder(jObj); }
        @Override public OrdersData parseOrders(Object jObj) { return Btce.parseOrders(jObj); }
        @Override public CancelOrderData parseCancelOrder(Object jObj) { return Btce.parseCancelOrders(jObj); }
        @Override public boolean retryFetch(Object obj) { return Btce.retryFetch(obj); }
        @Override public UrlDef apiTopEndpoint(Fetcher.FetchOptions options) { return Btce.fixEndpointForPairs(m_apiTopEndpoint, options); }
        @Override public UrlDef apiDeepEndpoint(Fetcher.FetchOptions options) { return Btce.fixEndpointForPairs(m_apiDeepEndpoint, options); }
        @Override public UrlDef apiTradesEndpoint(Fetcher.FetchOptions options) { return Btce.fixEndpointForPairs(m_apiTradesEndpoint, options); }
        @Override public double minPriceStep(Pair pair) { return Btce.minExchPriceStep(pair); }
        @Override public double minAmountStep(Pair pair) { return Btce.minAmountStep(pair); }
        @Override public double minOrderSize(Pair pair) { return Btce.minOrderToCreate(pair); }
    },
    MTGOX("mtgox", null, "mtgoxUSD", 3, 0.0025, false, 0, null, null, null, null, null, null, null, null, null, null, null), // DEAD
    CAMPBX("CampBX", null, "cbxUSD", 4, 0.0055, true, 2,
           campBxTopTestStr(), "http://CampBX.com/api/xticker.php",
           null, null, "", "", null, null, null, null, null),
    BITFINEX("Bitfinex", null, "bitfinexUSD", 5, 0.0015, true, 2,
           null, null,
           null, null, "", "", null, null, null, null, null),
    HITBTC("HitBtc", null, "hitbtcUSD", 6, 0.00085, true, 2,
           null, null,
           null, null, "", "", null, null, null, null, null),
    LAKEBTC("LakeBtc", null, "lakeUSD", 7, 0.003, true, 2,
           null, null,
           null, null, "", "", null, null, null, null, null),
    ITBIT("ItBit", null, "itbitUSD", 8, 0.0017, true, 2,
           null, null,
           null, null, "", "", null, null, null, null, null),
    BTCN("BtcChina", new Btcn(), "btcnCNY", 9, 0.00001, true, 2,
         null, "https://data.btcchina.com/data/ticker?market=XXXX", // XXXX like "btccny"
         null, "https://data.btcchina.com/data/orderbook?market=XXXX",
         "", "",
         null, new UrlDef("https://api.btcchina.com/api_trade_v1.php"),
         null, null, null) {
        @Override public TopsData parseTops(Object jObj, Pair[] pairs) { return Btcn.parseTops(jObj, pairs); }
        @Override public DeepData parseDeep(Object jObj) { return Btcn.parseDeep(jObj); }
        @Override public UrlDef apiTopEndpoint(Fetcher.FetchOptions options) { return Btcn.fixEndpointForPairs(m_apiTopEndpoint, options); }
        @Override public UrlDef apiDeepEndpoint(Fetcher.FetchOptions options) { return Btcn.fixEndpointForPairs(m_apiDeepEndpoint, options); }
        @Override public AccountData parseAccount(Object jObj) { return Btcn.parseAccount(jObj); }
    },
    OKCOIN("OkCoin", new OkCoin(), "okcoinCNY", 10, 0.00001, true, 2,
           null, "https://www.okcoin.cn/api/ticker.do?symbol=XXXX", // XXXX like "ltc_cny"
           null, "https://www.okcoin.cn/api/depth.do?symbol=XXXX", // XXXX like "ltc_cny"
           "", "",
           null, new UrlDef("https://www.okcoin.com/api/userinfo.do"),
           null, null, null) {
        @Override public TopData parseTop(Object jObj, Pair pair) { return OkCoin.parseTop(jObj, pair); }
        @Override public TopsData parseTops(Object jObj, Pair[] pairs) { return OkCoin.parseTops(jObj, pairs); }
        @Override public DeepData parseDeep(Object jObj) { return OkCoin.parseDeep(jObj); }
        @Override public UrlDef apiTopEndpoint(Fetcher.FetchOptions options) { return OkCoin.fixEndpointForPairs(m_apiTopEndpoint, options); }
        @Override public UrlDef apiDeepEndpoint(Fetcher.FetchOptions options) { return OkCoin.fixEndpointForPairs(m_apiDeepEndpoint, options); }
        @Override public AccountData parseAccount(Object jObj) { return OkCoin.parseAccount(jObj); }
    },
    ;

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

    public UrlDef m_orderEndpoint;
    public UrlDef m_ordersEndpoint;
    public UrlDef m_cancelEndpoint;

    public final boolean m_doWebUpdate;
    private final int m_priceDecimals;

    public String deepTestStr() { return m_deepTestStr; }

    Exchange(String name, BaseExch baseExch, String bitcoinchartsSymbol, int databaseId,
             double baseFee, boolean doWebUpdate, int priceDecimals,
             String topTestStr, String apiTopEndpoint,
             String deepTestStr, String apiDeepEndpoint,
             String tradesTestStr, String apiTradesEndpoint,
             String accountTestStr, UrlDef accountEndpoint,
             UrlDef orderEndpoint,
             UrlDef ordersEndpoint,
             UrlDef cancelEndpoint
    ) {
        m_name = name;
        m_baseExch = baseExch;
        m_bitcoinchartsSymbol = bitcoinchartsSymbol;
        m_databaseId = databaseId;
        m_baseFee = baseFee;
        m_doWebUpdate = doWebUpdate;
        m_priceDecimals = priceDecimals;

        m_apiTopEndpoint = new UrlDef(apiTopEndpoint);
        m_topTestStr = topTestStr;

        m_apiDeepEndpoint = new UrlDef(apiDeepEndpoint);
        m_deepTestStr = deepTestStr;

        m_apiTradesEndpoint = new UrlDef(apiTradesEndpoint);
        m_tradesTestStr = tradesTestStr;

        m_accountEndpoint = accountEndpoint;
        m_accountTestStr = accountTestStr;

        m_orderEndpoint = orderEndpoint;
        m_ordersEndpoint = ordersEndpoint;
        m_cancelEndpoint = cancelEndpoint;
    }

    public TopData parseTop(Object jObj, Pair pair) { return null; }
    public TopsData parseTops(Object jObj, Pair[] pairs) { return null; }
    public DeepData parseDeep(Object jObj) { return null; }
    public DeepsData parseDeeps(Object jObj, Pair[] pairs) { return null; }
    public TradesData parseTrades(Object jObj, Pair pair) { return null; }
    public Map<Pair, TradesData> parseTrades(Object jObj, Pair[] pairs) { return null; }
    public AccountData parseAccount(Object jObj) { return null; }
    public PlaceOrderData parseOrder(Object jObj) { return null; }
    public OrdersData parseOrders(Object jObj) { return null; }
    public CancelOrderData parseCancelOrder(Object jObj) { return null; }
    public boolean retryFetch(Object obj) { return false; }
    public UrlDef apiTopEndpoint(Fetcher.FetchOptions options) { return m_apiTopEndpoint; }
    public UrlDef apiDeepEndpoint(Fetcher.FetchOptions options) { return m_apiDeepEndpoint; }
    public UrlDef apiTradesEndpoint(Fetcher.FetchOptions options) { return m_apiTradesEndpoint; }
    public double minPriceStep(Pair pair) { return 0.01; }
    public double minAmountStep(Pair pair) { return 0.0001; }
    public double minOrderSize(Pair pair) { return 0.01; }

    private static String campBxTopTestStr() { return "{\"Last Trade\":\"717.58\",\"Best Bid\":\"715.00\",\"Best Ask\":\"720.00\"}"; }

    public double roundPrice(double price, Pair pair) { return m_baseExch.roundPrice(price, pair); }
    public String roundPriceStr(double price, Pair pair) { return m_baseExch.roundPriceStr(price, pair); }
    public double roundAmount(double amount, Pair pair) { return m_baseExch.roundAmount(amount, pair); }
    public String roundAmountStr(double amount, Pair pair) { return m_baseExch.roundAmountStr(amount, pair); }

    public int connectTimeout() { return m_baseExch.connectTimeout(); }
    public int readTimeout() { return m_baseExch.readTimeout(); }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
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

        public UrlDef replace(String key, String value) {
            if ((key != null) && (value != null)) {
                return new UrlDef(m_location.replace(key, value), m_paramName, m_paramValue);
            }
            return this;
        }
    }
}
