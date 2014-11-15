package bthdg.ws;

import org.glassfish.tyrus.client.ClientManager;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;

// http://img.okcoin.cn/about/ws_api.do
//  error codes: https://www.okcoin.cn/about/ws_request.do
//  ping, okcoin.com: https://www.okcoin.com/about/ws_faq.do
public class OkCoinWs {
    public static final String URL = "wss://real.okcoin.cn:10440/websocket/okcoinapi";
    public static final String SUBSCRIBE_BTCCNY_TICKER = "{'event':'addChannel','channel':'ok_btccny_ticker'}";
// [{"channel":"ok_btccny_ticker",
//   "data":{"buy":"2606.01",
//           "high":"2977.9",
//           "last":"2607.01",
//           "low":"2385",
//           "sell":"2607",
//           "timestamp":"1415921659329",
//           "vol":"607,881.07"}}]
// [{"channel":"ok_btccny_ticker",
//   "data":{"buy":"2606.03",
//           "high":"2977.9",
//           "last":"2606.03",
//           "low":"2385",
//           "sell":"2606.95",
//           "timestamp":"1415921660033",
//           "vol":"607,891.07"}}]
// [{"channel":"ok_btccny_ticker",
//   "data":{"buy":"2606.05",
//           "high":"2977.9",
//           "last":"2606.75",
//           "low":"2385",
//           "sell":"2606.73",
//           "timestamp":"1415921661992",
//           "vol":"607,889.10"}}]
// [{"channel":"ok_btccny_ticker",
//   "data":{"buy":"2606.05",
//           "high":"2977.9",
//           "last":"2606.05",
//           "low":"2385",
//           "sell":"2606.68",
//           "timestamp":"1415921663367",
//           "vol":"607,889.34"}}]

    // BTC 20 Market Depth
    public static final String SUBSCRIBE_BTCCNY_DEPTH = "{'event':'addChannel','channel':'ok_btccny_depth'}";
    // BTC 60 Market Depth: "ok_btccny_depth60"

// [{"channel":"ok_btccny_depth",
//   "data":{"bids":[[2637.37,2.727],[2637,1],[2636.44,3.999],[2635.07,5.024],[2635.05,5.624],[2635,0.2],[2634,1.3],[2633,9.206],[2632.22,6],[2632.1,22],[2630.01,80.4],[2630,3.633],[2626.24,0.47],[2625.78,0.5],[2624.31,2.102],[2624.3,4.04],[2624.2,0.1],[2623,34.52],[2622.79,6],[2621,40.229]],
//           "asks":[[2655,0.01],[2653.24,0.5],[2651.91,0.03],[2650,1],[2649.99,0.03],[2649.52,0.03],[2648.9,0.1],[2648.87,0.88],[2648.8,0.76],[2648.71,0.1],[2648,12.95],[2647.99,0.03],[2647.83,0.03],[2647.08,5],[2644.79,0.03],[2644.69,2.071],[2644.68,0.58],[2644.67,0.66],[2644.64,0.614],[2638.26,0.939]],
//           "timestamp":"1415831343133"}}]
// [{"channel":"ok_btccny_depth",
//   "data":{"bids":[[2637.37,2.727],[2637,1],[2636.44,3.999],[2635.07,5.024],[2635.05,5.624],[2635,0.2],[2634,1.3],[2633,9.206],[2632.22,6],[2632.1,22],[2630.01,80.4],[2630,3.633],[2626.24,0.47],[2625.78,0.5],[2624.31,2.102],[2624.3,4.04],[2624.2,0.1],[2623,34.52],[2622.79,6],[2621,40.229]],
//           "asks":[[2655,0.01],[2653.24,0.5],[2651.91,0.03],[2650,1],[2649.99,0.03],[2649.52,0.03],[2648.9,0.1],[2648.87,0.88],[2648.8,0.76],[2648.71,0.1],[2648,12.95],[2647.99,0.03],[2647.83,0.03],[2647.08,5],[2644.79,0.03],[2644.69,2.071],[2644.68,0.58],[2644.67,0.66],[2644.64,0.614],[2638.26,0.909]],
//           "timestamp":"1415831343526"}}]

    public static final String SUBSCRIBE_BTCCNY_TRADES = "{'event':'addChannel','channel':'ok_btccny_trades'}";
// [{"channel":"ok_btccny_trades",
//   "data":[["2595.86","0.01","07:50:37","bid"],
//           ["2595.65","0.706","07:50:37","ask"],
//           ["2595.63","8.579","07:50:38","ask"],
//           ["2595.47","0.199","07:50:38","ask"],
//           ["2595.47","1.801","07:50:38","ask"],
//           ["2595.56","0.5","07:50:38","bid"],
//           ["2595.56","0.791","07:50:38","bid"],
//           ["2595.56","1.914","07:50:38","bid"],
//           ...
//           ["2595.63","0.168","07:50:46","ask"],
//           ["2595.74","0.066","07:50:47","ask"]]}]
// [{"channel":"ok_btccny_trades",
//   "data":[["2595.74","0.066","07:50:47","ask"]]}]
// [{"channel":"ok_btccny_trades",
//   "data":[["2595.74","0.38","07:50:47","ask"]]}]

    public static final String UNSUBSCRIBE_BTCCNY_TRADES = "{'event':'removeChannel','channel':'ok_btccny_trades'}";

//    websocket.send("{'event':'ping'}");
//function spotTrade(channel, symbol, type, price, amount) {//下单接口  使用前请先配置partner 和 secretKey
//    doSend("{'event':'addChannel','channel':'"+channel+"','parameters':{'partner':'"+partner+"','secretkey':'"+secretKey+"','symbol':'"+symbol+"','type':'"+type+"','price':'"+price+"','amount':'"+amount+"'}}");
//}
//function spotCancelOrder(channel, symbol, order_id) { //撤销订单
//        doSend("{'event':'addChannel','channel':'"+channel+"','parameters':{'partner':'"+partner+"','secretkey':'"+secretKey+"','symbol':'"+symbol+"','order_id':'"+order_id+"'}}");
//}

    public static void main(String[] args) {
        try {
            ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();
            Session session = client.connectToServer(new Endpoint() {
                @Override public void onOpen(final Session session, EndpointConfig config) {
                    System.out.println("onOpen");
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            private int m_counter;
                            @Override public void onMessage(String message) {
                                System.out.println("Received message: " + message);
                                m_counter++;
                                if (m_counter == 4) {
                                    try {
                                        session.getBasicRemote().sendText(UNSUBSCRIBE_BTCCNY_TRADES);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });
//                        session.getBasicRemote().sendText(SUBSCRIBE_BTCCNY_TICKER);
//                        session.getBasicRemote().sendText(SUBSCRIBE_BTCCNY_DEPTH);
                        session.getBasicRemote().sendText(SUBSCRIBE_BTCCNY_TRADES);
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

}
