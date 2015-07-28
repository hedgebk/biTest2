package bthdg.ws;

import bthdg.exch.*;
import bthdg.util.Utils;
import org.glassfish.tyrus.client.ClientManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;

// http://img.okcoin.cn/about/ws_api.do
//  error codes: https://www.okcoin.cn/about/ws_request.do
//  ping, okcoin.com: https://www.okcoin.com/about/ws_faq.do
//
// ! How to know whether connection is lost?
//     OKCoin provides heartbeat verification process. Clients send: {'event':'ping'}, the server returns heartbeats {"event":"pong"} to indicate connection between the clients and the server. If the clients stop receiving the heartbeats, then they will need to reconnect with the server.
//
// https://github.com/OKCoin/websocket/blob/master/java/src/com/okcoin/websocket/WebSocketBase.java
public class OkCoinWs extends BaseWs {
    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Hong_Kong"); // utc+08:00 Beijing, Hong Kong, Urumqi
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH); // 09:26:02
    static {
        sdf.setTimeZone(TZ);
    }

    public static final String URL = "wss://real.okcoin.cn:10440/websocket/okcoinapi";
    public static final String BTCCNY_TICKER_CHANNEL = "ok_btccny_ticker";
//    public static final String SUBSCRIBE_BTCCNY_TICKER = "{'event':'addChannel','channel':'ok_btccny_ticker'}";
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

    public static final String BTCCNY_TRADES_CHANNEL = "ok_btccny_trades";
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

    private Session m_session;
    private final Map<String,MessageHandler.Whole<Object>> m_channelListeners = new HashMap<String,MessageHandler.Whole<Object>>();
    private MessageHandler.Whole<String> m_messageHandler;
    private boolean m_stopped;

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
//System.out.println("Received message: " + message);
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
                        session.getBasicRemote().sendText(""                            /* SUBSCRIBE_BTCCNY_TRADES */);
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

    public static IWs create(Properties keys) {
        OkCoin.init(keys);
        return new OkCoinWs();
    }

    @Override public Exchange exchange() {
        return Exchange.OKCOIN;
    }

    @Override public void subscribeTrades(Pair pair, ITradesListener listener) throws Exception {
        if (pair != Pair.BTC_CNH) {
            throw new RuntimeException("pair " + pair + " not supported yet");
        }
        m_tradesListener = listener;
        m_channelListeners.put(BTCCNY_TRADES_CHANNEL, new MessageHandler.Whole<Object>() {
            @Override public void onMessage(Object json) { // [["2228.01","0.2","09:26:02","ask"]]
//System.out.println("   tradesDataListener.onMessage() json=" + json);
                if (json instanceof JSONArray) {
                    JSONArray array = (JSONArray) json;
                    int length = array.size();
//System.out.println("    trades array length = " + length);
                    for(int i = 0; i < length; i++) {
                        Object tradeObj = array.get(i); // ["2231.73","0.056","10:10:00","ask"]
//System.out.println("     tradeObj["+i+"]: " + tradeObj + ";  class="+tradeObj.getClass());
                        if (tradeObj instanceof JSONArray) {
                            JSONArray tradeItem = (JSONArray) tradeObj;
//System.out.println("     tradeItem[" + i + "]: " + tradeItem);
                            String priceStr = (String) tradeItem.get(0);
                            String size = (String) tradeItem.get(1);
                            String time = (String) tradeItem.get(2);
                            String side = (String) tradeItem.get(3);
                            long millis = parseTimeToDate(time);
//System.out.println("     price=" + priceStr + "; size=" + size + "; time=" + time + "; side=" + side + "; millis=" + millis);
                            if (millis != 0) {
                                double amount = Utils.getDouble(size);
                                double price = Utils.getDouble(priceStr);
                                TradeType type = TradeType.get(side);
                                TradeData tdata = new TradeData(amount, price, millis, 0, type);
//System.out.println("      TradeData=" + tdata);
                                if (m_tradesListener != null) {
                                    m_tradesListener.onTrade(tdata);
                                }
                            }
                        }
                    }
                }
            }
        });
        subscribe(BTCCNY_TRADES_CHANNEL);
    }

    @Override public void subscribeTop(Pair pair, ITopListener listener) throws Exception {
        if (pair != Pair.BTC_CNH) {
            throw new RuntimeException("pair " + pair + " not supported yet");
        }
        m_topListener = listener;
        m_channelListeners.put(BTCCNY_TICKER_CHANNEL, new MessageHandler.Whole<Object>() {
            @Override public void onMessage(Object json) { // {"high":"1976.29","vol":"65,980.35","last":"1957.91","low":"1917.19","buy":"1957.85","sell":"1957.91","timestamp":"1419804927532"}
//System.out.println("   topDataListener.onMessage() json=" + json + ";  class=" + json.getClass());
                if (json instanceof JSONObject) {
                    JSONObject jsonObject = (JSONObject) json;
                    String buyStr = (String) jsonObject.get("buy");
                    String sellStr = (String) jsonObject.get("sell");
                    String timestampStr = (String) jsonObject.get("timestamp");
//System.out.println("     buyStr=" + buyStr + "; sellStr=" + sellStr + "; timestampStr=" + timestampStr);
                    double buy = Utils.getDouble(buyStr);
                    double sell = Utils.getDouble(sellStr);
                    long timestamp = Utils.getLong(timestampStr);
//System.out.println("      buy=" + buy + "; sell=" + sell + "; timestamp=" + timestamp);
                    if (m_topListener != null) {
                        m_topListener.onTop(timestamp, buy, sell);
                    }
                }
            }
        });
        subscribe(BTCCNY_TICKER_CHANNEL);
    }

    @Override public void stop() {
        try {
            m_stopped = true;
            if (m_session != null) {
                m_session.close();
                System.out.println("OkCoin: session closed");
            }
        } catch (IOException e) {
            System.out.println("error close OkCoin session: " + e);
            e.printStackTrace();
        }
    }

    @Override public String getPropPrefix() {
        return "ok.";
    }

    private void subscribe(final String channel) throws Exception {
        if( m_session == null ) {
            connect(new Runnable() {
                @Override public void run() {
                    sendSubscribe(channel);
                }
            });
        } else {
            sendSubscribe(channel);
        }
    }

    private void sendSubscribe(String channel) {
        String subscribeCmd = "{'event':'addChannel','channel':'" + channel + "'}";
        System.out.println("sendSubscribe() channel=" + channel + "; subscribeCmd=" + subscribeCmd + "; m_session=" + m_session);

        addMessageHandlerIfNeeded();
        try {
            m_session.getBasicRemote().sendText(subscribeCmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addMessageHandlerIfNeeded() {
        if( m_messageHandler == null ) {
            m_messageHandler = new MessageHandler.Whole<String>() {
                @Override public void onMessage(String message) {
//System.out.println("Received message: " + message);
                    Object obj = parseJson(message);
                    OkCoinWs.this.onMessage(obj);
                }
            };
            m_session.addMessageHandler(m_messageHandler);
        }
    }

    private void onMessage(Object json) {
//System.out.println("Received json message (class="+json.getClass()+"): " + json);
        // [{ "channel":"ok_btccny_trades","data":[["2228.01","0.2","09:26:02","ask"]]}]
        if (json instanceof JSONArray) {
            JSONArray array = (JSONArray) json;
            int length = array.size();
//                    System.out.println(" array length = " + length);
            for(int i = 0; i < length; i++) {
                Object channelObj = array.get(i);
//                        System.out.println(" channelObj["+i+"]: " + channelObj + ";  class="+channelObj.getClass());
                if(channelObj instanceof JSONObject) {
                    JSONObject channelItem = (JSONObject)channelObj;
//                            System.out.println(" channelItem["+i+"]: " + channelItem);
                    String channel = (String) channelItem.get("channel");
//                            System.out.println("  channel: " + channelItem);
                    MessageHandler.Whole<Object> channelListener = m_channelListeners.get(channel);
                    if(channelListener != null) {
                        Object data = channelItem.get("data");
//                                System.out.println("  data: " + data);
                        channelListener.onMessage(data);
                    } else {
                        System.out.println("no listener for channel '" + channel + "'; keys:" + m_channelListeners.keySet());
                    }
                }
            }
        }
    }

    private Object parseJson(String str) {
        JSONParser parser = new JSONParser();
        try {
            return parser.parse(str);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void connect(final Runnable callback) throws Exception {
        try {
            ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override public void onOpen(final Session session, EndpointConfig config) {
                    System.out.println("onOpen session=" + session + "; EndpointConfig=" + config);
                    m_session = session;
                    callback.run();
                }

                @Override public void onClose(Session session, CloseReason closeReason) {
                    System.out.println("onClose session=" + session + "; closeReason=" + closeReason);
                    m_messageHandler = null;
                    if (m_stopped) {
                        System.out.println("session stopped - no reconnect");
                    } else {
                        new ReconnectThread().start();
                    }
                }

                @Override public void onError(Session session, Throwable thr) {
                    System.out.println("onError session=" + session + "; Throwable=" + thr);
                    thr.printStackTrace();
                }
            }, cec, new URI(URL));
            System.out.println("session isOpen=" + m_session.isOpen() + "; session=" + m_session);
        } catch (Exception e) {
            System.out.println("connectToServer error: " + e);
            throw e;
        }
    }

    private static Calendar NOW_CALENDAR = Calendar.getInstance(TZ, Locale.ENGLISH);
    private static Calendar OUT_CALENDAR = Calendar.getInstance(TZ, Locale.ENGLISH);

    // we got only time in mkt tick - need to figure out the date
    private static long parseTimeToDate(String time) {
        try {
            Date date;
            synchronized (sdf) { // parse time
                date = sdf.parse(time);
            }

            long nowMillis = System.currentTimeMillis();
            int year;
            int month;
            int day;
            synchronized (NOW_CALENDAR) {
                NOW_CALENDAR.setTimeInMillis(nowMillis);
                year = NOW_CALENDAR.get(Calendar.YEAR);
                month = NOW_CALENDAR.get(Calendar.MONTH);
                day = NOW_CALENDAR.get(Calendar.DAY_OF_MONTH);
            }

            synchronized (OUT_CALENDAR) {
                OUT_CALENDAR.setTime(date);
                OUT_CALENDAR.set(Calendar.YEAR, year);
                OUT_CALENDAR.set(Calendar.MONTH, month);
                OUT_CALENDAR.set(Calendar.DAY_OF_MONTH, day);
                long outMillis = OUT_CALENDAR.getTimeInMillis();

                long millisDiff = nowMillis - outMillis;
                if (Math.abs(millisDiff) > (Utils.ONE_DAY_IN_MILLIS >> 1)) {
                    OUT_CALENDAR.add(Calendar.DAY_OF_MONTH, (outMillis > nowMillis) ? -1 : 1);
                }
                return OUT_CALENDAR.getTimeInMillis();
            }
        } catch (java.text.ParseException e) {
            System.out.println("error parsing time=" + time);
        }
        return 0;
    }

    private class ReconnectThread extends Thread {
        @Override public void run() {
            System.out.println("reconnect thread started");
            try {
                long reconnectTimeout = 3000;
                int attempt = 1;
                boolean iterate = true;
                while (iterate) {
                    System.out.println("reconnect attempt " + attempt + "; waiting " + reconnectTimeout + " ms...");
                    Thread.sleep(reconnectTimeout);
                    try {
                        connect(new Runnable() {
                            @Override public void run() {
                                System.out.println("reconnected. resubscribing...");
                                resubscribe();
                            }
                        });
                        iterate = false;
                    } catch (Exception e) {
                        System.out.println("reconnect error: " + e);
                        attempt++;
                        reconnectTimeout = reconnectTimeout * 3 / 2;
                    }
                }
                System.out.println("reconnect thread finished");
            } catch (Exception e) {
                System.out.println("reconnect error: " + e);
                e.printStackTrace();
            }
        }
    }

    private void resubscribe() {
        try {
            if (m_tradesListener != null) {
                subscribe(BTCCNY_TRADES_CHANNEL);
            }
            if (m_topListener != null) {
                subscribe(BTCCNY_TICKER_CHANNEL);
            }
        } catch (Exception e) {
            System.out.println("resubscribe error: " + e);
            e.printStackTrace();
        }
    }
}
