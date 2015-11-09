package bthdg.exch;

import bthdg.Fetcher;
import bthdg.util.Post;
import bthdg.util.Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.*;

// SEE HERE
//  https://github.com/abwaters/bitstamp-api/blob/master/src/com/abwaters/bitstamp/Bitstamp.java
//
// Do not make more than 600 request per 10 minutes or we will ban your IP address.
//
public class Bitstamp extends BaseExch {
    public static final String CRYPTO_ALGO = "HmacSHA256";
    private static final boolean LOG_PARSE = true;
    private static String SECRET;
    private static String KEY;
    private static String CLIENT_ID;

// TODO - need to incorporate:
//  min trade = 5 usd
//  a fee of 0.111 will be charged as 0.12.
//   As our fees are calculated to two decimal places, all fees which might exceed this limitation are rounded up.
//    The rounding up is executed in such a way, that the second decimal digit is always one digit value higher than it was before the rounding up.
    // supported pairs
    static final Pair[] PAIRS = {Pair.BTC_USD};
    // supported currencies
    private static final Currency[] CURRENCIES = {Currency.USD, Currency.BTC};

    private static String s_bitstampDeepTestStr = null;

    @Override public String getNextNonce() { return Long.toString(System.currentTimeMillis() / 100); }
    @Override protected String getCryproAlgo() { return CRYPTO_ALGO; }
    @Override protected String getSecret() { return SECRET; }
    @Override protected String getApiEndpoint() { return "https://www.bitstamp.net/api/balance/"; }
    @Override public Pair[] supportedPairs() { return PAIRS; }
    @Override public Currency[] supportedCurrencies() { return CURRENCIES; }

    private static final Map<Pair, DecimalFormat> s_amountFormatMap = new HashMap<Pair, DecimalFormat>();
    private static final Map<Pair, Double> s_minAmountStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, DecimalFormat> s_priceFormatMap = new HashMap<Pair, DecimalFormat>();
    private static final Map<Pair, Double> s_minExchPriceStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, Double> s_minOurPriceStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, Double> s_minOrderToCreateMap = new HashMap<Pair, Double>();

    static {           // priceFormat minExchPriceStep  minOurPriceStep  amountFormat   minAmountStep   minOrderToCreate
        put(Pair.BTC_USD, "0.##",     0.01,             0.02,            "0.0#######",  0.00000001,     0.01);
    }

    protected static void put(Pair pair, String priceFormat, double minExchPriceStep, double minOurPriceStep,
                              String amountFormat, double minAmountStep, double minOrderToCreate) {
        s_amountFormatMap.put(pair, mkFormat(amountFormat));
        s_minAmountStepMap.put(pair, minAmountStep);
        s_priceFormatMap.put(pair, mkFormat(priceFormat));
        s_minExchPriceStepMap.put(pair, minExchPriceStep);
        s_minOurPriceStepMap.put(pair, minOurPriceStep);
        s_minOrderToCreateMap.put(pair, minOrderToCreate);
    }

    @Override protected DecimalFormat priceFormat(Pair pair) { return s_priceFormatMap.get(pair); }
    @Override protected DecimalFormat amountFormat(Pair pair) { return s_amountFormatMap.get(pair); }
    @Override public double minOurPriceStep(Pair pair) { return s_minOurPriceStepMap.get(pair); }
    @Override public double minExchPriceStep(Pair pair) { return s_minExchPriceStepMap.get(pair); }
    @Override public double minAmountStep(Pair pair) { return s_minAmountStepMap.get(pair); }
    @Override public double minOrderToCreate(Pair pair) { return s_minOrderToCreateMap.get(pair); }

    @Override public void initFundMap() {
        Map<Currency,Double> distributeRatio = new HashMap<Currency, Double>();
        distributeRatio.put(Currency.BTC, 0.5);
        distributeRatio.put(Currency.USD, 0.5);
        FundMap.s_map.put(Exchange.BITSTAMP, distributeRatio);
    }

    public Bitstamp() {}

    public static void main(String[] args) {
        try {
            new Bitstamp().start();
        } catch (Exception e) {
            log("ERROR: " + e);
            e.printStackTrace();
        }
    }

    @Override public List<Post.NameValue> getPostParams(String nonce, Exchange.UrlDef apiEndpoint, Fetcher.FetchCommand command, Fetcher.FetchOptions options) throws Exception {
        String encoded = encode(nonce.getBytes(), CLIENT_ID.getBytes(), KEY.getBytes());
        String signature = encoded.toUpperCase();

        List<Post.NameValue> postParams = new ArrayList<Post.NameValue>();
        postParams.add(new Post.NameValue("key", URLEncoder.encode(KEY)));
        postParams.add(new Post.NameValue("nonce", URLEncoder.encode(nonce)));
        postParams.add(new Post.NameValue("signature", URLEncoder.encode(signature)));
        switch (command) {
            case ORDER:
                OrderData order = options.getOrderData();
                Pair pair = order.m_pair;

                String amountStr = roundAmountStr(order.m_amount, pair);
                postParams.add(new Post.NameValue("amount", amountStr));

                String priceStr = roundPriceStr(order.m_price, pair);
                postParams.add(new Post.NameValue("price", priceStr));
                break;
            case CANCEL: {
                String orderId = options.getOrderId();
                postParams.add(new Post.NameValue("id", orderId));
                break;
            }
        }
        return postParams;
    }

    private void start() throws Exception {
        init();
        String nonce = getNextNonce();

        String encoded = encode(nonce.getBytes(), CLIENT_ID.getBytes(), KEY.getBytes());
        String signature = encoded.toUpperCase();
        log("signature: " + signature);

        String query = new StringBuilder("key=")
                .append(URLEncoder.encode(KEY))
                .append("&")
                .append("nonce=")
                .append(URLEncoder.encode(nonce))
                .append("&")
                .append("signature=")
                .append(URLEncoder.encode(signature))
                .toString();

        initSsl();

        Map<String, String> headerLines = new HashMap<String, String>();  // Create a new map for the header lines.
        headerLines.put("Content-Type", APPLICATION_X_WWW_FORM_URLENCODED);
        headerLines.put("User-Agent", USER_AGENT); // Add the key to the header lines.

        String json = loadJsonStr(headerLines, query);
        log("Loaded json: " + json);
    }

    @Override protected String encodeHexString(byte[] hash) {
        return Utils.encodeHexString064x(hash);
    }

    public static String topTestStr() {
        return "{\"timestamp\":\"1391517889\",\"last\":\"803.10\",\"volume\":\"6728.44691118\",\"high\":\"813.91\",\"ask\":\"803.10\",\"low\":\"796.83\",\"bid\":\"803.00\"}";
    }

    public static String deepTestStr() {
        if (s_bitstampDeepTestStr == null) {
            StringBuilder sb = new StringBuilder();
            try {
                Reader fr = new FileReader("bitstampDeep.json");
                BufferedReader reader = new BufferedReader(fr);
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                } finally {
                    reader.close();
                }
            } catch (Exception e) {
                log("error reading bitstampDeepTestStr: " + e);
                e.printStackTrace();
            }
            s_bitstampDeepTestStr = sb.toString();
        }
        return s_bitstampDeepTestStr;
    }

    public static TopData parseTop(Object obj) {
        JSONObject jObj = (JSONObject) obj;
//        log("BITSTAMP.parseTop() " + jObj);
        String last = (String) jObj.get("last");
        String bid = (String) jObj.get("bid");
        String ask = (String) jObj.get("ask");
//        log("bid=" + bid + ", ask=" + ask + ", last=" + last);
        return new TopData(bid, ask, last);
    }

    public static DeepData parseDeep(Object obj) {
        JSONObject jObj = (JSONObject) obj;
//        log("BITSTAMP.parseDeep() " + jObj);

        JSONArray bids = (JSONArray) jObj.get("bids");
//        log(" class="+bids.getClass()+", bids=" + bids);
        JSONArray asks = (JSONArray) jObj.get("asks");
//        log(" class="+asks.getClass()+", asks=" + asks);

        return DeepData.create(bids, asks);
    }

    public static TradesData parseTrades(Object object) {
//        log("BITSTAMP.parseTrades() " + object);
        JSONArray array = (JSONArray) object;
        int len = array.size();
        List<TradeData> trades = new ArrayList<TradeData>(len);
        for (int i = 0; i < len; i++) {
            JSONObject tObj = (JSONObject) array.get(i); // {"amount":"0.02500000","price":"683.00","tid":3406676,"date":"1391901009"}
            double amount = Utils.getDouble(tObj.get("amount"));
            double price = Utils.getDouble(tObj.get("price"));
            long timestamp = Utils.getLong(tObj.get("date")) * 1000;
            long tid = (Long) tObj.get("tid");
            TradeData tdata = new TradeData(amount, price, timestamp, tid, null);
            trades.add(tdata);
        }
        return new TradesData(trades);
    }

    public static AccountData parseAccount(Object obj) {
        JSONObject jObj = (JSONObject) obj;
        log("BITSTAMP.parseAccount() " + jObj);
        double usd = Utils.getDouble(jObj.get("usd_balance"));
        double fee = Utils.getDouble(jObj.get("fee")) / 100;
        double btc = Utils.getDouble(jObj.get("btc_balance"));
        AccountData ret = new AccountData(Exchange.BITSTAMP, fee);
        ret.setAvailable(Currency.USD, usd);
        ret.setAvailable(Currency.BTC, btc);
        return ret;
    }

    public static String accountTestStr() {
        return "{\"usd_balance\":\"10.488\",\"fee\":\"0.2000\",\"btc_balance\":\"0.01900000\",\"btc_reserved\":\"0\",\"usd_reserved\":\"0\",\"btc_available\":\"0.01900000\",\"usd_available\":\"10.488\"}";
//        return "{\"usd_balance\":\"10.488\",\"fee\":\"0.5000\",\"btc_balance\":\"0.01900000\",\"btc_reserved\":\"0\",\"usd_reserved\":\"0\",\"btc_available\":\"0.01900000\",\"usd_available\":\"10.488\"}";
    }

    public static String tradesTestStr() {
        return "[{\"amount\":\"0.02500000\",\"price\":\"683.00\",\"tid\":3406676,\"date\":\"1391901009\"},{\"amount\":\"3.44293760\",\"price\":\"680.10\",\"tid\":3406675,\"date\":\"1391901004\"},{\"amount\":\"0.35592240\",\"price\":\"681.95\",\"tid\":3406674,\"date\":\"1391901004\"},{\"amount\":\"0.06000000\",\"price\":\"681.95\",\"tid\":3406673,\"date\":\"1391901004\"},{\"amount\":\"3.93895847\",\"price\":\"684.90\",\"tid\":3406672,\"date\":\"1391900996\"},{\"amount\":\"1.96647298\",\"price\":\"684.90\",\"tid\":3406671,\"date\":\"1391900985\"},{\"amount\":\"2.03352702\",\"price\":\"684.90\",\"tid\":3406670,\"date\":\"1391900981\"},{\"amount\":\"2.96647298\",\"price\":\"684.89\",\"tid\":3406669,\"date\":\"1391900981\"},{\"amount\":\"0.13300000\",\"price\":\"684.89\",\"tid\":3406668,\"date\":\"1391900974\"},{\"amount\":\"0.74886633\",\"price\":\"684.89\",\"tid\":3406667,\"date\":\"1391900960\"},{\"amount\":\"0.32373920\",\"price\":\"684.00\",\"tid\":3406666,\"date\":\"1391900960\"},{\"amount\":\"1.00000000\",\"price\":\"681.02\",\"tid\":3406665,\"date\":\"1391900960\"},{\"amount\":\"5.82453974\",\"price\":\"680.02\",\"tid\":3406664,\"date\":\"1391900959\"},{\"amount\":\"4.17546026\",\"price\":\"680.04\",\"tid\":3406663,\"date\":\"1391900959\"},{\"amount\":\"0.02500000\",\"price\":\"684.00\",\"tid\":3406662,\"date\":\"1391900957\"}]";
    }

    public void init() {
        try {
            init(loadKeys());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("error reading properties");
        }
    }

    public static boolean init(Properties properties) {
        SECRET = properties.getProperty("bitstamp_secret");
        if (SECRET != null) {
            KEY = properties.getProperty("bitstamp_key");
            if (KEY != null) {
                CLIENT_ID = properties.getProperty("bitstamp_clientId");
                if (CLIENT_ID != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Exchange.UrlDef fixEndpointForSide(Exchange.UrlDef apiOrderEndpoint, Fetcher.FetchOptions options) {
        OrderSide side = options.getOrderData().m_side;
        return apiOrderEndpoint.replace("XXXX", side.m_name); // XXXX ->  buy | sell
    }

    public static OrdersData parseOrders(Object obj, Pair pair) {
        // parseOrders obj=[{"id":30113196,"amount":"0.01000000","price":"523.56","datetime":"2014-07-20 22:41:18","type":0}]
        if (LOG_PARSE) {
            log("BITSTAMP.parseOrders() " + obj);
        }
        JSONArray orders = (JSONArray) obj;

        Map<String,OrdersData.OrdData> ords = new HashMap<String,OrdersData.OrdData>();
        int size = orders.size();
        for(int i = 0; i < size; i++) {
            JSONObject order = (JSONObject) orders.get(i);
            String orderId = order.get("id").toString();
            String type = order.get("type").toString();
            double rate = Utils.getDouble(order.get("price"));
            double remainedAmount = Utils.getDouble(order.get("amount"));
            OrdersData.OrdData ord = new OrdersData.OrdData(orderId, 0, 0, remainedAmount, rate, -1l, null, pair, getOrderSide(type));
            ords.put(orderId, ord);
        }
        return new OrdersData(ords);
    }

    public static PlaceOrderData parseOrder(Object obj) {
//        parseOrder obj={"id":30113196,"amount":"0.01","price":"523.56","datetime":"2014-07-20 22:41:17.425558","type":0}
// BITSTAMP.parseOrder() {"error":{"__all__":["You need $188.71 to open that order. You have only $148.41 available. Check your account balance for details."]}}
        if (LOG_PARSE) {
            log("BITSTAMP.parseOrder() " + obj);
        }
        JSONObject jObj = (JSONObject) obj;
        Object error = jObj.get("error");
        if(error != null) {
            JSONObject jError = (JSONObject) error;
            JSONArray errors = (JSONArray) jError.get("__all__");
            int size = errors.size();
            StringBuffer sb = new StringBuffer();
            for(int i = 0; i < size; i++) {
                String errorStr = (String) errors.get(i);
                if(sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(errorStr);
            }
            return new PlaceOrderData(sb.toString());
        } else {
            Long id = (Long) jObj.get("id");
            if (id != null) {
                double remains = Utils.getDouble(jObj.get("amount"));
                return new PlaceOrderData(id, remains, 0, null);
            } else {
                throw new RuntimeException("parseOrder error on Bitstamp : " + jObj);
            }
        }
    }

    public static CancelOrderData parseCancelOrders(Object obj) {
//        parseCancelOrders obj=true
//        parseCancelOrders obj={"error":"Order not found"}
        if (LOG_PARSE) {
            log("BITSTAMP.parseCancelOrders() " + obj);
        }
        if(obj instanceof Boolean) {
            Boolean success = (Boolean) obj;
            return new CancelOrderData(null, null);
        } else {
            JSONObject jObj = (JSONObject) obj;
            String error = (String) jObj.get("error");
            log(" error: " + error);
            return new CancelOrderData(error);

        }
// Returns 'true' if order has been found and canceled.
    }
}


//////////////////////////////////////////////////////////////
// https://www.bitstamp.net/api/
//  https://www.bitstamp.net/api/ticker/
//     last - last BTC price
//     high - last 24 hours price high
//     low - last 24 hours price low
//     volume - last 24 hours volume
//     bid - highest buy order
//     ask - lowest sell order
//            {"high": "349.90",
//             "last": "335.23",
//             "timestamp": "1384198415",
//             "bid": "335.00",
//             "volume": "33743.67611671",
//             "low": "300.28",
//             "ask": "335.23"}
//    ticker_url = { "method": "GET", "url": "https://www.bitstamp.net/api/ticker/" }
//        buy_url = { "method": "POST", "url": "https://www.bitstamp.net/api/buy/" }
//        sell_url = { "method": "POST", "url": "https://www.bitstamp.net/api/sell/" }
