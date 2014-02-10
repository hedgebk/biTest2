import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

//
// SEE HERE
//  https://github.com/abwaters/bitstamp-api/blob/master/src/com/abwaters/bitstamp/Bitstamp.java
//

public class Bitstamp {
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; BTCE-API/1.0; MSIE 6.0 compatible; +https://github.com/abwaters/bitstamp-api)" ;
    // https://www.bitfinex.com/  ?
    // crypto-trade.com ?
    private static String s_bitstampDeepTestStr = null;

    public static void main(String[] args) {
        try {
            String key = "wGwivLv2Ue3IfSmEAzvFB1kzkmEx7Mka";
            String secret="OEV0Hgx3a0r2jH9YJIl98ReElN4YGvar";
            String clientId = "605976";
            String nonce = String.valueOf(System.currentTimeMillis());

            //String message = nonce + clientId + key;

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKey);

            mac.update(nonce.getBytes()) ;
            mac.update(clientId.getBytes()) ;
            mac.update(key.getBytes()) ;

            byte[] hash = mac.doFinal(/*message.getBytes()*/);

            String signature = Utils.encodeHexString(hash).toUpperCase();
            System.out.println("signature: " + signature);

            String query = "key="+ URLEncoder.encode(key);
            query += "&";
            query += "nonce="+ URLEncoder.encode(nonce);
            query += "&";
            query += "signature="+URLEncoder.encode(signature);

            SSLContext sslctx = SSLContext.getInstance("SSL");
            sslctx.init(null, null, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sslctx.getSocketFactory());

            URL url = new URL("https://www.bitstamp.net/api/balance/");

            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setUseCaches(false) ;
            con.setDoOutput(true);

            con.setRequestProperty("Content-Type","application/x-www-form-urlencoded") ;
            con.setRequestProperty("User-Agent",USER_AGENT) ;

            OutputStream os = con.getOutputStream();
            os.write(query.getBytes());
            //PrintStream ps = new PrintStream(os);
            //ps.println(query);
            //ps.close();

            con.connect();

            if (con.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                String json = "";
                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String text;
                while((text = br.readLine()) != null) {
                    json += text;
                }
                br.close();
                System.out.println("json: " + json);
            }else{
                ;
            }
            con.disconnect();
            System.out.println("Code Response: " + con.getResponseCode());


        } catch (Exception e) {
            e.printStackTrace();
        }


//        HttpClient client = new DefaultHttpClient();
//
//                List<NameValuePair> qparams = new ArrayList<NameValuePair>();
//                qparams.add(new BasicNameValuePair("apikey",
//                        "f165dc40-ce3f-6864-7d5e-27a7188b2e62"));
//                qparams.add(new BasicNameValuePair("salt", "0123456789"));
//                qparams.add(new BasicNameValuePair("signature", "mbrhpXkP0LzNMNDygHAorqMx%2FDGovl%2FauMTOMB6RNMA%3D"));
//
//                HttpPost httpget = new HttpPost(
//                        "http://aruntest.kayako.com/api/index.php?e=/Core/Test");
//
//                HttpResponse response = client.execute(httpget);
//                System.out.println(response.getProtocolVersion());
//                System.out.println(response.getStatusLine().getStatusCode());
//                System.out.println(response.getStatusLine().getReasonPhrase());
//                System.out.println(response.getStatusLine().toString());
    }

    public static String topTestStr() {
        return "{\"timestamp\":\"1391517889\",\"last\":\"803.10\",\"volume\":\"6728.44691118\",\"high\":\"813.91\",\"ask\":\"803.10\",\"low\":\"796.83\",\"bid\":\"803.00\"}";
    }

    public static String deepTestStr() {
        if( s_bitstampDeepTestStr == null ) {
            StringBuilder sb = new StringBuilder();
            try {
                Reader fr = new FileReader("bitstampDeep.json");
                BufferedReader reader = new BufferedReader(fr);
                try {
                    String line;
                    while((line=reader.readLine())!=null) {
                        sb.append(line);
                    }
                } finally {
                    reader.close();
                }
            } catch (Exception e) {
                System.out.println("error reading bitstampDeepTestStr: " + e);
                e.printStackTrace();
            }
            s_bitstampDeepTestStr = sb.toString();
        }
        return s_bitstampDeepTestStr;
    }

    public static TopData parseTop(Object obj) {
        JSONObject jObj = (JSONObject) obj;
//        System.out.println("BITSTAMP.parseTop() " + jObj);
        String last = (String) jObj.get("last");
        String bid = (String) jObj.get("bid");
        String ask = (String) jObj.get("ask");
//        System.out.println("bid=" + bid + ", ask=" + ask + ", last=" + last);
        return new TopData(bid, ask, last);
    }

    public static DeepData parseDeep(Object obj) {
        JSONObject jObj = (JSONObject) obj;
//        System.out.println("BITSTAMP.parseDeep() " + jObj);

        JSONArray bids = (JSONArray) jObj.get("bids");
//        System.out.println(" class="+bids.getClass()+", bids=" + bids);
        JSONArray asks = (JSONArray) jObj.get("asks");
//        System.out.println(" class="+asks.getClass()+", asks=" + asks);

        return DeepData.create(bids, asks);
    }

    public static TradesData parseTrades(Object object) {
        System.out.println("BITSTAMP.parseTrades() " + object);
        JSONArray array = (JSONArray)object;
        int len = array.size();
        List<TradesData.TradeData> trades = new ArrayList<TradesData.TradeData>(len);
        for (int i = 0; i < len; i++) {
            JSONObject tObj = (JSONObject) array.get(i); // {"amount":"0.02500000","price":"683.00","tid":3406676,"date":"1391901009"}
            double amount = Fetcher.getDouble(tObj.get("amount"));
            double price = Fetcher.getDouble(tObj.get("price"));
            long timestamp = Fetcher.getLong(tObj.get("date")) * 1000;
            long tid = (Long) tObj.get("tid");
            TradesData.TradeData tdata = new TradesData.TradeData(amount, price, timestamp, tid, null);
            trades.add(tdata);
        }
        return new TradesData(trades);
    }

    public static String tradesTestStr() {
        return "[{\"amount\":\"0.02500000\",\"price\":\"683.00\",\"tid\":3406676,\"date\":\"1391901009\"},{\"amount\":\"3.44293760\",\"price\":\"680.10\",\"tid\":3406675,\"date\":\"1391901004\"},{\"amount\":\"0.35592240\",\"price\":\"681.95\",\"tid\":3406674,\"date\":\"1391901004\"},{\"amount\":\"0.06000000\",\"price\":\"681.95\",\"tid\":3406673,\"date\":\"1391901004\"},{\"amount\":\"3.93895847\",\"price\":\"684.90\",\"tid\":3406672,\"date\":\"1391900996\"},{\"amount\":\"1.96647298\",\"price\":\"684.90\",\"tid\":3406671,\"date\":\"1391900985\"},{\"amount\":\"2.03352702\",\"price\":\"684.90\",\"tid\":3406670,\"date\":\"1391900981\"},{\"amount\":\"2.96647298\",\"price\":\"684.89\",\"tid\":3406669,\"date\":\"1391900981\"},{\"amount\":\"0.13300000\",\"price\":\"684.89\",\"tid\":3406668,\"date\":\"1391900974\"},{\"amount\":\"0.74886633\",\"price\":\"684.89\",\"tid\":3406667,\"date\":\"1391900960\"},{\"amount\":\"0.32373920\",\"price\":\"684.00\",\"tid\":3406666,\"date\":\"1391900960\"},{\"amount\":\"1.00000000\",\"price\":\"681.02\",\"tid\":3406665,\"date\":\"1391900960\"},{\"amount\":\"5.82453974\",\"price\":\"680.02\",\"tid\":3406664,\"date\":\"1391900959\"},{\"amount\":\"4.17546026\",\"price\":\"680.04\",\"tid\":3406663,\"date\":\"1391900959\"},{\"amount\":\"0.02500000\",\"price\":\"684.00\",\"tid\":3406662,\"date\":\"1391900957\"}]";
    }
}



//////////////////////////////////////////////////////////////
// https://www.bitstamp.net/api/
//  https://www.bitstamp.net/api/ticker/
//     last - last BTC price
//     high - last 24 hours price high
//     low - last 24 hours price low
//     volume - last 24 hours volume
//     bid - highest buy order
//     ask - lowest sell order
//            {"high": "349.90",
//             "last": "335.23",
//             "timestamp": "1384198415",
//             "bid": "335.00",
//             "volume": "33743.67611671",
//             "low": "300.28",
//             "ask": "335.23"}
// Do not make more than 600 request per 10 minutes or we will ban your IP address.
//    ticker_url = { "method": "GET", "url": "https://www.bitstamp.net/api/ticker/" }
//        buy_url = { "method": "POST", "url": "https://www.bitstamp.net/api/buy/" }
//        sell_url = { "method": "POST", "url": "https://www.bitstamp.net/api/sell/" }
//        #order_url = { "method": "POST", "url": "https://data.mtgox.com/api/1/generic/private/order/result" }
//        #open_orders_url = { "method": "POST", "url": "https://data.mtgox.com/api/1/generic/private/orders" }
