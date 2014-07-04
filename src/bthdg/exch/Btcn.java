package bthdg.exch;

import bthdg.Fetcher;
import bthdg.util.Post;
import bthdg.util.Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.bind.DatatypeConverter;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;

/**
 * https://github.com/agent462/chinashop/tree/master/lib/chinashop
 * ## Private API   http://btcchina.org/api-trade-documentation-en
 * ## Public API    http://btcchina.org/api-market-data-documentation-en       */
public class Btcn extends BaseExch {
    private static String KEY;
    private static String SECRET;
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    public static boolean LOG_PARSE = true;
    private static boolean JOIN_SMALL_QUOTES = false;

    // supported pairs
    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_CNH, Pair.LTC_CNH };
    // supported currencies
    private static final Currency[] CURRENCIES = { Currency.BTC, Currency.LTC, Currency.CNH };

    private static final Map<Pair, DecimalFormat> s_amountFormatMap = new HashMap<Pair, DecimalFormat>();
    private static final Map<Pair, Double> s_minAmountStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, DecimalFormat> s_priceFormatMap = new HashMap<Pair, DecimalFormat>();
    private static final Map<Pair, Double> s_minExchPriceStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, Double> s_minOurPriceStepMap = new HashMap<Pair, Double>();
    private static final Map<Pair, Double> s_minOrderToCreateMap = new HashMap<Pair, Double>();

    static {           // priceFormat minExchPriceStep  minOurPriceStep  amountFormat   minAmountStep   minOrderToCreate
        put(Pair.BTC_CNH, "0.##",     0.01,             0.02,            "0.0###",      0.0001,         0.001);
        put(Pair.LTC_CNH, "0.##",     0.01,             0.01,            "0.0##",       0.001,          0.01);
        put(Pair.LTC_BTC, "0.####",   0.0001,           0.0001,          "0.0###",      0.001,          0.01);
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
        distributeRatio.put(Currency.BTC, 0.3);
        distributeRatio.put(Currency.CNH, 0.4);
        distributeRatio.put(Currency.LTC, 0.3);
        FundMap.s_map.put(Exchange.BTCN, distributeRatio);
    }

    @Override public String getNextNonce() { return Long.toString(System.currentTimeMillis() * 1000); }
    @Override protected String getCryproAlgo() { return null; }
    @Override protected String getSecret() { return null; }
    @Override protected String getApiEndpoint() { return "https://api.btcchina.com/api_trade_v1.php"; }

    @Override public Pair[] supportedPairs() { return PAIRS; }
    @Override public Currency[] supportedCurrencies() { return CURRENCIES; };

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
//            TopsData topsData = Fetcher.fetchTops(Exchange.BTCN, Pair.BTC_CNH);
//            log("topsData: " + topsData);

            Fetcher.LOG_JOBJ = true;
            DeepData deepData = Fetcher.fetchDeep(Exchange.BTCN, Pair.BTC_CNH);
            log("deepData: " + deepData);
//            run("x");
//            AccountData account = Fetcher.fetchAccount(Exchange.BTCN);
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
            case BTC_CNH: return "btccny";
            case LTC_CNH: return "ltccny";
            case LTC_BTC: return "ltcbtc";
            default: return "?";
        }
    }

    //http://btcchina.org/api-market-data-documentation-en#ticker
    public static TopsData parseTops(Object obj, Pair[] pairs) {
        if (LOG_PARSE) {
            log("BTCN.parseTops() " + obj);
        }
        // todo: add parsing of 'get data for ALL pairs'
        TopsData ret = new TopsData();
        Pair pair = pairs[0];
        TopData top = parseTopInt(obj, pair);
        ret.put(pair, top);
        return ret;
    }

    public static TopData parseTop(Object jObj, Pair pair) {
        return parseTopInt(jObj, pair);
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
        DeepData deep = parseDeepInt(jObj);
        if (JOIN_SMALL_QUOTES) {
            deep.joinSmallQuotes(Exchange.BTCE, pair);
        }
        return deep;
    }

    private static DeepData parseDeepInt(Object obj) {
        if (LOG_PARSE) {
            log("BTCN.parseDeep() " + obj);
        }
        JSONObject pairData = (JSONObject) obj;
        JSONArray bids = (JSONArray) pairData.get("bids");
        JSONArray asks = (JSONArray) pairData.get("asks");

        return DeepData.create(bids, asks);
    }

    private JSONObject run(String method) throws Exception {
        initSsl();

        String nonce = getNextNonce();
        String params = "tonce=" + nonce +
                "&accesskey=" + KEY +
                "&requestmethod=post" +
                "&id=" + nonce +
                "&method=getAccountInfo" +
                "&params=";
        String hash = getSignature(params, SECRET);
        URL obj = new URL(getApiEndpoint());
        HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
        String userpass = KEY + ":" + hash;
        String basicAuth = "Basic " + DatatypeConverter.printBase64Binary(userpass.getBytes());
        //add request header
        con.setRequestMethod("POST");
        con.setRequestProperty("Json-Rpc-Tonce", nonce.toString());
        con.setRequestProperty ("Authorization", basicAuth);
        String postdata = "{\"method\": \"getAccountInfo\", \"params\": [], \"id\": 1}";

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(postdata);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + getApiEndpoint());
        System.out.println("Post parameters : " + postdata);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        try {
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            //print result
            log("Loaded: " + response.toString());
        } finally {
            in.close();
        }

        return null;
    }

    @Override public IPostData getPostData(Exchange.UrlDef apiEndpoint, Fetcher.FetchCommand command, Fetcher.FetchOptions options) throws Exception {
        switch (command) {
            case ACCOUNT: {
                String nonce = getNextNonce();
                return buildPostData(nonce, "getAccountInfo", "");
            }
            case ORDER: {
                OrderData order = options.getOrderData();
                Pair pair = order.m_pair;
                String priceStr = roundPriceStr(order.m_price, pair);
                String amountStr = roundAmountStr(order.m_amount, pair);
                String market = getPairParam(pair).toUpperCase();
                String methodParams = priceStr + "," + amountStr + ",\"" + market + "\"";
                String nonce = getNextNonce();
                String method = getOrderSideMethod(order);
                return buildPostData(nonce, method, methodParams);
            }
            case ORDERS: {
//                Name 	   Value   Required Description
//                openonly 	boolean 	NO 	Default is 'true'. Only open orders are returned.
//                market 	string 	    NO 	Default to “BTCCNY”. [ BTCCNY | LTCCNY | LTCBTC | ALL]
//                limit 	integer 	NO 	Limit the number of transactions, default value is 1000.
//                offset 	integer 	NO 	Start index used for pagination, default value is 0.
//                {"method":"getOrders","params":[],"id":1}
//                {"method":"getOrders","params":[false],"id":1}
//                ## all orders from all markets limit to 2 orders per market ##
//                {"method":"getOrders","params":[false,"ALL",2],"id":1}
                String nonce = getNextNonce();
                return buildPostData(nonce, "getOrders", "true,\"ALL\"");
            }
            case CANCEL: {
//                Name 	    Value 	Required 	Description
//                id 	    number 	YES 	    The order id to cancel.
//                market 	string 	NO      	Default to “BTCCNY”. [ BTCCNY | LTCCNY | LTCBTC ]
                Pair pair = options.getPair();
                String orderId = options.getOrderId();
                String nonce = getNextNonce();
                String market = getPairParam(pair).toUpperCase();
                return buildPostData(nonce, "cancelOrder", orderId + ",\"" + market + "\"");
            }
        }
        return null;
    }

    protected static String getOrderSideMethod(OrderData order) {
//        return order.m_side.isBuy() ? "buyOrder" : "sellOrder";
        return order.m_side.isBuy() ? "buyOrder2" : "sellOrder2";
    }

    private IPostData buildPostData(String nonce, String method, String methodParams) throws Exception {
        String params = buildPostParams(nonce, method, methodParams)
                .replace("\"", "") // do not send " symbol in signature
                .replace("true", "1")  // send 1 instead of true in signature
                .replace("false", ""); // do not send false in signature
log("PostParams="+params);
        // todo: check: use encode() instead if getSignature() ?
        //String encoded = encode(nonce.getBytes(), CLIENT_ID.getBytes(), KEY.getBytes());

        String hash = getSignature(params, SECRET);
        String userPass = KEY + ":" + hash;
        String basicAuth = "Basic " + DatatypeConverter.printBase64Binary(userPass.getBytes());

        final String postStr = "{\"method\": \"" + method + "\",\"params\":[" + methodParams + "],\"id\": " + nonce + "}";
log("postStr="+postStr);
        final Map<String, String> headerLines = new HashMap<String, String>();
        headerLines.put("Json-Rpc-Tonce", nonce);
        headerLines.put("Authorization", basicAuth);
        return new IPostData() {
            @Override public String postStr() { return postStr; }
            @Override public Map<String, String> headerLines() { return headerLines; }
        };
    }

    private String buildPostParams(String nonce, String method, String methodParams) {
        List<Post.NameValue> postParams = new ArrayList<Post.NameValue>();
        postParams.add(new Post.NameValue("tonce", nonce));
        postParams.add(new Post.NameValue("accesskey", KEY));
        postParams.add(new Post.NameValue("requestmethod", "post"));
        postParams.add(new Post.NameValue("id", nonce));
        postParams.add(new Post.NameValue("method", method));
        postParams.add(new Post.NameValue("params", methodParams));
        return Post.buildPostQueryString(postParams);
    }

    public static String getSignature(String data, String key) throws Exception {
        // get an hmac_sha1 key from the raw key bytes
        SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
        // get an hmac_sha1 Mac instance and initialize with the signing key
        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        mac.init(signingKey);
        // compute the hmac on input data bytes
        byte[] rawHmac = mac.doFinal(data.getBytes());
        return bytArrayToHex(rawHmac);
    }

    private static String bytArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        for (byte b : a) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public static AccountData parseAccount(Object obj) {
        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("BTCN.parseAccount() " + jObj);
        }

        JSONObject ret = (JSONObject) jObj.get("result");
        JSONObject balance = (JSONObject) ret.get("balance");
        AccountData accountData = parseFunds(balance);
        return accountData;

//        {"id":"1",
//         "result":{
//            "balance":{
//                "cny":{"amount_integer":"0","amount":"0.00000","symbol":"¥","amount_decimal":5,"currency":"CNY"},
//                "ltc":{"amount_integer":"0","amount":"0.00000000","symbol":"?","amount_decimal":8,"currency":"LTC"},
//                "btc":{"amount_integer":"0","amount":"0.00000000","symbol":"?","amount_decimal":8,"currency":"BTC"}},
//            "frozen":{
//                "cny":{"amount_integer":"0","amount":"0.00000","symbol":"¥","amount_decimal":5,"currency":"CNY"},
//                "ltc":{"amount_integer":"0","amount":"0.00000000","symbol":"?","amount_decimal":8,"currency":"LTC"},
//                "btc":{"amount_integer":"0","amount":"0.00000000","symbol":"?","amount_decimal":8,"currency":"BTC"}},
//            "profile":{
//                "btc_deposit_address":"1234123412341234124312341234124",
//                "trade_password_enabled":true,
//                "username":"x@gmail.com",
//                "trade_fee_btcltc":0,
//                "ltc_withdrawal_address":"",
//                "ltc_deposit_address":"12341234123412341234123412341243",
//                "daily_btc_limit":10,
//                "trade_fee":0,
//                "btc_withdrawal_address":"",
//                "otp_enabled":false,
//                "trade_fee_cnyltc":0,
//                "api_key_permission":1,
//                "daily_ltc_limit":400}}}
    }

    private static AccountData parseFunds(JSONObject balance) {
        AccountData accountData = new AccountData(Exchange.BTCN, Double.MAX_VALUE);
        setBalance(balance, accountData, "btc", Currency.BTC);
        setBalance(balance, accountData, "ltc", Currency.LTC);
        setBalance(balance, accountData, "cny", Currency.CNH);
        return accountData;
    }

    private static void setBalance(JSONObject balance, AccountData accountData, String key, Currency curr) {
        JSONObject btc = (JSONObject) balance.get(key);
        double btcVal = Utils.getDouble(btc.get("amount"));
        accountData.setAvailable(curr, btcVal);
    }

    /*

        String params = "tonce=" + tonce.toString() + "&accesskey="
                            + ACCESS_KEY
                            + "&requestmethod=post&id=1&method="+buyOrderOrSellOrder+"&params="+price+","+amount;

        String postdata = "{\"method\": \""+buyOrderOrSellOrder+"\", \"params\": ["+price+","+amount+"], \"id\": 1}";

    */
    public static BigDecimal truncateAmount(BigDecimal value) {
        return value.setScale(3, RoundingMode.FLOOR).stripTrailingZeros();
    }

    public static PlaceOrderData parseOrder(Object obj) {
        // {"result":12345,"id":"1"}
//        {"error":{
//            "code":-32003,
//            "message":"Insufficient CNY balance",
//            "id": 1
//            }
//        }
        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("Btcn.parseOrder() " + jObj);
        }
        Object result = jObj.get("result");
        if( result != null ) {
            long orderId = Utils.getLong(result);
            return new PlaceOrderData(orderId);
        } else {
            String errMsg = parseError(jObj);
            log(" error: " + errMsg);
            return new PlaceOrderData(errMsg); // order is not placed
        }
    }

    private static String parseError(JSONObject jObj) {
        JSONObject error = (JSONObject)jObj.get("error");
        String msg = (String) error.get("message");
        long code = Utils.getLong(error.get("code"));
        return code + ": " + msg;
//        Code 	Message
//        -32000 	Internal error
//        -32003 	Insufficient CNY balance
//        -32004 	Insufficient BTC balance
//        -32005 	Order not found
//        -32006 	Invalid user
//        -32007 	Invalid currency
//        -32008 	Invalid amount
//        -32009 	Invalid wallet address
//        -32010 	Withdrawal not found
//        -32011 	Deposit not found
//        -32017 	Invalid type
//        -32018 	Invalid price
//        -32019 	Invalid parameter
//        -32062 	Lack of liquidity
//        -32065 	Invalid market
    }

    public static OrdersData parseOrders(Object obj) {
        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("Btcn.parseOrders() " + jObj);
        }
        JSONObject result = (JSONObject)jObj.get("result");
        if( result != null ) {
            Map<String,OrdersData.OrdData> ords = new HashMap<String,OrdersData.OrdData>();
            for(Pair pair : PAIRS) {
                String pairParam = getPairParam(pair);
                String key = "order_" + pairParam;
                JSONArray orders = (JSONArray) result.get(key);
                int size = orders.size();
                for(int i = 0; i < size; i++) {
                    JSONObject order = (JSONObject) orders.get(i);
//                            "currency": "CNY",
//                            "date": 1396255376,
                    String orderId = ((Long) order.get("id")).toString();
                    String type = (String) order.get("type");
                    double rate = Utils.getDouble(order.get("price"));
                    double remainedAmount = Utils.getDouble(order.get("amount"));
                    double orderAmount = Utils.getDouble(order.get("amount_original"));
                    String status = Utils.getString(order.get("status"));
                    OrdersData.OrdData ord = new OrdersData.OrdData(orderId, orderAmount, remainedAmount, rate, -1l, status, pair, getOrderSide(type));
                    ords.put(orderId, ord);
                }
            }
            return new OrdersData(ords);
        } else {
            String errMsg = parseError(jObj);
            log(" error: " + errMsg);
            return new OrdersData(errMsg);
        }
    }

    public static CancelOrderData parseCancelOrders(Object obj) {
//{"result":true,"id":"1"}
        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("Btcn.parseCancelOrders() " + jObj);
        }
        Boolean result = (Boolean)jObj.get("result");
        if( result != null ) {
            return new CancelOrderData(null, null);
        } else {
            String errMsg = parseError(jObj);
            log(" error: " + errMsg);
            return new CancelOrderData(errMsg);
        }
    }
}
