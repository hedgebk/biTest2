package bthdg.ws;

import com.github.nkzawa.emitter.Emitter;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

// http://btcchina.org/websocket-api-market-data-documentation-en#java
// https://github.com/BTCChina/btcchina-websocket-api-java/blob/master/WebsocketClient.java
// https://github.com/timmolter/XChange/tree/develop/xchange-btcchina/src/main/java/com/xeiam/xchange/btcchina/service/streaming
public class BtcnWs extends SocketIoWs {
    @Override protected String getEndpointUrl() { return "https://websocket.btcchina.com"; }

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
                    wsClient.emit("subscribe", "marketdata_cnybtc");
                    wsClient.emit("subscribe", "grouporder_cnybtc");

//                //Use 'private' method to subscribe the order and account_info feed
//                try {
//                    List arg = new ArrayList();
//                    arg.add(sm.get_payload());
//                    arg.add(sm.get_sign());
//                    socket.emit("private",arg);
//                } catch (Exception e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
                }

                @Override public void onDisconnect() {}
            });

            Thread.sleep(150000);
            System.out.println("done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
