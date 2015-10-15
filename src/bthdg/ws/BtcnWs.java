package bthdg.ws;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Socket;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

// http://btcchina.org/websocket-api-market-data-documentation-en#java
// https://github.com/BTCChina/btcchina-websocket-api-java/blob/master/WebsocketClient.java
// https://github.com/timmolter/XChange/tree/develop/xchange-btcchina/src/main/java/com/xeiam/xchange/btcchina/service/streaming
public class BtcnWs extends SocketIoWs {
    @Override protected String getEndpointUrl() { return "https://websocket.btcc.com"; }

    public BtcnWs() throws MalformedURLException, NoSuchAlgorithmException, URISyntaxException {
        super();
    }

    public static void main(String[] args) {
        try {
            final BtcnWs wsClient = new BtcnWs();
            wsClient.connect(new IConnectCallback() {
                @Override public void onConnect() {
                    wsClient.on("trade", new Emitter.Listener() {
                        @Override public void call(Object... args) {
                            System.out.println("trade: " + Arrays.asList(args));
                            // trade: [{"date":1415956792,"market":"btccny","amount":"0.00010000","trade_id":11536815,"price":"2353.40","type":"buy"}]
                        }
                    });
                    wsClient.on("ticker", new Emitter.Listener() {
                        @Override public void call(Object... args) {
                            System.out.println("ticker: " + Arrays.asList(args));
                            // ticker: [{"ticker":{"date":1415956792,"market":"btccny","high":2901.1,"vol":605173.1823,"last":2418.01,"low":2354.74,"buy":2353.08,"sell":2353.4,"vwap":2420.21,"open":2611.01,"prev_close":2611.01}}]
                        }
                    });
                    wsClient.on("grouporder", new Emitter.Listener() {
                        @Override public void call(Object... args) {
                            System.out.println("grouporder: " + Arrays.asList(args));
                            // grouporder: [{"grouporder":{"market":"btccny","ask":[{"totalamount":2.4282,"price":2388,"type":"ask"},{"totalamount":2.9999,"price":2387.99,"type":"ask"},{"totalamount":1.8588,"price":2387.98,"type":"ask"},{"totalamount":0.206,"price":2386.97,"type":"ask"},{"totalamount":0.2111,"price":2386.88,"type":"ask"}],"bid":[{"totalamount":0.18,"price":2382.01,"type":"bid"},{"totalamount":4.1706,"price":2377.11,"type":"bid"},{"totalamount":0.25,"price":2377.1,"type":"bid"},{"totalamount":2.35,"price":2377.03,"type":"bid"},{"totalamount":0.1,"price":2375.63,"type":"bid"}]}}]
                        }
                    });
                    wsClient.on("order", new Emitter.Listener() {
                        @Override public void call(Object... args) {
                            System.out.println("order: " + Arrays.asList(args));
                            // ?
                        }
                    });
                    wsClient.on("account_info", new Emitter.Listener() {
                        @Override public void call(Object... args) {
                            System.out.println("account_info: " + Arrays.asList(args));
                            // ?
                        }
                    });
                    wsClient.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                        @Override public void call(Object... args) {
                            System.out.println("EVENT_DISCONNECT: " + Arrays.asList(args));
                            // reconnect
                        }
                    });
                    wsClient.emit("subscribe", "marketdata_cnybtc");
                    wsClient.emit("subscribe", "grouporder_cnybtc");

                    //Use 'private' method to subscribe the order and account_info feed
                    try {
                        String[] arg = new String[]{
                                wsClient.get_payload(),
                                wsClient.get_sign()};
                        wsClient.emit("private", arg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override public void onDisconnect() {
                    System.out.println("onDisconnect()");
                }
            });

            Thread.sleep(150000);
            System.out.println("done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String get_payload() throws Exception {
        String postdata = "{\"tonce\":\"" + tonce.toString() + "\",\"accesskey\":\"" + ACCESS_KEY + "\",\"requestmethod\": \"post\",\"id\":\"" + tonce.toString() + "\",\"method\": \"subscribe\", \"params\": [\"order_cnyltc\",\"account_info\"]}";//subscribe order feed for cnyltc market and balance feed
        System.out.println("postdata is: " + postdata);
        return postdata;
    }

    public String get_sign() throws Exception {
        String params = "tonce=" + tonce.toString() + "&accesskey=" + ACCESS_KEY + "&requestmethod=post&id=" + tonce.toString() + "&method=subscribe&params=order_cnyltc,account_info"; //subscribe the order of cnyltc market and the account_info
        String hash = getSignature(params, SECRET_KEY);
        String userpass = ACCESS_KEY + ":" + hash;
        String basicAuth = DatatypeConverter.printBase64Binary(userpass.getBytes());
        return basicAuth;
    }

    public String getSignature(String data, String key) throws Exception {
        // get an hmac_sha1 key from the raw key bytes
        SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
        // get an hmac_sha1 Mac instance and initialize with the signing key
        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        mac.init(signingKey);
        // compute the hmac on input data bytes
        byte[] rawHmac = mac.doFinal(data.getBytes());
        return bytArrayToHex(rawHmac);
    }

    private String bytArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        for (byte b : a) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private String ACCESS_KEY = "YOUR_ACCESS_KEY";
    private String SECRET_KEY = "YOUR_SECRET_KEY";
    private static String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    private String postdata = "";
    private String tonce = "" + (System.currentTimeMillis() * 1000);
}
