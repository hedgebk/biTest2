import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class Fetcher {
    static final boolean SIMULATE_ACCEPT_ORDER_PRICE = true;
    private static final boolean USE_TOP_TEST_STR = false;
    private static final boolean USE_DEEP_TEST_STR = false;
    private static final boolean USE_TRADES_TEST_STR = false;
    public static final int MIN_DELAY = 1500;
    public static final long MOVING_AVERAGE = 25 * 60 * 1000;
    public static final int EXPECTED_GAIN = 3; // 5
                              // todo: make this price/commission dependent
    public static final double COMMISSION_AMOUNT = 7; // 6.8; // 7 ... 19
    public static final double TARGET_DELTA = COMMISSION_AMOUNT + EXPECTED_GAIN;
    public static final double HALF_TARGET_DELTA = TARGET_DELTA / 2;

    private static final int MAX_READ_ATTEMPTS = 100; // 5;
    public static final int START_REPEAT_DELAY = 200;
    public static final int REPEAT_DELAY_INCREMENT = 200;
    public static final int CONNECT_TIMEOUT = 5000;
    public static final int READ_TIMEOUT = 7000;

    public static STATE s_state = STATE.NONE;
    private static double s_ask; // bought @
    private static double s_bid; // sold @
    private static double s_diff; // open diff
    private static double s_totalGain;
    private static int s_totalRuns;
    private static long s_openMillis;

    public static void main(String[] args) {
        System.out.println("Started.  millis=" + System.currentTimeMillis());
        try {
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
        } finally {
            System.out.println("@@@@@@@@@@@@@@  total: runs=" + s_totalRuns + ", gain=" + s_totalGain);
        }
    }

    private static void pool(Exchange exch1, Exchange exch2) throws Exception {
        PrintStream os = new PrintStream(new FileOutputStream("fetch.leg.txt"));
        try {
            pool(exch1, exch2, os);
        } finally {
            os.close();
        }
    }

    private static void pool(Exchange exch1, Exchange exch2, PrintStream os) throws Exception {
        ExchangeData exch1data = new ExchangeData(exch1);
        ExchangeData exch2data = new ExchangeData(exch2);
        ExchangesData data = new ExchangesData(exch1data, exch2data);
        long startMillis = System.currentTimeMillis();
        int iterationCounter = 0;
        while (true) {
            iterationCounter++;
            System.out.println("---------------------------------------------- iteration: "+iterationCounter);

            IterationContext iContext = new IterationContext();
            try {
                if( data.checkState(iContext)){
                    System.out.println("GOT finish request");
                    break;
                }
            } catch (Exception e) {
                System.out.println("GOT exception during processing. setting ERROR, closing everything...");
                data.setState(ExchangesState.ERROR);
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

    private static void Oldpool(Exchange exch1, Exchange exch2, PrintStream os) throws Exception {
        ExchangeData exch1data = new ExchangeData(exch1);
        ExchangeData exch2data = new ExchangeData(exch2);

        long start = System.currentTimeMillis();
        Utils.AverageCounter diffAverageCounter = new Utils.AverageCounter(MOVING_AVERAGE);
        long delay; // start with 5sec
        int iterationCounter = 0;
        while(true) {
            System.out.println("----------------------------------------------");
            iterationCounter++;

            // check, do we have bracket orders executed
            OrderData exch2MktOrder = exch1data.oldCheckExecutedBrackets();
            OrderData exch1MktOrder = exch2data.oldCheckExecutedBrackets();

            // load top mkt data
            long millis0 = System.currentTimeMillis();
            TopData top1 = exch1data.fetchTop();
            long top1Millis = System.currentTimeMillis();
            TopData top2 = exch2data.fetchTop();
            long top2Millis = System.currentTimeMillis();
            System.out.println("  loaded in " + (top1Millis - millis0) + " and " + (top2Millis - top1Millis) + " ms");
            log(exch1, top1);
            log(exch2, top2);
            System.out.println();

            boolean executed1 = exch1data.oldExecuteOpenMktOrder(exch1MktOrder);
            boolean executed2 = exch2data.oldExecuteOpenMktOrder(exch2MktOrder);
            if (executed1 && executed2) {
                s_state = STATE.OPENED;
            } else {
                s_state = STATE.ERROR; // close everything opened
            }

            TopData.TopDataEx diff = TopData.calcDiff(top1, top2); // top1 - top2
            System.out.print("diff=" + diff);

            double midDiff = diff.m_mid;
            long millis = System.currentTimeMillis();
            double midDiffAverage = diffAverageCounter.add(millis, midDiff);
            System.out.println(";  avg=" + format(midDiffAverage));

            double delta = midDiff - midDiffAverage;

//            exch1data.m_sumBidAskDiff += (top1.m_ask - top1.m_bid); // ASK > BID
//            exch2data.m_sumBidAskDiff += (top2.m_ask - top2.m_bid);
//            exch1data.m_bidAskDiffCounter++;
//            exch2data.m_bidAskDiffCounter++;

            exch1data.m_bidAskDiffCalculator.addValue(top1.m_ask - top1.m_bid); // ASK > BID
            exch2data.m_bidAskDiffCalculator.addValue(top2.m_ask - top2.m_bid);


            System.out.println("delta: " + format(delta) + ",  waiting for: " + HALF_TARGET_DELTA +
                               ", avgBidAskDiff1=" + exch1data.avgBidAskDiffStr() +
                               ", avgBidAskDiff2=" + exch2data.avgBidAskDiffStr() +
                               ", millis=" + millis +
                               ", tile=" + new Date(millis));
            os.println("" + millis + "\t" + format(midDiff) + "\t" + format(midDiffAverage));
            double waitDistance = TARGET_DELTA;
            if( s_state == STATE.NONE ) {
//                double move2to1 = top1.m_bid - top2.m_ask;
//                double move1to2 = top2.m_bid - top1.m_ask;
//                double q1 = move2to1 - midDiffAverage;
//                double q2 = move1to2 + midDiffAverage;
//                if( q1 >= HALF_TARGET_DELTA ) {
//                    System.out.println("@@@@@@@@@@@@@@ we can open TOP @ MKT");
//                    openAtMkt(exch1, exch2, top1, top2, diff, STATE.TOP);
//                } else if( q2 >= HALF_TARGET_DELTA ) {
//                    System.out.println("@@@@@@@@@@@@@@ we can open BOTTOM @ MKT");
//                    openAtMkt(exch2, exch1, top2, top1, diff, STATE.BOTTOM);
//                } else {
                    exch1data.oldPlaceBrackets(top2, midDiffAverage);
                    exch2data.oldPlaceBrackets(top1, -midDiffAverage);
                    waitDistance = HALF_TARGET_DELTA - Math.abs(delta);
                    s_state = STATE.BRACKETS_OPENED;
//                }
            } else if( s_state == STATE.OPENED ) {

            }
//            else if( s_state == STATE.TOP ) {
//                waitDistance = delta - (s_diff - TARGET_DELTA);
//                if( s_diff - midDiff > TARGET_DELTA ) {
//                    System.out.println("@@@@@@@@@@@@@@ we can close TOP @ MKT");
//                    close(exch1data, exch2data, top1, top2);
//                } else if( midDiffAverage - s_diff > TARGET_DELTA ) {
//                    System.out.println("@@@@@@@@@@@@@@ we should drop TOP");
//                    close(exch1data, exch2data, top1, top2);
//                }
//            } else if( s_state == STATE.BOTTOM ) {
//                waitDistance = (s_diff + TARGET_DELTA) - delta;
//                if( midDiff - s_diff > TARGET_DELTA ) {
//                    System.out.println("@@@@@@@@@@@@@@ we can close BOTTOM @ MKT");
//                    close(exch2data, exch1data, top2, top1);
//                } else if (s_diff - midDiffAverage > TARGET_DELTA) {
//                    System.out.println("@@@@@@@@@@@@@@ we should drop BOTTOM");
//                    close(exch2data, exch1data, top2, top1);
//                }
//            }

            System.out.println("waitDistance="+waitDistance);
            delay = (long) (MIN_DELAY + MIN_DELAY * 4 * Math.min(1, Math.abs(waitDistance) / HALF_TARGET_DELTA));
            delay = Math.max(delay,1000);
            long running = System.currentTimeMillis() - start;
            System.out.println("wait "+delay+" ms. total running " + Utils.millisToDHMSStr(running) + ", counter="+iterationCounter);
            Thread.sleep(delay);
        }
    }

    // todo: get this based on both exch account info
    static double calcAmountToOpen() {
        return 1.0; // 1.0 BTC
    }

    private static void oldClose(ExchangeData exch1data, ExchangeData exch2data, TopData top1, TopData top2) {
        double bid = top2.m_bid;
        System.out.println("@@@@@@@@@@@@@@ will sell MKT on " + exch2data.exchName() + " @ " + bid);
        double gain2 = bid - s_ask;
        System.out.println("@@@@@@@@@@@@@@  was bought @ " + s_ask + ", gain=" + gain2);

        double ask = top1.m_ask;
        System.out.println("@@@@@@@@@@@@@@ will buy MKT on " + exch1data.exchName() + " @ " + ask);
        double gain1 = s_bid - ask;
        System.out.println("@@@@@@@@@@@@@@  was sold @ " + s_bid + ", gain=" + gain1);

        double gain = gain1 + gain2;
        System.out.println("@@@@@@@@@@@@@@ this gain=" + gain);
        s_totalGain += gain;
        s_totalRuns++;
        System.out.println("@@@@@@@@@@@@@@  total: runs=" + s_totalRuns + ", gain=" + s_totalGain);

        s_state = STATE.NONE;
        long doneMillis = System.currentTimeMillis() - s_openMillis;
        System.out.println("@@@@@@@@@@@@@@   done in " + Utils.millisToDHMSStr(doneMillis));
    }

    private static void oldOpenAtMkt(Exchange sellExch, Exchange buyExch, TopData sellExchTop, TopData buyExchTop,
                                  TopData.TopDataEx diff, STATE state) {
        // ASK > BID
        s_ask = buyExchTop.m_ask;
        System.out.println("@@@@@@@@@@@@@@ will buy on "+buyExch.m_name+" @ " + s_ask);
        s_bid = sellExchTop.m_bid;
        System.out.println("@@@@@@@@@@@@@@ will sell on "+sellExch.m_name+" @ " + s_bid);
        s_diff = s_bid-s_ask; // diff.m_mid;

        s_state = state;
        s_openMillis = System.currentTimeMillis();
    }

    public enum STATE {
        NONE,
        BRACKETS_OPENED,
        OPENED,

        ERROR
//        TOP,
//        BOTTOM
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

    public enum FetchCommand {
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
