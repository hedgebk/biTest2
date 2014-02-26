package bthdg;

import bthdg.exch.BaseExch;
import org.json.simple.JSONArray;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;

public class SendSms {
    public static void main(String[] args) {
// see http://stackoverflow.com/questions/18018674/not-receiving-sms-sent-from-sms-gateway-in-java
//            https://ideone.com/fork/SsYQwM
//            https://github.com/iiitd-ucla-pc3/SensorActVPDS-2.0/blob/master/app/controllers/temp.java
// http://blog.mashape.com/post/56272188360/list-of-50-sms-apis
//   http://preview.smsglobal.com/apis/http/

//        try {
//            Properties properties = BaseExch.loadKeys();
//            String key = properties.getProperty("sms_key", null);
//            log("SendSms key: " + key);
//
//            URL url = new URL("http://www.FreeSMSGateway.com/api_send");
//
//            String nos[] = {"3102168156"};
//            String to = URLEncoder.encode(JsonUtil.json.toJson(nos));
//            log(JsonUtil.json.toJson(nos));
//
//            String msg = URLEncoder.encode("hello", "UTF-8");
//
//            String query = "key=" + URLEncoder.encode(KEY);
//            query += "&";
//            query += "nonce=" + URLEncoder.encode(nonce);
//            query += "&";
//            query += "signature=" + URLEncoder.encode(signature);
//
//
//            urlParameters.add(new BasicNameValuePair("access_token", access_token));
//            urlParameters.add(new BasicNameValuePair("message", message));
//            urlParameters.add(new BasicNameValuePair("send_to", send_to));
//            urlParameters.add(new BasicNameValuePair("post_contacts", jPostContact.toString())
//
//            HttpURLConnection con = (HttpURLConnection) url.openConnection();
//            try {
//                con.setRequestMethod("POST");
//                con.setUseCaches(false);
//                con.setDoOutput(true);
//
//                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
//                con.setRequestProperty("User-Agent", USER_AGENT);
//
//                OutputStream os = con.getOutputStream();
//                try {
//                    os.write(query.getBytes());
//                    con.connect();
//                    if (con.getResponseCode() == HttpsURLConnection.HTTP_OK) {
//                        readJson(con);
//                    } else {
//                        log("ERROR: unexpected ResponseCode: " + con.getResponseCode());
//                    }
//                } finally {
//                    os.close();
//                }
//            } finally {
//                con.disconnect();
//            }
//        } catch (IOException e) {
//            log("error in SendSms: " + e);
//            e.printStackTrace();
//        }
    }
}
