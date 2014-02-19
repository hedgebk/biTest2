package bthdg;// https://btc-e.com/api/documentation
//
//
// https://btc-e.com/api/2/btc_usd/depth
//    {"asks":[[714,0.93506666],[714.104,0.0998],[714.111,0.01],[714.15,0.01],[714.314,0.14083594],[714.474,0.011],[714.665,2.465],[714.666,3.75571029],[714.68,0.073],[714.692,0.13972],[714.695,0.13972],[714.871,0.035],[715,0.52282746],[715.002,0.01],[7

//
// https://btc-e.com/api/2/btc_usd/depth
//    {"asks":[[712.348,0.63],[713.5,0.011],[713.712,0.01],[713.99,2],[714,0.93506666],[714.104,0.0998],[714.111,0.01],[714.15,0.01],[714.314,0.14083594],[714.474,0.011],[714.665,2.465],[714.666,3.75571029],[714.68,0.073],[714.692,0.13972],[7
//

import bthdg.DeepData;
import bthdg.TopData;
import bthdg.TradesData;
import bthdg.Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Btce {
    public static final String CRYPTO_ALGO = "HmacSHA512";
    private static String SECRET;
    private static String KEY;
    private static int s_nonce = (int) (System.currentTimeMillis() / 1000);

    public static void init() {
        try {
            Properties properties = new Properties();
            properties.load(new FileReader("keys.txt"));
            init(properties);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("error reading properties");
        }
    }

    static boolean init(Properties properties) {
        SECRET = properties.getProperty("btce_secret");
        if(SECRET != null) {
            KEY = properties.getProperty("btce_key");
            if(SECRET != null) {
                return true;
            }
        }
        return false;
    }

    private static String getNextNonce() { return Integer.toString(s_nonce++); }

    public static void main(String[] args) {
        try {
            run("getInfo", null);
//            run("TransHistory", null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JSONObject run(String method, Map<String, String> arguments) throws Exception {
        Map<String, String> headerLines = new HashMap<String, String>();  // Create a new map for the header lines.

        if (arguments == null) {  // If the user provided no arguments, just create an empty argument array.
            arguments = new HashMap<String, String>();
        }

        String nonce = getNextNonce();

        arguments.put("method", method);  // Add the method to the post data.
        arguments.put("nonce", nonce);

        StringBuilder buffer = new StringBuilder();
        for (Map.Entry<String, String> argument : arguments.entrySet()) {
            if (buffer.length() > 0) {
                buffer.append("&");
            }
            buffer.append(argument.getKey());
            buffer.append("=");
            buffer.append(argument.getValue());
        }
        String postData = buffer.toString();

        // Create a new secret key
        SecretKeySpec key;
        try {
            key = new SecretKeySpec(SECRET.getBytes("UTF-8"), CRYPTO_ALGO);
        } catch (UnsupportedEncodingException uee) {
            System.err.println("Unsupported encoding exception: " + uee.toString());
            return null;
        }

        // Create a new mac
        Mac mac;
        try {
            mac = Mac.getInstance(CRYPTO_ALGO);
        } catch (NoSuchAlgorithmException nsae) {
            System.err.println("No such algorithm exception: " + nsae.toString());
            return null;
        }

        // Init mac with key.
        try {
            mac.init(key);
        } catch (InvalidKeyException ike) {
            System.err.println("Invalid key exception: " + ike.toString());
            return null;
        }

        // Add the key to the header lines.
        headerLines.put("Key", KEY);

        // Encode the post data by the secret and encode the result as base64.
        try {
            byte[] bytes = mac.doFinal(postData.getBytes("UTF-8"));
            headerLines.put("Sign", Utils.encodeHexString(bytes));
        } catch (UnsupportedEncodingException uee) {
            System.err.println("Unsupported encoding exception: " + uee.toString());
            return null;
        }

        SSLContext sslctx = SSLContext.getInstance("SSL");
        sslctx.init(null, null, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(sslctx.getSocketFactory());

        URL url = new URL("https://btc-e.com/tapi");

        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        try {
            con.setRequestMethod("POST");
            con.setUseCaches(false);
            con.setDoOutput(true);

            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            //con.setRequestProperty("User-Agent",USER_AGENT) ;
            for (Map.Entry<String, String> headerLine : headerLines.entrySet()) {
                con.setRequestProperty(headerLine.getKey(), headerLine.getValue());
            }

            OutputStream os = con.getOutputStream();
            try {
                os.write(postData.getBytes());
                con.connect();

                int responseCode = con.getResponseCode();
                if (responseCode == HttpsURLConnection.HTTP_OK) {
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
                    System.out.println("json: " + json);
                } else {
                    System.out.println("ERROR: unexpected ResponseCode: " + responseCode);
                }
                System.out.println("Code Response: " + responseCode);
            } finally {
                os.close();
            }
        } finally {
            con.disconnect();
        }

        return null;
    }

    public static String topTestStr() {
        return "{\"ticker\":{\"vol\":3771263.18392,\"last\":793.9,\"updated\":1391636232,\"sell\":792,\"buy\":793.9,\"server_time\":1391636234,\"high\":806.99902,\"avg\":796.995025,\"low\":786.99103,\"vol_cur\":4741.20896}}";
    }

    public static String deepTestStr() {
        return "{\"btc_usd\":{\"asks\":[[775,0.12396113],[775.1,0.31645816],[776.711,0.01],[777.452,0.02],[778,2.5],[778.106,0.01],[778.33,0.013],[778.9,0.43163451],[779,1.9155],[779.24,0.8575292],[779.241,0.024],[779.751,0.021956],[780,6.86432966],[780.153,0.024],[780.444,0.01],[781.066,0.012],[781.399,0.67619994],[781.4,0.20323273],[781.451,0.01],[781.75,0.24],[781.98,0.011],[781.996,0.0507],[782,0.01],[782.126,0.01],[782.75,0.15375077],[782.868,0.013],[783.002,0.25],[783.454,0.0104],[783.465,0.01],[783.468,0.09047411],[783.757,0.023],[784.2,0.023904],[784.419,2.55476731],[784.42,1.5438],[784.461,0.0104],[784.472,0.01],[784.48,0.288],[784.63,0.018924],[784.647,0.015],[784.898,18.95747814],[784.9,0.09565123],[785,1.3433208],[785.13,11.135],[785.31,0.02988],[785.538,0.023],[785.66,0.336],[786,0.49658614],[786.43,0.015],[786.566,0.011],[787,0.4173],[787.143,0.017],[787.21,0.3],[787.31,0.021912],[787.62,0.0249],[787.782,0.012],[787.829,0.015],[788.074,0.011],[788.5,0.01],[788.59,0.01],[788.62,0.046812],[788.97,0.037848],[789,5.976559],[789.063,0.011],[789.089,0.016],[789.12,0.015],[789.507,0.01],[789.677,0.016],[789.99,0.036852],[790,0.387],[790.07,0.01992],[790.073,0.011],[790.159,0.02],[790.265,0.016],[790.33,0.04482],[790.39,0.015],[790.514,0.01],[790.625,0.01000994],[790.7,0.01],[790.85,0.05],[790.86,0.06972],[790.93,0.02],[790.94,0.9],[790.95,0.046812],[790.965,0.9],[791.017,0.8],[791.062,0.011],[791.124,0.01],[791.184,0.04],[791.6,0.01],[791.847,0.015],[792,5.94845008],[792.064,0.0104],[792.093,0.011],[792.24,0.156372],[792.298,2],[792.42,0.01],[792.85,2.4],[793,0.01],[793.226,0.011],[793.262,0.061],[793.3,0.01],[793.31,0.01],[793.36,0.203],[793.5,0.1],[793.535,0.01],[793.731,0.01],[793.82,0.08],[793.998,0.015],[793.999,94.98597809],[794,5],[794.039,0.02],[794.1,0.01],[794.225,0.011],[794.247,0.059093],[794.26,0.08964],[794.264,2],[794.426,0.01],[794.542,0.01],[794.7,1.00000001],[794.754,0.01],[794.811,0.01],[794.9,0.01],[794.98,0.01],[795,5.3513019],[795.2,0.011],[795.25,0.0138945],[795.272,0.01016],[795.281,0.01016],[795.283,0.01016],[795.3,0.01107],[795.306,0.02],[795.777,0.2994],[795.954,4.8902],[796,12.62292888],[796.052,0.02],[796.09,0.015],[796.1,0.01],[796.19,0.2],[796.23,2],[796.27,0.01],[796.3,0.02661333],[796.338,0.011],[796.462,0.02],[796.499,5],[796.777,0.0998],[796.863,0.01832155],[796.91,0.01],[796.938,0.02],[797,4.6038],[797.101,0.01]],\"bids\":[[774.35,6.65004037],[774.332,0.02],[774.222,0.90814769],[774.164,0.02],[774.05,0.01011],[774,5.1294],[773.999,0.47049985],[773.983,0.01119557],[773.95,0.97],[773.916,0.01184062],[773.899,1.15],[773.566,0.01],[773.4,0.04655428],[773.353,0.011],[773.3,3.51],[773.11,0.0102],[773.1,0.015],[773.06,0.01],[773,1.22838955],[772.86,0.1],[772.783,0.02],[772.778,0.01],[772.709,0.01005],[772.706,0.02],[772.556,0.18245585],[772.54,0.32],[772.5,0.097],[772.468,0.60959543],[772.359,0.011],[772.337,0.022],[772.333,0.01943],[772.315,0.060047],[772.3,1],[772.2,0.39506997],[772.16,0.01295068],[772.149,0.24646432],[772.011,0.02],[772,15.77008569],[771.999,0.81],[771.959,0.71723652],[771.787,0.0205],[771.745,0.018],[771.58,0.01],[771.51,1],[771.432,0.011],[771.25,0.01178724],[771.207,0.01003],[771.161,0.02],[771.15,0.2],[771.128,0.07237805],[771.118,0.018],[771.11,5],[771.101,0.1630119],[771.1,0.1319],[771.01,0.01236682],[771,11.2410226],[770.98,0.161],[770.799,0.1],[770.76,0.01],[770.5,0.0125],[770.432,0.02],[770.428,0.02],[770.234,0.01135981],[770.179,0.01103133],[770.12,0.1],[770.11,0.08285091],[770.1,1.13778784],[770.011,0.05],[770.01,0.525],[770.001,28.51],[770,76.53047152],[769.933,0.01167187],[769.73,0.02],[769.671,0.0355013],[769.636,2.7],[769.47,0.288],[769.028,0.02],[769.011,0.2133817],[769,0.01],[768.748,0.22272494],[768.706,2],[768.326,0.02],[768.314,0.35],[768.3,0.336],[768.25,0.2415],[768.2,0.1],[768.07,0.13019642],[768.001,0.03291698],[768,9.65449491],[767.91,0.09544194],[767.777,4.1939261],[767.623,0.021],[767.13,0.1],[767.09,0.0128],[767,1.47536923],[766.94,0.0163],[766.752,0.024],[766.74,2],[766.666,0.02],[766.6,1],[766.49,0.26923471],[766.438,0.01184062],[766.431,0.01119557],[766.38,0.100201],[766.112,0.01],[766.1,2.21],[766.063,14.25],[766.062,5],[766,7.73563237],[765.882,0.025],[765.65,1.42],[765.5,0.013],[765.453,0.86052592],[765.43,0.0254189],[765.4,0.72],[765.3,0.8268],[765.2,0.0146],[765.1,0.51357641],[765.02,0.01303],[765.013,0.025],[765.011,0.01],[765.01,0.505],[765.002,5],[765.001,4.01],[765,64.58516667],[764.774,2],[764.757,0.4008],[764.5,0.01],[764.38,1],[764.321,1],[764.119,0.025],[764.003,1],[764.001,0.62],[764,1.16560698],[763.999,1],[763.945,0.01],[763.818,0.38484184],[763.55,0.84354188],[763.33,0.126],[763.3,15],[763.23,0.3808],[763.226,0.025],[763.2,0.2],[763.1,0.02],[763.002,0.87630335],[763,4.19005],[762.808,2],[762.793,0.01135981],[762.7,8.016],[762.57,0.1574]]}}";
    }

    public static String tradesTestStr() {
        return "{\"btc_usd\":[{\"timestamp\":1391896680,\"amount\":7.23385,\"price\":700.7,\"tid\":29248920,\"type\":\"bid\"},{\"timestamp\":1391896675,\"amount\":0.12,\"price\":700.7,\"tid\":29248919,\"type\":\"bid\"},{\"timestamp\":1391896673,\"amount\":0.03,\"price\":700.7,\"tid\":29248917,\"type\":\"bid\"},{\"timestamp\":1391896672,\"amount\":0.157424,\"price\":700.7,\"tid\":29248916,\"type\":\"bid\"},{\"timestamp\":1391896670,\"amount\":0.01,\"price\":700.7,\"tid\":29248915,\"type\":\"bid\"},{\"timestamp\":1391896669,\"amount\":0.3,\"price\":700.7,\"tid\":29248914,\"type\":\"bid\"},{\"timestamp\":1391896668,\"amount\":0.224025,\"price\":700.477,\"tid\":29248913,\"type\":\"ask\"},{\"timestamp\":1391896668,\"amount\":0.499222,\"price\":700.5,\"tid\":29248912,\"type\":\"ask\"},{\"timestamp\":1391896668,\"amount\":0.016,\"price\":700.69,\"tid\":29248911,\"type\":\"ask\"},{\"timestamp\":1391896667,\"amount\":0.018,\"price\":700.7,\"tid\":29248910,\"type\":\"ask\"},{\"timestamp\":1391896667,\"amount\":0.013,\"price\":700.7,\"tid\":29248909,\"type\":\"ask\"},{\"timestamp\":1391896667,\"amount\":0.187993,\"price\":700.7,\"tid\":29248908,\"type\":\"ask\"},{\"timestamp\":1391896667,\"amount\":1.633,\"price\":700.7,\"tid\":29248907,\"type\":\"ask\"},{\"timestamp\":1391896667,\"amount\":0.035,\"price\":700.7,\"tid\":29248906,\"type\":\"ask\"},{\"timestamp\":1391896667,\"amount\":5.94333,\"price\":700.7,\"tid\":29248905,\"type\":\"ask\"},{\"timestamp\":1391896667,\"amount\":0.5,\"price\":700.72,\"tid\":29248904,\"type\":\"ask\"},{\"timestamp\":1391896667,\"amount\":0.022,\"price\":701.212,\"tid\":29248903,\"type\":\"ask\"},{\"timestamp\":1391896667,\"amount\":0.016,\"price\":701.261,\"tid\":29248902,\"type\":\"ask\"},{\"timestamp\":1391896667,\"amount\":0.01,\"price\":701.993,\"tid\":29248901,\"type\":\"ask\"},{\"timestamp\":1391896667,\"amount\":0.0104,\"price\":703.079,\"tid\":29248900,\"type\":\"ask\"},{\"timestamp\":1391896666,\"amount\":0.108,\"price\":703.787,\"tid\":29248899,\"type\":\"ask\"},{\"timestamp\":1391896666,\"amount\":0.18,\"price\":703.787,\"tid\":29248898,\"type\":\"ask\"},{\"timestamp\":1391896663,\"amount\":0.05,\"price\":703.787,\"tid\":29248890,\"type\":\"ask\"},{\"timestamp\":1391896655,\"amount\":0.05,\"price\":703.787,\"tid\":29248888,\"type\":\"ask\"},{\"timestamp\":1391896653,\"amount\":0.17,\"price\":703.787,\"tid\":29248882,\"type\":\"ask\"},{\"timestamp\":1391896646,\"amount\":0.346,\"price\":703.787,\"tid\":29248881,\"type\":\"ask\"},{\"timestamp\":1391896646,\"amount\":0.454,\"price\":704,\"tid\":29248880,\"type\":\"ask\"},{\"timestamp\":1391896646,\"amount\":0.05,\"price\":704,\"tid\":29248879,\"type\":\"ask\"},{\"timestamp\":1391896643,\"amount\":0.069,\"price\":705.9,\"tid\":29248878,\"type\":\"bid\"},{\"timestamp\":1391896643,\"amount\":0.01,\"price\":705.794,\"tid\":29248877,\"type\":\"bid\"},{\"timestamp\":1391896643,\"amount\":0.011,\"price\":705.482,\"tid\":29248876,\"type\":\"bid\"},{\"timestamp\":1391896643,\"amount\":0.01,\"price\":704.804,\"tid\":29248875,\"type\":\"bid\"},{\"timestamp\":1391896641,\"amount\":0.5,\"price\":704,\"tid\":29248874,\"type\":\"bid\"},{\"timestamp\":1391896641,\"amount\":0.05,\"price\":703.787,\"tid\":29248873,\"type\":\"bid\"},{\"timestamp\":1391896635,\"amount\":0.228423,\"price\":700.7,\"tid\":29248871,\"type\":\"ask\"},{\"timestamp\":1391896635,\"amount\":2.63274,\"price\":700.701,\"tid\":29248870,\"type\":\"ask\"},{\"timestamp\":1391896635,\"amount\":0.0104,\"price\":700.975,\"tid\":29248869,\"type\":\"ask\"},{\"timestamp\":1391896635,\"amount\":0.8,\"price\":703.744,\"tid\":29248868,\"type\":\"ask\"},{\"timestamp\":1391896635,\"amount\":1.4189,\"price\":703.787,\"tid\":29248867,\"type\":\"ask\"},{\"timestamp\":1391896633,\"amount\":0.0101,\"price\":703.787,\"tid\":29248866,\"type\":\"bid\"},{\"timestamp\":1391896633,\"amount\":0.02,\"price\":703.787,\"tid\":29248865,\"type\":\"bid\"},{\"timestamp\":1391896633,\"amount\":0.01,\"price\":703.787,\"tid\":29248864,\"type\":\"bid\"},{\"timestamp\":1391896631,\"amount\":0.01,\"price\":703.744,\"tid\":29248863,\"type\":\"bid\"},{\"timestamp\":1391896625,\"amount\":2.49513,\"price\":700.7,\"tid\":29248861,\"type\":\"ask\"},{\"timestamp\":1391896625,\"amount\":0.017,\"price\":701.261,\"tid\":29248860,\"type\":\"ask\"},{\"timestamp\":1391896624,\"amount\":4,\"price\":702.999,\"tid\":29248859,\"type\":\"bid\"},{\"timestamp\":1391896617,\"amount\":1.30501,\"price\":700.7,\"tid\":29248857,\"type\":\"ask\"},{\"timestamp\":1391896617,\"amount\":0.011,\"price\":700.766,\"tid\":29248856,\"type\":\"ask\"},{\"timestamp\":1391896617,\"amount\":0.01,\"price\":700.82,\"tid\":29248855,\"type\":\"ask\"},{\"timestamp\":1391896617,\"amount\":0.015,\"price\":700.869,\"tid\":29248854,\"type\":\"ask\"},{\"timestamp\":1391896617,\"amount\":0.015,\"price\":700.938,\"tid\":29248853,\"type\":\"ask\"},{\"timestamp\":1391896617,\"amount\":0.142658,\"price\":700.97,\"tid\":29248852,\"type\":\"ask\"},{\"timestamp\":1391896617,\"amount\":0.99,\"price\":700.98,\"tid\":29248851,\"type\":\"ask\"},{\"timestamp\":1391896617,\"amount\":0.211335,\"price\":701,\"tid\":29248850,\"type\":\"ask\"},{\"timestamp\":1391896615,\"amount\":0.1,\"price\":702.999,\"tid\":29248849,\"type\":\"bid\"},{\"timestamp\":1391896607,\"amount\":0.0153326,\"price\":701,\"tid\":29248844,\"type\":\"bid\"},{\"timestamp\":1391896606,\"amount\":6,\"price\":701,\"tid\":29248843,\"type\":\"bid\"},{\"timestamp\":1391896606,\"amount\":0.015,\"price\":701,\"tid\":29248842,\"type\":\"bid\"},{\"timestamp\":1391896606,\"amount\":0.100285,\"price\":701,\"tid\":29248841,\"type\":\"bid\"},{\"timestamp\":1391896597,\"amount\":0.0403227,\"price\":701,\"tid\":29248832,\"type\":\"bid\"},{\"timestamp\":1391896596,\"amount\":0.028,\"price\":701,\"tid\":29248831,\"type\":\"bid\"},{\"timestamp\":1391896596,\"amount\":1.07985,\"price\":701,\"tid\":29248830,\"type\":\"bid\"},{\"timestamp\":1391896595,\"amount\":2.14562,\"price\":701,\"tid\":29248826,\"type\":\"bid\"},{\"timestamp\":1391896595,\"amount\":0.01,\"price\":700.98,\"tid\":29248821,\"type\":\"ask\"},{\"timestamp\":1391896595,\"amount\":0.02,\"price\":701,\"tid\":29248814,\"type\":\"bid\"},{\"timestamp\":1391896595,\"amount\":0.01,\"price\":701,\"tid\":29248813,\"type\":\"bid\"},{\"timestamp\":1391896595,\"amount\":1.07985,\"price\":701,\"tid\":29248812,\"type\":\"bid\"},{\"timestamp\":1391896595,\"amount\":0.05,\"price\":701,\"tid\":29248811,\"type\":\"bid\"},{\"timestamp\":1391896595,\"amount\":6,\"price\":701,\"tid\":29248810,\"type\":\"bid\"},{\"timestamp\":1391896595,\"amount\":0.01,\"price\":701,\"tid\":29248809,\"type\":\"bid\"},{\"timestamp\":1391896595,\"amount\":0.502407,\"price\":701,\"tid\":29248807,\"type\":\"bid\"},{\"timestamp\":1391896595,\"amount\":0.502407,\"price\":701,\"tid\":29248806,\"type\":\"bid\"},{\"timestamp\":1391896595,\"amount\":0.370295,\"price\":701,\"tid\":29248805,\"type\":\"bid\"},{\"timestamp\":1391896594,\"amount\":0.233091,\"price\":701,\"tid\":29248802,\"type\":\"bid\"},{\"timestamp\":1391896594,\"amount\":2.28902,\"price\":701,\"tid\":29248800,\"type\":\"bid\"},{\"timestamp\":1391896594,\"amount\":0.01,\"price\":701,\"tid\":29248796,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.109496,\"price\":701,\"tid\":29248795,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":1,\"price\":701,\"tid\":29248794,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":1.25107,\"price\":701,\"tid\":29248793,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":2.049,\"price\":701,\"tid\":29248792,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.052,\"price\":701,\"tid\":29248791,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.05,\"price\":701,\"tid\":29248790,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.972168,\"price\":701,\"tid\":29248789,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.0102,\"price\":701,\"tid\":29248788,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.041,\"price\":701,\"tid\":29248787,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":10.02,\"price\":701,\"tid\":29248786,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.5,\"price\":701,\"tid\":29248785,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":1.02932,\"price\":701,\"tid\":29248784,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.0964335,\"price\":701,\"tid\":29248783,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.1942,\"price\":701,\"tid\":29248782,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.1,\"price\":701,\"tid\":29248781,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.2,\"price\":701,\"tid\":29248780,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":2.133,\"price\":701,\"tid\":29248779,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.07,\"price\":701,\"tid\":29248778,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":1.909,\"price\":701,\"tid\":29248777,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.211,\"price\":701,\"tid\":29248776,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.01,\"price\":701,\"tid\":29248775,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.85,\"price\":701,\"tid\":29248774,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.02,\"price\":701,\"tid\":29248773,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.2,\"price\":701,\"tid\":29248772,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":1,\"price\":701,\"tid\":29248771,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.636,\"price\":701,\"tid\":29248770,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.0114,\"price\":701,\"tid\":29248769,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.01002,\"price\":701.01,\"tid\":29248768,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":1,\"price\":701.01,\"tid\":29248767,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.285299,\"price\":701.02,\"tid\":29248766,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.849879,\"price\":701.1,\"tid\":29248765,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.03115,\"price\":701.1,\"tid\":29248764,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.05,\"price\":701.17,\"tid\":29248763,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.05,\"price\":701.17,\"tid\":29248762,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.05,\"price\":701.17,\"tid\":29248761,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.05,\"price\":701.17,\"tid\":29248760,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.016,\"price\":701.181,\"tid\":29248759,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.0104,\"price\":701.239,\"tid\":29248758,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.0104,\"price\":701.24,\"tid\":29248757,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.0100394,\"price\":701.326,\"tid\":29248756,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.0104,\"price\":701.349,\"tid\":29248755,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":1.42202,\"price\":701.369,\"tid\":29248754,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":2.0282,\"price\":701.4,\"tid\":29248753,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.02,\"price\":701.481,\"tid\":29248752,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":1.5146,\"price\":701.5,\"tid\":29248751,\"type\":\"ask\"},{\"timestamp\":1391896594,\"amount\":0.64,\"price\":701.5,\"tid\":29248750,\"type\":\"ask\"},{\"timestamp\":1391896584,\"amount\":0.0373565,\"price\":701.564,\"tid\":29248748,\"type\":\"ask\"},{\"timestamp\":1391896576,\"amount\":0.05,\"price\":701.564,\"tid\":29248745,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":2.36264,\"price\":701.564,\"tid\":29248744,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":0.0123,\"price\":701.58,\"tid\":29248743,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":0.011,\"price\":701.608,\"tid\":29248742,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":0.016,\"price\":701.671,\"tid\":29248741,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":0.01,\"price\":701.751,\"tid\":29248740,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":0.624319,\"price\":701.753,\"tid\":29248739,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":0.01,\"price\":701.81,\"tid\":29248738,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":0.0104,\"price\":701.821,\"tid\":29248737,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":0.00333765,\"price\":702,\"tid\":29248736,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":1.02594,\"price\":702,\"tid\":29248735,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":1.95849,\"price\":702,\"tid\":29248734,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":0.5,\"price\":702,\"tid\":29248733,\"type\":\"ask\"},{\"timestamp\":1391896571,\"amount\":3.45558,\"price\":702,\"tid\":29248732,\"type\":\"ask\"},{\"timestamp\":1391896569,\"amount\":0.01,\"price\":702,\"tid\":29248729,\"type\":\"ask\"},{\"timestamp\":1391896569,\"amount\":1,\"price\":702,\"tid\":29248728,\"type\":\"ask\"},{\"timestamp\":1391896567,\"amount\":0.534425,\"price\":702,\"tid\":29248724,\"type\":\"ask\"},{\"timestamp\":1391896567,\"amount\":0.315578,\"price\":702,\"tid\":29248723,\"type\":\"ask\"},{\"timestamp\":1391896567,\"amount\":0.017,\"price\":702,\"tid\":29248722,\"type\":\"ask\"},{\"timestamp\":1391896567,\"amount\":0.5,\"price\":702,\"tid\":29248721,\"type\":\"ask\"},{\"timestamp\":1391896567,\"amount\":6.13,\"price\":702,\"tid\":29248720,\"type\":\"ask\"},{\"timestamp\":1391896567,\"amount\":0.109784,\"price\":702,\"tid\":29248719,\"type\":\"ask\"},{\"timestamp\":1391896567,\"amount\":0.203,\"price\":702,\"tid\":29248718,\"type\":\"ask\"},{\"timestamp\":1391896567,\"amount\":0.01002,\"price\":702.01,\"tid\":29248717,\"type\":\"ask\"},{\"timestamp\":1391896567,\"amount\":2.18019,\"price\":702.1,\"tid\":29248716,\"type\":\"ask\"},{\"timestamp\":1391896567,\"amount\":0.05,\"price\":702.1,\"tid\":29248715,\"type\":\"ask\"},{\"timestamp\":1391896563,\"amount\":0.287233,\"price\":703,\"tid\":29248711,\"type\":\"bid\"}]}\n";
    }

    public static TopData parseTop(Object obj) {
        JSONObject jObj = (JSONObject) obj;
//        System.out.println("BTCE.parseTop() " + jObj);
        JSONObject ticker = (JSONObject) jObj.get("btc_usd"); // ticker
//        System.out.println(" class="+ticker.getClass()+", ticker=" + ticker);
        double last = Utils.getDouble(ticker, "last");
        double bid = Utils.getDouble(ticker, "sell");
        double ask = Utils.getDouble(ticker, "buy");
//        System.out.println("bid=" + bid + ", ask=" + ask + ", last=" + last);
        return new TopData(bid, ask, last);
    }

    public static DeepData parseDeep(Object obj) {
        JSONObject jObj  = (JSONObject) obj;
        System.out.println("BTCE.parseDeep() " + jObj);
        JSONObject btc_usd = (JSONObject) jObj.get("btc_usd");
        System.out.println(" class="+btc_usd.getClass()+", btc_usd=" + btc_usd);
        JSONArray bids = (JSONArray) btc_usd.get("bids");
        System.out.println(" class="+bids.getClass()+", bids=" + bids);
        JSONArray asks = (JSONArray) btc_usd.get("asks");
        System.out.println(" class="+asks.getClass()+", asks=" + asks);

        return DeepData.create(bids, asks);
    }

    public static TradesData parseTrades(Object obj) {
        JSONObject jObj = (JSONObject) obj;
//        System.out.println("BTCE.parseTrades() " + jObj);
        JSONArray array = (JSONArray) jObj.get("btc_usd");
//        System.out.println(" class=" + array.getClass() + ", btc_usd=" + array);
        int len = array.size();
        List<TradesData.TradeData> trades = new ArrayList<TradesData.TradeData>(len);
        for (int i = 0; i < len; i++) { // {"amount":7.23385,"timestamp":1391896680,"price":700.7,"tid":29248920,"type":"bid"}
            JSONObject tObj = (JSONObject) array.get(i);
            double amount = Utils.getDouble(tObj.get("amount"));
            double price = Utils.getDouble(tObj.get("price"));
            long timestamp = Utils.getLong(tObj.get("timestamp")) * 1000;
            long tid = Utils.getLong(tObj.get("tid"));
            String typeStr = (String) tObj.get("type");
            TradesData.TradeType type = TradesData.TradeType.get(typeStr);
            TradesData.TradeData tdata = new TradesData.TradeData(amount, price, timestamp, tid, type);
            trades.add(tdata);
        }
        return new TradesData(trades);
    }
}


//////////////////////////////////////////////////////////////
//            https://btc-e.com/api/2/btc_usd/ticker
//  jObj={"ticker":{"vol":3771263.18392,"last":793.9,"updated":1391636232,"sell":792,"buy":793.9,"server_time":1391636234,"high":806.99902,"avg":796.995025,"low":786.99103,"vol_cur":4741.20896}}
//            https://btc-e.com/api/2/btc_usd/ticker
//            { "ticker": { "high":810,
//                          "low":792.23297,
//                          "avg":801.116485,
//                          "vol":4655425.43998,
//                          "vol_cur":5811.2853,
//                          "last":801.05,
//                          "buy":801.05,
//                          "sell":800.05,
//                          "updated":1391514767,
//                          "server_time":1391514768}}
// https://btc-e.com/api/2/btc_eur/ticker
// https://btc-e.com/api/2/btc_rub/ticker
//
// https://btc-e.com/api/2/btc_usd/depth
//
// https://btc-e.com/api/2/btc_usd/trades
//  What does the is_your_order field mean in a btc-e response?
//   http://bitcoin.stackexchange.com/questions/10683/what-does-the-is-your-order-field-mean-in-a-btc-e-response?rq=1
//
// place order
//  You incorrectly entered one of fields.
//   http://bitcoin.stackexchange.com/questions/8274/parameters-for-buy-order-on-btc-e?rq=1
//
// There is an API call to find the correct maximum number of places, as well as the minimum order for each currency pair.
//  https://btc-e.com/api/3/info returns a list of currencies and information.
//   https://btc-e.com/api/3/documentation#info
//
// https://btc-e.com/api/3/trades/btc_usd
//{
//	"btc_usd":[
//		{
//			"type":"ask",
//			"price":103.6,
//			"amount":0.101,
//			"tid":4861261,
//			"timestamp":1370818007
//		},
//		{
//			"type":"bid",
//			"price":103.989,
//			"amount":1.51414,
//			"tid":4861254,
//			"timestamp":1370817960
//		},
//		...
//	]
//	...
//}
//
//