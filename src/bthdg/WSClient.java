package bthdg;

import bthdg.exch.Pair;
import bthdg.exch.TradeData;
import bthdg.osc.OscCalculator;
import bthdg.util.Utils;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;
import bthdg.ws.OkCoinWs;
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
    public static int LEN1 = 14;
    public static int LEN2 = 14;
    public static int K = 3;
    public static int D = 3;
    private static final long BAR_SIZE = Utils.toMillis("15s");

    // http://download.finance.yahoo.com/d/quotes.csv?s=USDCNY=X&f=snl1d1t1ab
    // http://finance.yahoo.com/webservice/v1/symbols/allcurrencies/quote

//  !!!!!!  https://github.com/BTCChina/btcchina-websocket-api-java/blob/master/WebsocketClient.java
//           http://btcchina.org/websocket-api-market-data-documentation-en

    public static void main(String[] args) {
        IWs ws1 = OkCoinWs.create();
//        HuobiWs.main(args);
//        BtcnWs.main(args);
//        BitstampWs.main(args);

        final OscCalculator calc = new OscCalculator(LEN1, LEN2, K, D, BAR_SIZE, 0) {
            @Override public void fine(long stamp, double stoch1, double stoch2) {
                System.out.println(" fine " + stamp + ": " + stoch1 + "; " + stoch2);
            }

            @Override public void bar(Long barStart, double stoch1, double stoch2) {
                System.out.println(" ------------bar " + barStart + ": " + stoch1 + "; " + stoch2);
            }
        };

        ws1.subscribeTrades(Pair.BTC_CNH, new ITradesListener() {
            @Override public void onTrade(TradeData tdata) {
                System.out.println("got Trade=" + tdata);
                calc.update(tdata.m_timestamp, tdata.m_price);
            }
        });

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
