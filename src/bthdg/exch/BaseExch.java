package bthdg.exch;

import bthdg.*;
import bthdg.util.Post;
import bthdg.util.Utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;
import java.io.*;
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

    public abstract String getNextNonce();
    protected abstract String getCryproAlgo();
    protected abstract String getSecret();
    protected abstract String getApiEndpoint();

    public double roundPrice(double price, Pair pair) { throw new RuntimeException("roundPrice not implemented on " + this ); }
    public double roundAmount(double amount, Pair pair) { throw new RuntimeException("roundAmount not implemented on " + this ); }
    public String roundPriceStr(double price, Pair pair) { throw new RuntimeException("roundPriceStr not implemented on " + this ); }
    public String roundAmountStr(double amount, Pair pair) { throw new RuntimeException("roundAmountStr not implemented on " + this ); }

    public abstract Pair[] supportedPairs();
    public abstract double minOurPriceStep(Pair pair);

    public int connectTimeout() { return DEF_CONNECT_TIMEOUT; };
    public int readTimeout() { return DEF_READ_TIMEOUT; };

    public List<Post.NameValue> getPostParams(String nonce, Exchange.UrlDef apiEndpoint, Fetcher.FetchCommand command, Fetcher.FetchOptions options) throws Exception {return null;};
    public Map<String, String> getHeaders(String postData) throws Exception { return new HashMap<String, String>(); }
    private static void log(String s) { Log.log(s); }

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
        String encoded = Utils.encodeHexString(hash);
        return encoded;
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
        return properties;
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

    protected static DecimalFormat mkFormat(String format) {
        return new DecimalFormat(format, DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    }

    protected double defRoundPrice(double price, Pair pair) {
        String str = roundPriceStr(price, pair);
        double ret = Double.parseDouble(str);
        return ret;
    }

    protected double defRoundAmount(double amount, Pair pair) {
        String str = roundAmountStr(amount, pair);
        double ret = Double.parseDouble(str);
        return ret;
    }

    //*************************************************************************
    public interface IPostData {
        String postStr();
        Map<String,String> headerLines();
    }
}
