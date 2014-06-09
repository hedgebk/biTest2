package bthdg.exch;

import bthdg.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Huobi extends BaseExch {
    private static final int READ_TIMEOUT = 10000;

    private static String SECRET;
    private static String PARTNER;
    public static boolean LOG_PARSE = false;

    @Override public int readTimeout() { return READ_TIMEOUT; };
    @Override public String getNextNonce() { return null; }
    @Override protected String getCryproAlgo() { return null; }
    @Override protected String getSecret() { return null; }
    @Override protected String getApiEndpoint() { return null; }
    @Override public double roundPrice(double price, Pair pair) { return 0; }
    @Override public String roundPriceStr(double price, Pair pair) { return null; }
    @Override public double roundAmount(double amount, Pair pair) { return 0; }
    @Override public String roundAmountStr(double amount, Pair pair) { return null; }

    private static void log(String s) { Log.log(s); }

    public static void main(String[] args) {
        try {
            new Huobi().start();
        } catch (Exception e) {
            log("ERROR: " + e);
            e.printStackTrace();
        }
    }

    public void start() {
        init();
        run();
    }

    private void init() {
        try {
            init(loadKeys());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("error reading properties");
        }
    }

    public static boolean init(Properties properties) {
//        SECRET = properties.getProperty("okcoin_secret");
//        if(SECRET != null) {
//            PARTNER = properties.getProperty("okcoin_partner");
//            if(PARTNER != null) {
                return true;
//            }
//        }
//        return false;
    }

    private void run() {
        try {
            TopData topData = Fetcher.fetchTop(Exchange.HUOBI, Pair.BTC_CNH);
            log("huobi:   " + topData);
            topData = Fetcher.fetchTop(Exchange.OKCOIN, Pair.BTC_CNH);
            log("OkCoin:  " + topData);
            topData = Fetcher.fetchTop(Exchange.BTCN, Pair.BTC_CNH);
            log("Btcn:    " + topData);
            topData = Fetcher.fetchTop(Exchange.BTCE, Pair.BTC_CNH);
            log("Btce:    " + topData);
            topData = Fetcher.fetchTop(Exchange.BITSTAMP, Pair.BTC_USD);
            log("Bitstamp:" + topData);
//            DeepData deepData = Fetcher.fetchDeep(Exchange.HUOBI, Pair.BTC_CNH);
//            log("deepData: " + deepData);
//            acct();
//            AccountData account = Fetcher.fetchAccount(Exchange.HUOBI);
//            log("account: " + account);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Exchange.UrlDef fixEndpointForPairs(Exchange.UrlDef apiTopEndpoint, Fetcher.FetchOptions options) {
        Pair[] pairs = options.getPairs();
        return apiTopEndpoint.replace("XXXX", getPairParam(pairs[0]));
    }

    private static String getPairParam(Pair pair) {
        switch (pair) {
            case BTC_CNH: return "btc";
            case LTC_CNH: return "ltc";
            default: return "?";
        }
    }

    // https://www.huobi.com/help/index.php?a=market_help
    public static TopsData parseTops(Object obj, Pair[] pairs) {
        if (LOG_PARSE) {
            log("HUOBI.parseTops() " + obj);
        }
        TopsData ret = new TopsData();
        Pair pair = pairs[0];
        TopData top = parseTopInt(obj, pair);
        ret.put(pair, top);
        return ret;
    }

    public static TopData parseTop(Object obj, Pair pair) {
        return parseTopInt(obj, pair);
    }

    // {"ticker":{"high":"86.48","low":"79.75","last":"83.9","vol":2239560.1752883,"buy":"83.88","sell":"83.9"}}
    private static TopData parseTopInt(Object obj, Pair pair) {
        JSONObject jObj = (JSONObject) obj;
        JSONObject ticker = (JSONObject) jObj.get("ticker");
        double last = Utils.getDouble(ticker, "last");
        double ask= Utils.getDouble(ticker, "sell");
        double bid  = Utils.getDouble(ticker, "buy");
        return new TopData(bid, ask, last);
    }

    public static DeepData parseDeep(Object jObj) {
        return parseDeepInt(jObj);
    }

    // {"asks":[["90.8",0.5],...],"bids":[["86.06",79.243],...]]
    private static DeepData parseDeepInt(Object obj) {
        if (LOG_PARSE) {
            log("HUOBI.parseDeep() " + obj);
        }
        JSONObject pairData = (JSONObject) obj;
        JSONArray bids = (JSONArray) pairData.get("bids");
        JSONArray asks = (JSONArray) pairData.get("asks");

        return DeepData.create(bids, asks);
    }
}
