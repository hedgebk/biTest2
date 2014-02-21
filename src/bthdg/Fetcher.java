package bthdg;

import bthdg.exch.BaseExch;
import bthdg.exch.Bitstamp;
import bthdg.exch.Btce;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

/**
 * todo:
 *  - make delay between runs mkt data related - distance to nearest order driven
 *  - support partial fills - do forks
 *  - support DROP
 *  - count all downloaded traffic
 *  - proxy sdt/out logging
 *  - report running time in servlet
 *  - try sleep instead of wait
 *  - add pause for servlet to redeploy new version and continue
 */
public class Fetcher {
    static final boolean SIMULATE_ACCEPT_ORDER_PRICE = false;
    private static final boolean USE_TOP_TEST_STR = false;
    private static final boolean USE_DEEP_TEST_STR = false;
    private static final boolean USE_TRADES_TEST_STR = false;
    public static final long MOVING_AVERAGE = 25 * 60 * 1000;
    public static final int EXPECTED_GAIN = 3; // 5

    private static final int MAX_READ_ATTEMPTS = 100; // 5;
    public static final int START_REPEAT_DELAY = 200;
    public static final int REPEAT_DELAY_INCREMENT = 200;
    public static final int CONNECT_TIMEOUT = 6000; // todo: maybe make it different for exchanges
    public static final int READ_TIMEOUT = 7000;

    public static void main(String[] args) {
        System.out.println("Started.  millis=" + System.currentTimeMillis());
        try {
            Properties keys = BaseExch.loadKeys();
            Bitstamp.init(keys);
            Btce.init(keys);
//            TradesData trades1 = fetchTrades(Exchange.BITSTAMP);
//            TradesData trades2 = fetchTrades(Exchange.BTCE);

//            TopData bitstamp = fetchTop(Exchange.BITSTAMP);
//            TopData btce = fetchTop(Exchange.BTCE);
//            TopData campbx = fetchTop(Exchange.CAMPBX);
//            System.out.println("----------------------------------------------");
//            log(Exchange.BITSTAMP, bitstamp);
//            log(Exchange.BTCE, btce);
//            log(Exchange.CAMPBX, campbx);

//            DeepData deep1 = fetchDeep(Exchange.BTCE);
//            DeepData deep2 = fetchDeep(Exchange.BITSTAMP);
//            printDeeps(deep1, deep2);

            pool(Exchange.BITSTAMP, Exchange.BTCE);
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
    }

    private static void pool(Exchange exch1, Exchange exch2) throws Exception {
        PairExchangeData data = new PairExchangeData(exch1, exch2);
        long startMillis = System.currentTimeMillis();
        int iterationCounter = 0;
        while (true) {
            iterationCounter++;
            System.out.println("---------------------------------------------- iteration: "+iterationCounter);

            IterationContext iContext = new IterationContext();
            try {
                if(checkState(data, iContext)){
                    System.out.println("GOT finish request");
                    break;
                }
            } catch (Exception e) {
                System.out.println("GOT exception during processing. setting ERROR, closing everything...");
                e.printStackTrace();
                data.setState(ForkState.ERROR); // error - stop ALL
                iContext.delay(0);
            }

            long running = System.currentTimeMillis() - startMillis;
            long delay = iContext.m_nextIterationDelay;
            if( delay > 0 ) {
                System.out.println("wait " + delay + " ms. total running " + Utils.millisToDHMSStr(running) + ", counter=" + iterationCounter);
                Thread.sleep(delay);
            } else {
                System.out.println("go to next iteration without sleep. total running " + Utils.millisToDHMSStr(running) + ", counter=" + iterationCounter);
            }
        }
        System.out.println("FINISHED.");
    }

    private static boolean checkState(PairExchangeData data, IterationContext iContext) throws Exception {
        boolean ret = data.checkState(iContext);
        String serialized = data.serialize();
        System.out.println("serialized(len=" + serialized.length() + ")=" + serialized);
        PairExchangeData deserialized = Deserializer.deserialize(serialized);
        deserialized.compare(data); // make sure all fine
        return ret;
    }

    private static void printDeeps(DeepData deep1, DeepData deep2) {
        for( int i = 0; i < 5; i++ ) {
            DeepData.Deep bid1 = deep1.m_bids.get(i);
            DeepData.Deep ask1 = deep1.m_asks.get(i);
            DeepData.Deep bid2 = deep2.m_bids.get(i);
            DeepData.Deep ask2 = deep2.m_asks.get(i);
            System.out.println(format(bid1.m_size) +"@"+ format(bid1.m_price) +"  " +
                               format(ask1.m_size) +"@"+ format(ask1.m_price) +"      " +
                               format(bid2.m_size) +"@"+ format(bid2.m_price) +"  " +
                               format(ask2.m_size) +"@"+ format(ask2.m_price) +"  ");
        }
    }

    static TradesData fetchTrades(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.TRADES);
//        System.out.println("jObj=" + jObj);
        TradesData tradesData = exchange.parseTrades(jObj);
//        System.out.println("tradesData=" + tradesData);
        return tradesData;
    }

    static TradesData fetchTradesOnce(Exchange exchange) {
        try {
            Object jObj = fetchOnce(exchange, FetchCommand.TRADES);
//        System.out.println("jObj=" + jObj);
            TradesData tradesData = exchange.parseTrades(jObj);
//        System.out.println("tradesData=" + tradesData);
            return tradesData;
        } catch (Exception e) {
            System.out.println(" loading error: " + e);
            e.printStackTrace();
        }
        return null;
    }

    private static DeepData fetchDeep(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.DEEP);
        System.out.println("jObj=" + jObj);
        DeepData deepData = exchange.parseDeep(jObj);
        System.out.println("deepData=" + deepData);
        return deepData;
    }

    static TopData fetchTop(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.TOP);
        //System.out.println("jObj=" + jObj);
        TopData topData = exchange.parseTop(jObj);
        //System.out.println("topData=" + topData);
        return topData;
    }

    static TopData fetchTopOnce(Exchange exchange) {
        try {
            Object jObj = fetchOnce(exchange, FetchCommand.TOP);
            //System.out.println("jObj=" + jObj);
            TopData topData = exchange.parseTop(jObj);
            //System.out.println("topData=" + topData);
            return topData;
        } catch (Exception e) {
            System.out.println(" loading error: " + e);
            e.printStackTrace();
        }
        return null;
    }

    private static Object fetch(Exchange exchange, FetchCommand command) throws Exception {
        long delay = START_REPEAT_DELAY;
        for (int attempt = 1; attempt <= MAX_READ_ATTEMPTS; attempt++) {
            try {
                return fetchOnce(exchange, command);
            } catch (Exception e) {
                System.out.println(" loading error (attempt " + attempt + "): " + e);
                e.printStackTrace();
            }
            Thread.sleep(delay);
            delay += REPEAT_DELAY_INCREMENT;
        }
        throw new RuntimeException("unable to load after " + MAX_READ_ATTEMPTS + " attempts");
    }

    private static Object fetchOnce(Exchange exchange, FetchCommand command) throws Exception {
        Reader reader;
        if (command.useTestStr()) {
            String str = command.getTestStr(exchange);
            reader = new StringReader(str);
        } else {
            String location = command.getApiEndpoint(exchange);
            System.out.print("loading from " + location + "...  ");
            URL url = new URL(location);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            //con.setRequestMethod("POST");
            con.setUseCaches(false) ;
            con.setConnectTimeout(CONNECT_TIMEOUT);
            con.setReadTimeout(READ_TIMEOUT);
            //con.setDoOutput(true);

            //con.setRequestProperty("Content-Type","application/x-www-form-urlencoded") ;
            con.setRequestProperty("User-Agent","Mozilla/5.0") ; //  USER_AGENT     // 'Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)'
                                // "Mozilla/5.0 (compatible; BTCE-API/1.0; MSIE 6.0 compatible; +https://github.com/abwaters/bitstamp-api)"
            //con.setRequestProperty("Accept","application/json, text/javascript, */*; q=0.01");

            InputStream inputStream = con.getInputStream(); //url.openStream();
                // 502 Bad Gateway - The server was acting as a gateway or proxy and received an invalid response from the upstream server
                // 403 Forbidden
            int responseCode = con.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                System.out.print(" responseCode: " + responseCode);
            }

            int available = inputStream.available();
            System.out.print(" available " + available + " bytes; ");
            reader = new InputStreamReader(inputStream);
        }
        return parseJson(reader);
    }

    private static Object parseJson(Reader reader) throws IOException, ParseException {
        try {
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(reader);
            System.out.print("parsed; ");
            return obj;
        } finally {
            reader.close();
        }
    }

    static void log(Exchange exch, TopData top) {
        if (top == null) {
            System.out.print("NO top data for '" + exch.m_name + "'\t\t");
        } else {
            System.out.print(exch.m_name +
                    ": bid: " + format(top.m_bid) +
                    ", ask: " + format(top.m_ask) +
                    ", last: " + format(top.m_last) +
                    ", bid_ask_dif: " + format(top.m_ask - top.m_bid) +
                    "\t\t");
        }
    }


//    The following snippet uses the Google HTTP client library and json-simple to issue a Freebase query and parse the result.
//
//    HttpTransport httpTransport = new NetHttpTransport();
//    HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
//    JSONParser parser = new JSONParser();
//    String query = "[{\"limit\": 5,\"name\":null,\"type\":\"/medicine/disease\"}]";
//    GenericUrl url = new GenericUrl("https://www.googleapis.com/freebase/v1/mqlread");
//    url.put("key", "YOUR-API-KEY-GOES-HERE");
//    url.put("query", query);
//    HttpRequest request = requestFactory.buildGetRequest(url);
//    HttpResponse httpResponse = request.execute();
//    JSONObject response = (JSONObject)parser.parse(httpResponse.parseAsString());
//    JSONArray results = (JSONArray)response.get("result");
//    for (Object result : results) {
//      System.out.println(result.get("name").toString());
//    }

    static String format(Double mktPrice) {
        return (mktPrice == null) ? "-" : Utils.XX_YYYY.format(mktPrice);
    }

    private enum FetchCommand {
        TOP {
            @Override public String getTestStr(Exchange exchange) { return exchange.m_topTestStr; }
            @Override public String getApiEndpoint(Exchange exchange) { return exchange.m_apiTopEndpoint; }
            @Override public boolean useTestStr() { return USE_TOP_TEST_STR; }
        },
        DEEP {
            @Override public String getTestStr(Exchange exchange) { return exchange.deepTestStr(); }
            @Override public String getApiEndpoint(Exchange exchange) { return exchange.m_apiDeepEndpoint; }
            @Override public boolean useTestStr() { return USE_DEEP_TEST_STR; }
        },
        TRADES {
            @Override public String getTestStr(Exchange exchange) { return exchange.m_tradesTestStr; }
            @Override public String getApiEndpoint(Exchange exchange) { return exchange.m_apiTradesEndpoint; }
            @Override public boolean useTestStr() { return USE_TRADES_TEST_STR; }
        };

        public String getTestStr(Exchange exchange) { return null; }
        public String getApiEndpoint(Exchange exchange) { return null; }
        public boolean useTestStr() { return false; }
    } // FetchCommand

}
