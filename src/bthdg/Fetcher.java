package bthdg;

import bthdg.exch.BaseExch;
import bthdg.exch.Bitstamp;
import bthdg.exch.Btce;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * todo:
 *  - make delay between runs mkt data related - distance to nearest order driven
 *  - support DROP ?
 *  - count all downloaded traffic
 *  - add pause for servlet to redeploy new version and continue as is
 *  - use PEG/PEG_MID as close orders
 *  - save state to file for restarts - serialize
 *  - calculate requests/minute-do not cross the limit 600 request per 10 minutes
 *  - check fading moving average
 *  - simulate trade at MKT price fast (instanteously)
 */
public class Fetcher {
    static final boolean SIMULATE_ACCEPT_ORDER_PRICE = false;
    static final double SIMULATE_ACCEPT_ORDER_PRICE_RATE = 0.7;
    private static final boolean USE_TOP_TEST_STR = false;
    private static final boolean USE_DEEP_TEST_STR = false;
    private static final boolean USE_TRADES_TEST_STR = false;
    private static final boolean USE_ACCOUNT_TEST_STR = true;
    public static final long MOVING_AVERAGE = 60 * 60 * 1000; // better simulated = 1h 10 min
    public static final double EXPECTED_GAIN = 3; // better simulated = 4.3
    public static final PriceAlgo PRICE_ALGO = PriceAlgo.MARKET;
    private static final boolean DO_DB_DROP = true;

    private static final int MAX_READ_ATTEMPTS = 100; // 5;
    public static final int START_REPEAT_DELAY = 200;
    public static final int REPEAT_DELAY_INCREMENT = 200;
    public static final int CONNECT_TIMEOUT = 6000; // todo: maybe make it different for exchanges
    public static final int READ_TIMEOUT = 7000;

    public static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; bitcoin-API/1.0; MSIE 6.0 compatible)";
    public static boolean LOG_LOADING = true;
    public static boolean MUTE_SOCKET_TIMEOUTS = false;


    public static void main(String[] args) {
        log("Started.  millis=" + System.currentTimeMillis());
        try {
            Properties keys = BaseExch.loadKeys();
            Bitstamp.init(keys);
            Btce.init(keys);

//            AccountData account = fetchAccount(Exchange.BITSTAMP);
//            AccountData account = fetchAccount(Exchange.BTCE);

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

//            pool(Exchange.BITSTAMP, Exchange.BTCE, null);

            DbReady.goWithDb(new DbReady.IDbRunnable() {
                public void run(Connection connection) throws SQLException {
                    try {
                        if( DO_DB_DROP ) {
                            drop(connection);
                        }
                        pool(Exchange.BITSTAMP, Exchange.BTCE, connection);
                    } catch (Exception e) {
                        log("error: " + e);
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            log("error: " + e);
            e.printStackTrace();
        }
    }

    private static void log(String x) {
        Log.log(x);
    }

    private static void pool(Exchange exch1, Exchange exch2, final Connection connection) throws Exception {
        PairExchangeData data = new PairExchangeData(exch1, exch2);
        long startMillis = System.currentTimeMillis();
        int iterationCounter = 0;
        while (true) {
            iterationCounter++;
            log("---------------------------------------------- iteration: " + iterationCounter);

            IterationContext iContext = new IterationContext(new DbRecorder(connection));
            try {
                if (checkState(data, iContext)) {
                    log("GOT finish request");
                    break;
                }
            } catch (Exception e) {
                log("GOT exception during processing. setting ERROR, closing everything...");
                e.printStackTrace();
                data.setState(ForkState.ERROR); // error - stop ALL
                iContext.delay(0);
            }

            long running = System.currentTimeMillis() - startMillis;
            long delay = iContext.m_nextIterationDelay;
            if (delay > 0) {
                log("wait " + delay + " ms. total running " + Utils.millisToDHMSStr(running) + ", counter=" + iterationCounter);
                Thread.sleep(delay);
            } else {
                log("go to next iteration without sleep. total running " + Utils.millisToDHMSStr(running) + ", counter=" + iterationCounter);
            }
            logIntoDb(connection, data);
        }
        log("FINISHED.");
    }

    private static boolean checkState(PairExchangeData data, IterationContext iContext) throws Exception {
        boolean ret = data.checkState(iContext);
//        String serialized = data.serialize();
//        log("serialized(len=" + serialized.length() + ")=" + serialized);
//        PairExchangeData deserialized = Deserializer.deserialize(serialized);
//        deserialized.compare(data); // make sure all fine
        return ret;
    }

    private static void printDeeps(DeepData deep1, DeepData deep2) {
        for( int i = 0; i < 5; i++ ) {
            DeepData.Deep bid1 = deep1.m_bids.get(i);
            DeepData.Deep ask1 = deep1.m_asks.get(i);
            DeepData.Deep bid2 = deep2.m_bids.get(i);
            DeepData.Deep ask2 = deep2.m_asks.get(i);
            log(format(bid1.m_size) +"@"+ format(bid1.m_price) +"  " +
                               format(ask1.m_size) +"@"+ format(ask1.m_price) +"      " +
                               format(bid2.m_size) +"@"+ format(bid2.m_price) +"  " +
                               format(ask2.m_size) +"@"+ format(ask2.m_price) +"  ");
        }
    }

    public static AccountData fetchAccount(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.ACCOUNT, null);
//        log("jObj=" + jObj);
        AccountData accountData = exchange.parseAccount(jObj);
        log("accountData=" + accountData);
        return accountData;
        // todo: handle if query unsuccessful
    }

    static TradesData fetchTrades(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.TRADES, Pair.BTC_USD);
//        log("jObj=" + jObj);
        TradesData tradesData = exchange.parseTrades(jObj);
//        log("tradesData=" + tradesData);
        return tradesData;
    }

    static TradesData fetchTradesOnce(Exchange exchange) {
        try {
            Object jObj = fetchOnce(exchange, FetchCommand.TRADES, Pair.BTC_USD);
//        log("jObj=" + jObj);
            TradesData tradesData = exchange.parseTrades(jObj);
//        log("tradesData=" + tradesData);
            return tradesData;
        } catch (Exception e) {
            log(" loading error: " + e);
            e.printStackTrace();
        }
        return null;
    }

    private static DeepData fetchDeep(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.DEEP, Pair.BTC_USD);
        log("jObj=" + jObj);
        DeepData deepData = exchange.parseDeep(jObj);
        log("deepData=" + deepData);
        return deepData;
    }

    static TopData fetchTop(Exchange exchange) throws Exception {
        return fetchTopOnce(exchange, Pair.BTC_USD);
    }

    static TopData fetchTop(Exchange exchange, Pair pair) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.TOP, pair);
        //log("jObj=" + jObj);
        TopData topData = exchange.parseTop(jObj, pair);
        //log("topData=" + topData);
        return topData;
    }

    static Map<Pair,TopData> fetchTops(Exchange exchange, Pair ... pairs) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.TOP, pairs);
        Map<Pair,TopData> topData = exchange.parseTops(jObj, pairs);
        return topData;
    }

    static TopData fetchTopOnce(Exchange exchange) {
        return fetchTopOnce(exchange, Pair.BTC_USD);
    }

    static TopData fetchTopOnce(Exchange exchange, Pair pair) {
        try {
            Object jObj = fetchOnce(exchange, FetchCommand.TOP, pair);
//            log("jObj=" + jObj);
            TopData topData = exchange.parseTop(jObj, pair);
//            log("topData=" + topData);
            return topData;
        } catch (Exception e) {
            log(" loading error: " + e);
            e.printStackTrace();
        }
        return null;
    }

    private static Object fetch(Exchange exchange, FetchCommand command, Pair ... pairs) throws Exception {
        long delay = START_REPEAT_DELAY;
        for (int attempt = 1; attempt <= MAX_READ_ATTEMPTS; attempt++) {
            try {
                return fetchOnce(exchange, command, pairs);
            } catch (Exception e) {
                if (!MUTE_SOCKET_TIMEOUTS || !(e instanceof SocketTimeoutException)) {
                    log(" loading error (attempt " + attempt + "): " + e);
                    e.printStackTrace();
                }
            }
            Thread.sleep(delay);
            delay += REPEAT_DELAY_INCREMENT;
        }
        throw new RuntimeException("unable to load after " + MAX_READ_ATTEMPTS + " attempts");
    }

    private static Object fetchOnce(Exchange exchange, FetchCommand command, Pair ... pairs) throws Exception {
        Reader reader;
        if (command.useTestStr()) {
            String str = command.getTestStr(exchange);
            reader = new StringReader(str);
        } else {
            Exchange.UrlDef apiEndpoint = command.getApiEndpoint(exchange, pairs);
            String location = apiEndpoint.m_location;
            if (LOG_LOADING) {
                log("loading from " + location + "...  ");
            }
            URL url = new URL(location);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setDoOutput(true);

            boolean doPost = command.doPost();
            if (doPost) {
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", APPLICATION_X_WWW_FORM_URLENCODED);
            }
            con.setUseCaches(false);
            con.setConnectTimeout(CONNECT_TIMEOUT);
            con.setReadTimeout(READ_TIMEOUT);
            con.setRequestProperty("User-Agent", USER_AGENT);
            //con.setRequestProperty("Accept","application/json, text/javascript, */*; q=0.01");

            if (command.needSsl() || (con instanceof HttpsURLConnection)) {
                BaseExch.initSsl();
            }

            BaseExch baseExch = exchange.m_baseExch;
            String postData = null;
            if (doPost) {
                String nonce = baseExch.getNextNonce();
                Map<String, String> postParams = baseExch.getPostParams(nonce, apiEndpoint);
                postData = BaseExch.buildQueryString(postParams);

                Map<String, String> headerLines = baseExch.getHeaders(postData);
                if (headerLines != null) {
                    for (Map.Entry<String, String> headerLine : headerLines.entrySet()) {
                        con.setRequestProperty(headerLine.getKey(), headerLine.getValue());
                    }
                }
                OutputStream os = con.getOutputStream();
                try {
                    os.write(postData.getBytes());
                } finally {
                    os.close();
                }
            }

            con.connect();

            int responseCode = con.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                log(" responseCode: " + responseCode);
            }

            InputStream inputStream = con.getInputStream(); //url.openStream();
                // 502 Bad Gateway - The server was acting as a gateway or proxy and received an invalid response from the upstream server
                // 403 Forbidden
//            int available = inputStream.available();
//            System.out.print(" available " + available + " bytes; ");
            reader = new InputStreamReader(inputStream);
        }
        return parseJson(reader);
    }

    private static Object parseJson(Reader reader) throws IOException, ParseException {
        try {
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(reader);
            return obj;
        } finally {
            reader.close();
        }
    }

    static void logTop(Exchange exch, TopData top) {
        if (top == null) {
            log("NO top data for '" + exch.m_name + "'\t\t");
        } else {
//            log(exch.m_name +
//                    ": bid: " + format(top.m_bid) +
//                    ", ask: " + format(top.m_ask) +
//                    ", last: " + format(top.m_last) +
//                    ", bid_ask_dif: " + format(top.m_ask - top.m_bid) +
//                    "\t\t");
        }
    }

    static String format(Double mktPrice) {
        return (mktPrice == null) ? "-" : Utils.XX_YYYY.format(mktPrice);
    }

    private static final String INSERT_TRACE_SQL =
            "INSERT INTO Trace ( stamp, bid1, ask1, bid2, ask2, fork, buy1, sell1, buy2, sell2 ) VALUES (?,?,?,?,?,?,?,?,?,?)";

    private static void logIntoDb(Connection connection, PairExchangeData data) {
        if (connection != null) {
            try {
                TopData top1 = data.m_sharedExch1.m_lastTop;
                TopData top2 = data.m_sharedExch2.m_lastTop;

                PreparedStatement statement = connection.prepareStatement(INSERT_TRACE_SQL);
                try {
                    List<ForkData> forks = data.m_forks;
                    for (int i = 0, forksSize = forks.size(); i < forksSize; i++) {
                        Thread.sleep(1);

                        ForkData fork = forks.get(i);

                        statement.setLong(1, System.currentTimeMillis());
                        if (i == 0) {
                            statement.setDouble(2, top1.m_bid);
                            statement.setDouble(3, top1.m_ask);
                            statement.setDouble(4, top2.m_bid);
                            statement.setDouble(5, top2.m_ask);
                        } else {
                            statement.setNull(2, Types.DOUBLE);
                            statement.setNull(3, Types.DOUBLE);
                            statement.setNull(4, Types.DOUBLE);
                            statement.setNull(5, Types.DOUBLE);
                        }
                        statement.setLong(6, fork.m_id);
                        setCross(fork.m_openCross, statement, 7);
                        setCross(fork.m_closeCross, statement, 9);

                        statement.executeUpdate(); // execute insert SQL statement - will be auto committed
                    }
                } finally {
                    statement.close();
                }
            } catch (Exception e) {
                System.out.println("Error: " + e);
                e.printStackTrace();
            }
        }
    }

    private static void setCross(CrossData cross, PreparedStatement statement, int index) throws SQLException {
        if ((cross != null) && cross.isActive()) {
            OrderData buyOrder = cross.m_buyOrder;
            statement.setDouble(index, buyOrder.m_price);
            OrderData sellOrder = cross.m_sellOrder;
            statement.setDouble(index + 1, sellOrder.m_price);
        } else {
            statement.setNull(index, Types.DOUBLE);
            statement.setNull(index + 1, Types.DOUBLE);
        }
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


    enum FetchCommand {
        TOP {
            @Override public String getTestStr(Exchange exchange) { return exchange.m_topTestStr; }
            @Override public Exchange.UrlDef getApiEndpoint(Exchange exchange, Pair ... pairs) { return exchange.apiTopEndpoint(pairs); }
            @Override public boolean useTestStr() { return USE_TOP_TEST_STR; }
        },
        DEEP {
            @Override public String getTestStr(Exchange exchange) { return exchange.deepTestStr(); }
            @Override public Exchange.UrlDef getApiEndpoint(Exchange exchange, Pair ... pairs) { return exchange.m_apiDeepEndpoint; }
            @Override public boolean useTestStr() { return USE_DEEP_TEST_STR; }
        },
        TRADES {
            @Override public String getTestStr(Exchange exchange) { return exchange.m_tradesTestStr; }
            @Override public Exchange.UrlDef getApiEndpoint(Exchange exchange, Pair ... pairs) { return exchange.m_apiTradesEndpoint; }
            @Override public boolean useTestStr() { return USE_TRADES_TEST_STR; }
        },
        ACCOUNT {
            @Override public String getTestStr(Exchange exchange) { return exchange.m_accountTestStr; }
            @Override public Exchange.UrlDef getApiEndpoint(Exchange exchange, Pair ... pairs) { return exchange.m_accountEndpoint; }
            @Override public boolean useTestStr() { return USE_ACCOUNT_TEST_STR; }
            @Override public boolean doPost() { return true; }
            @Override public boolean needSsl() { return true; }
        },
        ;
        public String getTestStr(Exchange exchange) { return null; }
        public Exchange.UrlDef getApiEndpoint(Exchange exchange, Pair ... pairs) { return null; }
        public boolean useTestStr() { return false; }
        public boolean doPost() { return false; }
        public boolean needSsl() { return false; }
    } // FetchCommand


    private static class DbRecorder implements IterationContext.IRecorder {
        private final Connection m_connection;

        public DbRecorder(Connection connection) {
            m_connection = connection;
        }

        @Override public void recordOrderFilled(SharedExchangeData shExchData, OrderData orderData, CrossData crossData) {
            recordTrade(m_connection, shExchData, orderData, crossData);
        }

        @Override public void recordTrades(SharedExchangeData shExchData, TradesData data) {
            recordTos(m_connection, shExchData, data);
        }
    }

    private static final String INSERT_TRACE_TRADE_SQL =
            "INSERT INTO TraceTrade ( stamp, exch, side, price, amount, crossId, forkId ) VALUES (?,?,?,?,?,?,?)";

    private static void recordTrade(Connection connection, SharedExchangeData shExchData, OrderData orderData, CrossData crossData) {
        if (connection != null) {
            try {
                PreparedStatement statement = connection.prepareStatement(INSERT_TRACE_TRADE_SQL);
                try {
                    Thread.sleep(1);

                    statement.setLong(1, System.currentTimeMillis());
                    statement.setInt(2, shExchData.m_exchange.m_databaseId);
                    statement.setString(3, orderData.m_side.m_char);
                    statement.setDouble(4, orderData.m_price);
                    statement.setDouble(5, orderData.m_amount);
                    statement.setLong(6, crossData.m_start);
                    statement.setLong(7, crossData.m_forkData.m_id);

                    statement.executeUpdate(); // execute insert SQL statement - will be auto committed
                } finally {
                    statement.close();
                }
            } catch (Exception e) {
                System.out.println("Error: " + e);
                e.printStackTrace();
            }
        }
    }

    private static void recordTos(Connection connection, SharedExchangeData shExchData, TradesData data) {
        if (connection != null) {
            try {
                PreparedStatement statement = connection.prepareStatement(INSERT_TRACE_TRADE_SQL);
                try {
                    for (TradeData trade : data.m_trades) {
                        Thread.sleep(1);

                        statement.setLong(1, trade.m_timestamp);
                        statement.setInt(2, shExchData.m_exchange.m_databaseId);
                        statement.setNull(3, Types.VARCHAR);
                        statement.setDouble(4, trade.m_price);
                        statement.setDouble(5, trade.m_amount);
                        statement.setNull(6, Types.BIGINT);
                        statement.setNull(7, Types.BIGINT);

                        statement.executeUpdate(); // execute insert SQL statement - will be auto committed
                    }
                } finally {
                    statement.close();
                }
            } catch (Exception e) {
                System.out.println("Error: " + e);
                e.printStackTrace();
            }
        }
    }

}
