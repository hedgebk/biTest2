package bthdg;

import bthdg.ws.HuobiWs;
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

    public static void main(String[] args) {
        HuobiWs.main(args);
//        BtcnWs.main(args);
//        BitstampWs.main(args);

//        final IWs ws1 = OkCoinWs.create();
//        ws1.subscribeTrades(Pair.BTC_CNH, new ITradesListener() {
//            int i = 0;
//            @Override public void onTrade(TradeData tdata) {
//                System.out.println("got Trade=" + tdata);
//
//                if(i++ == 10) {
//                    ws1.subscribeTop(Pair.BTC_CNH, new ITopListener() {
//                        @Override public void onTop(long timestamp, double buy, double sell) {
//                            System.out.println("got Top: timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell + "; date=" + new Date(timestamp));
//                        }
//                    });
//                }
//            }
//        });

        try {
            Thread thread = Thread.currentThread();
            synchronized (thread) {
                thread.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
