package bthdg.ws;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import javax.net.ssl.SSLContext;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public abstract class SocketIoWs {
    private final Socket m_socket;

    protected abstract String getEndpointUrl();

    static {
        try {
            IO.setDefaultSSLContext(SSLContext.getDefault()); // default SSLContext for all sockets
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public SocketIoWs() throws MalformedURLException, NoSuchAlgorithmException, URISyntaxException {
//        m_socket = new SocketIO(getEndpointUrl());
//        m_socket.setDefaultSSLSocketFactory(SSLContext.getDefault());

        IO.Options opt = new IO.Options();
//        opt.forceNew = true;
//        opt.sslContext = mySSLContext; // per connection SSLContext
        opt.reconnection = true;
        m_socket = IO.socket(getEndpointUrl(), opt);
    }

    protected void on(String key, Emitter.Listener callback) {
        m_socket.on(key, callback );
    }

    void connect(final IConnectCallback callback) {
        m_socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override public void call(Object... args) {
                System.out.println("connected");
                callback.onConnect();
            }
        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override public void call(Object... args) {
                System.out.println("disconnected");
                callback.onDisconnect();
            }
        }).on(Socket.EVENT_ERROR, new Emitter.Listener() {
            @Override public void call(Object... args) {
                System.out.println("EVENT_ERROR: "+ Arrays.asList(args));
            }
        }).on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
            @Override public void call(Object... args) {
                System.out.println("EVENT_MESSAGE: "+Arrays.asList(args));
            }
        }).on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override public void call(Object... args) {
                System.out.println("EVENT_CONNECT_ERROR: "+Arrays.asList(args));
            }
        }).on(Socket.EVENT_CONNECT_TIMEOUT, new Emitter.Listener() {
            @Override public void call(Object... args) {
                System.out.println("EVENT_CONNECT_TIMEOUT: "+Arrays.asList(args));
            }
        }).on(Socket.EVENT_RECONNECT, new Emitter.Listener() {
            @Override public void call(Object... args) {
                System.out.println("EVENT_RECONNECT: "+Arrays.asList(args));
            }
        }).on(Socket.EVENT_RECONNECT_ERROR, new Emitter.Listener() {
            @Override public void call(Object... args) {
                System.out.println("EVENT_RECONNECT_ERROR: "+Arrays.asList(args));
            }
        }).on(Socket.EVENT_RECONNECT_FAILED, new Emitter.Listener() {
            @Override public void call(Object... args) {
                System.out.println("EVENT_RECONNECT_FAILED: "+Arrays.asList(args));
            }
        }).on(Socket.EVENT_RECONNECT_ATTEMPT, new Emitter.Listener() {
            @Override public void call(Object... args) {
                System.out.println("EVENT_RECONNECT_ATTEMPT: "+Arrays.asList(args));
            }
        }).on(Socket.EVENT_RECONNECTING, new Emitter.Listener() {
            @Override public void call(Object... args) {
                System.out.println("EVENT_RECONNECTING: "+Arrays.asList(args));
            }
        })
        ;

        m_socket.connect();

//        m_socket.connect(new IOCallback() {
//            @Override public void onMessage(JSONObject json, IOAcknowledge ack) {
//                try {
//                    System.out.println("Server said:" + json.toString(2));
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//            }
//
//            @Override public void onMessage(String data, IOAcknowledge ack) {
//                System.out.println("Server said: " + data);
//            }
//
//            @Override public void onError(SocketIOException socketIOException) {
//                System.out.println("an Error occurred: " + socketIOException);
//                socketIOException.printStackTrace();
//            }
//
//            @Override public void onDisconnect() {
//                System.out.println("Connection terminated.");
//                callback.onDisconnect();
//            }
//
//            @Override public void onConnect() {
//                System.out.println("Connection established");
//                callback.onConnect();
////                    System.out.println("str to emit: " + str);
////                    socket.emit("request", str);
//            }
//
//            @Override public void on(String event, IOAcknowledge ack, Object... args) {
//                System.out.println("Server triggered event '" + event + "'; args=" + args );
//                List<Object> array = Arrays.asList(args);
//                System.out.println(" array=" + array );
//            }
//        });
    }

    public void emit(String event, String ... arg) {
        m_socket.emit(event, arg);
    }

    public interface IConnectCallback {
        void onConnect();
        void onDisconnect();
    }
}
