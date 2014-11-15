package bthdg.ws;

import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.SubscriptionEventListener;

//https://www.bitstamp.net/websocket/
// https://github.com/timmolter/XChange/blob/develop/xchange-bitstamp/src/main/java/com/xeiam/xchange/bitstamp/service/streaming/BitstampPusherService.java
public class BitstampWs {
    private static final String PUSHER_KEY = "de504dc5763aeef9ff52"; // https://www.bitstamp.net/websocket/

    private Pusher client;

    public BitstampWs() {
        PusherOptions pusherOpts = new PusherOptions();
        pusherOpts.setEncrypted(false); // data stream is public
        pusherOpts.setActivityTimeout(4 * 120000); // Keep-alive interval
        pusherOpts.setPongTimeout(120000); // Response timeout

        client = new Pusher(PUSHER_KEY, pusherOpts);
    }

    public static void main(String[] args) {
        try {
            BitstampWs wsClient = new BitstampWs();
            wsClient.connect();

            Thread.sleep(150000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("done");
    }

    private void connect() {
        client.connect();

//        Channel order = client.subscribe("order_book");
//        SubscriptionEventListener listener = new SubscriptionEventListener() {
//            @Override public void onEvent(String channelName, String eventName, String data) {
//                System.out.println("orderBook: channelName=" + channelName + "; eventName="+eventName+"; data="+data);
//
//// data={"bids": [["403.46", "0.02478560"], ["403.36", "0.02479174"], ["403.26", "0.03719684"], ["403.16", "0.02480404"], ["403.06", "0.03721530"], ["403.04", "0.04210000"], ["403.03", "6.31645570"], ["402.96", "0.02481635"], ["402.93", "0.06000000"], ["402.86", "0.03723377"], ["402.79", "6.52000000"], ["402.76", "0.02482868"], ["402.66", "0.02483484"], ["402.56", "0.03726152"], ["402.53", "0.49585957"], ["402.51", "0.09000000"], ["402.46", "0.02484718"], ["402.36", "0.03728004"], ["402.26", "0.02485954"], ["402.21", "2.36600000"]],
////       "asks": [["403.99", "0.49506175"], ["404.00", "2.23080522"], ["404.15", "2.23400000"], ["404.78", "0.76100000"], ["404.83", "2.06700000"], ["405.50", "2.30900000"], ["405.51", "1.30200000"], ["405.99", "1.67455171"], ["406.10", "16.30000000"], ["406.15", "1.00000000"], ["406.20", "1.81300000"], ["406.30", "1.00000000"], ["406.34", "4.00000000"], ["406.40", "1.00000000"], ["406.71", "0.64619000"], ["406.80", "1.00000000"], ["406.89", "2.48400000"], ["406.93", "21.06150000"], ["406.96", "26.66616183"], ["406.98", "1.00000000"]]}
//            }
//        };
//        order.bind("data", listener);

        Channel order = client.subscribe("diff_order_book");
        SubscriptionEventListener listener = new SubscriptionEventListener() {
            @Override public void onEvent(String channelName, String eventName, String data) {
                System.out.println("diff_order_book: channelName=" + channelName + "; eventName="+eventName+"; data="+data);
// data={"bids": [["403.19", "0"], ["402.27", "0.05000000"], ["402.00", "7.00000000"], ["401.71", "6.30000000"], ["401.45", "0"], ["398.37", "0"], ["396.72", "0"], ["393.03", "94.16823503"], ["392.17", "22.58367523"], ["391.37", "56.39861971"], ["387.60", "0.01547988"], ["387.50", "0"]],
//       "asks": [["404.77", "0"], ["443.20", "0.27279502"]]}

            }
        };
        order.bind("data", listener);

//        Channel trade = client.subscribe("live_trades");
//        SubscriptionEventListener listener2 = new SubscriptionEventListener() {
//            @Override public void onEvent(String channelName, String eventName, String data) {
//                System.out.println("trades: channelName=" + channelName + "; eventName="+eventName+"; data="+data);
//
//// data={"price": 403.45999999999998, "amount": 0.024785600000000001, "id": 6669417}
//
//            }
//        };
//        trade.bind("trade", listener2);
    }

    private  void disconnect() {
        client.disconnect();
    }
}
