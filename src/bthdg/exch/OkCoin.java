package bthdg.exch;

import bthdg.Exchange;
import bthdg.Fetcher;
import bthdg.Log;
import bthdg.Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.Properties;

public class OkCoin extends BaseExch {
    private static String SECRET;
    private static String KEY;
    public static boolean LOG_PARSE = false;

    @Override public String getNextNonce() {
        return null;
    }

    @Override protected String getCryproAlgo() {
        return null;
    }

    @Override protected String getSecret() {
        return null;
    }

    @Override protected String getApiEndpoint() {
        return null;
    }

    @Override public double roundPrice(double price, Pair pair) {
        return 0;
    }

    @Override public String roundPriceStr(double price, Pair pair) {
        return null;
    }

    @Override public double roundAmount(double amount, Pair pair) {
        return 0;
    }

    @Override public String roundAmountStr(double amount, Pair pair) {
        return null;
    }

    private static void log(String s) { Log.log(s); }

    public static void main(String[] args) {
        try {
            new OkCoin().start();
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
        SECRET = properties.getProperty("okcoin_secret");
        if(SECRET != null) {
            KEY = properties.getProperty("okcoin_key");
            if(KEY != null) {
                return true;
            }
        }
        return false;
    }

    private void run() {
        try {
//            TopData topData = Fetcher.fetchTop(Exchange.OKCOIN, Pair.BTC_CNH);
//            log("topData: " + topData);
            DeepData deepData = Fetcher.fetchDeep(Exchange.OKCOIN, Pair.BTC_CNH);
            log("deepData: " + deepData);
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
            case BTC_CNH: return "btc_cny";
            case LTC_CNH: return "ltc_cny";
//            case LTC_BTC: return "ltcbtc";
            default: return "?";
        }
    }

    // https://www.okcoin.com/about/publicApi.do
    public static TopsData parseTops(Object obj, Pair[] pairs) {
        if (LOG_PARSE) {
            log("OkCoin.parseTops() " + obj);
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

    private static TopData parseTopInt(Object obj, Pair pair) {
        JSONObject jObj = (JSONObject) obj;
        JSONObject ticker = (JSONObject) jObj.get("ticker");
        double last = Utils.getDouble(ticker, "last");
        double bid = Utils.getDouble(ticker, "sell");
        double ask = Utils.getDouble(ticker, "buy");
        return new TopData(bid, ask, last);
    }

    public static DeepData parseDeep(Object jObj) {
        return parseDeepInt(jObj);
    }

    private static DeepData parseDeepInt(Object obj) {
        if (LOG_PARSE) {
            log("OkCoin.parseDeep() " + obj);
        }
        JSONObject pairData = (JSONObject) obj;
        JSONArray bids = (JSONArray) pairData.get("bids");
        JSONArray asks = (JSONArray) pairData.get("asks");

        return DeepData.create(bids, asks);
    }

}
