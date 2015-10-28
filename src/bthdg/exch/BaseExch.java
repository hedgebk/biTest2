package bthdg.exch;

import bthdg.Config;
import bthdg.Fetcher;
import bthdg.Log;
import bthdg.util.Post;
import bthdg.util.Utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

public abstract class BaseExch {
    private static boolean s_sslInitialized;
    public static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    static final String USER_AGENT = "Mozilla/5.0 (compatible; bitcoin-API/1.0; MSIE 6.0 compatible)";
    public static final int DEF_CONNECT_TIMEOUT = 6000;
    public static final int DEF_READ_TIMEOUT = 7000;

    protected DecimalFormat priceFormat(Pair pair) { throw new RuntimeException("priceFormat() not implemented on " + this); }
    protected DecimalFormat amountFormat(Pair pair) { throw new RuntimeException("amountFormat() not implemented on " + this); }
    public double minOurPriceStep(Pair pair) { throw new RuntimeException("minOurPriceStep() not implemented on " + this); }
    public double minExchPriceStep(Pair pair) { throw new RuntimeException("minExchPriceStep() not implemented on " + this); }
    public double minAmountStep(Pair pair) { throw new RuntimeException("minAmountStep() not implemented on " + this); }
    public double minOrderToCreate(Pair pair) { throw new RuntimeException("minOrderToCreate() not implemented on " + this); }

    public double roundPrice(double price, Pair pair){
        return roundPrice(price, pair, RoundingMode.HALF_UP);
    }
    public double roundPrice(double price, Pair pair, RoundingMode round){ // ROUND_UP, ROUND_DOWN, ROUND_HALF_UP
        DecimalFormat decimalFormat = priceFormat(pair);
        int scale = decimalFormat.getMaximumFractionDigits();
        return round(price, scale, round);
    }

    public double roundAmount(double amount, Pair pair){
        DecimalFormat decimalFormat = amountFormat(pair);
        int scale = decimalFormat.getMaximumFractionDigits();
        return round(amount, scale, RoundingMode.DOWN);
    }

    public String roundPriceStr(double price, Pair pair) {
        return roundPriceStr(price, pair, RoundingMode.HALF_UP);
    }

    private String roundPriceStr(double price, Pair pair, RoundingMode round) {
        double rounded = roundPrice(price, pair, round);
        DecimalFormat decimalFormat = priceFormat(pair);
        String ret = decimalFormat.format(rounded);
        return ret;
    }

    public String roundAmountStr(double amount, Pair pair) {
        double rounded = roundAmount(amount, pair);
        DecimalFormat decimalFormat = amountFormat(pair);
        return decimalFormat.format(rounded);
    }

    private static double round(double value, int scale, RoundingMode round) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(scale,round);
        double ret = bd.doubleValue();
        return ret;
    }

    public abstract String getNextNonce();
    public abstract Pair[] supportedPairs();
    public abstract Currency[] supportedCurrencies();
    protected abstract String getCryproAlgo();
    protected abstract String getSecret();
    protected abstract String getApiEndpoint();

    public int connectTimeout() { return DEF_CONNECT_TIMEOUT; };
    public int readTimeout() { return DEF_READ_TIMEOUT; };

    public List<Post.NameValue> getPostParams(String nonce, Exchange.UrlDef apiEndpoint, Fetcher.FetchCommand command, Fetcher.FetchOptions options) throws Exception {return null;};
    public Map<String, String> getHeaders(String postData) throws Exception { return new HashMap<String, String>(); }
    protected static void log(String s) { Log.log(s); }
    protected static void err(String s, Exception e) { Log.err(s, e); }
    public static void initSsl() throws NoSuchAlgorithmException, KeyManagementException {

        if(!Config.s_runOnServer && !s_sslInitialized) {
            // btce ssl certificate fix
            X509TrustManager tm = new X509TrustManager() {
                @Override public void checkClientTrusted(java.security.cert.X509Certificate[] x509Certificates, String s) throws java.security.cert.CertificateException {}
                @Override public void checkServerTrusted(java.security.cert.X509Certificate[] x509Certificates, String s) throws java.security.cert.CertificateException {}
                @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] { tm }, null);
            SSLSocketFactory socketFactory = context.getSocketFactory();

//            SSLContext sslctx = SSLContext.getInstance("SSL");
//            sslctx.init(null, null, null);
//            SSLSocketFactory socketFactory = sslctx.getSocketFactory();

            HttpsURLConnection.setDefaultSSLSocketFactory(socketFactory);
            s_sslInitialized = true;
        }
    }

    protected String encode(byte[] ... bytes) throws InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException {
        Mac mac = initMac();
        for(byte[] b: bytes) {
            mac.update(b);
        }
        byte[] hash = mac.doFinal();
        String encoded = encodeHexString(hash);
        return encoded;
    }

    protected String encodeHexString(byte[] hash) {
        return Utils.encodeHexString(hash);
    }

    protected final Mac initMac() throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        Mac mac = getMac();
        SecretKeySpec key = createSecretKey();
        mac.init(key);
        return mac;
    }

    protected final Mac getMac() throws NoSuchAlgorithmException {
        return Mac.getInstance(getCryproAlgo());
    }

    protected SecretKeySpec createSecretKey() throws UnsupportedEncodingException {
        return new SecretKeySpec(getSecret().getBytes("UTF-8"), getCryproAlgo());
    }

    public static Properties loadKeys() throws IOException {
        Properties properties = new Properties();
        properties.load(new FileReader("keys.txt"));

        Properties ret = new Properties();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String value = (String) entry.getValue();
            int index = value.indexOf('#');
            if (index != -1) { // cut comments
                value = value.substring(0, index).trim();
            }
            Object key = entry.getKey();
            ret.put(key, value);
        }
        return ret;
    }

    protected static String readJson(HttpURLConnection con) throws IOException {
        StringBuilder json = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
        try {
            String text;
            while ((text = br.readLine()) != null) {
                json.append(text);
            }
        } finally {
            br.close();
        }
        log("json: " + json);
        return json.toString();
    }

    protected void readJson0(HttpsURLConnection con) throws IOException {
        String json = "";
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
        try {
            String text;
            while ((text = br.readLine()) != null) {
                json += text;
            }
        } finally {
            br.close();
        }
        log("json: " + json);
    }

    protected String loadJsonStr3(String query) throws Exception {
        String json;
        URL url = new URL(getApiEndpoint());
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        try {
            con.setRequestMethod("POST");
            con.setUseCaches(false);
            con.setDoOutput(true);

            con.setRequestProperty("Content-Type", APPLICATION_X_WWW_FORM_URLENCODED);
            con.setRequestProperty("User-Agent", USER_AGENT);

            OutputStream os = con.getOutputStream();
            try {
                os.write(query.getBytes());
                con.connect();
                if (con.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                    json = readJson(con);
                } else {
                    throw new Exception("ERROR: unexpected ResponseCode: " + con.getResponseCode());
                }
            } finally {
                os.close();
            }
        } finally {
            con.disconnect();
        }
        return json;
    }

    protected String loadJsonStr(Map<String, String> headerLines, String postData) throws Exception {
        String json;
        URL url = new URL(getApiEndpoint());
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        try {
            con.setRequestMethod("POST");
            con.setUseCaches(false);
            con.setDoOutput(true);

            if( headerLines != null ) {
                for (Map.Entry<String, String> headerLine : headerLines.entrySet()) {
                    con.setRequestProperty(headerLine.getKey(), headerLine.getValue());
                }
            }

            OutputStream os = con.getOutputStream();
            try {
                os.write(postData.getBytes());
                os.flush();
                os.close(); // close early
                os = null;
                con.connect();

                int responseCode = con.getResponseCode();
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    json = readJson(con);
                } else {
                    throw new Exception("ERROR: unexpected ResponseCode: " + responseCode);
                }
            } finally {
                if(os != null) {
                    os.close();
                }
            }
        } finally {
            con.disconnect();
        }
        return json;
    }

    public IPostData getPostData(Exchange.UrlDef apiEndpoint, Fetcher.FetchCommand command, Fetcher.FetchOptions options) throws Exception {
        String nonce = getNextNonce();
        List<Post.NameValue> postParams = getPostParams(nonce, apiEndpoint, command, options);
        final String postStr = Post.buildPostQueryString(postParams);
        final Map<String, String> headerLines = getHeaders(postStr);
        headerLines.put("Content-Type", APPLICATION_X_WWW_FORM_URLENCODED);
        return new IPostData() {
            @Override public String postStr() { return postStr; }
            @Override public Map<String, String> headerLines() { return headerLines; }
        };
    }

    protected static String getOrderSideStr(OrderData order) {
        return order.m_side.isBuy() ? "buy" : "sell";
    }

    protected static OrderSide getOrderSide(String side) {
        return side.equals("buy") || side.equals("bid")|| side.equals("buy_market")
                ? OrderSide.BUY
                : side.equals("sell") || side.equals("ask") || side.equals("sell_market")
                    ? OrderSide.SELL
                    : null;
    }

    protected static DecimalFormat mkFormat(String format) {
        return new DecimalFormat(format, DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    }

    public void initFundMap() {}

    //*************************************************************************
    public interface IPostData {
        String postStr();
        Map<String,String> headerLines();
    }
}
