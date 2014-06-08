package bthdg.exch;

import bthdg.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class OkCoin extends BaseExch {
    private static String SECRET;
    private static String PARTNER;
    public static boolean LOG_PARSE = false;

    @Override public String getNextNonce() { return null; }
    @Override protected String getCryproAlgo() { return null; }
    @Override protected String getSecret() { return null; }
    @Override protected String getApiEndpoint() { return null; }
    @Override public double roundPrice(double price, Pair pair) { return 0; }
    @Override public String roundPriceStr(double price, Pair pair) { return null; }
    @Override public double roundAmount(double amount, Pair pair) { return 0; }
    @Override public String roundAmountStr(double amount, Pair pair) { return null; }

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

    private void acct() throws Exception {
        Map<String,String> sArray = new HashMap<String, String>();
      	sArray.put("partner", PARTNER);
        String sign = buildMysign(sArray, SECRET);

        List<NameValue> postParams = new ArrayList<NameValue>();
        postParams.add(new NameValue("partner", PARTNER));
        postParams.add(new NameValue("sign", sign));
        String postData = buildPostQueryString(postParams);

        initSsl();

        String json = loadJsonStr(null, postData);
        log("Loaded json: " + json);
    }

    public static String buildMysign(Map<String, String> sArray,String secretKey) {
    	String mysign = "";
		try {
			String prestr = createLinkString(sArray);
	        prestr = prestr + secretKey;
	        mysign = getMD5String(prestr);
		} catch (Exception e) {
			e.printStackTrace();
		}
        return mysign;
    }

    public static String createLinkString(Map<String, String> params) {
        List<String> keys = new ArrayList<String>(params.keySet());
        Collections.sort(keys);
        StringBuilder buff = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = params.get(key);
            if (i != 0) {
                buff.append("&");
            }
            buff.append(key);
            buff.append("=");
            buff.append(value);
        }
        return buff.toString();
    }

    private static final char HEX_DIGITS[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static String getMD5String(String str) {
        try {
            if ((str == null) || (str.trim().length() == 0)) {
                return "";
            }
            byte[] bytes = str.getBytes();
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(bytes);
            bytes = messageDigest.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                sb.append(HEX_DIGITS[(bytes[i] & 0xf0) >> 4] + "" + HEX_DIGITS[bytes[i] & 0xf]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public IPostData getPostData(Exchange.UrlDef apiEndpoint, Fetcher.FetchCommand command, Fetcher.FetchOptions options) throws Exception {
        Map<String,String> sArray = new HashMap<String, String>();
      	sArray.put("partner", PARTNER);
        String sign = buildMysign(sArray, SECRET);

        List<NameValue> postParams = new ArrayList<NameValue>();
        postParams.add(new NameValue("partner", PARTNER));
        postParams.add(new NameValue("sign", sign));
        final String postData = buildPostQueryString(postParams);
        return new IPostData() {
            @Override public String postStr() { return postData; }
            @Override public Map<String, String> headerLines() { return null; }
        };
    }

    public static AccountData parseAccount(Object obj) {
        JSONObject jObj = (JSONObject) obj;
        if (LOG_PARSE) {
            log("BTCN.parseAccount() " + jObj);
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
        AccountData accountData = new AccountData(Exchange.BTCE.m_name, 0, btc, Double.MAX_VALUE);
        double ltc = Utils.getDouble(free.get("ltc"));
        accountData.setAvailable(Currency.LTC, ltc);
        double cny = Utils.getDouble(free.get("cny"));
        accountData.setAvailable(Currency.CNH, cny);
        return accountData;
    }


}
