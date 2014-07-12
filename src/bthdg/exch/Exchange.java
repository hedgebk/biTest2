package bthdg.exch;

import bthdg.Fetcher;

import java.math.RoundingMode;
import java.util.*;

// to support others ?
// https://www.kraken.com
// https://vircurex.com
// ? www.itbit.com
//    http://docs.itbit.apiary.io/
//    https://www.itbit.com/api/feeds/ticker/XBTUSD
// ? bitcoin-central.net
// crypto-trade.com ?
// huobi ? https://github.com/xiaojay/huobi/blob/master/huobi.py
// check more exchnages examples here https://github.com/mobnetic/BitcoinChecker/tree/master/DataModule/src/com/mobnetic/coinguardian/model/market
//   https://github.com/timmolter/XChange
public enum Exchange {
    BITSTAMP("bitstamp", new Bitstamp(), "bitstampUSD", 1, 0.002, true,
             Bitstamp.topTestStr(), "https://www.bitstamp.net/api/ticker/",
             null, "https://www.bitstamp.net/api/order_book/",
             Bitstamp.tradesTestStr(), "https://www.bitstamp.net/api/transactions/?time=minute",
             Bitstamp.accountTestStr(), new UrlDef("https://www.bitstamp.net/api/balance/"),
             null, null, null
    ) {
        @Override public TopData parseTop(Object jObj, Pair pair) { return Bitstamp.parseTop(jObj); }
        @Override public DeepData parseDeep(Object jObj, Pair pair) { return Bitstamp.parseDeep(jObj); }
        @Override public TradesData parseTrades(Object jObj, Pair pair) { return Bitstamp.parseTrades(jObj); }
        @Override public AccountData parseAccount(Object jObj) { return Bitstamp.parseAccount(jObj); }
        @Override public String deepTestStr() { return Bitstamp.deepTestStr(); }
    },
    BTCE("btce", new Btce(), "btceUSD", 2, 0.002, true,
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
        @Override public DeepData parseDeep(Object jObj, Pair pair) { return Btce.parseDeep(jObj, pair); }
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
        @Override public boolean supportsQueryOrdersBySymbol() { return false; }
        @Override public boolean supportsMultiplePairsRequest() { return true; }
        @Override public Currency baseCurrency() { return Currency.BTC; }
        @Override public double getFee(Pair pair, double commonFee) { return Btce.getFee(pair, commonFee); }
    },
    @Deprecated MTGOX("mtgox", null, "mtgoxUSD", 3, 0.0025, false, null, null, null, null, null, null, null, null, null, null, null), // DEAD
    CAMPBX("CampBX", null, "cbxUSD", 4, 0.0055, true,
           campBxTopTestStr(), "http://CampBX.com/api/xticker.php",
           null, null, "", "", null, null, null, null, null),
// read some spec: https://github.com/timmolter/XChange/blob/develop/xchange-bitfinex/api-specification.txt
//  sign https://github.com/timmolter/XChange/blob/develop/xchange-bitfinex/src/main/java/com/xeiam/xchange/bitfinex/v1/service/BitfinexHmacPostBodyDigest.java
    BITFINEX("Bitfinex", null, "bitfinexUSD", 5, 0.0015, true,
           null, null,
           null, null, "", "", null, null, null, null, null),
    HITBTC("HitBtc", null, "hitbtcUSD", 6, 0.00085, true,
           null, null,
           null, null, "", "", null, null, null, null, null),
    LAKEBTC("LakeBtc", null, "lakeUSD", 7, 0.003, true,
           null, null,
           null, null, "", "", null, null, null, null, null),
    ITBIT("ItBit", null, "itbitUSD", 8, 0.0017, true,
           null, null,
           null, null, "", "", null, null, null, null, null),
    BTCN("BtcChina", new Btcn(), "btcnCNY", 9, 0.00001, true,
         null, "https://data.btcchina.com/data/ticker?market=XXXX", // XXXX like "btccny"
         null, "https://data.btcchina.com/data/orderbook?market=XXXX",
         "", "",
         null, new UrlDef("https://api.btcchina.com/api_trade_v1.php"),
         new UrlDef("https://api.btcchina.com/api_trade_v1.php"),
         new UrlDef("https://api.btcchina.com/api_trade_v1.php"),
         new UrlDef("https://api.btcchina.com/api_trade_v1.php")) {
        @Override public void init(Properties keys) { Btcn.init(keys); }
        @Override public TopData parseTop(Object jObj, Pair pair) { return Btcn.parseTop(jObj, pair); }
        @Override public TopsData parseTops(Object jObj, Pair[] pairs) { return Btcn.parseTops(jObj, pairs); }
        @Override public DeepData parseDeep(Object jObj, Pair pair) { return Btcn.parseDeep(jObj, pair); }
        @Override public UrlDef apiTopEndpoint(Fetcher.FetchOptions options) { return Btcn.fixEndpointForPairs(m_apiTopEndpoint, options); }
        @Override public UrlDef apiDeepEndpoint(Fetcher.FetchOptions options) { return Btcn.fixEndpointForPairs(m_apiDeepEndpoint, options); }
        @Override public AccountData parseAccount(Object jObj) { return Btcn.parseAccount(jObj); }
        @Override public PlaceOrderData parseOrder(Object jObj) { return Btcn.parseOrder(jObj); }
        @Override public OrdersData parseOrders(Object jObj) { return Btcn.parseOrders(jObj); }
        @Override public CancelOrderData parseCancelOrder(Object jObj) { return Btcn.parseCancelOrders(jObj); }
        @Override public Currency baseCurrency() { return Currency.CNH; }
        @Override public boolean requirePairForCancel() { return true; }
    },
    OKCOIN("OkCoin", new OkCoin(), "okcoinCNY", 10, 0.00001, true,
           null, "https://www.okcoin.cn/api/ticker.do?symbol=XXXX", // XXXX like "ltc_cny"
           null, "https://www.okcoin.cn/api/depth.do?symbol=XXXX", // XXXX like "ltc_cny"
           "", "",
           null, new UrlDef("https://www.okcoin.com/api/userinfo.do"),
           new UrlDef("https://www.okcoin.com/api/trade.do"),
           new UrlDef("https://www.okcoin.com/api/getorder.do"),
           new UrlDef("https://www.okcoin.com/api/cancelorder.do")) {
        @Override public void init(Properties keys) { OkCoin.init(keys); }
        @Override public TopData parseTop(Object jObj, Pair pair) { return OkCoin.parseTop(jObj, pair); }
        @Override public TopsData parseTops(Object jObj, Pair[] pairs) { return OkCoin.parseTops(jObj, pairs); }
        @Override public DeepData parseDeep(Object jObj, Pair pair) { return OkCoin.parseDeep(jObj, pair); }
        @Override public UrlDef apiTopEndpoint(Fetcher.FetchOptions options) { return OkCoin.fixEndpointForPairs(m_apiTopEndpoint, options); }
        @Override public UrlDef apiDeepEndpoint(Fetcher.FetchOptions options) { return OkCoin.fixEndpointForPairs(m_apiDeepEndpoint, options); }
        @Override public AccountData parseAccount(Object jObj) { return OkCoin.parseAccount(jObj); }
        @Override public PlaceOrderData parseOrder(Object jObj) { return OkCoin.parseOrder(jObj); }
        @Override public OrdersData parseOrders(Object jObj) { return OkCoin.parseOrders(jObj); }
        @Override public CancelOrderData parseCancelOrder(Object jObj) { return OkCoin.parseCancelOrders(jObj); }
        @Override public Currency baseCurrency() { return Currency.CNH; }
        @Override public boolean requirePairForOrders() { return true; }
        @Override public boolean requirePairForCancel() { return true; }
    },
    HUOBI("Huobi", new Huobi(), "", 11, 0.00001, false,
           null, "http://market.huobi.com/staticmarket/ticker_XXXX_json.js", // XXXX like "btc"
           null, "http://market.huobi.com/staticmarket/depth_XXXX_json.js", // XXXX like "btc"
           "", "",
           null, new UrlDef("https://api.huobi.com/api.php"),
           null, null, null) {
        @Override public void init(Properties keys) { Huobi.init(keys); }
        @Override public TopData parseTop(Object jObj, Pair pair) { return Huobi.parseTop(jObj, pair); }
        @Override public TopsData parseTops(Object jObj, Pair[] pairs) { return Huobi.parseTops(jObj, pairs); }
        @Override public DeepData parseDeep(Object jObj, Pair pair) { return Huobi.parseDeep(jObj, pair); }
        @Override public UrlDef apiTopEndpoint(Fetcher.FetchOptions options) { return Huobi.fixEndpointForPairs(m_apiTopEndpoint, options); }
        @Override public UrlDef apiDeepEndpoint(Fetcher.FetchOptions options) { return Huobi.fixEndpointForPairs(m_apiDeepEndpoint, options); }
        @Override public AccountData parseAccount(Object jObj) { return Huobi.parseAccount(jObj); }
    },
    BTER("Bter", null, "", 12, 0.00001, false,       // https://bter.com/api
           null, null, // XXXX like "btc"
           null, null, // XXXX like "btc"
           "", "",
           null, null,
           null, null, null) {
    },
    ;

    static {
        for(Exchange exchange: values()) {
            if (exchange.m_baseExch != null) {
                exchange.m_baseExch.initFundMap();
            }
        }
    }

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

    public String deepTestStr() { return m_deepTestStr; }

    Exchange(String name, BaseExch baseExch, String bitcoinchartsSymbol, int databaseId,
             double baseFee, boolean doWebUpdate,
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

    public TopData parseTop(Object jObj, Pair pair) { throw new RuntimeException("parseTop not implemented on " + this ); }
    public TopsData parseTops(Object jObj, Pair[] pairs) { throw new RuntimeException("parseTops not implemented on " + this ); }
    public DeepData parseDeep(Object jObj, Pair pair) { throw new RuntimeException("parseDeep not implemented on " + this ); }
    public DeepsData parseDeeps(Object jObj, Pair[] pairs) { throw new RuntimeException("parseDeeps not implemented on " + this ); }
    public TradesData parseTrades(Object jObj, Pair pair) { throw new RuntimeException("parseTrades not implemented on " + this ); }
    public Map<Pair, TradesData> parseTrades(Object jObj, Pair[] pairs) { throw new RuntimeException("parseTrades not implemented on " + this ); }
    public AccountData parseAccount(Object jObj) { throw new RuntimeException("parseAccount not implemented on " + this ); }
    public PlaceOrderData parseOrder(Object jObj) { throw new RuntimeException("parseOrder not implemented on " + this ); }
    public OrdersData parseOrders(Object jObj) { throw new RuntimeException("parseOrders not implemented on " + this ); }
    public CancelOrderData parseCancelOrder(Object jObj) { throw new RuntimeException("parseCancelOrder not implemented on " + this ); }
    public boolean retryFetch(Object obj) { return false; }
    public UrlDef apiTopEndpoint(Fetcher.FetchOptions options) { return m_apiTopEndpoint; }
    public UrlDef apiDeepEndpoint(Fetcher.FetchOptions options) { return m_apiDeepEndpoint; }
    public UrlDef apiTradesEndpoint(Fetcher.FetchOptions options) { return m_apiTradesEndpoint; }

    public double minExchPriceStep(Pair pair) { return m_baseExch.minExchPriceStep(pair); }
    public double minOurPriceStep(Pair pair) { return m_baseExch.minOurPriceStep(pair); }
    public double minAmountStep(Pair pair) { return m_baseExch.minAmountStep(pair); }
    public double minOrderToCreate(Pair pair) { return m_baseExch.minOrderToCreate(pair); }

    public double roundPrice(double price, Pair pair) { return m_baseExch.roundPrice(price, pair); }
    public double roundPrice(double price, Pair pair, RoundingMode round) { return m_baseExch.roundPrice(price, pair, round); }
    public String roundPriceStr(double price, Pair pair) { return m_baseExch.roundPriceStr(price, pair); }
    public double roundAmount(double amount, Pair pair) { return m_baseExch.roundAmount(amount, pair); }
    public String roundAmountStr(double amount, Pair pair) { return m_baseExch.roundAmountStr(amount, pair); }

    public int connectTimeout() { return m_baseExch.connectTimeout(); }
    public int readTimeout() { return m_baseExch.readTimeout(); }
    public Pair[] supportedPairs() { return m_baseExch.supportedPairs(); }
    public boolean supportsMultiplePairsRequest() { return false; }
    public boolean supportsQueryAllOrders() { return true; }
    public boolean supportsQueryOrdersBySymbol() { return true; }
    public boolean requirePairForOrders() { return false; }
    public boolean requirePairForCancel() { return false; }
    public Currency[] supportedCurrencies() { return m_baseExch.supportedCurrencies(); }
    public Currency baseCurrency() { throw new RuntimeException("baseCurrency not implemented on " + this ); }
    public double getFee(Pair pair, double commonFee) { return commonFee; }

    private static String campBxTopTestStr() { return "{\"Last Trade\":\"717.58\",\"Best Bid\":\"715.00\",\"Best Ask\":\"720.00\"}"; }

    public boolean supportPair(Currency from, Currency to) {
        for( Pair pair: supportedPairs()) {
            if((pair.m_from == from) && (pair.m_to == to)) {
                return true;
            }
            if((pair.m_to == from) && (pair.m_from == to)) {
                return true;
            }
        }
        return false;
    }

    public static List<Exchange> resolveExchange(String exchName) {
        List<Exchange> startsWith = new ArrayList<Exchange>();
        for (Exchange exchange : Exchange.values()) {
            String name = exchange.name();
            if (name.equalsIgnoreCase(exchName)) {
                startsWith.add(exchange);
                return startsWith;
            }
            if (name.startsWith(exchName)) {
                startsWith.add(exchange);
            }
        }
        return startsWith;
    }

    public static Exchange getExchange(String exchName) {
        for (Exchange exchange : Exchange.values()) {
            String name = exchange.name();
            if (name.equalsIgnoreCase(exchName)) {
                return exchange;
            }
        }
        throw new RuntimeException("no exchange with name '" + exchName + "'");
    }

    public boolean supportsCurrency(Currency currency) {
        Currency[] currencies = supportedCurrencies();
        int indx = Arrays.binarySearch(currencies, currency);
        return (indx >= 0);
    }

    public void init(Properties keys) {}

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
