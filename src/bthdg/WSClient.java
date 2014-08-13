package bthdg;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;
import org.glassfish.tyrus.client.ClientManager;
import org.json.JSONException;
import org.json.JSONObject;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

public class WSClient {

    public static final String URL = "wss://real.okcoin.cn:10440/websocket/okcoinapi";
    public static final String SUBSCRIBE_BTCCNY_TICKER = "{'event':'addChannel','channel':'ok_btccny_ticker'}";
    public static final String SUBSCRIBE_BTCCNY_DEPTH = "{'event':'addChannel','channel':'ok_btccny_depth'}";

    public static void _main(String[] args) {
        try {
            ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();
            Session session = client.connectToServer(new Endpoint() {
                @Override public void onOpen(Session session, EndpointConfig config) {
                    System.out.println("onOpen");
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override public void onMessage(String message) {
                                System.out.println("Received message: " + message);
                            }
                        });
//                        session.getBasicRemote().sendText(SUBSCRIBE_BTCCNY_TICKER);
                        session.getBasicRemote().sendText(SUBSCRIBE_BTCCNY_DEPTH);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, new URI(URL));
            System.out.println("session isOpen="+session.isOpen() + "; session="+session);
            Thread.sleep(15000);
            System.out.println("done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            SocketIO socket = new SocketIO("http://hq.huobi.com:80/");
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
                }

                @Override public void on(String event, IOAcknowledge ack, Object... args) {
                    System.out.println("Server triggered event '" + event + "'; args=" + args );
                    List<Object> array = Arrays.asList(args);
                    System.out.println(" array=" + array );
                }
            });
//            socket.send("{\"symbolId\":\"btccny\",\"version\":1,\"msgType\":\"reqMarketDepthTop\",\"requestIndex\":1405131204513}");

            String str = "{\"symbolId\":\"btccny\",\"version\":1,\"msgType\":\"reqMarketDepthTop\",\"requestIndex\":1405131204513}";
            System.out.println("str to emit: " + str);
            socket.emit("request", str);

//            socket.emit("request", "{\"symbolId\":\"btccny\",\"version\":1,\"msgType\":\"reqTimeLine\",\"requestIndex\":1405131204513}");

//            socket.emit("request", "{\"version\":1,\"msgType\":\"reqSymbolList\"}");
//            socket.send("{\"version\":1,\"msgType\":\"reqSymbolList\"}");
            Thread.sleep(15000);
            System.out.println("done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
