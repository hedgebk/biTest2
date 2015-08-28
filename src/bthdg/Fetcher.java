package bthdg;

import bthdg.duplet.PairExchangeData;
import bthdg.exch.*;
import bthdg.util.Utils;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * todo:
 * - make delay between runs mkt data related - distance to nearest order driven
 * - support DROP ?
 * - count all downloaded traffic
 * - add pause for servlet to redeploy new version and continue as is
 * - use PEG/PEG_MID as close orders
 * - save state to file for restarts - serialize
 * - calculate requests/minute-do not cross the limit 600 request per 10 minutes
 * - check fading moving average
 * - simulate trade at MKT price fast (instantaneously)
 */
public class Fetcher {
    public static boolean SIMULATE_ORDER_EXECUTION = false;
    public static boolean SIMULATE_ACCEPT_ORDER_PRICE = false;
    public static double SIMULATE_ACCEPT_ORDER_PRICE_RATE = 0.7;
    private static final boolean USE_TOP_TEST_STR = false;
    private static final boolean USE_DEEP_TEST_STR = false;
    private static final boolean USE_TRADES_TEST_STR = false;
    public static boolean USE_ACCOUNT_TEST_STR = false;
    public static final long MOVING_AVERAGE = 60 * 60 * 1000; // better simulated = 1h 10 min
    public static final double EXPECTED_GAIN = 3; // better simulated = 4.3
    public static final PriceAlgo PRICE_ALGO = PriceAlgo.MARKET;
    private static final boolean DO_DB_DROP = true;

    private static final int MAX_READ_ATTEMPTS = 500;
    public static final int START_REPEAT_DELAY = 200;
    public static final int REPEAT_DELAY_INCREMENT = 200;

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; bitcoin-API/1.0; MSIE 6.0 compatible)";
    public static boolean LOG_ON_WIRE = false;
    public static boolean LOG_LOADING = true;
    public static boolean LOG_LOADING_TIME = false;
    public static boolean LOG_JOBJ = false;
    public static boolean MUTE_SOCKET_TIMEOUTS = false;
    public static boolean COUNT_TRAFFIC = false;

    public static void main(String[] args) {
        log("Started.  millis=" + System.currentTimeMillis());
        try {
            Properties keys = BaseExch.loadKeys();
            Bitstamp.init(keys);
            Btce.init(keys);

            AccountData account = fetchAccount(Exchange.BITSTAMP);
//            AccountData account = fetchAccount(Exchange.BTCE);
            log("account=" + account);

            // placeOrder();

            cancelLiveOrders();

//            TradesData trades1 = fetchTrades(Exchange.BITSTAMP);
//            TradesData trades2 = fetchTrades(Exchange.BTCE);

//            TopData bitstamp = fetchTop(Exchange.BITSTAMP);
//            TopData btce = fetchTop(Exchange.BTCE);
//            TopData campbx = fetchTop(Exchange.CAMPBX);
//            log("----------------------------------------------");
//            log(Exchange.BITSTAMP, bitstamp);
//            log(Exchange.BTCE, btce);
//            log(Exchange.CAMPBX, campbx);

//            DeepData deep1 = fetchDeep(Exchange.BTCE);
//            DeepData deep2 = fetchDeep(Exchange.BITSTAMP);
//            printDeeps(deep1, deep2);

//            pool(Exchange.BITSTAMP, Exchange.BTCE);

        } catch (Exception e) {
            log("error: " + e);
            e.printStackTrace();
        }
    }

    private static void cancelLiveOrders() throws Exception {
        OrdersData od = fetchOrders(Exchange.BTCE, null);
        log("ordersData=" + od);
        String error = od.m_error;
        if (error == null) {
            for (OrdersData.OrdData ord : od.m_ords.values()) {
                String orderId = ord.m_orderId;
                log(" next order to cancel: " + orderId);
                CancelOrderData coData = cancelOrder(Exchange.BTCE, orderId, null);
                log("  cancel order data: " + coData);
                String error2 = coData.m_error;
                if (error2 == null) {
                    String orderId2 = coData.m_orderId;
                    log("   orderId: " + orderId2);
                } else {
                    log("   error: " + error);
                }
            }
        } else {
            log("error: " + error);
        }
    }

    private static void placeOrder() throws Exception {
        OrderData order = new OrderData(Pair.BTC_EUR, OrderSide.SELL, 800, 0.01);
        Exchange exchange = Exchange.BTCE;
        placeOrder(order, exchange);
    }

    public static PlaceOrderData placeOrder(OrderData order, Exchange exchange) throws Exception {
        PlaceOrderData poData = placeOrder(exchange, order);
        //log("placeOrder returns: " + poData);
        if (poData != null) {
            String error = poData.m_error;
            if (error == null) {
                long orderId = poData.m_orderId;
                //log("orderId: " + orderId);
                order.m_orderId = Long.toString(orderId);
            } else {
                log("error: " + error);
            }
        }
        return poData;
    }

    private static void pool(final Exchange exch1, final Exchange exch2) {
        DbReady.goWithDb(new DbReady.IDbRunnable() {
            public void run(Connection connection) throws SQLException {
                try {
                    if (DO_DB_DROP) {
                        drop(connection);
                    }
                    PairExchangeData.pool(exch1, exch2, connection);
                } catch (Exception e) {
                    log("error: " + e);
                    e.printStackTrace();
                }
            }
        });
    }

    private static void log(String x) {
        Log.log(x);
    }

    private static void printDeeps(DeepData deep1, DeepData deep2) {
        for (int i = 0; i < 5; i++) {
            DeepData.Deep bid1 = deep1.m_bids.get(i);
            DeepData.Deep ask1 = deep1.m_asks.get(i);
            DeepData.Deep bid2 = deep2.m_bids.get(i);
            DeepData.Deep ask2 = deep2.m_asks.get(i);
            log(format(bid1.m_size) + "@" + format(bid1.m_price) + "  " +
                    format(ask1.m_size) + "@" + format(ask1.m_price) + "      " +
                    format(bid2.m_size) + "@" + format(bid2.m_price) + "  " +
                    format(ask2.m_size) + "@" + format(ask2.m_price) + "  ");
        }
    }

    public static OrdersData fetchOrders(Exchange exchange) throws Exception {
        if (exchange.supportsQueryAllOrders()) {
            return fetchOrders(exchange, null);
        }
        if (!exchange.supportsQueryOrdersBySymbol()) {
            // query by pair & aggregate
            Map<String, OrdersData.OrdData> ords = new HashMap<String, OrdersData.OrdData>();
            log("fetching per-pair orders");
            Pair[] pairs = exchange.supportedPairs();
            for (Pair pair : pairs) {
                OrdersData ordersData = fetchOrders(exchange, pair);
                String error = ordersData.m_error;
                if (error == null) {
                    for (OrdersData.OrdData ord : ordersData.m_ords.values()) {
                        ords.put(ord.m_orderId, ord);
                    }
                } else {
                    return new OrdersData("fetch orders for " + exchange + "/" + pair + " error: " + error);
                }
            }
            return new OrdersData(ords);
        }
        throw new RuntimeException("fetch orders not supported/configured");
    }

    public static OrdersData fetchOrders(Exchange exchange, final Pair pair) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.ORDERS, new FetchOptions() {
            @Override
            public Pair getPair() {
                return pair;
            }
        });
        if (LOG_JOBJ) {
            log("jObj=" + jObj);
        }
        OrdersData oData = exchange.parseOrders(jObj, pair);
        return oData;
    }

    private static PlaceOrderData placeOrder(final Exchange exchange, final OrderData order) throws Exception {
        Object jObj;
        try {
            jObj = fetchOnce(exchange, FetchCommand.ORDER, new FetchOptions() {
                @Override
                public OrderData getOrderData() {
                    return order;
                }
            });
        } catch (IOException e) {
            String error = "place order error: " + e;
            log(error);
            return new PlaceOrderData(error);
        }
        if (LOG_JOBJ) {
            log("jObj=" + jObj);
        }
        PlaceOrderData poData = exchange.parseOrder(jObj);
        poData.m_received = exchange.roundAmount(poData.m_received, order.m_pair); // to fix like "received":9.9999999947364E-9,
        poData.m_remains = exchange.roundAmount(poData.m_remains, order.m_pair);
        return poData;
    }

    public static CancelOrderData cancelOrder(Exchange exchange, final String orderId, final Pair pair) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.CANCEL, new FetchOptions() {
            @Override
            public String getOrderId() {
                return orderId;
            }

            @Override
            public Pair getPair() {
                return pair;
            }
        });
        if (LOG_JOBJ) {
            log("jObj=" + jObj);
        }
        CancelOrderData coData = exchange.parseCancelOrder(jObj);
        return coData;
    }


    public static AccountData fetchAccount(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.ACCOUNT, null);
        if (LOG_JOBJ) {
            log("jObj=" + jObj);
        }
        AccountData accountData = exchange.parseAccount(jObj);
//        log("accountData=" + accountData);
        if (accountData != null) {
            if (accountData.m_fee == Double.MAX_VALUE) {
                accountData.m_fee = exchange.m_baseFee;
            }
            return accountData;
        }
        // todo: handle if query unsuccessful
        return null;
    }

    static TradesData fetchTrades(Exchange exchange) throws Exception {
        return fetchTrades(exchange, Pair.BTC_USD);
    }

    static TradesData fetchTrades(Exchange exchange, Pair pair) throws Exception {
        Map<Pair, TradesData> map = fetchTrades(exchange, new Pair[]{pair});
        return map.get(pair);
    }

    public static Map<Pair, TradesData> fetchTrades(Exchange exchange, Pair... pairs) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.TRADES, new PairsFetchOptions(pairs));
        Map<Pair, TradesData> trades = exchange.parseTrades(jObj, pairs);
        return trades;
    }

    public static TradesData fetchTradesOnce(Exchange exchange) {
        return fetchTradesOnce(exchange, Pair.BTC_USD);
    }

    private static TradesData fetchTradesOnce(Exchange exchange, Pair pair) {
        try {
            Object jObj = fetchOnce(exchange, FetchCommand.TRADES, new PairFetchOptions(pair));
            if (LOG_JOBJ) {
                log("jObj=" + jObj);
            }
            TradesData tradesData = exchange.parseTrades(jObj, pair);
            return tradesData;
        } catch (Exception e) {
            log(" loading error: " + e);
            e.printStackTrace();
        }
        return null;
    }

    public static DeepData fetchDeep(Exchange exchange) throws Exception {
        return fetchDeep(exchange, Pair.BTC_USD);
    }

    public static DeepData fetchDeep(Exchange exchange, Pair pair) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.DEEP, new PairFetchOptions(pair));
        if (LOG_JOBJ) {
            log("jObj=" + jObj);
        }
        return exchange.parseDeep(jObj, pair);
    }

    public static DeepsData fetchDeeps(Exchange exchange, Pair... pairs) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.DEEP, new PairsFetchOptions(pairs));
        DeepsData deepsData = exchange.parseDeeps(jObj, pairs);
        return deepsData;
    }

    public static TopData fetchTop(Exchange exchange) throws Exception {
        return fetchTop(exchange, Pair.BTC_USD);
    }

    public static TopData fetchTop(Exchange exchange, Pair pair) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.TOP, new PairFetchOptions(pair));
        if (LOG_JOBJ) {
            log("jObj=" + jObj);
        }
        TopData topData = exchange.parseTop(jObj, pair);
        //log("topData=" + topData);
        return topData;
    }

    public static TopsData fetchTops(Exchange exchange, Pair... pairs) throws Exception {
        if (exchange.supportsMultiplePairsRequest()) {
            PairsFetchOptions options = new PairsFetchOptions(pairs);
            Object jObj = fetch(exchange, FetchCommand.TOP, options);
            TopsData topData = exchange.parseTops(jObj, pairs);
            return topData;
        }
        // aggregate
        TopsData topsData = new TopsData();
        log("fetching per-pair tops");
        for (Pair pair : pairs) {
            TopData topData = fetchTop(exchange, pair);
            topsData.m_map.put(pair, topData);
        }
        return topsData;
    }

    public static TopData fetchTopOnce(Exchange exchange) {
        return fetchTopOnce(exchange, Pair.BTC_USD);
    }

    public static TopData fetchTopOnce(Exchange exchange, Pair pair) {
        try {
            Object jObj = fetchOnce(exchange, FetchCommand.TOP, new PairFetchOptions(pair));
            TopData topData = exchange.parseTop(jObj, pair);
            return topData;
        } catch (Exception e) {
            log(" loading error: " + e);
            e.printStackTrace();
        }
        return null;
    }

    private static Object fetch(Exchange exchange, FetchCommand command, FetchOptions options) throws Exception {
        long start = System.currentTimeMillis();
        long delay = START_REPEAT_DELAY;
        for (int attempt = 1; attempt <= MAX_READ_ATTEMPTS; attempt++) {
            try {
                Object obj = fetchOnce(exchange, command, options);
                if (exchange.retryFetch(obj)) { // this is to handle "error":"invalid sign"
                    log("  retry fetch attempt: " + obj);
                    continue;
                }
                if (MUTE_SOCKET_TIMEOUTS && (attempt > 1)) {
                    long end = System.currentTimeMillis();
                    log(" loaded with " + attempt + " attempt, in " + Utils.millisToDHMSStr(end - start));
                }
                return obj;
            } catch (Exception e) {
                if (!(MUTE_SOCKET_TIMEOUTS && ((e instanceof SocketTimeoutException) || (e instanceof SSLHandshakeException)))) {
                    log(" loading error (attempt " + attempt + ", currentDelay=" + delay + "ms): " + e);
                    e.printStackTrace();
                }
            }
            Thread.sleep(delay);
            delay += REPEAT_DELAY_INCREMENT;
        }
        throw new RuntimeException("unable to load after " + MAX_READ_ATTEMPTS + " attempts");
    }

    private static Object fetchOnce(Exchange exchange, FetchCommand command, FetchOptions options) throws Exception {
        long start = System.currentTimeMillis();
        Reader reader;
        if (command.useTestStr()) {
            String str = command.getTestStr(exchange);
            reader = new StringReader(str);
        } else {
            if (command.singleRequest()) {
                synchronized (exchange) { // one live request for exchange
                    reader = fetchInt(exchange, command, options);
                }
            } else {
                reader = fetchInt(exchange, command, options);
            }
        }
        Object o = parseJson(reader); // will close reader inside
        if (LOG_LOADING_TIME) {
            long end = System.currentTimeMillis();
            log("loaded in " + Utils.millisToDHMSStr(end - start));
        }
        return o;
    }

    private static Reader fetchInt(Exchange exchange, FetchCommand command, FetchOptions options) throws Exception {
        Reader reader;
        Exchange.UrlDef apiEndpoint = command.getApiEndpoint(exchange, options);
        if (apiEndpoint == null) {
            log("no API endpoint specified for exchange " + exchange + "; for command " + command);
        }
        String location = apiEndpoint.m_location;
        if (LOG_LOADING) {
            log("loading from " + location + "...  ");
        }
        URL url = new URL(location);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        boolean isHttps = con instanceof HttpsURLConnection;
        if (command.needSsl() || isHttps) {
            BaseExch.initSsl();
        }

        if (isHttps) {
            HttpsURLConnection scon = (HttpsURLConnection) con;
            scon.setHostnameVerifier(new NullHostNameVerifier());
        }

//            con.setDoOutput(true);

        con.setUseCaches(false);
        con.setConnectTimeout(exchange.connectTimeout());
        con.setReadTimeout(exchange.readTimeout());
        con.setRequestProperty("User-Agent", USER_AGENT);
        //con.setRequestProperty("Accept","application/json, text/javascript, */*; q=0.01");

        boolean doPost = command.doPost();
        if (doPost) {
            con.setRequestMethod("POST");
            con.setDoOutput(true);

            BaseExch baseExch = exchange.m_baseExch;
            BaseExch.IPostData postData = baseExch.getPostData(apiEndpoint, command, options);

            Map<String, String> headerLines = postData.headerLines();
            if (headerLines != null) {
                for (Map.Entry<String, String> headerLine : headerLines.entrySet()) {
                    con.setRequestProperty(headerLine.getKey(), headerLine.getValue());
                }
            }

            String postStr = postData.postStr();
            OutputStream os = con.getOutputStream();
            try {
                os.write(postStr.getBytes());
                os.flush();
            } finally {
                os.close();
            }
        }

        con.connect();

        int responseCode = con.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            log(" ERROR: responseCode: " + responseCode + "; responseMessage=" + con.getResponseMessage());
        }

        InputStream inputStream = con.getInputStream(); //url.openStream();
        // 502 Bad Gateway - The server was acting as a gateway or proxy and received an invalid response from the upstream server
        // 403 Forbidden
        reader = new InputStreamReader(inputStream);
        return reader;
    }

    private static Object parseJson(Reader inReader) throws IOException, ParseException {
        Reader reader0;
        if (LOG_ON_WIRE) {
            CharArrayWriter wireData = new CharArrayWriter();
            char[] arr = new char[128];
            int read;
            while ((read = inReader.read(arr)) > 0) {
                wireData.write(arr, 0, read);
            }
            char[] chars = wireData.toCharArray();
            log("was read " + chars.length + " bytes: " + new String(chars));
            reader0 = new CharArrayReader(chars);
        } else {
            reader0 = inReader;
        }

        Reader reader = COUNT_TRAFFIC ? new CountBytesReader(reader0) : reader0;
        try {
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(reader);
            return obj;
        } finally {
            reader.close();
        }
    }

    public static String format(Double mktPrice) {
        return (mktPrice == null) ? "-" : Utils.format8(mktPrice);
    }

    private static final String DELETE_TICKS_SQL = "DELETE FROM Trace";
    private static final String DELETE_TRADES_SQL = "DELETE FROM TraceTrade";

    private static void drop(Connection connection) {
        try {
            PreparedStatement pStatement = connection.prepareStatement(DELETE_TICKS_SQL);
            try {
                int deleted = pStatement.executeUpdate();
                System.out.println("Traces deleted: " + deleted);
            } finally {
                pStatement.close();
            }
            PreparedStatement pStatement2 = connection.prepareStatement(DELETE_TRADES_SQL);
            try {
                int deleted = pStatement2.executeUpdate();
                System.out.println("TraceTrades deleted: " + deleted);
            } finally {
                pStatement2.close();
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e);
            e.printStackTrace();
        }
    }


    public enum FetchCommand {
        TOP {
            @Override
            public String getTestStr(Exchange exchange) {
                return exchange.m_topTestStr;
            }

            @Override
            public Exchange.UrlDef getApiEndpoint(Exchange exchange, FetchOptions options) {
                return exchange.apiTopEndpoint(options);
            }

            @Override
            public boolean useTestStr() {
                return USE_TOP_TEST_STR;
            }
        },
        DEEP {
            @Override
            public String getTestStr(Exchange exchange) {
                return exchange.deepTestStr();
            }

            @Override
            public Exchange.UrlDef getApiEndpoint(Exchange exchange, FetchOptions options) {
                return exchange.apiDeepEndpoint(options);
            }

            @Override
            public boolean useTestStr() {
                return USE_DEEP_TEST_STR;
            }
        },
        TRADES {
            @Override
            public String getTestStr(Exchange exchange) {
                return exchange.m_tradesTestStr;
            }

            @Override
            public Exchange.UrlDef getApiEndpoint(Exchange exchange, FetchOptions options) {
                return exchange.apiTradesEndpoint(options);
            }

            @Override
            public boolean useTestStr() {
                return USE_TRADES_TEST_STR;
            }
        },
        ACCOUNT {
            @Override
            public String getTestStr(Exchange exchange) {
                return exchange.m_accountTestStr;
            }

            @Override
            public Exchange.UrlDef getApiEndpoint(Exchange exchange, FetchOptions options) {
                return exchange.m_accountEndpoint;
            }

            @Override
            public boolean useTestStr() {
                return USE_ACCOUNT_TEST_STR;
            }

            @Override
            public boolean doPost() {
                return true;
            }

            @Override
            public boolean needSsl() {
                return true;
            }

            @Override
            public boolean singleRequest() {
                return true;
            }
        },
        ORDER {
            @Override
            public Exchange.UrlDef getApiEndpoint(Exchange exchange, FetchOptions options) {
                return exchange.apiOrderEndpoint(options);
            }

            @Override
            public boolean doPost() {
                return true;
            }

            @Override
            public boolean needSsl() {
                return true;
            }

            @Override
            public boolean singleRequest() {
                return true;
            }
        },
        ORDERS {
            @Override
            public Exchange.UrlDef getApiEndpoint(Exchange exchange, FetchOptions options) {
                return exchange.m_ordersEndpoint;
            }

            @Override
            public boolean doPost() {
                return true;
            }

            @Override
            public boolean needSsl() {
                return true;
            }

            @Override
            public boolean singleRequest() {
                return true;
            }
        },
        CANCEL {
            @Override
            public Exchange.UrlDef getApiEndpoint(Exchange exchange, FetchOptions options) {
                return exchange.m_cancelEndpoint;
            }

            @Override
            public boolean doPost() {
                return true;
            }

            @Override
            public boolean needSsl() {
                return true;
            }

            @Override
            public boolean singleRequest() {
                return true;
            }
        };

        public String getTestStr(Exchange exchange) {
            return null;
        }

        public Exchange.UrlDef getApiEndpoint(Exchange exchange, FetchOptions options) {
            return null;
        }

        public boolean useTestStr() {
            return false;
        }

        public boolean doPost() {
            return false;
        }

        public boolean needSsl() {
            return false;
        }

        public boolean singleRequest() {
            return false;
        }
    } // FetchCommand


    public static class FetchOptions {
        public Pair[] getPairs() {
            return null;
        }

        public OrderData getOrderData() {
            return null;
        }

        public Pair getPair() {
            return null;
        }

        public String getOrderId() {
            return null;
        }
    }

    private static class PairFetchOptions extends FetchOptions {
        private final Pair m_pair;

        public PairFetchOptions(Pair pair) {
            m_pair = pair;
        }

        @Override
        public Pair[] getPairs() {
            return new Pair[]{m_pair};
        }
    }

    private static class PairsFetchOptions extends FetchOptions {
        private final Pair[] pairs;

        public PairsFetchOptions(Pair... pairs) {
            this.pairs = pairs;
        }

        @Override
        public Pair[] getPairs() {
            return pairs;
        }
    }

    /* Host name verifier that does not perform nay checks. */
    private static class NullHostNameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    private static class CountBytesReader extends Reader {
        private final Reader m_reader;
        private long m_count;

        public CountBytesReader(Reader reader) {
            m_reader = reader;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            int read = m_reader.read(cbuf, off, len);
            if (read > 0) {
                m_count += read;
            }
            return read;
        }

        @Override
        public int read() throws IOException {
            int read = m_reader.read();
            if (read >= 0) {
                m_count++;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            m_reader.close();
            log("was read " + m_count + " bytes");
        }
    }
}
