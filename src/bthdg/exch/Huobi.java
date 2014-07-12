package bthdg.exch;

import bthdg.Fetcher;
import bthdg.util.Md5;
import bthdg.util.Post;
import bthdg.util.Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.*;

public class Huobi extends BaseExch {
    private static final int READ_TIMEOUT = 10000;

    private static String SECRET;
    private static String KEY;
    public static boolean LOG_PARSE = false;
    public static boolean JOIN_SMALL_QUOTES = false;

    // supported pairs
    static final Pair[] PAIRS = {Pair.BTC_CNH, Pair.LTC_CNH };

    // supported currencies
    private static final Currency[] CURRENCIES = { Currency.BTC, Currency.LTC, Currency.CNH };

    @Override public int readTimeout() { return READ_TIMEOUT; };
    @Override public String getNextNonce() { return null; }
    @Override protected String getCryproAlgo() { return null; }
    @Override protected String getSecret() { return null; }
    @Override protected String getApiEndpoint() { return "https://api.huobi.com/api.php"; }
    @Override public Pair[] supportedPairs() { return PAIRS; }
    @Override public Currency[] supportedCurrencies() { return CURRENCIES; };
    @Override public double minOurPriceStep(Pair pair) { return 0.01; }

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
        SECRET = properties.getProperty("huobi_secret");
        if(SECRET != null) {
            KEY = properties.getProperty("huobi_key");
            if(KEY != null) {
                return true;
            }
        }
        return false;
    }

    private void run() {
        try {
            Fetcher.LOG_JOBJ = true;
//            TopData topData = Fetcher.fetchTop(Exchange.HUOBI, Pair.BTC_CNH);
//            log("huobi:   " + topData);
//            DeepData deepData = Fetcher.fetchDeep(Exchange.HUOBI, Pair.BTC_CNH);
//            log("deepData: " + deepData);
            acct();
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

    public static DeepData parseDeep(Object jObj, Pair pair) {
        return parseDeepInt(jObj, pair);
    }

    // {"asks":[["90.8",0.5],...],"bids":[["86.06",79.243],...]]
    private static DeepData parseDeepInt(Object obj, Pair pair) {
        if (LOG_PARSE) {
            log("HUOBI.parseDeep() " + obj);
        }
        JSONObject pairData = (JSONObject) obj;
        JSONArray bids = (JSONArray) pairData.get("bids");
        JSONArray asks = (JSONArray) pairData.get("asks");

        DeepData deeps = DeepData.create(bids, asks);
        if (JOIN_SMALL_QUOTES) {
            deeps.joinSmallQuotes(Exchange.BTCE, pair);
        }
        return deeps;
    }

    private void acct() throws Exception {
        String method = "get_account_info";

        long time = System.currentTimeMillis() / 1000;
        String created = Long.toString(time);

        Map<String,String> sArray = new HashMap<String, String>();
        sArray.put("method", method);
        sArray.put("access_key", KEY);
        sArray.put("created", created);
      	sArray.put("secret_key", SECRET);
        String str = Post.createHttpPostString(sArray, true);

        String sign = Md5.getMD5String(str);
        sign = sign.toLowerCase();

        List<Post.NameValue>    postParams = new ArrayList<Post.NameValue>();
        postParams.add(new Post.NameValue("method", method));
        postParams.add(new Post.NameValue("access_key", KEY));
        postParams.add(new Post.NameValue("created", created));
        postParams.add(new Post.NameValue("sign", sign));
        String postData = Post.buildPostQueryString(postParams);

        initSsl();

        String json = loadJsonStr(null, postData);
        log("Loaded json: " + json);
    }

    // https://github.com/peatio/bitbot-huobi/blob/master/spec/fixtures/vcr_cassettes/authorized/success/account.yml
    @Override public IPostData getPostData(Exchange.UrlDef apiEndpoint, Fetcher.FetchCommand command, Fetcher.FetchOptions options) throws Exception {
        String method = getMethod(command);

        long time = System.currentTimeMillis() / 1000;
        String created = Long.toString(time);

        Map<String,String> sArray = new HashMap<String, String>();
        sArray.put("method", method);
        sArray.put("access_key", KEY);
        sArray.put("created", created);
        sArray.put("secret_key", SECRET);
        String str = Post.createHttpPostString(sArray, true);

        String sign = Md5.getMD5String(str);
        sign = sign.toLowerCase();

        List<Post.NameValue>    postParams = new ArrayList<Post.NameValue>();
        postParams.add(new Post.NameValue("method", method));
        postParams.add(new Post.NameValue("access_key", KEY));
        postParams.add(new Post.NameValue("created", created));
        postParams.add(new Post.NameValue("sign", sign));
        final String postData = Post.buildPostQueryString(postParams);

        return new IPostData() {
            @Override public String postStr() { return postData; }
            @Override public Map<String, String> headerLines() { return null; }
        };
    }

    private String getMethod(Fetcher.FetchCommand command) {
        if(command == Fetcher.FetchCommand.ACCOUNT) {
            return "get_account_info";
        }
        throw new RuntimeException("Huobi: not supported yet command: " + command.name());
    }

    public static AccountData parseAccount(Object obj) {
        //{"total":"0.00",
        // "net_asset":"0.00",
        // "available_cny_display":"0.00",
        // "available_btc_display":"0.0000",
        // "frozen_cny_display":"0.00",
        // "frozen_btc_display":"0.0000",
        // "loan_cny_display":"0.00",
        // "loan_btc_display":"0.0000"}

        // {"time":1405122528,"code":66,"msg":"qwerty"}

        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("OkCoin.parseAccount() " + jObj);
        }

        Long code = (Long) jObj.get("code");
        if (code != null) {
            String msg = (String) jObj.get("msg");
            throw new RuntimeException("Account request error: code=" + code + ": " + msg);
        }

        AccountData accountData = new AccountData(Exchange.HUOBI, Double.MAX_VALUE);
        double btc = Utils.getDouble(jObj.get("available_btc_display"));
        accountData.setAvailable(Currency.BTC, btc);
        double cny = Utils.getDouble(jObj.get("available_cny_display"));
        accountData.setAvailable(Currency.CNH, cny);

        double btcLocked = Utils.getDouble(jObj.get("frozen_btc_display"));
        accountData.setAllocated(Currency.BTC, btcLocked);
        double cnyLocked = Utils.getDouble(jObj.get("frozen_cny_display"));
        accountData.setAllocated(Currency.CNH, cnyLocked);

        return accountData;
    }
}
