package bthdg.exch;

import bthdg.Fetcher;
import bthdg.util.Md5;
import bthdg.util.Post;
import bthdg.util.Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.text.DecimalFormat;
import java.util.*;

/** https://www.okcoin.com/about/publicApi.do
 * https://www.okcoin.com/t-1000097.html
 * webSocket https://www.okcoin.cn/about/webSocket.do
 *           http://docs.oracle.com/javaee/7/tutorial/doc/websocket003.htm */
public class OkCoin extends BaseExch {
    private static String SECRET;
    private static String PARTNER;
    public static boolean LOG_PARSE = false;
    public static boolean JOIN_SMALL_QUOTES = false;
    public static final int CONNECT_TIMEOUT = 10000; // todo: mk big for market; smaller for other orders
    public static final int READ_TIMEOUT = 11000;

    // supported pairs
    static final Pair[] PAIRS = {Pair.BTC_CNH, Pair.LTC_CNH };

    // supported currencies
    private static final Currency[] CURRENCIES = { Currency.BTC, Currency.LTC, Currency.CNH };

    private static final Map<Pair, DecimalFormat> s_amountFormatMap = new HashMap<Pair, DecimalFormat>();
    private static final Map<Pair, Double> s_minAmountStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, DecimalFormat> s_priceFormatMap = new HashMap<Pair, DecimalFormat>();
    private static final Map<Pair, Double> s_minExchPriceStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, Double> s_minOurPriceStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, Double> s_minOrderToCreateMap = new HashMap<Pair, Double>();

    static {           // priceFormat minExchPriceStep  minOurPriceStep  amountFormat   minAmountStep   minOrderToCreate
        put(Pair.BTC_CNH, "0.00",     0.01,             0.01,            "0.0##",       0.001,          0.01);
        put(Pair.LTC_CNH, "0.00",     0.01,             0.01,            "0.0##",       0.001,          0.1);
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

    private static Map<Long, String> ERROR_CODES = new HashMap<Long, String>();

    static {
        add(10000, "Required parameter can not be null");
        add(10001, "Requests are too frequent");
        add(10002, "System Error");
        add(10003, "Restricted list request, please try again later");
        add(10004, "IP restriction. not request the resource");
        add(10005, "Key does not exist");
        add(10006, "User does not exist");
        add(10007, "Signatures do not match");
        add(10008, "Illegal parameter");
        add(10009, "Order does not exist");
        add(10010, "Insufficient balance");
        add(10011, "Order is less than minimum trade amount");
        add(10012, "Unsupported symbol (not btc_cny or ltc_cny)");
        add(10013, "This interface only accepts https requests");
        add(10014, "Single price shall ≤ 0 or ≥ 1000000");
        add(10015, "Single price and the latest transaction price deviation is too large");
    }

    private static void add(long code, String str) {
        ERROR_CODES.put(code, str);
    }

    @Override public int connectTimeout() { return CONNECT_TIMEOUT; }
    @Override public int readTimeout() { return READ_TIMEOUT; }
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
        distributeRatio.put(Currency.LTC, 0.0);
        FundMap.s_map.put(Exchange.OKCOIN, distributeRatio);
    }

    @Override public String getNextNonce() { return null; }
    @Override protected String getCryproAlgo() { return null; }
    @Override protected String getSecret() { return null; }
    @Override protected String getApiEndpoint() { return null; }
    @Override public Pair[] supportedPairs() { return PAIRS; }
    @Override public Currency[] supportedCurrencies() { return CURRENCIES; };
    @Override public boolean requireConversionPrice(OrderType type, OrderSide side) {
        return (type == OrderType.MARKET) && (side == OrderSide.BUY);
    }

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
            init(Config.loadKeys());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("error reading properties");
        }
    }

    public static boolean init(Properties properties) {
        SECRET = properties.getProperty("okcoin_secret");
        if(SECRET != null) {
            PARTNER = properties.getProperty("okcoin_partner");
            if(PARTNER != null) {
                return true;
            }
        }
        return false;
    }

    private void run() {
        try {
            Fetcher.LOG_JOBJ = true;
            Fetcher.COUNT_TRAFFIC = true;
            Fetcher.LOG_LOADING_TIME = true;

            TopData topData = Fetcher.fetchTop(Exchange.OKCOIN, Pair.BTC_CNH);
            log("topData: " + topData);
            topData = Fetcher.fetchTop(Exchange.OKCOIN, Pair.LTC_CNH);
            log("topData: " + topData);

            DeepData deepData = Fetcher.fetchDeep(Exchange.OKCOIN, Pair.BTC_CNH);
            log("deepData: " + deepData);
            deepData = Fetcher.fetchDeep(Exchange.OKCOIN, Pair.LTC_CNH);
            log("deepData: " + deepData);

//            acct();
//            AccountData account = Fetcher.fetchAccount(Exchange.OKCOIN);
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
            case BTC_CNH: return "btc_cny";
            case LTC_CNH: return "ltc_cny";
            default: throw new RuntimeException("not supported pair: " + pair);
        }
    }

    private static Pair getPair(String pair) {
        if (pair.equals("btc_cny")) { return Pair.BTC_CNH; }
        if (pair.equals("ltc_cny")) { return Pair.LTC_CNH; }
        return null;
    }

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
        double ask = Utils.getDouble(ticker, "sell");
        double bid = Utils.getDouble(ticker, "buy");
        return new TopData(bid, ask, last);
    }

    public static DeepData parseDeep(Object jObj, Pair pair) {
        return parseDeepInt(jObj, pair);
    }

    private static DeepData parseDeepInt(Object obj, Pair pair) {
        if (LOG_PARSE) {
            log("OkCoin.parseDeep() " + obj);
        }
        JSONObject pairData = (JSONObject) obj;
        JSONArray bids = (JSONArray) pairData.get("bids");
        JSONArray asks = (JSONArray) pairData.get("asks");

        DeepData deep = DeepData.create(bids, asks);
        if (JOIN_SMALL_QUOTES) {
            deep.joinSmallQuotes(Exchange.BTCE, pair);
        }
        return deep;
    }

    private void acct() throws Exception {
        Map<String,String> sArray = new HashMap<String, String>();
      	sArray.put("partner", PARTNER);
        String sign = buildMysign(sArray, SECRET);

        List<Post.NameValue> postParams = new ArrayList<Post.NameValue>();
        postParams.add(new Post.NameValue("partner", PARTNER));
        postParams.add(new Post.NameValue("sign", sign));
        String postData = Post.buildPostQueryString(postParams);

        initSsl();

        String json = loadJsonStr(null, postData);
        log("Loaded json: " + json);
    }

    public static String buildMysign(Map<String, String> sArray, String secretKey) {
        String mysign = "";
        try {
            String prestr = Post.createHttpPostString(sArray, true);
            prestr = prestr + secretKey;
            mysign = Md5.getMD5String(prestr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mysign;
    }

    public static AccountData parseAccount(Object obj) {
        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("OkCoin.parseAccount() " + jObj);
        }

        JSONObject info = (JSONObject) jObj.get("info");
        JSONObject funds = (JSONObject) info.get("funds");
        JSONObject free = (JSONObject) funds.get("free");
        JSONObject freezed = (JSONObject) funds.get("freezed");
        AccountData accountData = parseFunds(free, freezed);
        return accountData;

        // {"info":{"funds":{"free":{"btc":"0","cny":"0","ltc":"0"},"freezed":{"btc":"0","cny":"0","ltc":"0"}}},"result":true}
    }
    private static AccountData parseFunds(JSONObject free, JSONObject freezed) {
        AccountData accountData = new AccountData(Exchange.OKCOIN, Double.MAX_VALUE);
        double btc = Utils.getDouble(free.get("btc"));
        accountData.setAvailable(Currency.BTC, btc);
        double ltc = Utils.getDouble(free.get("ltc"));
        accountData.setAvailable(Currency.LTC, ltc);
        double cny = Utils.getDouble(free.get("cny"));
        accountData.setAvailable(Currency.CNH, cny);

        double btcAllocated = Utils.getDouble(freezed.get("btc"));
        accountData.setAllocated(Currency.BTC, btcAllocated);
        double ltcAllocated = Utils.getDouble(freezed.get("ltc"));
        accountData.setAllocated(Currency.LTC, ltcAllocated);
        double cnyAllocated = Utils.getDouble(freezed.get("cny"));
        accountData.setAllocated(Currency.CNH, cnyAllocated);
        return accountData;
    }

    public static PlaceOrderData parseOrder(Object obj) {
        // {"result":true, "order_id":123456}
        //  "result":false,"errorCode":10000
        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("OkCoin.parseOrder() " + jObj);
        }
        Boolean result = (Boolean) jObj.get("result");
        if( result ) {
            long orderId = Utils.getLong(jObj.get("order_id"));
            return new PlaceOrderData(orderId);
        } else {
            String msg = parseError(jObj);
            log(msg);
            return new PlaceOrderData(msg); // order is not placed
        }
    }

    @Override public IPostData getPostData(Exchange.UrlDef apiEndpoint, Fetcher.FetchCommand command, Fetcher.FetchOptions options) throws Exception {
        switch (command) {
            case ACCOUNT: {
                Map<String,String> sArray = new HashMap<String, String>();
              	sArray.put("partner", PARTNER);
                String sign = buildMysign(sArray, SECRET);
                return getPostData(sArray, sign);
            }
            case ORDER: {
                OrderData order = options.getOrderData();
                String amountStr = getAmount(order);
                Pair pair = order.m_pair;

                Map<String, String> sArray = new HashMap<String, String>();
                sArray.put("partner", PARTNER);
                sArray.put("symbol", getPairParam(pair));
                sArray.put("type", getOrderSideString(order));
                if (order.m_type == OrderType.LIMIT) { // LIMIT
                    String priceStr = roundPriceStr(order.m_price, pair);
                    sArray.put("rate", priceStr);
                    sArray.put("amount", amountStr);
                } else { // MARKET
                    sArray.put(order.m_side.isBuy() ? "rate" : "amount", amountStr);
                }
                String sign = buildMysign(sArray, SECRET);
                return getPostData(sArray, sign);
            }
            case ORDERS: {
                Pair pair = options.getPair();
                Map<String, String> sArray = new HashMap<String, String>();
                sArray.put("partner", PARTNER);
                sArray.put("order_id", "-1");
                sArray.put("symbol", getPairParam(pair));
                String sign = buildMysign(sArray, SECRET);
                return getPostData(sArray, sign);
            }
            case CANCEL: {
                Pair pair = options.getPair();
                Map<String, String> sArray = new HashMap<String, String>();
                sArray.put("partner", PARTNER);
                sArray.put("order_id", options.getOrderId());
                sArray.put("symbol", getPairParam(pair));
                String sign = buildMysign(sArray, SECRET);
                return getPostData(sArray, sign);
            }
            case ORDER_STATUS: {
                Pair pair = options.getPair();
                Map<String, String> sArray = new HashMap<String, String>();
                sArray.put("partner", PARTNER);
                sArray.put("order_id", options.getOrderId());
                sArray.put("symbol", getPairParam(pair));
                String sign = buildMysign(sArray, SECRET);
                return getPostData(sArray, sign);
            }
        }
        throw new RuntimeException("not supported command=" + command);
    }

    private String getAmount(OrderData od) {
        double amount = od.m_amount;
        if ((od.m_type == OrderType.MARKET) && (od.m_side == OrderSide.BUY)) {
            double price = od.m_price;
            if (price == 0) {
                throw new RuntimeException("conversion price required for SELL@MARKET orders");
            }
            amount = amount * price;
        }
        return roundAmountStr(amount, od.m_pair);
    }

    private String getOrderSideString(OrderData order) {
        return (order.m_type == OrderType.MARKET)
                ? order.m_side.isBuy() ? "buy_market" : "sell_market" // MARKET
                : getOrderSideStr(order); // LIMIT
    }

    private IPostData getPostData(Map<String, String> sArray, String sign) {
        sArray.put("sign", sign);
        final String postData = Post.createHttpPostString(sArray, false);
        return new IPostData() {
            @Override public String postStr() { return postData; }
            @Override public Map<String, String> headerLines() { return null; }
        };
    }

    public static OrdersData parseOrders(Object obj) {
        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("OkCoin.parseOrders() " + jObj);
        }
        Boolean result = (Boolean) jObj.get("result");
        if( result ) {
            JSONArray orders = (JSONArray) jObj.get("orders");
            int size = orders.size();
            Map<String,OrdersData.OrdData> ords = new HashMap<String,OrdersData.OrdData>();
            for(int i = 0; i < size; i++) {
                JSONObject order = (JSONObject) orders.get(i);
                OrdersData.OrdData ord = parseOrdData(order);
                ords.put(ord.m_orderId, ord);
            }
            return new OrdersData(ords);
        } else {
            String msg = parseError(jObj);
            log(msg);
            return new OrdersData(msg); // orders error
        }
    }

    public static CancelOrderData parseCancelOrders(Object obj) {
// {"result":true, "order_id":123456}
// {"result":false,"errorCode":10000}
        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("OkCoin.parseCancelOrders() " + jObj);
        }
        try {
            Boolean result = (Boolean) jObj.get("result");
            if( result ) {
                String orderId = Utils.getString(jObj.get("order_id"));
                return new CancelOrderData(orderId, null);
            } else { // we may try to cancel already filled order
                String msg = parseError(jObj);
                log(msg);
                return new CancelOrderData(msg);
            }
        } catch (Exception e) {
            String msg = "OkCoin.parseCancelOrders error: " + e;
            err(msg, e);
            log(" jObj=" + jObj);
            return new CancelOrderData(msg);
        }
    }

    public static OrderStatusData parseOrderStatus(Object obj) {
//  {"result":false,"errorCode":10012}
//  {"result":true,"orders":[
//      {   "symbol":"btc_cny",
//          "amount":0,
//          "orders_id":1201806353,
//          "rate":20,
//          "avg_rate":1930.01,
//          "type":"buy_market",
//          "deal_amount":0.01,
//          "createDate":1445991766000,
//          "status":2}]}

        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("OkCoin.parseOrderStatus() " + jObj);
        }
        try {
            Boolean result = (Boolean) jObj.get("result");
            if( result ) {
log("OkCoin.parseOrderStatus() " + jObj);

                JSONArray orders = (JSONArray) jObj.get("orders");

                JSONObject order = (JSONObject) orders.get(0);
                OrdersData.OrdData ord = parseOrdData(order);
                ord.m_orderStatus = getOrderStatus(ord.m_status);

                return new OrderStatusData(ord);
            } else { // we may try to cancel already filled order
                String msg = parseError(jObj);
                log(msg);
                return new OrderStatusData(msg);
            }
        } catch (Exception e) {
            String msg = "OkCoin.parseOrderStatus error: " + e;
            err(msg, e);
            log(" jObj=" + jObj);
            return new OrderStatusData(msg);
        }
    }

    private static OrderStatus getOrderStatus(String status) {
        // -1: cancelled, 0: unfilled, 1: partially filled, 2: fully filled
        if (status.equals("0")) {
            return OrderStatus.SUBMITTED;
        }
        if (status.equals("1")) {
            return OrderStatus.PARTIALLY_FILLED;
        }
        if (status.equals("2")) {
            return OrderStatus.FILLED;
        }
        if (status.equals("-1")) {
            return OrderStatus.CANCELLED;
        }
        return null;
    }

    protected static OrdersData.OrdData parseOrdData(JSONObject order) {
        String orderId = order.get("orders_id").toString();
        long createDate = Utils.getLong(order.get("createDate"));
        double orderAmount = Utils.getDouble(order.get("amount"));
        double executedAmount = Utils.getDouble(order.get("deal_amount")); // Has been traded quantity
        double remainedAmount = orderAmount - executedAmount;
        double rate = Utils.getDouble(order.get("rate"));
        String status = Utils.getString(order.get("status")); // 0-pending?
        String pair = (String) order.get("symbol");
        String type = (String) order.get("type");
        double avgRate = Utils.getDouble(order.get("avg_rate"));

        OrdersData.OrdData ordData = new OrdersData.OrdData(orderId, orderAmount, executedAmount, remainedAmount, rate, createDate, status, getPair(pair), getOrderSide(type), getOrderType(type));
        ordData.m_avgPrice = avgRate;
        return ordData;
    }

    private static String parseError(JSONObject jObj) {
        Long errorCode = Utils.getLong(jObj.get("errorCode"));
        String error = ERROR_CODES.get(errorCode);
        return "errorCode: " + errorCode + ": " + error;
    }

    /*
https://www.okcoin.com/api/getorder.do
partner     true    long    partner
order_id    true    long    Order Number (-1 query all pending, otherwise the corresponding single number of inquiries pending)
symbol      true    string  The current currency exchange(btc_cny,ltc_cny)
sign        true    string  In the parameters of the request to make a signature
{
?????"result":true,
?????"orders":?{
?? ????????"orders_id":15088,
?? ????????"status":0,
??????????"symbol":"btc_cny",
?? ????????"type":"sell",
?????????? "rate":811,
?? ????????"amount":1.39901357,
?????????? "deal_amount":1,
                   "avg_rate":811
?????????} ,
?????????{
?? ??????? "orders_id":15088,
??????????"status":-1,
??????????"symbol":"btc_cny",
??????????"type":"sell",
??????????"rate":811,
??????????"amount":1.39901357,
??????????"deal_amount":1,
                 "avg_rate":811
? ????????}?
??? }

deal_amount - Has been traded quantity
avg_rate - The average transaction price
    		*/

}
