package bthdg.exch;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.util.Md5;
import bthdg.util.Post;
import bthdg.util.Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

/** https://www.okcoin.com/t-1000097.html */
public class OkCoin extends BaseExch {
    private static String SECRET;
    private static String PARTNER;
    public static boolean LOG_PARSE = true;

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
        put(Pair.BTC_CNH, "0.00",     0.01,             0.02,            "0.0##",       0.001,          0.01);
        put(Pair.LTC_CNH, "0.00",     0.01,             0.02,            "0.0##",       0.001,          0.01);
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
            PARTNER = properties.getProperty("okcoin_partner");
            if(PARTNER != null) {
                return true;
            }
        }
        return false;
    }

    private void run() {
        try {
//            TopData topData = Fetcher.fetchTop(Exchange.OKCOIN, Pair.BTC_CNH);
//            log("topData: " + topData);
            Fetcher.LOG_JOBJ = true;
            DeepData deepData = Fetcher.fetchDeep(Exchange.OKCOIN, Pair.BTC_CNH);
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
            default: return "?";
        }
    }

    private static Pair getPair(String pair) {
        if (pair.equals("btc_cny")) { return Pair.BTC_CNH; }
        if (pair.equals("ltc_cny")) { return Pair.LTC_CNH; }
        return null;
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
        double ask = Utils.getDouble(ticker, "sell");
        double bid = Utils.getDouble(ticker, "buy");
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
        AccountData accountData = parseFunds(free);
        return accountData;

        // {"info":{"funds":{"free":{"btc":"0","cny":"0","ltc":"0"},"freezed":{"btc":"0","cny":"0","ltc":"0"}}},"result":true}
    }
    private static AccountData parseFunds(JSONObject free) {
        AccountData accountData = new AccountData(Exchange.OKCOIN.m_name, Double.MAX_VALUE);
        double btc = Utils.getDouble(free.get("btc"));
        accountData.setAvailable(Currency.BTC, btc);
        double ltc = Utils.getDouble(free.get("ltc"));
        accountData.setAvailable(Currency.LTC, ltc);
        double cny = Utils.getDouble(free.get("cny"));
        accountData.setAvailable(Currency.CNH, cny);
        return accountData;
    }

    public static PlaceOrderData parseOrder(Object obj) {
        // ?{"result":true,"order_id":123456}
        // ?"result":false,"errorCode":10000?
        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("OkCoin.parseOrder() " + jObj);
        }
        Boolean result = (Boolean) jObj.get("result");
        if( result ) {
            long orderId = Utils.getLong(jObj.get("order_id"));
            return new PlaceOrderData(orderId);
        } else {
            String error = (String) jObj.get("errorCode");
            log(" error: " + error);
            return new PlaceOrderData(error); // order is not placed
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
                Pair pair = order.m_pair;
                String priceStr = roundPriceStr(order.m_price, pair);
                String amountStr = roundAmountStr(order.m_amount, pair);

                Map<String, String> sArray = new HashMap<String, String>();
                sArray.put("partner", PARTNER);
                sArray.put("symbol", getPairParam(pair));
                sArray.put("type", getOrderSideStr(order));
                sArray.put("rate", priceStr);
                sArray.put("amount", amountStr);
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
        }
        throw new RuntimeException("not supported");
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
                String orderId = ((Long) order.get("orders_id")).toString();
                double orderAmount = Utils.getDouble(order.get("amount"));
                double executedAmount = Utils.getDouble(order.get("deal_amount")); // Has been traded quantity
                double remainedAmount = orderAmount - executedAmount;
                double rate = Utils.getDouble(order.get("rate"));
                String status = Utils.getString(order.get("status")); // 0-pending?
                String pair = (String) order.get("symbol");
                String type = (String) order.get("type");
                OrdersData.OrdData ord = new OrdersData.OrdData(orderId, orderAmount, remainedAmount, rate, -1l, status, getPair(pair), getOrderSide(type));
                ords.put(orderId, ord);
            }
            return new OrdersData(ords);
        } else {
            String error = Utils.getString(jObj.get("errorCode"));
            log(" error: " + error);
            return new OrdersData(error); // orders error
        }
    }

    public static CancelOrderData parseCancelOrders(Object obj) {
//?{"result":true,"order_id":123456}
//?{"result":false,"errorCode":10000?
        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("OkCoin.parseCancelOrders() " + jObj);
        }
        Boolean result = (Boolean) jObj.get("result");
        if( result ) {
            String orderId = Utils.getString(jObj.get("order_id"));
            return new CancelOrderData(orderId, null);
        } else {
            String error = (String) jObj.get("errorCode");
            log(" error: " + error);
            return new CancelOrderData(error); // TODO - we have 'invalid parameter: order_id' here
        }
    }

    /*String url = " https://www.okcoin.com/api/trade.do";
    		Map<String, String> map = new HashMap<String, String>();
    		map.put("partner", "3283163");
    		map.put("symbol", "btc_cny");
    		map.put("type", "buy");
    		map.put("rate", "1411.99");
    		map.put("amount", "0.01");
    		//amount=1.0&partner=2088101568338364&rate=680&symbol=btc_cny&type=buy
    		String signString = "amount=0.01&partner=3283163&rate=1411.99&symbol=btc_cny&type=buy80CE60C9CF0CCAEDAE86A56CC7A31AB4";
    		map.put("sign", md5(signString).toUpperCase());//签�??需�?大写
    		String reslut = HttpUtil.http(url, map);
    		System.out.println(reslut);

    		{"result":true,"order_id":123456}
    		?"result":false,"errorCode":10000?

https://www.okcoin.com/api/cancelorder.do

Map<String, String> map = new HashMap<String, String>();
		map.put("order_id", orderId);
		map.put("partner", "3283163");
		map.put("symbol",  "btc_cny");
		//amount=1.0&partner=2088101568338364&rate=680&symbol=btc_cny&type=buy
		String signString = "order_id="+orderId+"&partner=3283163&symbol=btc_cny80CE60C9CF0CCAEDAE86A56CC7A31AB4";

?{"result":true,"order_id":123456}
?{"result":false,"errorCode":10000?


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

// error codes:
//    10001 - Too frequent user requests
//	          # too much api request, 2s interval
//            # TODO need to check whether the order was placed or not
//            print ("# too frequent api request, pls retry after 2s interval")

//    error codes =   { 10000 : 'Required parameter can not be null',
//                      10001 : 'Requests are too frequent',
//                      10002 : 'System Error',
//                      10003 : 'Restricted list request, please try again later',
//                      10004 : 'IP restriction',
//                      10005 : 'Key does not exist',
//                      10006 : 'User does not exist',
//                      10007 : 'Signatures do not match',
//                      10008 : 'Illegal parameter',
//                      10009 : 'Order does not exist',
//                      10010 : 'Insufficient balance',
//                      10011 : 'Order is less than minimum trade amount',
//                      10012 : 'Unsupported symbol (not btc_cny or ltc_cny)',
//                      10013 : 'This interface only accepts https requests' }
}
