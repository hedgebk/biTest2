package bthdg.exch;

import bthdg.Fetcher;
import bthdg.util.Md5;
import bthdg.util.Post;
import bthdg.util.Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

// https://www.huobi.com/help/index.php?a=api_help
// https://www.huobi.com/help/index.php?a=api_help_v2
//  https://api.huobi.com/apiv2.php
// HUOBI allows only 1 request in 1 second.
public class Huobi extends BaseExch {
    private static final int READ_TIMEOUT = 10000;

    private static String SECRET;
    private static String KEY;
    public static boolean LOG_PARSE = false;
    public static boolean JOIN_SMALL_QUOTES = false;

    // supported pairs
    static final Pair[] PAIRS = {Pair.BTC_CNH };

    // supported currencies
    private static final Currency[] CURRENCIES = { Currency.BTC, Currency.CNH };

    private static final Map<Pair, DecimalFormat> s_amountFormatMap = new HashMap<Pair, DecimalFormat>();
    private static final Map<Pair, Double> s_minAmountStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, DecimalFormat> s_priceFormatMap = new HashMap<Pair, DecimalFormat>();
    private static final Map<Pair, Double> s_minExchPriceStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, Double> s_minOurPriceStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, Double> s_minOrderToCreateMap = new HashMap<Pair, Double>();

    static {           // priceFormat minExchPriceStep  minOurPriceStep  amountFormat   minAmountStep   minOrderToCreate
        put(Pair.BTC_CNH, "0.##",     0.01,             0.02,            "0.0###",      0.0001,         0.001);
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

    @Override public int readTimeout() { return READ_TIMEOUT; };
    @Override public String getNextNonce() { return null; }
    @Override protected String getCryproAlgo() { return null; }
    @Override protected String getSecret() { return null; }
    @Override protected String getApiEndpoint() { return "https://api.huobi.com/api.php"; }
    @Override public Pair[] supportedPairs() { return PAIRS; }
    @Override public Currency[] supportedCurrencies() { return CURRENCIES; };

    @Override protected DecimalFormat priceFormat(Pair pair) { return s_priceFormatMap.get(pair); }
    @Override protected DecimalFormat amountFormat(Pair pair) { return s_amountFormatMap.get(pair); }
    @Override public double minOurPriceStep(Pair pair) { return s_minOurPriceStepMap.get(pair); }
    @Override public double minExchPriceStep(Pair pair) { return s_minExchPriceStepMap.get(pair); }
    @Override public double minAmountStep(Pair pair) { return s_minAmountStepMap.get(pair); }
    @Override public double minOrderToCreate(Pair pair) { return s_minOrderToCreateMap.get(pair); }

    @Override public void initFundMap() {
        Map<Currency,Double> distributeRatio = new HashMap<Currency, Double>();
        distributeRatio.put(Currency.BTC, 0.5);
        distributeRatio.put(Currency.CNH, 0.5);
        FundMap.s_map.put(Exchange.BTCN, distributeRatio);
    }

    private static Map<Long, String> ERROR_CODES = new HashMap<Long, String>();

    static {
        add(1,  "Server error");
        add(2,  "Insufficient CNY");
        add(3,  "Restarting failed");
        add(4,  "Transaction is over");
        add(10, "Insufficient BTC");
        add(26, "Order is not existed");
        add(41, "Unable to modify due to filled order");
        add(42, "Order has been cancelled, unable to modify");
        add(44, "Price is too low");
        add(45, "Price is too high");
        add(46, "Minimum amount is 0.001");
        add(47, "Exceed the limit amount");
        add(55, "105% higher than current price, not allowed");
        add(56, "95% lower than current price, not allowed");
        add(64, "Invalid request");
        add(65, "Invalid method");
        add(66, "Invalid access key");
        add(67, "Invalid private key");
        add(68, "Invalid price");
        add(69, "Invalid amount");
        add(70, "Invalid submitting time");
        add(71, "Too many requests");
        add(87, "Buying price cannot exceed 101% of last price when transaction amount is less than 0.1 BTC.");
        add(88, "Selling price cannot below 99% of last price when transaction amount is less than 0.1 BTC.");
    }

    private static void add(long code, String str) {
        ERROR_CODES.put(code, str);
    }

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

    // https://github.com/philsong/btcrobot/blob/master/src/huobi/tradeapi.go
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
        sign = sign.toLowerCase(); // MD5 signatures must be lowercased

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
        String method = getMethod(command, options);

        long time = System.currentTimeMillis() / 1000;
        String created = Long.toString(time);

        Map<String,String> sArray = new HashMap<String, String>();
        sArray.put("method", method);
        sArray.put("access_key", KEY);
        addCommandParams(sArray, command, options);
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
        addCommandParams(postParams, command, options);
        final String postData = Post.buildPostQueryString(postParams);

        return new IPostData() {
            @Override public String postStr() { return postData; }
            @Override public Map<String, String> headerLines() { return null; }
        };
    }

    private void addCommandParams(List<Post.NameValue> postParams, Fetcher.FetchCommand command, Fetcher.FetchOptions options) {
        if(command == Fetcher.FetchCommand.ORDER) {
            OrderData od = options.getOrderData();
            Pair pair = od.m_pair;
            String priceStr = roundPriceStr(od.m_price, pair);
            postParams.add(new Post.NameValue("price", priceStr));
            String amountStr = roundAmountStr(od.m_amount, pair);
            postParams.add(new Post.NameValue("amount", amountStr));
        } else if(command == Fetcher.FetchCommand.CANCEL) {
            String orderId = options.getOrderId();
            postParams.add(new Post.NameValue("id", orderId));
        }
    }

    private void addCommandParams(Map<String, String> sArray, Fetcher.FetchCommand command, Fetcher.FetchOptions options) {
        if(command == Fetcher.FetchCommand.ORDER) {
            OrderData od = options.getOrderData();
            Pair pair = od.m_pair;
            String priceStr = roundPriceStr(od.m_price, pair);
            sArray.put("price", priceStr);
            String amountStr = roundAmountStr(od.m_amount, pair);
            sArray.put("amount", amountStr);
        } else if(command == Fetcher.FetchCommand.CANCEL) {
            String orderId = options.getOrderId();
            sArray.put("id", orderId);
        }
    }

    private String getMethod(Fetcher.FetchCommand command, Fetcher.FetchOptions options) {
        if(command == Fetcher.FetchCommand.ACCOUNT) {
            return "get_account_info";
        }
        if(command == Fetcher.FetchCommand.ORDERS) {
            return "get_orders";
        }
        if(command == Fetcher.FetchCommand.ORDER) {
            OrderData od = options.getOrderData();
            OrderSide side = od.m_side;
            return (side == OrderSide.BUY) ? "buy" : "sell";
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
//        if (LOG_PARSE) {
            log("OkCoin.parseAccount() " + jObj);
//        }

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
        double ltc = Utils.getDouble(jObj.get("available_ltc_display"));
        accountData.setAvailable(Currency.LTC, ltc);

        double btcLocked = Utils.getDouble(jObj.get("frozen_btc_display"));
        accountData.setAllocated(Currency.BTC, btcLocked);
        double cnyLocked = Utils.getDouble(jObj.get("frozen_cny_display"));
        accountData.setAllocated(Currency.CNH, cnyLocked);
        double ltcLocked = Utils.getDouble(jObj.get("frozen_ltc_display"));
        accountData.setAllocated(Currency.LTC, ltcLocked);

        return accountData;
    }

    public static OrdersData parseOrders(Object obj) {
        JSONArray jArr = (JSONArray) obj;
//        if (LOG_PARSE) {
            log("OkCoin.parseOrders() " + jArr);
//        }

//        Field name	Type	    Description
//        method	    Required	Request method get_orders
//        access_key	Required	Access key
//        created	    Required	Submission time 10 integers timestamp
//        sign	    Required	MD5 signatures

//        Field name	        Description
//        id	                Ordersid
//        type	            buy1 2sell
//        order_price	        Order price
//        order_amount	    Order amount
//        processed_amount	Completed amount
//        order_time	        Order time

        Map<String,OrdersData.OrdData> ords = new HashMap<String,OrdersData.OrdData>();
//        for(Pair pair : PAIRS) {
//            String pairParam = getPairParam(pair);
//            String key = "order_" + pairParam;
//            JSONArray orders = (JSONArray) result.get(key);
//            int size = orders.size();
//            for(int i = 0; i < size; i++) {
//                JSONObject order = (JSONObject) orders.get(i);
////                            "currency": "CNY",
////                            "date": 1396255376,
//                String orderId = ((Long) order.get("id")).toString();
//                String type = (String) order.get("type");
//                double rate = Utils.getDouble(order.get("price"));
//                double remainedAmount = Utils.getDouble(order.get("amount"));
//                double orderAmount = Utils.getDouble(order.get("amount_original"));
//                String status = Utils.getString(order.get("status"));
//                OrdersData.OrdData ord = new OrdersData.OrdData(orderId, orderAmount, remainedAmount, rate, -1l, status, pair, getOrderSide(type));
//                ords.put(orderId, ord);
//            }
//        }
        return new OrdersData(ords);
    }

    public static PlaceOrderData parseOrder(Object obj) {
        JSONObject jObj = (JSONObject) obj;
//        if (LOG_PARSE) {
            log("Huobi.parseOrder() " + jObj);
//        }

        // {"time":1405207309,"code":2,"msg":"没有足够的人民币"}
        Long code = (Long) jObj.get("code");
        if (code == null) {
            return null;
        } else {
            String error = ERROR_CODES.get(code);
            String msg = "errorCode: " + code + ": " + error;
            return new PlaceOrderData(msg); // order is not placed
        }
    }
}
