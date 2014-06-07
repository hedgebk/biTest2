package bthdg.exch;

import bthdg.Exchange;
import bthdg.Fetcher;
import bthdg.Log;
import bthdg.Utils;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.Properties;

/**
 * https://github.com/agent462/chinashop/tree/master/lib/chinashop
 * */
public class Btcn extends BaseExch {
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
            new Btcn().start();
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
        SECRET = properties.getProperty("btcn_secret");
        if(SECRET != null) {
            KEY = properties.getProperty("btcn_key");
            if(KEY != null) {
                return true;
            }
        }
        return false;
    }

    private void run() {
        try {
            TopsData topsData = Fetcher.fetchTops(Exchange.BTCN, Pair.BTC_CNH);
            log("topsData: " + topsData);
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
            case BTC_CNH: return "btccny";
            case LTC_CNH: return "ltccny";
            case LTC_BTC: return "ltcbtc";
            default: return "?";
        }
    }

    //http://btcchina.org/api-market-data-documentation-en#ticker
    public static TopsData parseTops(Object obj, Pair[] pairs) {
        if (LOG_PARSE) {
            log("BTCE.parseTops() " + obj);
        }
        // todo: add parsing of 'get data for ALL pairs'
        TopsData ret = new TopsData();
        Pair pair = pairs[0];
        TopData top = parseTopInt(obj, pair);
        ret.put(pair, top);
        return ret;
    }

    //{"ticker":{
    //    "vol":"3650.64920000",
    //    "last":"4070.47",
    //    "sell":"4073.26",
    //    "buy":"4070.48",
    //    "high":"4104.99",
    //    "date":1402099192,
    //    "low":"4070.00"}}
    private static TopData parseTopInt(Object obj, Pair pair) {
        JSONObject jObj = (JSONObject) obj;
        JSONObject ticker = (JSONObject) jObj.get("ticker");
//        log(" class="+ticker.getClass()+", ticker=" + ticker);
        double last = Utils.getDouble(ticker, "last");
        double bid = Utils.getDouble(ticker, "sell");
        double ask = Utils.getDouble(ticker, "buy");
//        log("bid=" + bid + ", ask=" + ask + ", last=" + last);
        return new TopData(bid, ask, last);
    }
}
