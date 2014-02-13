import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class Fetcher {
    private static final boolean USE_TOP_TEST_STR = false;
    private static final boolean USE_DEEP_TEST_STR = false;
    private static final boolean USE_TRADES_TEST_STR = false;
    public static final int MIN_DELAY = 1500;
    public static final long MOVING_AVERAGE = 25 * 60 * 1000;
    public static final int EXPECTED_GAIN = 3; // 5
    public static final double COMMISSION_AMOUNT = 7; // 6.8; // 7 ... 19
    public static final double TARGET_DELTA = COMMISSION_AMOUNT + EXPECTED_GAIN;
    public static final double HALF_TARGET_DELTA = TARGET_DELTA / 2;

    private static final int MAX_READ_ATTEMPTS = 100; // 5;
    public static final int START_REPEAT_DELAY = 200;
    public static final int REPEAT_DELAY_INCREMENT = 200;

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
        while(true) {
            System.out.println("----------------------------------------------");
            iterationCounter++;

            IterationContext iContext = new IterationContext();
            data.checkState(iContext);

            long delay = iContext.m_nextIterationDelay;
            long running = System.currentTimeMillis() - startMillis;
            System.out.println("wait "+delay+" ms. total running " + Utils.millisToDHMSStr(running) + ", counter="+iterationCounter);
            Thread.sleep(delay);
        }
    }

    private static void Oldpool(Exchange exch1, Exchange exch2, PrintStream os) throws Exception {
        ExchangeData exch1data = new ExchangeData(exch1);
        ExchangeData exch2data = new ExchangeData(exch2);

        long start = System.currentTimeMillis();
        AverageCounter diffAverageCounter = new AverageCounter(MOVING_AVERAGE);
        long delay; // start with 5sec
        int iterationCounter = 0;
        while(true) {
            System.out.println("----------------------------------------------");
            iterationCounter++;

            // check, do we have bracket orders executed
            OrderData exch2MktOrder = exch1data.checkExecutedBrackets();
            OrderData exch1MktOrder = exch2data.checkExecutedBrackets();

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

            boolean executed1 = exch1data.executeOpenMktOrder(exch1MktOrder);
            boolean executed2 = exch2data.executeOpenMktOrder(exch2MktOrder);
            if(executed1 && executed2) {
                s_state = STATE.OPENED;
            } else {
                s_state = STATE.ERROR; // close everything opened
            }

            TopData.TopDataEx diff = calcDiff(top1, top2); // top1 - top2
            System.out.print("diff=" + diff);

            double midDiff = diff.m_mid;
            long millis = System.currentTimeMillis();
            double midDiffAverage = diffAverageCounter.add(millis, midDiff);
            System.out.println(";  avg=" + Utils.XX_YYYY.format(midDiffAverage));

            double delta = midDiff - midDiffAverage;

            exch1data.m_sumBidAskDiff += (top1.m_ask - top1.m_bid); // ASK > BID
            exch2data.m_sumBidAskDiff += (top2.m_ask - top2.m_bid);
            exch1data.m_bidAskDiffCounter++;
            exch2data.m_bidAskDiffCounter++;

            System.out.println("delta: " + Utils.XX_YYYY.format(delta) + ",  waiting for: " + HALF_TARGET_DELTA +
                               ", avgBidAskDiff1=" + exch1data.avgBidAskDiffStr() +
                               ", avgBidAskDiff2=" + exch2data.avgBidAskDiffStr() +
                               ", millis=" + millis +
                               ", tile=" + new Date(millis));
            os.println("" + millis + "\t" + Utils.XX_YYYY.format(midDiff) + "\t" + Utils.XX_YYYY.format(midDiffAverage));
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
                    exch1data.placeBrackets(top2, midDiffAverage);
                    exch2data.placeBrackets(top1, -midDiffAverage);
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

    private static double calcAmountToOpen() {
        return 1.0; // 1.0 BTC
    }

    private static void close(ExchangeData exch1data, ExchangeData exch2data, TopData top1, TopData top2) {
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

    private static void openAtMkt(Exchange sellExch, Exchange buyExch, TopData sellExchTop, TopData buyExchTop,
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

    private static TopData.TopDataEx calcDiff(TopData top1, TopData top2) {
        return new TopData.TopDataEx(top1.m_bid - top2.m_bid, top1.m_ask - top2.m_ask, top1.m_last - top2.m_last,
                                   ((top1.m_bid + top1.m_ask) - (top2.m_bid + top2.m_ask))/2 );
    }

    private static <T> void  removeOld(long limit, TreeMap<Long,T> map) {
        SortedMap<Long,T> toRemove = map.headMap(limit);
        if( !toRemove.isEmpty() ) {
//            System.out.println("keys toRemove="+toRemove);
            ArrayList<Long> keys = new ArrayList<Long>(toRemove.keySet());
            for( Long key: keys) {
                map.remove(key);
            }
        }
    }

    private static void printDeeps(DeepData deep1, DeepData deep2) {
        for( int i = 0; i < 5; i++ ) {
            DeepData.Deep bid1 = deep1.m_bids.get(i);
            DeepData.Deep ask1 = deep1.m_asks.get(i);
            DeepData.Deep bid2 = deep2.m_bids.get(i);
            DeepData.Deep ask2 = deep2.m_asks.get(i);
            System.out.println(Utils.XX_YYYY.format(bid1.m_size)+"@"+ Utils.XX_YYYY.format(bid1.m_price)+"  " +
                               Utils.XX_YYYY.format(ask1.m_size)+"@"+ Utils.XX_YYYY.format(ask1.m_price)+"      " +
                               Utils.XX_YYYY.format(bid2.m_size)+"@"+ Utils.XX_YYYY.format(bid2.m_price)+"  " +
                               Utils.XX_YYYY.format(ask2.m_size)+"@"+ Utils.XX_YYYY.format(ask2.m_price)+"  ");
        }
    }

    private static TradesData fetchTrades(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.TRADES);
        System.out.println("jObj=" + jObj);
        TradesData tradesData = exchange.parseTrades(jObj);
        System.out.println("tradesData=" + tradesData);
        return tradesData;
    }

    private static DeepData fetchDeep(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.DEEP);
        System.out.println("jObj=" + jObj);
        DeepData deepData = exchange.parseDeep(jObj);
        System.out.println("deepData=" + deepData);
        return deepData;
    }

    private static TopData fetchTop(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.TOP);
        //System.out.println("jObj=" + jObj);
        TopData topData = exchange.parseTop(jObj);
        //System.out.println("topData=" + topData);
        return topData;
    }

    private static TopData fetchTopOnce(Exchange exchange) throws Exception {
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
//        long millis = System.currentTimeMillis();
        long delay = START_REPEAT_DELAY;
        for( int attempt = 1; attempt <= MAX_READ_ATTEMPTS; attempt++ ) {
            try {
                return fetchOnce(exchange, command);
            } catch (Exception e) {
                System.out.println(" loading error (attempt "+attempt+"): " + e);
                e.printStackTrace();
            }
            Thread.sleep(delay);
            delay += REPEAT_DELAY_INCREMENT;
        }
        throw new RuntimeException("unable to load after "+MAX_READ_ATTEMPTS+" attempts");
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
            con.setConnectTimeout(5000);
            con.setReadTimeout(7000);
            //con.setDoOutput(true);

            //con.setRequestProperty("Content-Type","application/x-www-form-urlencoded") ;
            con.setRequestProperty("User-Agent","Mozilla/5.0") ; //  USER_AGENT     // 'Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)'
                                // "Mozilla/5.0 (compatible; BTCE-API/1.0; MSIE 6.0 compatible; +https://github.com/abwaters/bitstamp-api)"
            //con.setRequestProperty("Accept","application/json, text/javascript, */*; q=0.01");

            InputStream inputStream = con.getInputStream(); //url.openStream();
                // 502 Bad Gateway - The server was acting as a gateway or proxy and received an invalid response from the upstream server
                // 403 Forbidden
            int responseCode = con.getResponseCode();
            if(responseCode != HttpURLConnection.HTTP_OK) {
                System.out.print(" responseCode: " + responseCode);
            }

            int available = inputStream.available();
            System.out.print(" available " + available + " bytes; ");
            reader = new InputStreamReader(inputStream);
        }
        try {
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(reader);
            System.out.print("parsed; ");
            return obj;
        } finally {
            reader.close();
        }
    }

    private static void log(Exchange exch, TopData top) {
        System.out.print(exch.m_name +
                ": bid: " + Utils.XX_YYYY.format(top.m_bid) +
                ", ask: " + Utils.XX_YYYY.format(top.m_ask) +
                ", last: " + Utils.XX_YYYY.format(top.m_last) +
                ", bid_ask_dif: " + Utils.XX_YYYY.format(top.m_ask-top.m_bid) +
                "\t\t");
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

    public static double getDouble(JSONObject object, String key) {
        return getDouble(object.get(key));
    }

    public static double getDouble(Object obj) {
        if(obj instanceof Double) {
            return (Double) obj;
        } else if(obj instanceof Long) {
            return ((Long) obj).doubleValue();
        } else if(obj instanceof String) {
            return Double.parseDouble((String) obj);
        } else {
            throw new RuntimeException("un-supported class: " + obj.getClass());
        }
    }

    public static long getLong(Object obj) {
        if(obj instanceof Long) {
            return (Long) obj;
        } else if(obj instanceof String) {
            return Long.parseLong((String) obj);
        } else {
            throw new RuntimeException("un-supported class: " + obj.getClass());
        }
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
    }

    public static class AverageCounter {
        public final TreeMap<Long,Double> m_map = new TreeMap<Long, Double>(); // sorted by time
        private final long m_limit;

        public AverageCounter(long limit) {
            m_limit = limit;
        }

        public double add(long millis, double addValue) {
            justAdd(millis, addValue);
            return get();
        }

        private void justAdd(long millis, double addValue) {
            long limit = millis - m_limit;
            removeOld(limit, m_map);
            m_map.put(millis, addValue);
        }

        public double get() {
            double summ = 0.0;
            int counter = 0;
            for(Double value: m_map.values()) {
                summ += value;
                counter++;
            }
            return summ/counter;
        }
    }

    public static class ExchangeData {
        public final Exchange m_exch;
        public ExchangeState m_state = ExchangeState.NONE;
        // to calc average diff between bid and ask on exchange
        public double m_sumBidAskDiff = 0;
        public int m_bidAskDiffCounter = 0;
        //bitstamp avgBidAskDiff1=2.6743, btc-e avgBidAskDiff2=1.3724
        //bitstamp avgBidAskDiff1=2.1741, btc-e avgBidAskDiff2=1.2498
        //bitstamp avgBidAskDiff1=1.9107, btc-e avgBidAskDiff2=1.3497

        // moving bracket orders
        public OrderData m_buyOpenBracketOrder;
        public OrderData m_sellOpenBracketOrder;
        public Long m_lastProcessedTradesTime = 0l;
        private TopData m_lastTop;
        public final AverageCounter m_averageCounter = new AverageCounter(MOVING_AVERAGE);
        private static final double MKT_ORDER_THRESHOLD = 1.3; // market order price allowance
        private boolean m_braketExecuted;

        public ExchangeData(Exchange exch) {
            m_exch = exch;
        }

        public int exchId() { return m_exch.m_databaseId; }
        public double avgBidAskDiff() { return m_sumBidAskDiff/m_bidAskDiffCounter; }
        public String avgBidAskDiffStr() { return Utils.XX_YYYY.format(avgBidAskDiff()); }
        private String exchName() { return m_exch.m_name; }
        public boolean waitingForOpenBrackets() { return m_state == ExchangeState.OPEN_BRACKETS_WAITING; }

        public OrderData checkExecutedBrackets() throws Exception {
            OrderData ret = null;
            if ((m_buyOpenBracketOrder != null) || (m_sellOpenBracketOrder != null)) {
                long millis0 = System.currentTimeMillis();
                TradesData trades1 = Fetcher.fetchTrades(m_exch);
                long millis1 = System.currentTimeMillis();
                TradesData newTrades = trades1.newTrades(m_lastProcessedTradesTime);
                System.out.println(" loaded " + trades1.size() + " trades for '" + exchName() + "' in "+(millis1 - millis0)+" ms; new " + newTrades.size() + " trades: " + newTrades);

                List<TradesData.TradeData> newTradesList = newTrades.m_trades;
                if (m_buyOpenBracketOrder != null) {
                    ret = checkExecutedBracket(m_buyOpenBracketOrder, newTradesList);
                }
                if (m_sellOpenBracketOrder != null) {
                    ret = checkExecutedBracket(m_sellOpenBracketOrder, newTradesList);
                }
                // todo: if one of brackets is executed - the another one should be adjusted (cancelled?).

                for (TradesData.TradeData trade : newTradesList) {
                    long timestamp = trade.m_timestamp;
                    if (timestamp > m_lastProcessedTradesTime) {
                        m_lastProcessedTradesTime = timestamp;
                    }
                }
            }
            return ret;
        }

        // return opposite order to execute
        private OrderData checkExecutedBracket(OrderData bracketOrder, List<TradesData.TradeData> tradesList) {
            OrderData ret = null;
            for(TradesData.TradeData trade: tradesList) {
                double mktPrice = trade.m_price; // ASK > BID
                if(bracketOrder.acceptPrice(mktPrice)) {
                    double tradeAmount = trade.m_amount;
                    OrderSide side = bracketOrder.m_side;
                    System.out.println("@@@@@@@@@@@@@@ we have bracket trade "+ side +" " + tradeAmount +
                                       " on '" + exchName() + "' @ " + Utils.XX_YYYY.format(mktPrice) +
                                       ", waiting '" + bracketOrder.priceStr() + "', trade=" + trade);
                    double orderAmount = bracketOrder.m_amount;
                    if(orderAmount > tradeAmount) { // for now partial order execution it is complex to handle - we will execute the rest by MKT price
                        System.out.println("@@@@@@@@@@@@@@  for now partial order execution it is complex to handle: orderAmount="+orderAmount+", tradeAmount="+tradeAmount);
                    }
                    // here we pretend that the whole order was executed for now
                    bracketOrder.addExecution(bracketOrder.m_price, bracketOrder.m_amount); // todo: add partial order execution support later.
                    ret = new OrderData(side.opposite(), Double.MAX_VALUE /*MKT*/, orderAmount);
                }
            }
            return ret;
        }

        public void placeBrackets(TopData otherTop, double midDiffAverage) {
            double buy = otherTop.m_bid - HALF_TARGET_DELTA + midDiffAverage; // ASK > BID
            double sell = otherTop.m_ask + HALF_TARGET_DELTA + midDiffAverage;

            System.out.println("'"+ exchName() +"' buy: "+( (m_buyOpenBracketOrder !=null) ? m_buyOpenBracketOrder.priceStr() + "->" : "" )+ Utils.XX_YYYY.format(buy)+ ";  " +
                                                  "sell: "+( (m_sellOpenBracketOrder !=null) ? m_sellOpenBracketOrder.priceStr() + "->" : "" )+ Utils.XX_YYYY.format(sell));

            double amount = Fetcher.calcAmountToOpen(); // todo: get this based on both exch account info

            m_buyOpenBracketOrder = new OrderData(OrderSide.BUY, buy, amount);
            m_sellOpenBracketOrder = new OrderData(OrderSide.SELL, sell, amount);
        }

        public boolean placeBrackets(IterationContext iContext, TopData otherTop, double midDiffAverage) {
            double buy = otherTop.m_bid - HALF_TARGET_DELTA + midDiffAverage; // ASK > BID
            double sell = otherTop.m_ask + HALF_TARGET_DELTA + midDiffAverage;

            System.out.println("'"+ exchName() +"' buy: "+( (m_buyOpenBracketOrder !=null) ? m_buyOpenBracketOrder.priceStr() + "->" : "" )+ Utils.XX_YYYY.format(buy)+ ";  " +
                                                  "sell: "+( (m_sellOpenBracketOrder !=null) ? m_sellOpenBracketOrder.priceStr() + "->" : "" )+ Utils.XX_YYYY.format(sell));

            double amount = calcAmountToOpen();

            iContext.delay(0);
            m_state = ExchangeState.ERROR; // error by default
            m_buyOpenBracketOrder = new OrderData(OrderSide.BUY, buy, amount);
            boolean success = placeOrder(m_buyOpenBracketOrder);
            if( success ) {
                m_sellOpenBracketOrder = new OrderData(OrderSide.SELL, sell, amount);
                success = placeOrder(m_sellOpenBracketOrder);
                if( success ) {
                    m_state = ExchangeState.OPEN_BRACKETS_WAITING;
                    return true;
                }
            }
            return false;
        }

        public boolean moveBrackets(IterationContext iContext, TopData otherTop, double midDiffAverage) {
            double buy = otherTop.m_bid - HALF_TARGET_DELTA + midDiffAverage; // ASK > BID
            double sell = otherTop.m_ask + HALF_TARGET_DELTA + midDiffAverage;

            System.out.println("'"+ exchName() +"' buy: "+( (m_buyOpenBracketOrder !=null) ? m_buyOpenBracketOrder.priceStr() + "->" : "" )+ Utils.XX_YYYY.format(buy)+ ";  " +
                                                  "sell: "+( (m_sellOpenBracketOrder !=null) ? m_sellOpenBracketOrder.priceStr() + "->" : "" )+ Utils.XX_YYYY.format(sell));

            double amount = calcAmountToOpen(); // todo: this can be changed over the time - take special care rater

            // todo: do not move order if changed a little
            iContext.delay(0);
            m_state = ExchangeState.ERROR; // error by default
            cancelOrder(m_buyOpenBracketOrder);
            m_buyOpenBracketOrder = new OrderData(OrderSide.BUY, buy, amount);
            boolean success = placeOrder(m_buyOpenBracketOrder);
            if( success ) {
                cancelOrder(m_sellOpenBracketOrder);
                m_sellOpenBracketOrder = new OrderData(OrderSide.SELL, sell, amount);
                success = placeOrder(m_sellOpenBracketOrder);
                if( success ) {
                    m_state = ExchangeState.OPEN_BRACKETS_WAITING;
                    return true;
                }
            }
            return false;
        }

        private double calcAmountToOpen() {
            return 1.0 /*BTC*/ ;  // todo: get this based on both exch account info
        }

        public TopData fetchTop() throws Exception {
            m_lastTop = Fetcher.fetchTop(m_exch);
            m_averageCounter.add(System.currentTimeMillis(), m_lastTop.getMid());
            return m_lastTop;
        }

        public TopData fetchTopOnce() throws Exception {
            TopData top = Fetcher.fetchTopOnce(m_exch);
            if( top != null ) { // we got fresh top data
                m_lastTop = top; // update top
                m_averageCounter.add(System.currentTimeMillis(), m_lastTop.getMid());
            } else {
                m_lastTop.setObsolete();
            }
            return m_lastTop;
        }

        public boolean executeOpenMktOrder(OrderData mktOrder) {
            OrderSide side = mktOrder.m_side;
            double mktPrice = side.mktPrice(m_lastTop);
            double avgPrice = m_averageCounter.get();
            // do not execute MKT order at price too different from average
            if( Math.max(mktPrice, avgPrice) / Math.min(mktPrice, avgPrice) < MKT_ORDER_THRESHOLD ) {
                System.out.println("@@@@@@@@@@@@@@ we will open "+side+" MKT on " + exchName() + " @ " + mktPrice);
                mktOrder.m_price = mktPrice; // here we pretend that the whole order can be executed quickly
                // todo: in real life we have to send order, wait execution, only then to process further
                mktOrder.addExecution(mktPrice, mktOrder.m_amount);
                if(side == OrderSide.BUY) {
                    m_buyOpenBracketOrder = mktOrder;
                } else {
                    m_sellOpenBracketOrder = mktOrder;
                }
                // todo: if one of brackets is executed - the another one should be adjusted (cancelled?).
                return true;
            } else {
                System.out.println("@@@@@@@@@@@@@@ WARNING can not "+side+" MKT on " + exchName() + " @ " + mktPrice + ", MKT_ORDER_THRESHOLD exceed");
                return false;
            }
        }

        private boolean cancelOrder(OrderData orderData) {
            // todo: implement
            System.out.println("cancelOrder() not implemented yet");
            return true;
        }

        private boolean placeOrder(OrderData order) {
            // todo: implement
            System.out.println("placeOrder() not implemented yet");
            return true;
        }

        public void queryAccountData() {
            // todo: implement
            System.out.println("queryAccountData() not implemented yet");
        }

        public LiveOrdersData fetchLiveOrders() {
            // todo: implement
            System.out.println("fetchLiveOrders() not implemented yet");
            return new LiveOrdersData();
        }

        public TradesData fetchTrades() throws Exception {
            // todo: make fetch tradesOnce()
            return Fetcher.fetchTrades(m_exch);
        }

        public TradesData filterOnlyNewTrades(TradesData muchTrades) {
            TradesData newTrades = muchTrades.newTrades(m_lastProcessedTradesTime);
            for (TradesData.TradeData trade : newTrades.m_trades) {
                long timestamp = trade.m_timestamp;
                if (timestamp > m_lastProcessedTradesTime) {
                    m_lastProcessedTradesTime = timestamp;
                }
            }
            return newTrades;
        }

        public void checkState(IterationContext iContext) throws Exception {
            checkOrderState(m_buyOpenBracketOrder, iContext); // trace order executions separately
            checkOrderState(m_sellOpenBracketOrder, iContext);
            m_state.checkState(iContext, this);
        }

        private void checkOrderState(OrderData orderData, IterationContext iContext) throws Exception {
            if (orderData != null) {
                orderData.checkState(iContext, this);
            }
        }

        public void onBracketExecuted(OrderData orderData) {
            m_braketExecuted = true;
        }

        public enum ExchangeState {
            NONE,
            OPEN_BRACKETS_WAITING {
                @Override public void checkState(IterationContext iContext, ExchangeData exchData) {
                    System.out.println("ExchangeState.OPEN_BRACKETS_WAITING. check orders status");
                    // todo: check orders status here, can be submited/queued, placed, rejected, and even filled/partially-filled
                    LiveOrdersData liveOrdersState = iContext.getLiveOrdersState(exchData);

                    // actually one can be placed and another not - should b ehandled separately
                    { // pretend that both orders are placed fine
                        exchData.xAllBracketsPlaced(iContext); // start listen for order changes
                    }
                }
            },
            OPEN_BRACKETS_PLACED {
                @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
                    System.out.println("ExchangeState.OPEN_BRACKETS_PLACED. check if some order executed");

                    exchData.checkSomeBracketExecuted();


//                    // actually order execution should be checked via getLiveOrdersState()
//                    LiveOrdersData liveOrdersState = iContext.getLiveOrdersState(exchData);
//                    // but for simulation we are checking via trades
//                    TradesData newTrades = iContext.getNewTradesData(exchData);
//                    exchData.xCheckExecutedBrackets(newTrades);
//
//                    if( exchData.m_buyOpenBracketOrder.m_filled > 0 ) {
//                        // for now we close opposite bracket order
//                        exchData.closeOrder(exchData.m_sellOpenBracketOrder);
//                        exchData.m_state = ONE_OPEN_BRACKET_EXECUTED;
//                    }
//                    if( exchData.m_sellOpenBracketOrder.m_filled > 0 ) {
//                        // for now we close opposite bracket order
//                        exchData.closeOrder(exchData.m_buyOpenBracketOrder);
//                        exchData.m_state = ONE_OPEN_BRACKET_EXECUTED;
//                    }
                }
            },
            ONE_OPEN_BRACKET_EXECUTED,
            ERROR,
            ;

            public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
                System.out.println("checkState not implemented for ExchangeState." + this);
            }
        }

        private void checkSomeBracketExecuted() {
            if(m_braketExecuted) { // if some bracket executed - note: both can be executed in rare cases !!
                m_braketExecuted = false;
                OrderData openOrder = null;
                if( m_buyOpenBracketOrder.m_status == OrderStatus.FILLED ) {
                    if( m_sellOpenBracketOrder.m_status == OrderStatus.FILLED ) {
                        // todo: very rare case - both brackets ar executed - fine - just cache-out
                    } else {
                        // todo: if one of brackets is executed - the another one should be adjusted (for now cancell).
                        closeOrder(m_sellOpenBracketOrder); // close opposite
                        openOrder = m_buyOpenBracketOrder;
                    }
                } else if( m_sellOpenBracketOrder.m_status == OrderStatus.FILLED ) {
                        // todo: if one of brackets is executed - the another one should be adjusted (for now cancell).
                    closeOrder(m_buyOpenBracketOrder); // close opposite
                    openOrder = m_sellOpenBracketOrder;
                } else {
                    System.out.println("Error: braketExecuted, but no FILLED orders: m_buyOpenBracketOrder="+m_buyOpenBracketOrder+", m_sellOpenBracketOrder="+m_sellOpenBracketOrder);
                }
                if (openOrder != null) {
                }
            }
        }

        private void xAllBracketsPlaced(IterationContext iContext) {
            xBracketPlaced(m_buyOpenBracketOrder);
            xBracketPlaced(m_sellOpenBracketOrder);
            m_state = ExchangeState.OPEN_BRACKETS_PLACED;
            iContext.delay(0);
        }

        private void xBracketPlaced(OrderData orderData) {
            orderData.m_status = OrderStatus.SUBMITTED;
            orderData.m_state = OrderState.BRACKET_PLACED;
        }

//        private void xCheckExecutedBrackets(TradesData newTrades) {
//            List<TradesData.TradeData> newTradesList = newTrades.m_trades;
//            if (m_buyOpenBracketOrder != null) {
//                ret = checkExecutedBracket(m_buyOpenBracketOrder, newTradesList);
//            }
//            if (m_sellOpenBracketOrder != null) {
//                ret = checkExecutedBracket(m_sellOpenBracketOrder, newTradesList);
//            }
//            // todo: if one of brackets is executed - the another one should be adjusted (cancelled?).
//        }

        private void closeOrder(OrderData orderData) {
            System.out.println("closeOrder() not implemented");
        }
    }

    public static class OrderData {
        public OrderStatus m_status = OrderStatus.NEW;
        public OrderState m_state = OrderState.NONE;
        public OrderSide m_side;
        public double m_price;
        public double m_amount;
        public double m_filled;
        public List<Execution> m_executions;

        public String priceStr() { return Utils.XX_YYYY.format(m_price); }

        public OrderData(OrderSide side, double price, double amount) {
            m_side = side;
            m_price = price;
            m_amount = amount;
        }

        public boolean acceptPrice(double mktPrice) {
            return m_side.acceptPrice( m_price, mktPrice );
        }

        public void addExecution(double price, double amount) {
            m_executions.add(new Execution(price, amount));
            m_filled += amount;
            if (m_amount == m_filled) { // todo: add check for very small diff like 0.00001
                m_status = OrderStatus.FILLED;
            } else if (m_executions.size() == 1) { // got the first execution
                m_status = OrderStatus.PARTIALLY_FILLED;
            }
        }

        public void checkState(IterationContext iContext, ExchangeData exchangeData) throws Exception {
            m_state.checkState(iContext, exchangeData, this);
        }

        public void xCheckExecutedBracket(ExchangeData exchData, OrderData orderData, TradesData newTrades) {
            OrderSide side = orderData.m_side;
            double orderAmount = orderData.m_amount;
            double price = orderData.m_price;
            List<TradesData.TradeData> newTradesList = newTrades.m_trades;
            for (TradesData.TradeData trade : newTradesList) {
                double mktPrice = trade.m_price; // ASK > BID
                if (orderData.acceptPrice(mktPrice)) {
                    double tradeAmount = trade.m_amount;
                    System.out.println("@@@@@@@@@@@@@@ we have bracket trade " + side + " " + tradeAmount +
                            " on '" + exchData.exchName() + "' @ " + Utils.XX_YYYY.format(mktPrice) +
                            ", waiting '" + orderData.priceStr() + "', trade=" + trade);
                    if (orderAmount > tradeAmount) { // for now partial order execution it is complex to handle - we will execute the rest by MKT price
                        System.out.println("@@@@@@@@@@@@@@  for now partial order execution it is complex to handle: " +
                                "orderAmount=" + orderAmount + ", tradeAmount=" + tradeAmount);
                    }
                    // here we pretend that the whole order was executed for now
                    orderData.addExecution(price, orderAmount); // todo: add partial order execution support later.
                }
            }
        }
    }

    public static class Execution {
        public final double m_price;
        public final double m_amount;

        public Execution(double price, double amount) {
            m_price = price;
            m_amount = amount;
        }
    }

    public static enum OrderSide {
        BUY {
            @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice >= mktPrice; }
            @Override public OrderSide opposite() { return SELL; }
            @Override public double mktPrice(TopData top) { return top.m_ask; }
        },
        SELL {
            @Override public boolean acceptPrice(double orderPrice, double mktPrice) { return orderPrice <= mktPrice; }
            @Override public OrderSide opposite() { return BUY; }
            @Override public double mktPrice(TopData top) { return top.m_bid; }
        };

        public boolean acceptPrice(double orderPrice, double mktPrice) { return false; }
        public OrderSide opposite() { return null; }
        public double mktPrice(TopData top) { return 0; } // ASK > BID
    }

    public static enum OrderStatus {
        NEW,
        SUBMITTED,
        PARTIALLY_FILLED,
        FILLED,
        REJECTED,
        CANCELLED
    }

    public static enum OrderState {
        NONE,
        BRACKET_PLACED {
            @Override public void checkState(IterationContext iContext, ExchangeData exchData, OrderData orderData) throws Exception {
                System.out.println("OrderState.BRACKET_PLACED. check if order executed");
                // actually order execution should be checked via getLiveOrdersState()
                LiveOrdersData liveOrdersState = iContext.getLiveOrdersState(exchData);
                // but for simulation we are checking via trades
                TradesData newTrades = iContext.getNewTradesData(exchData);
//                exchData.xCheckExecutedBrackets(newTrades);

                orderData.xCheckExecutedBracket(exchData, orderData, newTrades);

                if (orderData.m_filled > 0) {
                    double orderAmount = orderData.m_amount;
                    if (orderData.m_filled == orderAmount) { // FILLED
                        exchData.onBracketExecuted(orderData);
                        orderData.m_state = NONE;
                    } else { // PARTIALLY FILLED
                        System.out.println("PARTIALLY FILLED, not supported yet - just wait more");
                    }
                }
            }
        },
        ;

        public void checkState(IterationContext iContext, ExchangeData exchangeData, OrderData orderData) throws Exception {
            System.out.println("checkState not implemented for OrderState." + this);
        }
    }

    private static class ExchangesData {
        public final ExchangeData m_exch1data;
        public final ExchangeData m_exch2data;
        public TopData.TopDataEx m_lastDiff;
        public ExchangesState m_state = ExchangesState.NONE;
        public AverageCounter m_diffAverageCounter = new AverageCounter(MOVING_AVERAGE);

        public ExchangesData(ExchangeData exch1data, ExchangeData exch2data) {
            m_exch1data = exch1data;
            m_exch2data = exch2data;
        }

        public void checkState(IterationContext iContext) throws Exception {
            m_exch1data.checkState(iContext);
            m_exch2data.checkState(iContext);
            m_state.checkState(iContext, this);
        }

        public void onTopsLoaded(TopDatas topDatas) {
            m_lastDiff = topDatas.calculateDiff(); // top1 - top2
            m_diffAverageCounter.justAdd(System.currentTimeMillis(), m_lastDiff.m_mid);
        }

        private enum ExchangesState {
            NONE {
                @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                    System.out.println("ExchangesState.NONE. queryAccountsData");
                    exchangesData.queryAccountsData();
                    exchangesData.m_state = START;
                    iContext.delay(0);
                }
            },
            START {
                @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                    System.out.println("ExchangesState.START. try to place open bracket orders");
                    TopDatas tops = iContext.getTopsData(exchangesData);
                    if (tops.bothFresh()) {
                        double midDiffAverage = exchangesData.m_diffAverageCounter.get();
                        System.out.println("diff=" + exchangesData.m_lastDiff + ";  avg=" + Utils.XX_YYYY.format(midDiffAverage));

                        exchangesData.m_state = ExchangesState.ERROR;
                        boolean success = exchangesData.m_exch1data.placeBrackets(iContext, tops.m_top2, midDiffAverage);
                        if( success ) {
                            success = exchangesData.m_exch2data.placeBrackets(iContext, tops.m_top1, -midDiffAverage);
                            if( success ) {
                                // i see the orders should be placed instantaneously
                                exchangesData.m_state = ExchangesState.OPEN_BRACKETS_PLACED; // WAITING_OPEN_BRACKETS;
                            }
                        }
                        iContext.delay(0); // no wait
                    } else {
                        System.out.println("some exchange top data is not fresh " +
                                           "(fresh1=" + tops.m_top1.m_live + ", fresh2=" + tops.m_top2.m_live + ") - do nothing");
                    }
                }
            },
            WAITING_OPEN_BRACKETS {
                @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                    // orders are placed just instantaneously - code here just in case and double check
                    System.out.println("ExchangesState.OPEN_BRACKETS_WAITING");
                    // todo: check for order status here - can be complex here - some order can be already placed and partially or fully executed
                    // can become SUBMITTED, REJECTED
                    if(!exchangesData.waitingForOpenBrackets()) {
                        exchangesData.m_state = ExchangesState.OPEN_BRACKETS_PLACED; // actually some order may have already another state
                        iContext.delay(0); // no wait
                    }
                }
            },
            OPEN_BRACKETS_PLACED {
                @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                    System.out.println("ExchangesState.OPEN_BRACKETS_PLACED move open bracket orders if needed");
                    TopDatas tops = iContext.getTopsData(exchangesData);
                    if (tops.bothFresh()) {
                        double midDiffAverage = exchangesData.m_diffAverageCounter.get();
                        System.out.println("diff=" + exchangesData.m_lastDiff + ";  avg=" + Utils.XX_YYYY.format(midDiffAverage));

                        exchangesData.m_state = ExchangesState.ERROR;
                        boolean success = exchangesData.m_exch1data.moveBrackets(iContext, tops.m_top2, midDiffAverage);
                        if( success ) {
                            success = exchangesData.m_exch2data.moveBrackets(iContext, tops.m_top1, -midDiffAverage);
                            if( success ) {
                                exchangesData.m_state = ExchangesState.OPEN_BRACKETS_PLACED;
                            }
                        }

                        double waitDistance = TARGET_DELTA;
                        System.out.println("waitDistance="+waitDistance);
                        long delay = (long) (MIN_DELAY + MIN_DELAY * 4 * Math.min(1, Math.abs(waitDistance) / HALF_TARGET_DELTA));
                        delay = Math.max(delay,1000);
                        iContext.delay(delay);
                    } else {
                        System.out.println("some exchange top data is not fresh " +
                                           "(fresh1=" + tops.m_top1.m_live + ", fresh2=" + tops.m_top2.m_live + ") - do nothing");
                    }
                }
            },
            ERROR  {
                // todo: close everything if opened
            },
            ;

            public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                System.out.println("checkState not implemented for " + this);
            }
        }

        private boolean waitingForOpenBrackets() {
            return m_exch1data.waitingForOpenBrackets() && m_exch2data.waitingForOpenBrackets();
        }

        private void queryAccountsData() {
            m_exch1data.queryAccountData();
            m_exch2data.queryAccountData();
        }
    }

    private static class IterationContext {
        public TopDatas m_top;
        public Map<Integer,LiveOrdersData> m_liveOrders;
        public long m_nextIterationDelay = 1000; // 1 sec by def
        private Map<Integer, TradesData> m_newTrades;

        public TopDatas getTopsData(ExchangesData exchangesData) throws Exception {
            if( m_top == null ){
                m_top = requestTopsData(exchangesData);
            }
            return m_top;
        }

        public LiveOrdersData getLiveOrdersState(ExchangeData exchangeData) {
            int exchId = exchangeData.m_exch.m_databaseId;
            LiveOrdersData data;
            if(m_liveOrders == null) {
                m_liveOrders = new HashMap<Integer, LiveOrdersData>();
                data = null;
            } else {
                data = m_liveOrders.get(exchId);
            }
            if(data == null) {
                data = exchangeData.fetchLiveOrders();
                m_liveOrders.put(exchId, data);
            }
            return data;
        }

        public TradesData getNewTradesData(ExchangeData exchData) throws Exception {
            int exchId = exchData.exchId();
            TradesData data;
            if (m_newTrades == null) {
                m_newTrades = new HashMap<Integer, TradesData>();
                data = null;
            } else {
                data = m_newTrades.get(exchId);
            }
            if (data == null) {
                long millis0 = System.currentTimeMillis();
                TradesData muchTrades = exchData.fetchTrades();
                data = exchData.filterOnlyNewTrades(muchTrades);
                long millis1 = System.currentTimeMillis();
                System.out.println(" loaded " + muchTrades.size() + " trades for '" + exchData.exchName() + "' " +
                                   "in " + (millis1 - millis0) + " ms; new " + data.size() + " trades: " + data);
                m_newTrades.put(exchId, data);
            }
            return data;
        }

        private TopDatas requestTopsData(ExchangesData exchangesData) throws Exception {
            ExchangeData exch1data = exchangesData.m_exch1data;
            ExchangeData exch2data = exchangesData.m_exch2data;

            // load top mkt data
            long millis0 = System.currentTimeMillis();
            TopData top1 = exch1data.fetchTopOnce();
            long top1Millis = System.currentTimeMillis();
            TopData top2 = exch2data.fetchTopOnce();
            long top2Millis = System.currentTimeMillis();
            System.out.println("  loaded in " + (top1Millis - millis0) + " and " + (top2Millis - top1Millis) + " ms");
            log(exch1data.m_exch, top1);
            log(exch1data.m_exch, top2);
            System.out.println();

            TopDatas ret = new TopDatas(top1, top2);
            exchangesData.onTopsLoaded(ret);
            return ret;
        }

        public void delay(long millis) {
            m_nextIterationDelay = millis;
        }
    }

    private static class LiveOrdersData {

    }

    private static class TopDatas {
        public final TopData m_top1;
        public final TopData m_top2;

        public TopDatas(TopData top1, TopData top2) {
            m_top1 = top1;
            m_top2 = top2;
        }

        public boolean bothFresh() {
            return m_top1.m_live && m_top2.m_live;
        }

        public TopData.TopDataEx calculateDiff() {  // top1 - top2
            return calcDiff(m_top1, m_top2);
        }
    }
}
