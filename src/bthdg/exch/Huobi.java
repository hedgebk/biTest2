package bthdg.exch;

import bthdg.Fetcher;
import bthdg.util.Md5;
import bthdg.util.Post;
import bthdg.util.Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.text.DecimalFormat;
import java.util.*;

// https://www.huobi.com/help/index.php?a=api_help
// https://www.huobi.com/help/index.php?a=api_help_v2
//  https://api.huobi.com/apiv2.php
// HUOBI allows only 1 request in 1 second.
// websocket: https://www.huobi.com/help/index.php?a=socket_help
public class Huobi extends BaseExch {
    private static final int READ_TIMEOUT = 10000; // todo: mk big for market; smaller for other orders
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int BETWEEN_REQUESTS_PERIOD = 2000;

    private static String SECRET;
    private static String KEY;
    public static boolean LOG_PARSE = false;
    public static boolean JOIN_SMALL_QUOTES = false;

    // supported pairs
    static final Pair[] PAIRS = {Pair.BTC_CNH, Pair.LTC_CNH};

    // supported currencies
    private static final Currency[] CURRENCIES = {Currency.BTC, Currency.CNH, Currency.LTC};

    private static final Map<Pair, DecimalFormat> s_amountFormatMap = new HashMap<Pair, DecimalFormat>();
    private static final Map<Pair, Double> s_minAmountStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, DecimalFormat> s_priceFormatMap = new HashMap<Pair, DecimalFormat>();
    private static final Map<Pair, Double> s_minExchPriceStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, Double> s_minOurPriceStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, Double> s_minOrderToCreateMap = new HashMap<Pair, Double>();

    private static final DecimalFormat MKT_AMOUNT_FORMAT = mkFormat("0.##");

    static {           // priceFormat minExchPriceStep  minOurPriceStep  amountFormat   minAmountStep   minOrderToCreate
        put(Pair.BTC_CNH, "0.##",     0.01,             0.01,            "0.0###",      0.0001,         0.01);
        put(Pair.LTC_CNH, "0.##",     0.01,             0.01,            "0.0###",      0.0001,         1);
    }

    private static long s_lastRequestTime = 0; // todo: probably maintain counter for each command type separately

    protected static void put(Pair pair, String priceFormat, double minExchPriceStep, double minOurPriceStep,
                              String amountFormat, double minAmountStep, double minOrderToCreate) {
        s_amountFormatMap.put(pair, mkFormat(amountFormat));
        s_minAmountStepMap.put(pair, minAmountStep);
        s_priceFormatMap.put(pair, mkFormat(priceFormat));
        s_minExchPriceStepMap.put(pair, minExchPriceStep);
        s_minOurPriceStepMap.put(pair, minOurPriceStep);
        s_minOrderToCreateMap.put(pair, minOrderToCreate);
    }

    @Override public int connectTimeout() { return CONNECT_TIMEOUT; }
    @Override public int readTimeout() { return READ_TIMEOUT; }
    @Override public String getNextNonce() { return null; }
    @Override protected String getCryproAlgo() { return null; }
    @Override protected String getSecret() { return null; }
    @Override protected String getApiEndpoint() { return "https://api.huobi.com/apiv3"; }
    @Override public Pair[] supportedPairs() { return PAIRS; }
    @Override public Currency[] supportedCurrencies() { return CURRENCIES; }
    @Override public boolean requireConversionPrice(OrderType type, OrderSide side) {
            return (type == OrderType.MARKET) && (side == OrderSide.BUY);
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
        distributeRatio.put(Currency.LTC, 0.0);
        distributeRatio.put(Currency.CNH, 0.5);
        FundMap.s_map.put(Exchange.HUOBI, distributeRatio);
    }

    private static Map<Long, String> ERROR_CODES = new HashMap<Long, String>();

    static {
        add(1,  "Server error");
        add(2,  "Insufficient CNY");
        add(3,  "Restarting failed - transaction has started, you can not start again");
        add(4,  "Transaction is over - transaction has ended");
        add(10, "Insufficient BTC");
        add(11, "Insufficient LTC");
        add(18, "funding password error - Incorrect payment password");
        add(26, "Order is not existed");
        add(41, "Unable to modify due to filled order, can not be modified");
        add(42, "Order has been cancelled, unable to modify");
        add(44, "Price is too low");
        add(45, "Price is too high");
        add(46, "The small number of transaction, the minimum number 0.001");
        add(47, "Exceed the limit amount");
        add(48, "Buy the amount can not be less than 1 yuan");
        add(55, "105% higher than current price, not allowed");
        add(56, "95% lower than current price, not allowed");
        add(63, "Request parameter is incorrect");
        add(64, "Invalid request");
        add(65, "Invalid method");
        add(66, "Invalid access key");
        add(67, "Invalid private key");
        add(68, "Invalid price");
        add(69, "Invalid amount");
        add(70, "Invalid submitting time - Invalid POST time");
        add(71, "Too many requests - Overflowed requests");
        add(87, "Buying price cannot exceed 101% of last price when transaction amount is less than 0.1 BTC.");
        add(88, "Selling price cannot below 99% of last price when transaction amount is less than 0.1 BTC.");
        add(89, "Buying price cannot exceed 1% of market price when transaction amount is less than 0.1 BTC.");
        add(90, "Selling price cannot be lower 1% of market price when transaction amount is less than 0.1 BTC.");
        add(91, "Invalid currency");
        add(92, "110% of the purchase price can not be higher than current prices");
        add(93, "selling price can not be less than 90% of the price of");
        add(97, "trading capital you have opened your password, please submit funding password parameters - Please enter payment password.");
        add(107, "Order is exist.");
        add(112, "Purchase amount of market order is rounded to 2 decimal places");
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
            init(Config.loadKeys());
        } catch (Exception e) {
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

        List<Post.NameValue> postParams = new ArrayList<Post.NameValue>();
        postParams.add(new Post.NameValue("method", method));
        postParams.add(new Post.NameValue("access_key", KEY));
        addCommandParams(postParams, command, options);
        postParams.add(new Post.NameValue("created", created));
        postParams.add(new Post.NameValue("sign", sign));
        final String postData = Post.buildPostQueryString(postParams);

        return new IPostData() {
            @Override public String postStr() { return postData; }
            @Override public Map<String, String> headerLines() { return null; }
        };
    }

    private void addCommandParams(List<Post.NameValue> postParams, Fetcher.FetchCommand command, Fetcher.FetchOptions options) {
        if (command == Fetcher.FetchCommand.ACCOUNT) {
        } else if (command == Fetcher.FetchCommand.ORDERS) {
            postParams.add(new Post.NameValue("coin_type", getCoinType(options.getPair())));
        } else if (command == Fetcher.FetchCommand.ORDER) {
            OrderData od = options.getOrderData();
            postParams.add(new Post.NameValue("coin_type", getCoinType(od.m_pair)));
            if (od.m_type == OrderType.LIMIT) { // skip for MARKET orders
                postParams.add(new Post.NameValue("price", getPrice(od)));
            }
            postParams.add(new Post.NameValue("amount", getAmount(od)));
        } else if (command == Fetcher.FetchCommand.CANCEL) {
            postParams.add(new Post.NameValue("coin_type", getCoinType(options.getPair())));
            postParams.add(new Post.NameValue("id", options.getOrderId()));
        } else if (command == Fetcher.FetchCommand.ORDER_STATUS) {
            postParams.add(new Post.NameValue("coin_type", getCoinType(options.getPair())));
            postParams.add(new Post.NameValue("id", options.getOrderId()));
        } else {
            throw new RuntimeException("not supported command=" + command);
        }
    }

    private void addCommandParams(Map<String, String> sArray, Fetcher.FetchCommand command, Fetcher.FetchOptions options) {
        if (command == Fetcher.FetchCommand.ACCOUNT) {
        } else if (command == Fetcher.FetchCommand.ORDERS) {
            sArray.put("coin_type", getCoinType(options.getPair()));
        } else if (command == Fetcher.FetchCommand.ORDER) {
            OrderData od = options.getOrderData();
            sArray.put("coin_type", getCoinType(od.m_pair));
            if (od.m_type == OrderType.LIMIT) { // skip for MARKET orders
                sArray.put("price", getPrice(od));
            }
            sArray.put("amount", getAmount(od));
        } else if (command == Fetcher.FetchCommand.CANCEL) {
            sArray.put("coin_type", getCoinType(options.getPair()));
            sArray.put("id", options.getOrderId());
        } else if (command == Fetcher.FetchCommand.ORDER_STATUS) {
            sArray.put("coin_type", getCoinType(options.getPair()));
            sArray.put("id", options.getOrderId());
        } else {
            throw new RuntimeException("Huobi: not supported command=" + command);
        }
    }

    private String getPrice(OrderData od) {
        return roundPriceStr(od.m_price, od.m_pair);
    }

    private String getAmount(OrderData od) {
        double amount = od.m_amount;
        String roundAmountStr;
        if ((od.m_type == OrderType.MARKET) && (od.m_side == OrderSide.BUY)) {
            double price = od.m_price;
            if (price == 0) {
                throw new RuntimeException("conversion price required for SELL@MARKET orders");
            }
            amount *= price;
            roundAmountStr = MKT_AMOUNT_FORMAT.format(amount);
        } else {
            roundAmountStr = roundAmountStr(amount, od.m_pair);
        }
        return roundAmountStr;
    }

    private String getCoinType(Pair pair) {
        switch (pair) {
            case BTC_CNH: return "1";
            case LTC_CNH: return "2";
            default: throw new RuntimeException("Huobi: not supported pair: " + pair);
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
            return od.m_type == OrderType.LIMIT
                    ? (side == OrderSide.BUY) ? "buy" : "sell" // LIMIT
                    :  (side == OrderSide.BUY) ? "buy_market" : "sell_market"; // MARKET
        }
        if(command == Fetcher.FetchCommand.CANCEL) {
            return "cancel_order";
        }
        if(command == Fetcher.FetchCommand.ORDER_STATUS) {
            return "order_info";
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
            log("Huobi.parseAccount() " + jObj);
        }

        Object codeObj = jObj.get("code");
        if (codeObj != null) {
            Long code = null;
            if (codeObj instanceof Long) {
                code = (Long) codeObj;
            } else if (codeObj instanceof String) {
                try {
                    code = Long.parseLong((String) codeObj);
                } catch (NumberFormatException e) { /*noop*/ }
            }

            if (code != null) {
                String msg = getErrorString(code);
                log(" error[code=" + code + "]: " + msg);
                throw new RuntimeException("Account request error: " + msg);
            } else {
                log("Huobi.parseAccount() some error: " + jObj);
                throw new RuntimeException("Account request error: codeObj: " + codeObj);
            }
        }

        AccountData accountData = new AccountData(Exchange.HUOBI, 0);
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

//        "loan_cny_display" - CNY Loan Amount
//        "loan_btc_display" - BTC Loan Amount
//        "loan_ltc_display" - LTC Loan Amount

        return accountData;
    }

    public static OrdersData parseOrders(Object obj, Pair pair) {
//        if (LOG_PARSE) {
            log("Huobi.parseOrders() " + obj);
//        }
        if (obj instanceof JSONArray) {
            // [{"id":26843461,"order_amount":"0.0166","processed_amount":"0.0000","order_time":1405284471,"type":2,"order_price":"3865.10"}]
            JSONArray orders = (JSONArray) obj;
            int size = orders.size();
            Map<String, OrdersData.OrdData> ords = new HashMap<String, OrdersData.OrdData>();
            for (int i = 0; i < size; i++) {
                JSONObject order = (JSONObject) orders.get(i);
                OrdersData.OrdData ord = parseOrdData(pair, order);
                long orderTime = Utils.getLong(order.get("order_time"));
                ord.m_createTime = orderTime;
                ords.put(ord.m_orderId, ord);
            }
            return new OrdersData(ords);
        } else if (obj instanceof JSONObject) {
            JSONObject jObj = (JSONObject) obj;
            String msg = parseError(jObj);
            log(" error: " + msg);
            return new OrdersData(msg);
        } else {
            throw new RuntimeException("not expected object class '" + obj.getClass().getName() + "': " + obj);
        }
    }

    protected static OrdersData.OrdData parseOrdData(Pair pair, JSONObject order) {
        String orderId = order.get("id").toString();
        double orderAmount = Utils.getDouble(order.get("order_amount"));
        double filled = Utils.getDouble(order.get("processed_amount"));
        double remainedAmount = orderAmount - filled;
        double rate = Utils.getDouble(order.get("order_price"));
        Long type = (Long) order.get("type");
        return new OrdersData.OrdData(orderId, orderAmount, filled, remainedAmount, rate, 0, null, pair,
                                      getOrderSide(type), getOrderType(type));
    }

    private static OrderSide getOrderSide(Long type) {
        return (type.equals(1L) || type.equals(3L))
                ? OrderSide.BUY
                : (type.equals(2L) || type.equals(4L))
                    ? OrderSide.SELL
                    : null;
    }

    private static OrderType getOrderType(Long type) {
        return (type.equals(1L) || type.equals(2L))
                ? OrderType.LIMIT
                : (type.equals(3L) || type.equals(4L))
                    ? OrderType.MARKET
                    : null;
    }

    public static PlaceOrderData parseOrder(Object obj) {
        JSONObject jObj = (JSONObject) obj;
//        if (LOG_PARSE) {
            log("Huobi.parseOrder() " + jObj);
//        }

        // {"id":26843461,"result":"success"}
        Object result = jObj.get("id");
        if( result != null ) {
            long orderId = Utils.getLong(result);
            return new PlaceOrderData(orderId);
        }

        String msg = parseError(jObj);
        log(" error: " + msg);
        return new PlaceOrderData(msg); // order is not placed
    }

    public static CancelOrderData parseCancelOrders(Object obj) {
        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("Huobi.parseCancelOrders() " + jObj);
        }

        // {"result":"success"} | "result":"fail"
        Object result = jObj.get("result");
        if (result != null) {
//            Long code = (Long) jObj.get("code");
            if (result.equals("success")) {
                return new CancelOrderData(null, null);
            }
        }
        String errMsg = parseError(jObj);
        log(" error: " + errMsg);
        return new CancelOrderData(errMsg);
    }

    private static String parseError(JSONObject jObj) {
        // {"time":1405207309,"code":2,"msg":"IYTRUTEYW"}
        Long code = (Long) jObj.get("code");
        if (code != null) {
            return getErrorString(code);
        }
        return "Unable to parse error. msg: " + jObj;
    }

    private static String getErrorString(Long code) {
        String error = ERROR_CODES.get(code);
        return "errorCode: " + code + ": " + error;
    }

    public static OrderStatusData parseOrderStatus(Object obj, Pair pair) {
        // {"msg":"YTYTIIUTY","code":63,"message":"YTYTIIUTY"}
        // {"total":"11.64","processed_price":"1974.50","processed_amount":"11.64","order_amount":"11.75","fee":"0.00","order_price":"0.00","vot":"11.64","id":352020770,"type":3,"status":2}
log("  parseOrderStatus: " + obj);
        JSONObject order = (JSONObject) obj;
        if (LOG_PARSE) {
            log("Huobi.parseOrderStatus() " + order);
        }

        Object errorCode = order.get("code");
        if (errorCode == null) {
            OrdersData.OrdData ord = parseOrdData(pair, order);
            String status = order.get("status").toString();
            ord.m_status = status;
            ord.m_orderStatus = getOrderStatus(status);
            ord.m_avgPrice = Utils.getDouble(order.get("processed_price"));

            // jObj={
            //          "id":351554291,
            //          "order_amount":"20.00",
            //          "processed_amount":"19.99", // Filled Amount
            //          "type":3,
            //          "order_price":"0.00",
            //
            //          "total":"19.99", // Total Transaction Amount
            //          "processed_price":"1999.13", // Average Filled Price
            //          "fee":"0.00",  // Trading Fee
            //          "vot":"19.99",  // Volume
            //          "status":2
            // }
            return new OrderStatusData(ord);
        }
        String errMsg = parseError(order);
        log(" error: " + errMsg);
        return new OrderStatusData(errMsg);
    }

    private static OrderStatus getOrderStatus(String status) {
        // Status　0 -Waiting　1 -Partially filled　2 -Filled　3 -Cancelled   7?
        if (status.equals("0")) {
            return OrderStatus.SUBMITTED;
        }
        if (status.equals("1")) {
            return OrderStatus.PARTIALLY_FILLED;
        }
        if (status.equals("2")) {
            return OrderStatus.FILLED;
        }
        if (status.equals("3")) {
            return OrderStatus.CANCELLED;
        }
        return null;
    }

    public static void waitIfNeeded() {
        long now = System.currentTimeMillis();
        long diff = now - s_lastRequestTime;
        long wait = BETWEEN_REQUESTS_PERIOD - diff;
log("~~~~~~ diff=" + diff + "; wait="+wait);
        if( wait > 0 ) {
            wait = Math.min(wait, BETWEEN_REQUESTS_PERIOD); // just in case
            log("wait " + wait + " ms before request");
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) { /*noop*/ }
            s_lastRequestTime = System.currentTimeMillis();
        } else {
            s_lastRequestTime = now;
        }
    }
}
