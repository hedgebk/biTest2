package bthdg.exch;

import bthdg.*;
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
import java.net.URL;
import java.util.*;

/**
 * https://github.com/agent462/chinashop/tree/master/lib/chinashop
 * */
public class Btcn extends BaseExch {
    private static String KEY;
    private static String SECRET;
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    public static boolean LOG_PARSE = true;

    @Override public String getNextNonce() { return Long.toString(System.currentTimeMillis() * 1000); }
    @Override protected String getCryproAlgo() { return null; }
    @Override protected String getSecret() { return null; }
    @Override protected String getApiEndpoint() { return "https://api.btcchina.com/api_trade_v1.php"; }
    @Override public double roundPrice(double price, Pair pair) { return 0; }
    @Override public String roundPriceStr(double price, Pair pair) { return null; }
    @Override public double roundAmount(double amount, Pair pair) { return 0; }
    @Override public String roundAmountStr(double amount, Pair pair) { return null; }

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
//            DeepData deepData = Fetcher.fetchDeep(Exchange.BTCN, Pair.BTC_CNH);
//            log("deepData: " + deepData);
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

    public static DeepData parseDeep(Object jObj) {
        return parseDeepInt(jObj);
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
                "&id=1" +
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

    public IPostData getPostData(Exchange.UrlDef apiEndpoint, Fetcher.FetchCommand command, Fetcher.FetchOptions options) throws Exception {
        String nonce = getNextNonce();
        String params = "tonce="+nonce+
                "&accesskey="+KEY+
                "&requestmethod=post" +
                "&id=1" +
                "&method=getAccountInfo" +
                "&params=";

        // todo: check: use encode() instead if getSignature() ?
        //String encoded = encode(nonce.getBytes(), CLIENT_ID.getBytes(), KEY.getBytes());

        String hash = getSignature(params, SECRET);
        String userPass = KEY + ":" + hash;
        String basicAuth = "Basic " + DatatypeConverter.printBase64Binary(userPass.getBytes());

        final String postStr = "{\"method\": \"getAccountInfo\", \"params\": [], \"id\": 1}";
        final Map<String, String> headerLines = new HashMap<String, String>();
        headerLines.put("Json-Rpc-Tonce", nonce);
        headerLines.put("Authorization", basicAuth);
        return new IPostData() {
            @Override public String postStr() { return postStr; }
            @Override public Map<String, String> headerLines() { return headerLines; }
        };
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
        JSONObject btc = (JSONObject) balance.get("btc");
        double btcVal = Utils.getDouble(btc.get("amount"));
        AccountData accountData = new AccountData(Exchange.BTCE.m_name, 0, btcVal, Double.MAX_VALUE);
        JSONObject ltc = (JSONObject) balance.get("ltc");
        double ltcVal = Utils.getDouble(ltc.get("amount"));
        accountData.setAvailable(Currency.LTC, ltcVal);
        JSONObject cny = (JSONObject) balance.get("cny");
        double cnyVal = Utils.getDouble(cny.get("amount"));
        accountData.setAvailable(Currency.CNH, cnyVal);
        return accountData;
    }
}
