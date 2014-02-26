package bthdg.exch;

import bthdg.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;

// SEE HERE
//  https://github.com/abwaters/bitstamp-api/blob/master/src/com/abwaters/bitstamp/Bitstamp.java
//
// Do not make more than 600 request per 10 minutes or we will ban your IP address.
//
public class Bitstamp extends BaseExch {
    public static final String CRYPTO_ALGO = "HmacSHA256";
    private static String SECRET;
    private static String KEY;
    private static String CLIENT_ID;

    private static String s_bitstampDeepTestStr = null;

    @Override public String getNextNonce() { return Long.toString(System.currentTimeMillis() / 100); }
    @Override protected String getCryproAlgo() { return CRYPTO_ALGO; }
    @Override protected String getSecret() { return SECRET; }
    @Override protected String getApiEndpoint() { return "https://www.bitstamp.net/api/balance/"; }
    private static void log(String s) { Log.log(s); }

    public Bitstamp() {}

    public static void main(String[] args) {
        try {
            new Bitstamp().start();
            // json: {"btc_reserved": "0", "fee": "0.5000", "btc_available": "0.03800000", "usd_reserved": "0", "btc_balance": "0.03800000", "usd_balance": "0.00", "usd_available": "0.00"}
        } catch (Exception e) {
            log("ERROR: " + e);
            e.printStackTrace();
        }
    }

    public Map<String,String> getPostParams(String nonce, Exchange.UrlDef apiEndpoint) throws Exception {
        String encoded = encode(nonce.getBytes(), CLIENT_ID.getBytes(), KEY.getBytes());
        String signature = encoded.toUpperCase();

        Map<String, String> postParams = new HashMap<String, String>();
        postParams.put("key", URLEncoder.encode(KEY));
        postParams.put("nonce", URLEncoder.encode(nonce));
        postParams.put("signature", URLEncoder.encode(signature));
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

        String json = loadJsonStr(null, query);
        log("Loaded json: " + json);
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
        JSONArray array = (JSONArray)object;
        int len = array.size();
        List<TradesData.TradeData> trades = new ArrayList<TradesData.TradeData>(len);
        for (int i = 0; i < len; i++) {
            JSONObject tObj = (JSONObject) array.get(i); // {"amount":"0.02500000","price":"683.00","tid":3406676,"date":"1391901009"}
            double amount = Utils.getDouble(tObj.get("amount"));
            double price = Utils.getDouble(tObj.get("price"));
            long timestamp = Utils.getLong(tObj.get("date")) * 1000;
            long tid = (Long) tObj.get("tid");
            TradesData.TradeData tdata = new TradesData.TradeData(amount, price, timestamp, tid, null);
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
        return new AccountData(Exchange.BITSTAMP.m_name, usd, btc, fee);
    }

    public static String accountTestStr() {
        return "{\"usd_balance\":\"0.00\",\"fee\":\"0.5000\",\"btc_balance\":\"0.03800000\",\"btc_reserved\":\"0\",\"usd_reserved\":\"0\",\"btc_available\":\"0.03800000\",\"usd_available\":\"0.00\"}";
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
//        #order_url = { "method": "POST", "url": "https://data.mtgox.com/api/1/generic/private/order/result" }
//        #open_orders_url = { "method": "POST", "url": "https://data.mtgox.com/api/1/generic/private/orders" }
