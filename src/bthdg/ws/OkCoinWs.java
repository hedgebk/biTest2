package bthdg.ws;

import bthdg.Log;
import bthdg.exch.*;
import bthdg.util.Utils;
import org.glassfish.tyrus.client.ClientManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.websocket.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

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

//        Logger.getAnonymousLogger()/*getGlobal()*/.setLevel(Level.ALL);
        Logger.getLogger(ClientManager.class.getName()).setLevel(Level.ALL);
    }

    public static final String URL = "wss://real.okcoin.cn:10440/websocket/okcoinapi";
    public static final int DEFAULT_MAX_SESSION_IDLE_TIMEOUT = 25000;

    public static final String BTCCNY_TICKER_CHANNEL = "ok_btccny_ticker";
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

    public static final String EXECS_CHANNEL = "ok_sub_spotcny_trades";
//    [
//    {
//        "channel": "ok_sub_spotusd_trades",
//            "data": {
//        "averagePrice": "0",
//                "completedTradeAmount": "0",
//                "createdDate": 1422258604000,
//                "id": 268013884,
//                "orderId": 268013884,
//                "sigTradeAmount": "0",
//                "sigTradePrice": "0",
//                "status": -1,
//                "symbol": "btc_usd",
//                "tradeAmount": "1.105",
//                "tradePrice": "0",
//                "tradeType": "buy",
//                "tradeUnitPrice": "1853.74",
//                "unTrade": "0"
//    }
//    }
//    ]
//    createdDate: created date
//    orderId: order ID
//    tradeType: trade type (buy: buy;sell: sell;buy_market: buy at market price;sell_market: sell at market price)
//    sigTradeAmount:quantity in a single transaction
//    sigTradePrice:rate in a single transaction
//    tradeAmount:for market sell orders: total sell quantity, for limit orders: order quantity
//    tradeUnitPrice:for market buy orders: total buy amount, for limit orders: order rate
//    symbol:btc_cny;ltc_cny
//    completedTradeAmount: filled 'tradeAmount'
//    tradePrice: filled amount
//    averagePrice: average transaction price
//    unTrade: unfilled amount in cny for market order, unfilled coin quantity for limit order
//    status: -1: cancelled, 0: pending, 1: partially filled, 2: fully filled, 4: cancel request in process
    public static final String UNSUBSCRIBE_BTCCNY_TRADES = "{'event':'removeChannel','channel':'ok_btccny_trades'}";

//    websocket.send("{'event':'ping'}");
//function spotTrade(channel, symbol, type, price, amount) {//下单接口  使用前请先配置partner 和 secretKey
//    doSend("{'event':'addChannel','channel':'"+channel+"','parameters':{'partner':'"+partner+"','secretkey':'"+secretKey+"','symbol':'"+symbol+"','type':'"+type+"','price':'"+price+"','amount':'"+amount+"'}}");
//}
//function spotCancelOrder(channel, symbol, order_id) { //撤销订单
//        doSend("{'event':'addChannel','channel':'"+channel+"','parameters':{'partner':'"+partner+"','secretkey':'"+secretKey+"','symbol':'"+symbol+"','order_id':'"+order_id+"'}}");
//}

    public static final String ACCT_CHANNEL = "ok_sub_spotcny_userinfo";



    private Session m_session;
    private final Map<String,MessageHandler.Whole<Object>> m_channelListeners = new HashMap<String,MessageHandler.Whole<Object>>();
    private MessageHandler.Whole<String> m_messageHandler;
    private boolean m_stopped;
    private boolean m_tradesSubscribed;
    private boolean m_topSubscribed;
    private Runnable m_callback;

    private static void log(String s) { Log.log("OK: "+s); }
    private static void err(String s, Exception e) { Log.err("OK: "+s, e); }

    public static void main_(String[] args) {
        try {
            ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
//Logger.getLogger(ClientManager.class.getName()).setLevel(Level.ALL);

//            Logger l = Logger.getLogger("org.glassfish.grizzly.http.server.HttpHandler");
//            l.setLevel(Level.FINE);
//            l.setUseParentHandlers(false);
//            ConsoleHandler ch = new ConsoleHandler();
//            ch.setLevel(Level.ALL);
//            l.addHandler(ch);

//            Handler fh = new ConsoleHandler(); // FileHandler("/tmp/jersey_test.log");
//            Logger.getLogger("").addHandler(fh);
//            Logger.getLogger("com.sun.jersey").setLevel(Level.FINEST);

//            -Djava.util.logging.config.file=log.conf
//              handlers=java.util.logging.ConsoleHandler
//              java.util.logging.ConsoleHandler.level=ALL
//              .level=ALL
//              org.glassfish.level=CONFIG
//              org.glassfish.level=ALL

//            handlers=java.util.logging.ConsoleHandler
//            .level=FINE
//            java.util.logging.ConsoleHandler.level=ALL

//            .handlers= java.util.logging.ConsoleHandler
//            .level= ALL
//            java.util.logging.FileHandler.pattern = logs/java%u.log
//            java.util.logging.FileHandler.limit = 50000
//            java.util.logging.FileHandler.count = 1
//            java.util.logging.FileHandler.formatter = java.util.logging.XMLFormatter
//            java.util.logging.ConsoleHandler.level = ALL
//            java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter
//            org.glassfish.level = FINEST

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
//                        session.getBasicRemote().sendText(SUBSCRIBE_BTCCNY_TICKER);
                        session.getBasicRemote().sendText("{'event':'addChannel','channel':'ok_sub_spotcny_btc_ticker'}");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, new URI(URL));
            log("session isOpen=" + session.isOpen() + "; session=" + session);
            Thread.sleep(25000);
            log("done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            Config config = new Config();
            Properties keys = config.m_keys;
            final OkCoinWs ok = OkCoinWs.create(keys);

            final Thread pingThread = new Thread() {
                @Override public void run() {
                    while (!isInterrupted()) {
                        try {
                            synchronized (this) {
                                wait(10000);
                            }
                        } catch (InterruptedException e) {
                            err("ping error: " + e, e);
                        }
                        ok.sendPing();
                    }
                    log("ping thread finished");
                }
            };

            ok.connect(new Runnable() {
                @Override public void run() {
                    log(" connected on " + this);
                    try {
//                        ok.subscribeExecs(Tres.PAIR, new IExecsListener() {});
                        ok.subscribeAcct(new IAcctListener() {});

                        pingThread.start();

                    } catch (Exception e) {
                        err("error: " + e, e);
                    }
                }
            });

//"tradeAmount":"0.034","createdDate":1456353398382,"orderId":2032282774,"unTrade":"0.034","completedTradeAmount":"0","averagePrice":"0","id":2032282774,"tradePrice":"0","tradeType":"buy","status":0,"tradeUnitPrice":"2806.97"}
// submitted
//"orderId":2032282774,"unTrade":"0","tradeUnitPrice":"2806.97","sigTradePrice":"0","tradeAmount":"0.034","createdDate":1456353398000,"completedTradeAmount":"0","averagePrice":"0","id":2032282774,"sigTradeAmount":"0","tradePrice":"0","tradeType":"buy","status":-1}
// cancelled
//
//"tradeAmount":"0","createdDate":1456353404432,"orderId":2032283532,"unTrade":"168.42","completedTradeAmount":"0","averagePrice":"0","id":2032283532,"tradePrice":"0","tradeType":"buy_market","status":0,"tradeUnitPrice":"168.42"}
// submitted
//"orderId":2032283532,"unTrade":"0","tradeUnitPrice":"168.42","sigTradePrice":"2807.27","tradeAmount":"0","createdDate":1456353404000,"completedTradeAmount":"0.059","averagePrice":"2807.27","id":2032283532,"sigTradeAmount":"0.059","tradePrice":"165.62","tradeType":"buy_market","status":2}
// filled
//
//"tradeAmount":"0.042","createdDate":1456353407996,"orderId":2032283966,"unTrade":"0.042","completedTradeAmount":"0","averagePrice":"0","id":2032283966,"tradePrice":"0","tradeType":"sell","status":0,"tradeUnitPrice":"2807.28"}
// submitted
//"orderId":2032283966,"unTrade":"0.031","tradeUnitPrice":"2807.28","sigTradePrice":"2807.35","tradeAmount":"0.042","createdDate":1456353407000,"completedTradeAmount":"0.011","averagePrice":"2807.35","id":2032283966,"sigTradeAmount":"0.011","tradePrice":"30.88","tradeType":"sell","status":1}
// partial
//"orderId":2032283966,"unTrade":"0","tradeUnitPrice":"2807.28","sigTradePrice":"2807.33","tradeAmount":"0.042","createdDate":1456353407000,"completedTradeAmount":"0.042","averagePrice":"2807.34","id":2032283966,"sigTradeAmount":"0.031","tradePrice":"117.90","tradeType":"sell","status":2}
// filled



//{"info":{"free":{"btc":0.64469,"ltc":0,"cny":1031.62907},"freezed":{"btc":0,"ltc":0,"cny":93.36303}}}
//{"info":{"free":{"btc":0.67769,"ltc":0,"cny":1031.63042},"freezed":{"btc":0,"ltc":0,"cny":0.01887}}}
//{"info":{"free":{"btc":0.67769,"ltc":0,"cny":774.2204},"freezed":{"btc":0,"ltc":0,"cny":257.4289}}}
//{"info":{"free":{"btc":0.69669,"ltc":0,"cny":774.2204},"freezed":{"btc":0,"ltc":0,"cny":203.68056}}}


            log("waiting 60sec");
            Thread.sleep(60000);
            pingThread.interrupt();
            log("done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPing() {
        if( m_session != null ) {
            try {
                log("send ping");
                m_session.getBasicRemote().sendText("{'event':'ping'}");
            } catch (IOException e) {
                err("ping send error", e);
            }

        } else {
            log("can not send PING - no session");
        }
    }

    public static OkCoinWs create(Properties keys) {
        OkCoin.init(keys);
        return new OkCoinWs();
    }

    @Override public Exchange exchange() {
        return Exchange.OKCOIN;
    }

    @Override public void subscribeExecs(Pair pair, IExecsListener listener) throws Exception {
        if (pair != Pair.BTC_CNH) {
            throw new RuntimeException("pair " + pair + " not supported yet");
        }
        m_execsListener = listener;
        m_channelListeners.put(EXECS_CHANNEL, new MessageHandler.Whole<Object>() {
            @Override public void onMessage(Object json) {
log("   execsListener.onMessage() json=" + json);
            }
        });
        subscribe(EXECS_CHANNEL, true);
    }

    public void subscribeAcct(IAcctListener listener) throws Exception {
        m_acctListener = listener;
        m_channelListeners.put(ACCT_CHANNEL, new MessageHandler.Whole<Object>() {
            @Override public void onMessage(Object json) {
log("   acctListener.onMessage() json=" + json);
            }
        });
        subscribe(ACCT_CHANNEL, true);
    }

    @Override public void subscribeTrades(Pair pair, ITradesListener listener) throws Exception {
        if (pair != Pair.BTC_CNH) {
            throw new RuntimeException("pair " + pair + " not supported yet");
        }
        m_tradesListener = listener;
        m_channelListeners.put(BTCCNY_TRADES_CHANNEL, new MessageHandler.Whole<Object>() {
            @Override public void onMessage(Object json) { // [["2228.01","0.2","09:26:02","ask"]]
//log("   tradesDataListener.onMessage() json=" + json);
                if (json instanceof JSONArray) {
                    JSONArray array = (JSONArray) json;
                    int length = array.size();
//log("    trades array length = " + length);
                    long lastMillis = 0;
                    for(int i = 0; i < length; i++) {
                        Object tradeObj = array.get(i); // ["2231.73","0.056","10:10:00","ask"]
//log("     tradeObj["+i+"]: " + tradeObj + ";  class="+tradeObj.getClass());
                        if (tradeObj instanceof JSONArray) {
                            JSONArray tradeItem = (JSONArray) tradeObj;
//log("     tradeItem[" + i + "]: " + tradeItem);
                            String priceStr = (String) tradeItem.get(0);
                            String size = (String) tradeItem.get(1);
                            String time = (String) tradeItem.get(2);
                            String side = (String) tradeItem.get(3);
                            long millis = parseTimeToDate(time);
                            if (millis < lastMillis) {
                                log("got not increasing time: millis=" + millis + ", lastMillis=" + lastMillis + "; diff=" + (millis - lastMillis));
                            }
//log("     price=" + priceStr + "; size=" + size + "; time=" + time + "; side=" + side + "; millis=" + millis);
                            if (millis != 0) {
                                double amount = Utils.getDouble(size);
                                double price = Utils.getDouble(priceStr);
                                TradeType type = TradeType.get(side);
                                TradeData tdata = new TradeData(amount, price, millis, 0, type);
//log("      TradeData=" + tdata);
                                if (m_tradesListener != null) {
                                    m_tradesListener.onTrade(tdata);
                                }
                                lastMillis = millis;
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
//log("   topDataListener.onMessage() json=" + json + ";  class=" + json.getClass());
                if (json instanceof JSONObject) {
                    JSONObject jsonObject = (JSONObject) json;
                    Object buyObj = jsonObject.get("buy");
                    Object sellObj = jsonObject.get("sell");
                    String timestampStr = (String) jsonObject.get("timestamp");
//log("     buyStr=" + buyStr + "; sellStr=" + sellStr + "; timestampStr=" + timestampStr);
                    double buy = Utils.getDouble(buyObj);
                    double sell = Utils.getDouble(sellObj);
                    long timestamp = Utils.getLong(timestampStr);
//log("      buy=" + buy + "; sell=" + sell + "; timestamp=" + timestamp);
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
                log("OkCoin: session closed");
            }
        } catch (IOException e) {
            err("error close OkCoin session: " + e, e);
        }
    }

    @Override public String getPropPrefix() {
        return "ok.";
    }

    @Override public void reconnect() {
        log("Huobi: reconnect requested");
        if (m_session.isOpen()) {
            try {
                m_session.close(new CloseReason(CloseReason.CloseCodes.SERVICE_RESTART, "reconnect requested"));
            } catch (IOException e) {
                err("reconnect error in close session: " + e, e);
            }
        } else {
            log(" session is closed");
        }
        new ReconnectThread(m_callback).start();
    }

    private void subscribe(final String channel) throws Exception {
        subscribe(channel, false);
    }

    private void subscribe(final String channel, final boolean toSign) throws Exception {
        log("subscribe() channel=" + channel + "; m_session=" + m_session);
        if( m_session == null ) {
            log(" no session. connecting...");
            connect(new Runnable() {
                @Override public void run() {
                    log("subscribe().connect().connected");
                    sendSubscribe(channel, toSign);
                }
            });
        } else {
            sendSubscribe(channel, toSign);
        }
    }

    private void sendSubscribe(String channel, boolean toSign) {
        String subscribeCmd;
        if(toSign) {
            OkCoin exch = (OkCoin) Exchange.OKCOIN.m_baseExch;
            Map<String, String> preMap = new HashMap<String, String>();
            String params = exch.signForWs(preMap);

            StringBuilder tradeStr = new StringBuilder(
                    "{'event': 'addChannel','channel': '"+channel+"','parameters': ")
                    .append(params).append("}");
            subscribeCmd = tradeStr.toString();
        } else {
            subscribeCmd = "{'event':'addChannel','channel':'" + channel + "'}";
        }

        log("sendSubscribe() toSign=" + toSign + "; channel=" + channel + "; subscribeCmd=" + subscribeCmd + "; m_session=" + m_session);

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
//log("Received message: " + message);
                    Object obj = parseJson(message);
                    OkCoinWs.this.onMessage(obj);
                }
            };
            m_session.addMessageHandler(m_messageHandler);
        }
    }

    private void onMessage(Object json) {
//log("Received json message (class="+json.getClass()+"): " + json);
        // [{ "channel":"ok_btccny_trades","data":[["2228.01","0.2","09:26:02","ask"]]}]
        if (json instanceof JSONArray) {
            JSONArray array = (JSONArray) json;
            int length = array.size();
//                    log(" array length = " + length);
            for(int i = 0; i < length; i++) {
                Object channelObj = array.get(i);
//                        log(" channelObj["+i+"]: " + channelObj + ";  class="+channelObj.getClass());
                if(channelObj instanceof JSONObject) {
                    JSONObject channelItem = (JSONObject)channelObj;
//                            log(" channelItem["+i+"]: " + channelItem);
                    String channel = (String) channelItem.get("channel");
//                            log("  channel: " + channelItem);
                    MessageHandler.Whole<Object> channelListener = m_channelListeners.get(channel);
                    if(channelListener != null) {
                        Object data = channelItem.get("data");
//                                log("  data: " + data);
                        channelListener.onMessage(data);
                    } else {
                        log("no listener for channel '" + channel + "'; keys:" + m_channelListeners.keySet());
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

    @Override public void connect(final Runnable callback) {
        m_callback = callback;
        try {
            ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

//            // JDK 7 client
//            // tyrus 1.8.3
//            //  If you do not mind using Tyrus specific API, the most straightforward way is to use:
//            ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());


            ClientManager client = ClientManager.createClient();

//            // SSL configuration
//            SslContextConfigurator sslContextConfigurator = new SslContextConfigurator();
//            sslContextConfigurator.setTrustStoreFile("...");
//            sslContextConfigurator.setTrustStorePassword("...");
//            sslContextConfigurator.setTrustStoreType("...");
//            sslContextConfigurator.setKeyStoreFile("...");
//            sslContextConfigurator.setKeyStorePassword("...");
//            sslContextConfigurator.setKeyStoreType("...");
//            SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(sslContextConfigurator, true, false, false);
//            client.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);

            // Client handshake request and response logging
            //  tyrus 1.8.3 ?
//            client.getProperties().put(ClientProperties.LOG_HTTP_UPGRADE, true);

            client.setAsyncSendTimeout(30000);
            client.setDefaultMaxSessionIdleTimeout(DEFAULT_MAX_SESSION_IDLE_TIMEOUT);
//client.getProperties().put(ClientProperties.HANDSHAKE_TIMEOUT, 60000); //  must be int and represents handshake timeout in milliseconds. Default value is 30000 (30 seconds).
            log("connect to server...");
            client.connectToServer(new Endpoint() {
                @Override public void onOpen(final Session session, EndpointConfig config) {
                    log("onOpen session=" + session + "; EndpointConfig=" + config);
                    m_session = session;
                    long maxIdleTimeout = m_session.getMaxIdleTimeout();
                    log(" maxIdleTimeout=" + maxIdleTimeout);
                    callback.run();
                    logInfo();
                }

                @Override public void onClose(Session session, CloseReason closeReason) {
                    log("onClose session=" + session + "; closeReason=" + closeReason);
                    closeAll();
                    if (m_stopped) {
                        log("session stopped - no reconnect");
                    } else {
                        new ReconnectThread(callback).start();
                    }
                    logInfo();
                }

                @Override public void onError(Session session, Throwable thr) {
                    log("onError session=" + session + "; Throwable=" + thr);
                    thr.printStackTrace();
                    closeAll();
                    logInfo();
                }
            }, cec, new URI(URL));
            log("session isOpen=" + m_session.isOpen() + "; session=" + m_session);
        } catch (Exception e) {
            err("connectToServer error: " + e, e);
            if (m_stopped) {
                log("session stopped - no reconnect");
            } else {
                new ReconnectThread(callback).start();
            }
        }

        ClientManager.ReconnectHandler reconnectHandler = new ClientManager.ReconnectHandler() {};


//        If you need semi-persistent client connection, you can always implement some reconnect logic by yourself, but Tyrus Client offers useful feature which should be much easier to use. See short sample code:
//
//        ClientManager client = ClientManager.createClient();
//        ClientManager.ReconnectHandler reconnectHandler = new ClientManager.ReconnectHandler() {
//
//            private int counter = 0;
//
//            @Override
//            public boolean onDisconnect(CloseReason closeReason) {
//                counter++;
//                if (counter <= 3) {
//                    System.out.println("### Reconnecting... (reconnect count: " + counter + ")");
//                    return true;
//                } else {
//                    return false;
//                }
//            }
//
//            @Override
//            public boolean onConnectFailure(Exception exception) {
//                counter++;
//                if (counter <= 3) {
//                    System.out.println("### Reconnecting... (reconnect count: " + counter + ") " + exception.getMessage());
//
//                    // Thread.sleep(...) or something other "sleep-like" expression can be put here - you might want
//                    // to do it here to avoid potential DDoS when you don't limit number of reconnects.
//                    return true;
//                } else {
//                    return false;
//                }
//            }
//
//            @Override
//            public long getDelay() {
//                return 1;
//            }
//        };
//
//        client.getProperties().put(ClientProperties.RECONNECT_HANDLER, reconnectHandler);
//
//        client.connectToServer(...)
//        ReconnectHandler contains three methods, onDisconnect, onConnectFailure and getDelay. First will be executed whenever @OnClose annotated method (or Endpoint.onClose(..)) is executed on client side - this should happen when established connection is lost for any reason. You can find the reason in methods parameter. Other one, called onConnectFailure is invoked when client fails to connect to remote endpoint, for example due to temporary network issue or current high server load. Method getDelay is called after any of previous methods returns true and the returned value will be used to determine delay before next connection attempt. Default value is 5 seconds.

    }

    protected void logInfo() {
        try {
            double activeCount = Thread.activeCount();
            log("thread group active thread count = " + activeCount);

            Set<Thread> threads = Thread.getAllStackTraces().keySet();
            int nbThreads = threads.size();
            int nbRunning = 0;
            for (Thread t : threads) {
                if (t.getState() == Thread.State.RUNNABLE) {
                    nbRunning++;
                }
            }
            log("jvm thread num = " + nbThreads + "; executing threads num = " + nbRunning );

            double threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
            log("all thread count = " + threadCount);
        } catch (Exception e) {
            err("logInfo error: " + e, e);
        }
    }

    protected void closeAll() {
        if (m_session != null) {
            if (m_session.isOpen()) {
                if (m_messageHandler != null) {
                    m_session.removeMessageHandler(m_messageHandler);
                }
                try {
                    m_session.close();
                } catch (IOException e) {
                    err("session close error: " + e, e);
                }
            }
            m_session = null;
        }
        m_messageHandler = null;
    }

    private static final Calendar NOW_CALENDAR = Calendar.getInstance(TZ, Locale.ENGLISH);
    private static final Calendar OUT_CALENDAR = Calendar.getInstance(TZ, Locale.ENGLISH);

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
            log("error parsing time=" + time);
        }
        return 0;
    }

    // ===================================================================================================
    private class ReconnectThread extends Thread {
        private final Runnable m_callback;

        public ReconnectThread(Runnable callback) {
            m_callback = callback;

            m_tradesSubscribed = false;
            m_topSubscribed = false;
        }

        @Override public void run() {
            log("reconnect thread started");
            try {
                long reconnectTimeout = 3000;
                int attempt = 1;
                boolean iterate = true;
                while (iterate) {
                    log("reconnect attempt " + attempt + "; waiting " + reconnectTimeout + " ms...");
                    Thread.sleep(reconnectTimeout);
                    try {
                        connect(new Runnable() {
                            @Override public void run() {
                                log("reconnected. resubscribing...");
                                resubscribe();
                                log("resubscribed. m_callback=" + m_callback);
                                m_callback.run();
                            }
                        });
                        iterate = false;
                    } catch (Exception e) {
                        log("reconnect error: " + e);
                        attempt++;
                        reconnectTimeout = reconnectTimeout * 3 / 2;
                    }
                }
                log("reconnect thread finished");
            } catch (Exception e) {
                log("reconnect error: " + e);
                e.printStackTrace();
            }
        }
    }

    private void resubscribe() {
        log("resubscribe()");
        try {
            if (m_tradesListener != null) {
                if (m_tradesSubscribed) {
                    log(" trades already Subscribed");
                } else {
                    subscribe(BTCCNY_TRADES_CHANNEL);
                    m_tradesSubscribed = true;
                }
            } else {
                log("no tradesListener");
            }
            if (m_topListener != null) {
                if (m_topSubscribed) {
                    log(" top already Subscribed");
                } else {
                    subscribe(BTCCNY_TICKER_CHANNEL);
                    m_topSubscribed = true;
                }
            } else {
                log("no topListener");
            }
        } catch (Exception e) {
            log("resubscribe error: " + e);
            e.printStackTrace();
        }
    }
}
