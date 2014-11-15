package bthdg;

import bthdg.ws.BitstampWs;
import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLContext;
import java.util.Arrays;
import java.util.List;

public class WSClient {

    // http://download.finance.yahoo.com/d/quotes.csv?s=USDCNY=X&f=snl1d1t1ab
    // http://finance.yahoo.com/webservice/v1/symbols/allcurrencies/quote

//  !!!!!!  https://github.com/BTCChina/btcchina-websocket-api-java/blob/master/WebsocketClient.java
//           http://btcchina.org/websocket-api-market-data-documentation-en

    public static void main(String[] args) {
//        OkCoinWs.main(args);
//        HuobiWs.main(args);
//        BtcnWs.main(args);
        BitstampWs.main(args);
    }

    public static void x_main(String[] args) {
        try {
            final SocketIO socket = new SocketIO("https://plug.coinsetter.com:3000");
            socket.setDefaultSSLSocketFactory(SSLContext.getDefault());
            socket.connect(new IOCallback() {
                @Override public void onMessage(JSONObject json, IOAcknowledge ack) {
                    try {
                        System.out.println("Server said:" + json.toString(2));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override public void onMessage(String data, IOAcknowledge ack) {
                    System.out.println("Server said: " + data);
                }

                @Override public void onError(SocketIOException socketIOException) {
                    System.out.println("an Error occurred: " + socketIOException);
                    socketIOException.printStackTrace();
                }

                @Override public void onDisconnect() {
                    System.out.println("Connection terminated.");
                }

                @Override public void onConnect() {
                    System.out.println("Connection established");
//                    System.out.println("str to emit: " + str);
//                    socket.emit("request", str);
                    socket.emit("last room", "");
                }

                @Override public void on(String event, IOAcknowledge ack, Object... args) {
                    System.out.println("Server triggered event '" + event + "'; args=" + args);
                    List<Object> array = Arrays.asList(args);
                    System.out.println(" array=" + array);
                }
            });

            Thread.sleep(150000);
            System.out.println("done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
