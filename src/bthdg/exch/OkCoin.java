package bthdg.exch;

import bthdg.*;
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
    public static boolean LOG_PARSE = false;

    // supported pairs
    static final Pair[] PAIRS = {Pair.BTC_CNH, Pair.LTC_CNH };

    static DecimalFormat s_priceFormat = mkFormat("0.00");
    static DecimalFormat s_amountFormat = mkFormat("0.000");

    @Override public String getNextNonce() { return null; }
    @Override protected String getCryproAlgo() { return null; }
    @Override protected String getSecret() { return null; }
    @Override protected String getApiEndpoint() { return null; }
    @Override public Pair[] supportedPairs() { return PAIRS; }
    @Override public double minOurPriceStep(Pair pair) { return 0.01; }

    @Override public double roundPrice(double price, Pair pair){
        return defRoundPrice(price, pair);
    }
    @Override public double roundAmount(double amount, Pair pair){
        return defRoundAmount(amount, pair);
    }

    @Override public String roundPriceStr(double price, Pair pair) {
        return s_priceFormat.format(price);
    }
    @Override public String roundAmountStr(double amount, Pair pair) {
        return s_amountFormat.format(amount);
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
//            DeepData deepData = Fetcher.fetchDeep(Exchange.OKCOIN, Pair.BTC_CNH);
//            log("deepData: " + deepData);
//            acct();
            AccountData account = Fetcher.fetchAccount(Exchange.OKCOIN);
            log("account: " + account);
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
        double btc = Utils.getDouble(free.get("btc"));
        AccountData accountData = new AccountData(Exchange.OKCOIN.m_name, 0, btc, Double.MAX_VALUE);
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
        Boolean success = (Boolean) jObj.get("result");
        if( success ) {
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
                return getPostData(sign);
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

                List<Post.NameValue> postParams = new ArrayList<Post.NameValue>();
                postParams.add(new Post.NameValue("partner", PARTNER));
                postParams.add(new Post.NameValue("symbol", getPairParam(pair)));
                postParams.add(new Post.NameValue("type", getOrderSideStr(order)));
                postParams.add(new Post.NameValue("rate", priceStr));
                postParams.add(new Post.NameValue("amount", amountStr));
                postParams.add(new Post.NameValue("sign", sign));
                final String postData = Post.buildPostQueryString(postParams);
                return new IPostData() {
                    @Override public String postStr() { return postData; }
                    @Override public Map<String, String> headerLines() { return null; }
                };

//                return getPostData(sign);
            }
        }
        throw new RuntimeException("not supported");
    }

    private IPostData getPostData(String sign) {
        List<Post.NameValue> postParams = new ArrayList<Post.NameValue>();
        postParams.add(new Post.NameValue("partner", PARTNER));
        postParams.add(new Post.NameValue("sign", sign));
        final String postData = Post.buildPostQueryString(postParams);
        return new IPostData() {
            @Override public String postStr() { return postData; }
            @Override public Map<String, String> headerLines() { return null; }
        };
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
}
