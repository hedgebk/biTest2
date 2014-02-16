import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class Fetcher {
    private static final boolean SIMULATE_ACCEPT_ORDER_PRICE = true;
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

    private static double calcAmountToOpen() {
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

    private static TradesData fetchTrades(Exchange exchange) throws Exception {
        Object jObj = fetch(exchange, FetchCommand.TRADES);
//        System.out.println("jObj=" + jObj);
        TradesData tradesData = exchange.parseTrades(jObj);
//        System.out.println("tradesData=" + tradesData);
        return tradesData;
    }

    private static TradesData fetchTradesOnce(Exchange exchange) throws Exception {
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

    private static void log(Exchange exch, TopData top) {
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

    private static String format(Double mktPrice) {
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

    public static class ExchangeData {
        private static final double MKT_ORDER_THRESHOLD = 1.3; // market order price allowance +-30%
        public static final double MOVE_BRACKET_ORDER_MIN_AMOUNT = 0.2;

        public final Exchange m_exch;
        public ExchangeState m_state = ExchangeState.NONE;

        // to calc average diff between bid and ask on exchange
        public Utils.DoubleAverageCalculator m_bidAskDiffCalculator = new Utils.DoubleAverageCalculator<Double>() {
            @Override public double getDoubleValue(Double tick) { return tick; }
        };
        //bitstamp avgBidAskDiff1=2.6743, btc-e avgBidAskDiff2=1.3724
        //bitstamp avgBidAskDiff1=2.1741, btc-e avgBidAskDiff2=1.2498
        //bitstamp avgBidAskDiff1=1.9107, btc-e avgBidAskDiff2=1.3497

        // moving bracket orders
        public OrderData m_buyOpenBracketOrder;
        public OrderData m_sellOpenBracketOrder;
        public Long m_lastProcessedTradesTime = 0l;
        private TopData m_lastTop;
        public final Utils.AverageCounter m_averageCounter = new Utils.AverageCounter(MOVING_AVERAGE);

        public ExchangeData(Exchange exch) {
            m_exch = exch;
        }

        @Override public String toString() {
            return "ExchangeData{" +
                    "exch=" + m_exch +
                    ", state=" + m_state +
                    '}';
        }

        public int exchId() { return m_exch.m_databaseId; }
        public String avgBidAskDiffStr() { return format(m_bidAskDiffCalculator.getAverage()); }
        private String exchName() { return m_exch.m_name; }
        public boolean waitingForOpenBrackets() { return m_state == ExchangeState.OPEN_BRACKETS_WAITING; }
        public boolean hasOpenBracketExecuted() { return m_state == ExchangeState.ONE_OPEN_BRACKET_EXECUTED; }
        public boolean hasOpenMktExecuted() { return m_state == ExchangeState.OPEN_AT_MKT_EXECUTED; }

        public OrderData oldCheckExecutedBrackets() throws Exception {
            OrderData ret = null;
            if ((m_buyOpenBracketOrder != null) || (m_sellOpenBracketOrder != null)) {
                long millis0 = System.currentTimeMillis();
                TradesData trades1 = Fetcher.fetchTrades(m_exch);
                long millis1 = System.currentTimeMillis();
                TradesData newTrades = trades1.newTrades(m_lastProcessedTradesTime);
                System.out.println(" loaded " + trades1.size() + " trades for '" + exchName() + "' in "+(millis1 - millis0)+" ms; new " + newTrades.size() + " trades: " + newTrades);

                List<TradesData.TradeData> newTradesList = newTrades.m_trades;
                if (m_buyOpenBracketOrder != null) {
                    ret = oldCheckExecutedBracket(m_buyOpenBracketOrder, newTradesList);
                }
                if (m_sellOpenBracketOrder != null) {
                    ret = oldCheckExecutedBracket(m_sellOpenBracketOrder, newTradesList);
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
        private OrderData oldCheckExecutedBracket(OrderData bracketOrder, List<TradesData.TradeData> tradesList) {
            OrderData ret = null;
            OrderSide orderSide = bracketOrder.m_side;
            double orderAmount = bracketOrder.m_amount;
            for (TradesData.TradeData trade : tradesList) {
                double mktPrice = trade.m_price; // ASK > BID
                if (bracketOrder.acceptPrice(mktPrice)) {
                    double tradeAmount = trade.m_amount;
                    String orderPriceStr = bracketOrder.priceStr();
                    System.out.println("@@@@@@@@@@@@@@ we have LMT order " + orderSide + " " + orderAmount + " @ " + orderPriceStr +
                                      " on '" + exchName() + "' got matched trade=" + trade);
                    if (orderAmount > tradeAmount) { // for now partial order execution it is complex to handle - we will execute the rest by MKT price
                        System.out.println("@@@@@@@@@@@@@@  for now partial order execution it is complex to handle: orderAmount=" + orderAmount + ", tradeAmount=" + tradeAmount);
                    }
                    // here we pretend that the whole order was executed for now
                    bracketOrder.addExecution(bracketOrder.m_price, bracketOrder.m_amount); // todo: add partial order execution support later.
                    ret = new OrderData(orderSide.opposite(), Double.MAX_VALUE /*MKT*/, orderAmount);
                    break; // exit for now. continue here with partial orders support.
                }
            }
            return ret;
        }

        public void oldPlaceBrackets(TopData otherTop, double midDiffAverage) {
            double buy = otherTop.m_bid - HALF_TARGET_DELTA + midDiffAverage; // ASK > BID
            double sell = otherTop.m_ask + HALF_TARGET_DELTA + midDiffAverage;

            System.out.println("'" + exchName() + "' buy: " + ((m_buyOpenBracketOrder != null) ? m_buyOpenBracketOrder.priceStr() + "->" : "") + format(buy) + ";  " +
                    "sell: " + ((m_sellOpenBracketOrder != null) ? m_sellOpenBracketOrder.priceStr() + "->" : "") + format(sell));

            double amount = Fetcher.calcAmountToOpen(); // todo: get this based on both exch account info

            m_buyOpenBracketOrder = new OrderData(OrderSide.BUY, buy, amount);
            m_sellOpenBracketOrder = new OrderData(OrderSide.SELL, sell, amount);
        }

        public boolean placeBrackets(TopData top, TopData otherTop, double midDiffAverage) { // ASK > BID
            double buy = otherTop.m_bid - HALF_TARGET_DELTA + midDiffAverage;
            double sell = otherTop.m_ask + HALF_TARGET_DELTA + midDiffAverage;

            logOrdersAndPrices(top, buy, sell);

            double amount = calcAmountToOpen();

            m_buyOpenBracketOrder = new OrderData(OrderSide.BUY, buy, amount);
            boolean success = placeOrderBracket(m_buyOpenBracketOrder);
            if (success) {
                m_sellOpenBracketOrder = new OrderData(OrderSide.SELL, sell, amount);
                success = placeOrderBracket(m_sellOpenBracketOrder);
                if (success) {
                    setState(ExchangeState.OPEN_BRACKETS_PLACED);
                }
            }
            if (!success) {
                System.out.println("ERROR: " + exchName() + " placeBrackets failed");
                setState(ExchangeState.ERROR);
            }
//            iContext.delay(0);
            return success;
        }

        public boolean placeCloseBracket(TopData top, TopData otherTop, double midDiffAverage, OrderSide orderSide) { // ASK > BID
            boolean isBuy = (orderSide == OrderSide.BUY);
            Double buy = isBuy ? otherTop.m_bid - HALF_TARGET_DELTA + midDiffAverage : null;
            Double sell = !isBuy ? otherTop.m_ask + HALF_TARGET_DELTA + midDiffAverage : null;

            logOrdersAndPrices(top, buy, sell);

            boolean success;
            double amount = calcAmountToOpen();
            if(isBuy) {
                m_buyOpenBracketOrder = new OrderData(OrderSide.BUY, buy, amount);
                success = placeOrderBracket(m_buyOpenBracketOrder);
            } else {
                m_sellOpenBracketOrder = new OrderData(OrderSide.SELL, sell, amount);
                success = placeOrderBracket(m_sellOpenBracketOrder);
            }
            if (success) {
                setState(ExchangeState.CLOSE_BRACKET_PLACED);
            } else {
                System.out.println("ERROR: " + exchName() + " placeBrackets failed");
                setState(ExchangeState.ERROR);
            }
//            iContext.delay(0);
            return success;
        }

        private void setState(ExchangeState state) {
            System.out.println("Exchange '" + exchName() + "' state " + m_state + " -> " + state);
            m_state = state;
        }

        public boolean moveBrackets(TopData top, TopData otherTop, double midDiffAverage) {
            double buy = otherTop.m_bid - HALF_TARGET_DELTA + midDiffAverage; // ASK > BID
            double sell = otherTop.m_ask + HALF_TARGET_DELTA + midDiffAverage;

            logOrdersAndPrices(top, buy, sell);

            double amount = calcAmountToOpen(); // todo: this can be changed over the time - take special care rater

            // todo: do not move order if changed just a little - define accepted order change delta

            boolean success = true;
            double buyDelta = top.m_bid - buy;
            if (Math.abs(buyDelta) < MOVE_BRACKET_ORDER_MIN_AMOUNT) { // do not move order if changed just a little
                System.out.println("  do not mode buy bracket, delta=" + buyDelta);
            } else {
                cancelOrder(m_buyOpenBracketOrder);
                m_buyOpenBracketOrder = new OrderData(OrderSide.BUY, buy, amount);
                success = placeOrderBracket(m_buyOpenBracketOrder);
            }

            if (success) {
                double sellDelta = sell - top.m_ask;
                if (Math.abs(sellDelta) < MOVE_BRACKET_ORDER_MIN_AMOUNT) { // do not move order if changed just a little
                    System.out.println("  do not mode sel bracket, delta=" + sellDelta);
                } else {
                    cancelOrder(m_sellOpenBracketOrder);
                    m_sellOpenBracketOrder = new OrderData(OrderSide.SELL, sell, amount);
                    success = placeOrderBracket(m_sellOpenBracketOrder);
                }
            }

            if (!success) {
                System.out.println("ERROR: " + exchName() + " moveBrackets failed");
                setState(ExchangeState.ERROR);
            }
            return success;
        }

        private void logOrdersAndPrices(TopData top, Double newBuyPrice, Double newSellPrice) {
            System.out.println("'" + exchName() + "' buy: " + logPriceAndChange(m_buyOpenBracketOrder, newBuyPrice) + "  " +
                                                    logPriceDelta(top.m_bid, newBuyPrice) + "  " +
                                                    "[bid=" + top.bidStr() + ", ask=" + top.askStr() + "]  " + // ASK > BID
                                                    logPriceDelta(newSellPrice, top.m_ask) + "  " +
                                                    "sell: " + logPriceAndChange(m_sellOpenBracketOrder, newSellPrice));
        }

        private static String logPriceDelta(Double price1, Double price2) {
            if ((price1 != null) && (price2 != null)) {
                return "<" + format(price1 - price2) + ">";
            } else {
                return "?";
            }
        }

        private static String logPriceAndChange(OrderData order, Double price) {
            if (order == null) {
                if (price == null) {
                    return " ";
                } else {
                    return format(price);
                }
            } else {
                String orderPrice = order.priceStr();
                if (price == null) {
                    return orderPrice;
                } else {
                    return orderPrice + "->" + format(price);
                }
            }
        }

        private double calcAmountToOpen() {
            // could be different depending on exchange since the price is different
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
                if(m_lastTop != null) {
                    m_lastTop.setObsolete();
                }
            }
            return m_lastTop;
        }

        public boolean oldExecuteOpenMktOrder(OrderData mktOrder) {
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
                // todo: if one of brackets is executed - another one should be adjusted (cancelled?).
                return true;
            } else {
                System.out.println("@@@@@@@@@@@@@@ WARNING can not "+side+" MKT on " + exchName() + " @ " + mktPrice + ", MKT_ORDER_THRESHOLD exceed");
                return false;
            }
        }

        private boolean cancelOrder(OrderData orderData) {
            // todo: implement
            System.out.println("cancelOrder() not implemented yet: " + orderData);
            orderData.m_status = OrderStatus.CANCELLED;
            orderData.m_state = OrderState.NONE;
            return true;
        }

        private boolean placeOrderBracket(OrderData orderData) {
            return placeOrder(orderData, OrderState.BRACKET_PLACED);
        }

        public boolean placeOrder(OrderData orderData, OrderState state) {
            // todo: implement
            System.out.println("placeOrder() not implemented yet, on '"+exchName()+"': " + orderData);
            orderData.m_status = OrderStatus.SUBMITTED;
            orderData.m_state = state;

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
            return Fetcher.fetchTradesOnce(m_exch);
        }

        public TradesData filterOnlyNewTrades(TradesData trades) {
            TradesData newTrades = trades.newTrades(m_lastProcessedTradesTime);
            for (TradesData.TradeData trade : newTrades.m_trades) {
                long timestamp = trade.m_timestamp;
                if (timestamp > m_lastProcessedTradesTime) {
                    m_lastProcessedTradesTime = timestamp;
                }
            }
            return newTrades;
        }

        public void checkExchState(IterationContext iContext) throws Exception {
            checkOrderState(m_buyOpenBracketOrder, iContext); // trace order executions separately
            checkOrderState(m_sellOpenBracketOrder, iContext);
            m_state.checkState(iContext, this);
        }

        public OrderData getOpenBracketOrder() {
            return ((m_buyOpenBracketOrder != null) && (m_buyOpenBracketOrder.m_status == OrderStatus.FILLED))
                    ? m_buyOpenBracketOrder
                    : ((m_sellOpenBracketOrder != null) && (m_sellOpenBracketOrder.m_status == OrderStatus.FILLED))
                        ? m_sellOpenBracketOrder
                        : null;
        }

        private void checkOrderState(OrderData orderData, IterationContext iContext) throws Exception {
            if (orderData != null) {
                orderData.m_state.checkState(iContext, this, orderData);
            }
        }

//        public void onBracketExecuted(OrderData orderData) {
//            // set flag that at least one bracket is executed, actually can be both executed
//            // will be analyzed later
//            m_bracketExecuted = true;
//        }

        public void closeOrders() {
            closeBuyOrder();
            closeSellOrder();
            setState(ExchangeState.NONE); // nothing to wait
        }

        private void checkMktBracketExecuted(IterationContext iContext) {
            System.out.println("check if MKT bracket executed");
            boolean buyExecuted = (m_buyOpenBracketOrder != null) && (m_buyOpenBracketOrder.m_status == OrderStatus.FILLED);
            boolean sellExecuted = (m_sellOpenBracketOrder != null) && (m_sellOpenBracketOrder.m_status == OrderStatus.FILLED);
            OrderData openOrder = null;
            if (buyExecuted) {
                if (sellExecuted) {
                    System.out.println("we should have only ONE mkt order");
                    setState(ExchangeState.ERROR);
                } else {
                    System.out.println("BUY OpenMktBracketOrder FILLED");
                    openOrder = m_buyOpenBracketOrder;
                }
            } else if (sellExecuted) {
                System.out.println("SELL OpenMktBracketOrder FILLED");
                openOrder = m_sellOpenBracketOrder;
            } else {
                System.out.println(" no FILLED open MKT bracket order. waiting more");
            }
            if (openOrder != null) {
                System.out.println("we have open MKT bracket order on '" + exchName() + "': " + openOrder);
                setState(ExchangeState.OPEN_AT_MKT_EXECUTED);
                iContext.delay(0);
            }
        }

        private void checkSomeBracketExecuted(IterationContext iContext) {
            System.out.println("check if some bracket executed"); // todo: note: both can be executed (for OPEN case) in rare cases !!
            // todo: also some bracket can be partially executed - complex scenario
            boolean buyExecuted = (m_buyOpenBracketOrder != null) && (m_buyOpenBracketOrder.m_status == OrderStatus.FILLED);
            boolean sellExecuted = (m_sellOpenBracketOrder != null) && (m_sellOpenBracketOrder.m_status == OrderStatus.FILLED);
            OrderData openOrder = null;
            if (buyExecuted) {
                if (sellExecuted) {
                    // todo: very rare case - both brackets ar executed on the same exchange - fine - just cache-out - diff should be enough
                    System.out.println("!!! both brackets ar executed - BINGO - just cache-out");
                    setState(ExchangeState.ERROR);
                } else {
                    // todo: if one of brackets is executed - the another one should be adjusted (for now cancel).
                    System.out.println("BUY OpenBracketOrder FILLED, closing opposite bracket: " + m_sellOpenBracketOrder);
                    // close opposite
                    closeSellOrder();
                    openOrder = m_buyOpenBracketOrder;
                }
            } else if (sellExecuted) {
                // todo: if one of brackets is executed - the another one should be adjusted (for now cancel).
                System.out.println("SELL OpenBracketOrder FILLED, closing opposite bracket: " + m_buyOpenBracketOrder);
                // close opposite
                closeBuyOrder();
                openOrder = m_sellOpenBracketOrder;
            } else {
                System.out.println(" no FILLED bracket orders: m_buyOpenBracketOrder=" + m_buyOpenBracketOrder + ", m_sellOpenBracketOrder=" + m_sellOpenBracketOrder);
            }
            if (openOrder != null) {
                System.out.println("we have open bracket order executed on '" + exchName() + "': " + openOrder);
                setState(ExchangeState.ONE_OPEN_BRACKET_EXECUTED);
                iContext.delay(0);
            }
        }

        private void closeBuyOrder() {
            closeOrder(m_buyOpenBracketOrder);
            m_buyOpenBracketOrder = null;
        }

        private void closeSellOrder() {
            closeOrder(m_sellOpenBracketOrder);
            m_sellOpenBracketOrder = null;
        }

        private void xAllBracketsPlaced(IterationContext iContext) {
            xBracketPlaced(m_buyOpenBracketOrder);
            xBracketPlaced(m_sellOpenBracketOrder);
            setState(ExchangeState.OPEN_BRACKETS_PLACED);
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

        private boolean closeOrder(OrderData orderData) {
            System.out.println("closeOrder() not implemented: " + orderData);
            if (orderData != null) {
                orderData.m_status = OrderStatus.CANCELLED;
                orderData.m_state = OrderState.NONE;
            }
            return true; // todo: but actually can be already executed
        }

        public boolean openOrderAtMarket(OrderSide side, TopData top) {
            boolean placed = false;
            System.out.println("openOrderAtMarket() openedExch='"+exchName()+"', side=" + side);
            if (TopData.isLive(top)) {
                double price = side.mktPrice(top);

                logOrdersAndPrices(top, (side == OrderSide.BUY) ? price : null, (side == OrderSide.SELL) ? price : null);

                double amount = calcAmountToOpen();
                OrderData order = new OrderData(side, price, amount);
                boolean success = placeOrder(order, OrderState.MARKET_PLACED);
                if (success) {
                    placed = true;
                    if (side == OrderSide.BUY) {
                        m_buyOpenBracketOrder = order;
                    } else {
                        m_sellOpenBracketOrder = order;
                    }
                    setState(ExchangeState.OPEN_AT_MKT_PLACED);
                } else {
                    System.out.println("error opening order at MKT: " + order);
                    setState(ExchangeState.ERROR);
                }
            } else {
                System.out.println("will not open OrderAtMarket price - waiting - no live topData now : " + top);
            }
            return placed;
        }

        public void cleanOrders() {
            m_buyOpenBracketOrder = null;
            m_sellOpenBracketOrder = null;
        }
    } // ExchangeData

    public enum ExchangeState {
        NONE {
            @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
                System.out.println("ExchangeState.NONE(" + exchData.exchName() + ").");
            }
        },
        OPEN_BRACKETS_WAITING {
            @Override public void checkState(IterationContext iContext, ExchangeData exchData) {
                System.out.println("ExchangeState.OPEN_BRACKETS_WAITING(" + exchData.exchName() + "). check orders status");
                // todo: check orders status here, can be submited/queued, placed, rejected, and even filled/partially-filled
                LiveOrdersData liveOrdersState = iContext.getLiveOrdersState(exchData);

                // actually one can be placed and another not - should be handled separately
                { // pretend that both orders are placed fine
                    exchData.xAllBracketsPlaced(iContext); // start listen for order changes
                }
            }
        },
        OPEN_BRACKETS_PLACED {
            @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
                System.out.println("ExchangeState.OPEN_BRACKETS_PLACED(" + exchData.exchName() + "). check if some order executed");
                exchData.checkSomeBracketExecuted(iContext);
            }
        },
        ONE_OPEN_BRACKET_EXECUTED {
            @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
                System.out.println("ExchangeState.ONE_OPEN_BRACKET_EXECUTED(" + exchData.exchName() + "). do nothing");
            }
        },
        OPEN_AT_MKT_PLACED {
            @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
                System.out.println("ExchangeState.OPEN_AT_MKT_PLACED(" + exchData.exchName() + "). check if MKT order executed");
                exchData.checkMktBracketExecuted(iContext);
            }
        },
        OPEN_AT_MKT_EXECUTED {

        },
        CLOSE_BRACKET_PLACED {
            @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
                System.out.println("ExchangeState.CLOSE_BRACKET_PLACED(" + exchData.exchName() + "). check if some order executed");
                exchData.checkSomeBracketExecuted(iContext);
                //here to finish
            }
        },
        ERROR,
        ;

        public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
            System.out.println("checkState not implemented for ExchangeState." + this);
        }
    } // ExchangeData

    public static class OrderData {
        public OrderStatus m_status = OrderStatus.NEW;
        public OrderState m_state = OrderState.NONE;
        public OrderSide m_side;
        public double m_price;
        public double m_amount;
        public double m_filled;
        public List<Execution> m_executions;

        @Override public String toString() {
            return "OrderData{" +
                    "side=" + m_side +
                    ", amount=" + m_amount +
                    ", price=" + format(m_price) +
                    ", status=" + m_status +
                    ", state=" + m_state +
                    ", filled=" + m_filled +
                    '}';
        }

        public String priceStr() { return format(m_price); }

        public OrderData(OrderSide side, double price, double amount) {
            m_side = side;
            m_price = price;
            m_amount = amount;
        }

        public boolean isActive() { return m_status.isActive(); }

        public boolean acceptPrice(double mktPrice) {
            return m_side.acceptPrice(m_price, mktPrice);
        }

        public void addExecution(double price, double amount) {
            List<Execution> executions = getExecutions();
            executions.add(new Execution(price, amount));
            m_filled += amount;
            if (m_amount == m_filled) { // todo: add check for very small diff like 0.00001
                m_status = OrderStatus.FILLED;
            } else if (executions.size() == 1) { // just got the very first execution
                m_status = OrderStatus.PARTIALLY_FILLED;
            }
        }

        private List<Execution> getExecutions() {
            if (m_executions == null) { // lazily create
                m_executions = new ArrayList<Execution>();
            }
            return m_executions;
        }

        public boolean xCheckExecutedLimit(IterationContext iContext, ExchangeData exchData, OrderData orderData, TradesData newTrades) {
            OrderSide orderSide = orderData.m_side;
            double orderAmount = orderData.m_amount;
            double price = orderData.m_price;
            for (TradesData.TradeData trade : newTrades.m_trades) {
                double mktPrice = trade.m_price; // ASK > BID

                boolean acceptPriceSimulated = false;
                //noinspection PointlessBooleanExpression,ConstantConditions
                if (SIMULATE_ACCEPT_ORDER_PRICE && !iContext.m_acceptPriceSimulated
                        && new Random().nextBoolean() && new Random().nextBoolean()) {
                    System.out.println("@@@@@@@@@@@@@@  !!!!!!!! SIMULATE ACCEPT_ORDER_PRICE mktPrice=" + format(mktPrice) + ", order=" + this);
                    acceptPriceSimulated = true;
                    iContext.m_acceptPriceSimulated = true; // one accept order price simulation per iteration
                }

                if (acceptPriceSimulated || orderData.acceptPrice(mktPrice)) {
                    double tradeAmount = trade.m_amount;
                    System.out.println("@@@@@@@@@@@@@@ we have LMT order " + orderSide + " " + orderAmount + " @ " + orderData.priceStr() +
                                      " on '" + exchData.exchName() + "' got matched trade=" + trade);

                    if (orderAmount > tradeAmount) { // for now partial order execution it is complex to handle - todo: we may execute the rest by MKT price
                        System.out.println("@@@@@@@@@@@@@@  for now partial order execution it is complex to handle: " +
                                "orderAmount=" + orderAmount + ", tradeAmount=" + tradeAmount);
                    }
                    // here we pretend that the whole order was executed for now
                    orderData.addExecution(price, orderAmount); // todo: add partial order execution support later.
                    return true; // simulated that the whole order executed
                }
            }
            return false;
        }
    } // OrderData

    public static enum OrderState {
        NONE,
        BRACKET_PLACED {
            @Override public void checkState(IterationContext iContext, ExchangeData exchData, OrderData orderData) throws Exception {
                System.out.println("OrderState.BRACKET_PLACED. check if order executed");
                trackLimitOrderExecution(iContext, exchData, orderData);
            }
        },
        MARKET_PLACED {
            @Override public void checkState(IterationContext iContext, ExchangeData exchData, OrderData orderData) throws Exception {
                System.out.println("OrderState.MARKET_PLACED. check if order executed");
                boolean executed = trackLimitOrderExecution(iContext, exchData, orderData);
                if( executed ) {
                    System.out.println(" OPEN MKT bracket order executed. we are fully OPENED");
                } else {
                    System.out.println(" MKT order not yet executed - check and move ef needed");
                }
            }
        };

        private static boolean trackLimitOrderExecution(IterationContext iContext, ExchangeData exchData, OrderData orderData) throws Exception {
            // actually order execution should be checked via getLiveOrdersState()
            LiveOrdersData liveOrdersState = iContext.getLiveOrdersState(exchData);
            // but for simulation we are checking via trades
            TradesData newTrades = iContext.getNewTradesData(exchData);
            orderData.xCheckExecutedLimit(iContext, exchData, orderData, newTrades);
            if (orderData.m_filled > 0) {
                if (orderData.m_status == OrderStatus.FILLED) {
                    orderData.m_state = NONE;
                    return true;
                } else { // PARTIALLY FILLED
                    System.out.println("PARTIALLY FILLED, not supported yet - just wait more");
                }
            }
            return false;
        }

        public void checkState(IterationContext iContext, ExchangeData exchangeData, OrderData orderData) throws Exception {
            System.out.println("checkState not implemented for OrderState." + this);
        }
    } // OrderState

    private static class ExchangesData {
        public final ExchangeData m_exch1data;
        public final ExchangeData m_exch2data;
        public TopData.TopDataEx m_lastDiff;
        public ExchangesState m_state = ExchangesState.NONE;
        public Utils.AverageCounter m_diffAverageCounter = new Utils.AverageCounter(MOVING_AVERAGE);
        // OPEN sides
        private ExchangeData m_openBuyExchange;
        private ExchangeData m_openSellExchange;
        private OrderData m_openBuyOrder;
        private OrderData m_openSellOrder;

        private boolean waitingForAllBracketsOpen() { return m_exch1data.waitingForOpenBrackets() && m_exch2data.waitingForOpenBrackets(); }
        private boolean hasAnyBracketExecuted() { return m_exch1data.hasOpenBracketExecuted() || m_exch2data.hasOpenBracketExecuted(); }
        private boolean hasBothBracketExecuted() { return (m_exch1data.hasOpenBracketExecuted() && m_exch2data.hasOpenMktExecuted())
                                                       || (m_exch2data.hasOpenBracketExecuted() && m_exch1data.hasOpenMktExecuted()); }

        public ExchangesData(ExchangeData exch1data, ExchangeData exch2data) {
            m_exch1data = exch1data;
            m_exch2data = exch2data;
        }

        public void checkState(IterationContext iContext) throws Exception {
            System.out.println("Exchanges state: " + m_state);

            if(m_state.queryTrades()) {
                iContext.getNewTradesData(m_exch1data);
                iContext.getNewTradesData(m_exch2data);
            }

            m_exch1data.checkExchState(iContext);
            m_exch2data.checkExchState(iContext);
            m_state.checkState(iContext, this);
        }

        public void onTopsLoaded(TopDatas topDatas) {
            m_lastDiff = topDatas.calculateDiff(); // top1 - top2
            if (m_lastDiff != null) {
                m_diffAverageCounter.justAdd(System.currentTimeMillis(), m_lastDiff.m_mid);
            }
            m_exch1data.m_lastTop = topDatas.m_top1;
            m_exch2data.m_lastTop = topDatas.m_top2;
        }

        private void setState(ExchangesState state) {
            System.out.println("Exchanges state " + m_state + " -> " + state);
            m_state = state;
        }

        private void queryAccountsData() {
            m_exch1data.queryAccountData();
            m_exch2data.queryAccountData();
        }

        private void doWithFreshTopData(IterationContext iContext, Runnable run) throws Exception {
            TopDatas tops = iContext.getTopsData(this);
            if (tops.bothFresh()) {
                logDiffAverageDelta();

                run.run();

                double waitDistance = TARGET_DELTA;
                System.out.println("waitDistance="+waitDistance);
                long delay = (long) (MIN_DELAY + MIN_DELAY * 4 * Math.min(1, Math.abs(waitDistance) / HALF_TARGET_DELTA));
                delay = Math.max(delay,1000);
                iContext.delay(delay);
            } else {
                System.out.println("some exchange top data is not fresh " +
                        "(fresh1=" + tops.top1fresh() + ", fresh2=" + tops.top2fresh() + ") - do nothing");
            }
        }

        private void logDiffAverageDelta() {
            double midDiffAverage = m_diffAverageCounter.get();
            double delta = m_lastDiff.m_mid - midDiffAverage;
            System.out.println("diff=" + m_lastDiff + ";  avg=" + format(midDiffAverage) + ";  delta=" + format(delta));
        }

        private void moveBracketsIfNeeded(IterationContext iContext) throws Exception {
            System.out.println(" move open bracket orders if needed");
            doWithFreshTopData(iContext, new Runnable() {
                public void run() {
                    TopData top1 = m_exch1data.m_lastTop;
                    TopData top2 = m_exch2data.m_lastTop;
                    double midDiffAverage = m_diffAverageCounter.get();
                    boolean success = m_exch1data.moveBrackets(top1, top2, midDiffAverage);
                    if (success) {
                        success = m_exch2data.moveBrackets(top2, top1, -midDiffAverage);
                        if (success) {
                            System.out.println("  on both exchanges open bracket orders are moved");
                        }
                    }
                    if (!success) {
                        System.out.println("ERROR: some exch moveBrackets failed");
                        setState(ExchangesState.ERROR);
                    }
                }
            });
        }

        private void placeOpenBrackets(IterationContext iContext) throws Exception {
            System.out.println(" try place OpenBrackets");
            doWithFreshTopData(iContext, new Runnable() {
                public void run() {
                    TopData top1 = m_exch1data.m_lastTop;
                    TopData top2 = m_exch2data.m_lastTop;
                    double midDiffAverage = m_diffAverageCounter.get();
                    boolean success = m_exch1data.placeBrackets(top1, top2, midDiffAverage);
                    if (success) {
                        success = m_exch2data.placeBrackets(top2, top1, -midDiffAverage);
                        if (success) {
                            // i see the orders should be placed instantaneously
                            setState(ExchangesState.OPEN_BRACKETS_PLACED);
                        }
                    }
                    if (!success) {
                        System.out.println("ERROR: some exch placeOpenBrackets failed");
                        setState(ExchangesState.ERROR);
                    }
                }
            });
        }

        public void placeCloseBrackets(IterationContext iContext) throws Exception {
            System.out.println(" try place CloseBrackets");
            doWithFreshTopData(iContext, new Runnable() {
                public void run() {
                    TopData top1 = m_exch1data.m_lastTop;
                    TopData top2 = m_exch2data.m_lastTop;
                    double midDiffAverage = m_diffAverageCounter.get();

                    boolean exch1openSell = (m_openSellExchange == m_exch1data);
                    boolean success = m_exch1data.placeCloseBracket(top1, top2, midDiffAverage,
                            (exch1openSell ? OrderSide.BUY : OrderSide.SELL));
                    if (success) {
                        success = m_exch2data.placeCloseBracket(top2, top1, -midDiffAverage,
                                (exch1openSell ? OrderSide.SELL : OrderSide.BUY));
                    }
                    if (success) {
                        // i see the orders should be placed instantaneously
                        setState(ExchangesState.CLOSE_BRACKET_PLACED);
                    } else {
                        System.out.println("ERROR: some exch placeCloseBracket failed");
                        setState(ExchangesState.ERROR);
                    }
                }
            });
        }

        private void openOtherSideAtMarket(IterationContext iContext) throws Exception {
            TopDatas tops = iContext.getTopsData(this); // make sure top data is loaded
            boolean hasBr1 = m_exch1data.hasOpenBracketExecuted();
            boolean hasBr2 = m_exch2data.hasOpenBracketExecuted();
            if(hasBr1 && hasBr2) { // todo: ok if at different sides, bad if at the same - complex. this need to be handled here
                System.out.println("ERROR: both exchanges have bracket executed at this point.");
                setState(ExchangesState.ERROR);
            } else if(hasBr1) {
                openOtherSideAtMarket( m_exch2data, m_exch1data, tops.m_top2);
            } else if (hasBr2) {
                openOtherSideAtMarket(m_exch1data, m_exch2data, tops.m_top1);
            } else {
                System.out.println("ERROR: no open orders found at both exch.");
                setState(ExchangesState.ERROR);
            }
        }

        private void openOtherSideAtMarket(ExchangeData toOpenExch, ExchangeData openedExch, TopData top) {
            OrderData openedBracketOrder = openedExch.getOpenBracketOrder();
            System.out.println("openOtherSideAtMarket() openedExch='" + openedExch.exchName() + "', openedBracketOrder=" + openedBracketOrder);
            if (openedBracketOrder != null) {
                OrderSide side = openedBracketOrder.m_side;
                toOpenExch.closeOrders();
                boolean placed = toOpenExch.openOrderAtMarket(side.opposite(), top);
                if (placed) {
                    setState(ExchangesState.WAITING_OTHER_SIDE_AT_MKT);
                }
            } else {
                System.out.println("ERROR: no open orders found at " + openedExch);
                setState(ExchangesState.ERROR);
            }
        }

        private boolean verifyAndLogOpen(IterationContext iContext) throws Exception {
            iContext.getTopsData(this);
            System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
            System.out.println("% '" + m_exch1data.exchName() + "' " + m_exch1data.m_state);
            System.out.println("%    buy:  " + m_exch1data.m_buyOpenBracketOrder);
            System.out.println("%    sell: " + m_exch1data.m_sellOpenBracketOrder);
            System.out.println("% '" + m_exch2data.exchName() + "' " + m_exch2data.m_state);
            System.out.println("%    buy:  " + m_exch2data.m_buyOpenBracketOrder);
            System.out.println("%    sell: " + m_exch2data.m_sellOpenBracketOrder);
            String err = null;
            ExchangeData buyExch = null;
            ExchangeData sellExch = null;
            if ((m_exch1data.m_buyOpenBracketOrder != null) && (m_exch2data.m_sellOpenBracketOrder != null)) {
                if (m_exch1data.m_sellOpenBracketOrder != null) {
                    err = "unexpected sellOpenBracketOrder on " + m_exch1data.exchName();
                } else if (m_exch2data.m_buyOpenBracketOrder != null) {
                    err = "unexpected buyOpenBracketOrder on " + m_exch2data.exchName();
                } else {
                    buyExch = m_exch1data;
                    sellExch = m_exch2data;
                }
            } else if ((m_exch2data.m_buyOpenBracketOrder != null) && (m_exch1data.m_sellOpenBracketOrder != null)) {
                if (m_exch2data.m_sellOpenBracketOrder != null) {
                    err = "unexpected sellOpenBracketOrder on " + m_exch2data.exchName();
                } else if (m_exch1data.m_buyOpenBracketOrder != null) {
                    err = "unexpected buyOpenBracketOrder on " + m_exch1data.exchName();
                } else {
                    buyExch = m_exch2data;
                    sellExch = m_exch1data;
                }
            } else {
                err = "no buy/sell OpenBracketOrders found";
            }
            boolean noErrors = (err == null);
            if (noErrors) {
                // save OPEN sides
                m_openBuyExchange = buyExch;
                m_openSellExchange = sellExch;
                m_openBuyOrder = buyExch.m_buyOpenBracketOrder;
                m_openSellOrder = sellExch.m_sellOpenBracketOrder;

                System.out.println("% BUY  on '" + buyExch.exchName() + "' @ " + m_openBuyOrder.priceStr());
                m_openBuyExchange.logOrdersAndPrices(m_openBuyExchange.m_lastTop, null, null);
                System.out.println("% SELL on '" + sellExch.exchName() + "' @ " + m_openSellOrder.priceStr());
                m_openSellExchange.logOrdersAndPrices(m_openSellExchange.m_lastTop, null, null);

                double midDiffAverage = m_diffAverageCounter.get();
                double sellPrice = m_openSellOrder.m_price;
                double buyPrice = m_openBuyOrder.m_price;
                double priceDiff = sellPrice - buyPrice;

                logDiffAverageDelta();

                double openEarn = priceDiff;
                if(m_openSellExchange == m_exch1data) { // sell on exch 1
                    openEarn -= midDiffAverage;
                } else { // sell on exch 2
                    openEarn += midDiffAverage;
                }
                System.out.println("%   >>>  priceDiff=" + format(priceDiff) + ",  openEarn=" + format(openEarn));
            } else {
                setState(ExchangesState.ERROR);
            }
            return noErrors;
        }

        public void cleanOrders() {
            m_exch1data.cleanOrders();
            m_exch2data.cleanOrders();
        }
    } // ExchangesData

    private enum ExchangesState {
        NONE {
            @Override public boolean queryTrades() { return false; }
            @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                System.out.println("ExchangesState.NONE. queryAccountsData");
                exchangesData.queryAccountsData();
                exchangesData.setState(START);
                iContext.delay(0);
            }
        },
        START {
            @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                System.out.println("ExchangesState.START. try to place open bracket orders");
                exchangesData.placeOpenBrackets(iContext);
            }
        },
        WAITING_OPEN_BRACKETS {
            @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                // orders are placed just instantaneously - code here just in case and double check
                System.out.println("ExchangesState.OPEN_BRACKETS_WAITING");
                // todo: check for order status here - can be complex here - some order can be already placed and partially or fully executed
                // can become SUBMITTED, REJECTED
                if(!exchangesData.waitingForAllBracketsOpen()) {
                    exchangesData.setState(ExchangesState.OPEN_BRACKETS_PLACED); // actually some order may have already another state
                    iContext.delay(0); // no wait
                }
            }
        },
        OPEN_BRACKETS_PLACED {
            @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                System.out.println("ExchangesState.OPEN_BRACKETS_PLACED checkState()");
                if( exchangesData.hasAnyBracketExecuted()) {
                    System.out.println(" Bracket on some exchnage Executed !!!");
                    exchangesData.setState(OPENING_OTHER_SIDE_AT_MKT);
                    exchangesData.openOtherSideAtMarket(iContext);
                } else {
                    exchangesData.moveBracketsIfNeeded(iContext);
                }
            }
        },
        OPENING_OTHER_SIDE_AT_MKT {
            @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                System.out.println("ExchangesState.OPENING_OTHER_SIDE_AT_MKT checkState()");
                exchangesData.openOtherSideAtMarket(iContext);
            }
        },
        WAITING_OTHER_SIDE_AT_MKT {
            @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                System.out.println("ExchangesState.WAITING_OTHER_SIDE_AT_MKT checkState()");
                if( exchangesData.hasBothBracketExecuted()) {
                    System.out.println(" Brackets on both exchanges Executed !!!");
                    if(exchangesData.verifyAndLogOpen(iContext)) {
                        exchangesData.cleanOrders();
                        System.out.println("  placing CloseBrackets");
                        exchangesData.setState(BOTH_SIDES_OPENED);
                        exchangesData.placeCloseBrackets(iContext);
                    }
                }
            }
        },
        BOTH_SIDES_OPENED {
            @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                System.out.println("ExchangesState.BOTH_SIDES_OPENED will need to place close brackets");
                exchangesData.placeCloseBrackets(iContext);
            }
        },
        CLOSE_BRACKET_PLACED {
            @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
                System.out.println("ExchangesState.CLOSE_BRACKET_PLACED monitor order...");
            }
        },
        ERROR  {
            // todo: close everything if opened
        },
        ;

        public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
            System.out.println("checkState not implemented for " + this);
        }

        public boolean queryTrades() { return true; }
    }

    private static class IterationContext {
        public TopDatas m_top;
        public Map<Integer,LiveOrdersData> m_liveOrders;
        public long m_nextIterationDelay = 1000; // 1 sec by def
        private Map<Integer, TradesData> m_newTrades;
        public boolean m_acceptPriceSimulated;

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
                TradesData trades = exchData.fetchTrades();
                if(trades == null) {
                    System.out.println(" NO trades loaded for '" + exchData.exchName() + "' this time" );
                    data = new TradesData(new ArrayList<TradesData.TradeData>()); // empty
                } else {
                    data = exchData.filterOnlyNewTrades(trades); // this will update last processed trade time
                    long millis1 = System.currentTimeMillis();
                    System.out.println(" loaded " + trades.size() + " trades for '" + exchData.exchName() + "' " +
                                       "in " + (millis1 - millis0) + " ms; new " + data.size() + " trades: " + data);
                }
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
            log(exch2data.m_exch, top2);
            System.out.println();

            TopDatas ret = new TopDatas(top1, top2);
            exchangesData.onTopsLoaded(ret);
            return ret;
        }

        public void delay(long millis) {
            m_nextIterationDelay = millis;
        }
    } // IterationContext

    private static class LiveOrdersData {

    }

}
