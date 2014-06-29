package bthdg.triplet;

import bthdg.DbReady;
import bthdg.exch.Exchange;
import bthdg.exch.Pair;
import bthdg.exch.TopData;
import bthdg.exch.TopsData;
import bthdg.util.Utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogProcessor extends DbReady {
    private static final String LOG_FILE = "logs\\triplet.199.log"; // processed 3 - 199

    private static Pattern DATE_PATTERN = Pattern.compile(".*iteration.*date=(.*)");

//    loaded tops* 142ms; take 142ms; avg 260ms: {BTC_EUR=Top{bid=351.12001, ask=354.09999, last=0.00000}, EUR_USD=Top{bid=1.35702, ask=1.35714, last=0.00000}, LTC_BTC=Top{bid=0.02494, ask=0.02496, last=0.00000},
//       LTC_USD=Top{bid=11.940100, ask=11.990000, last=0.000000}, BTC_USD=Top{bid=478.000, ask=478.500, last=0.000}, LTC_EUR=Top{bid=8.750, ask=8.780, last=0.000}}

    private static String ONE = ".*?(..._...)=Top\\{bid=(\\d+(\\.\\d+)?), ask=(\\d+(\\.\\d+)?)";
    private static Pattern LINE_3_PATTERN =  Pattern.compile(ONE+ONE+ONE+".*");
    private static Pattern LINE_2_PATTERN =  Pattern.compile(ONE+ONE+".*");
    private static Pattern LINE_1_PATTERN =  Pattern.compile(ONE+".*");
    private static Pattern LINE_PATTERN_OLD =  Pattern.compile(ONE+ONE+ONE+ONE+ONE+ONE+".*");

    public static final String CREATE_TOPS_SQL = "CREATE TABLE IF NOT EXISTS Tops ( " +
            " stamp BIGINT NOT NULL, " +
            " src   INTEGER NOT NULL, " +
            " pair  INTEGER NOT NULL, " +
            " bid   DOUBLE, " +
            " ask   DOUBLE, " +
            " last  DOUBLE " +
            ")";
    public static final String CREATE_TOPS_INDEX_SQL = "CREATE INDEX srsttops on Tops (src, stamp)";
    private static final String INSERT_TOPS_SQL = "INSERT INTO Tops ( stamp, src, pair, bid, ask ) VALUES (?,?,?,?,?)";
    private static final String DELETE_TOPS_SQL = "DELETE FROM Tops WHERE src = ? AND stamp = ?";
    private static final String DELETE_ALL_TOPS_SQL = "DELETE FROM Tops";
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy");

    public static void main(String[] args) {
        System.out.println("LogProcessor Started.");
        long millis = Utils.logStartTimeMemory();

        updateFromLog();

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private static void updateFromLog() {
        goWithDb(new DbReady.IDbRunnable() {
            public void run(Connection connection) throws SQLException {
                createTableIfNeeded(connection);
                updateFromLog(connection);
            }
        });
    }

    private static void createTableIfNeeded(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            int ret;
//            ret = statement.executeUpdate(CREATE_TOPS_SQL);
//            System.out.println("--- CREATE TABLE Tops  returns " + ret);
//
//            ret = statement.executeUpdate(CREATE_TOPS_INDEX_SQL);
//            System.out.println("--- CREATE INDEX srsttops on Tops  returns " + ret);
//
//            ret = statement.executeUpdate(DELETE_ALL_TOPS_SQL);
//            System.out.println("--- DELETE ALL from Tops  returns " + ret);
        } finally {
            statement.close();
        }
    }

    private static void updateFromLog(Connection connection) {
        long start = System.currentTimeMillis();
        long millis = start;
        int counter = 0;
        try {
            System.out.println(" processing file " + LOG_FILE);
            BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE));
            try {
                connection.setAutoCommit(false); // for fast inserts/updates
                PreparedStatement insertStatement = connection.prepareStatement(INSERT_TOPS_SQL);
                PreparedStatement deleteStatement = connection.prepareStatement(DELETE_TOPS_SQL);
                try {
                    Data data = new Data();
                    State state = State.START;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        state = state.process(line, data, insertStatement, deleteStatement);

                        if (data.m_counter % 10 == 0) {
                            counter += data.m_counter;
                            data.m_counter = 0;
                            connection.commit(); // commit every 10 saved tops
                        }

                        long millis2 = System.currentTimeMillis();
                        if (millis2 - millis > 5000) { // log every 5 sec
                            System.out.println(" saved " + counter + " Tops in " + (millis2 - start) + "ms");
                            millis = millis2;
                        }
                    }
                    counter += data.m_counter;
                } finally {
                    System.out.println(" committing...");
                    connection.commit();
                    insertStatement.close();
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(" total saved " + counter + " Tops");
    }

    private static void match(Matcher matcher, int indx, Data data) {
        String pairStr = matcher.group(indx);
        String bidStr = matcher.group(indx + 1);
        String askStr = matcher.group(indx + 3);
        double bid = Double.parseDouble(bidStr);
        double ask = Double.parseDouble(askStr);
        TopData top = new TopData(bid, ask);
        Pair pair = Pair.getByName(pairStr);
        data.m_tops.put(pair, top);
    }

    private static void save(PreparedStatement insertStatement, PreparedStatement deleteStatement, Data data) throws SQLException {
        int dbId = Exchange.BTCE.m_databaseId;
        long timestamp = data.m_date.getTime();

        deleteStatement.setInt(1, dbId); // src
        deleteStatement.setLong(2, timestamp); // stamp
        int deleted = deleteStatement.executeUpdate();
//System.out.println("deleted " + deleted);

        // "INSERT INTO Ticks ( stamp, src, pair, bid, ask ) VALUES (?,?,?,?,?)";
        insertStatement.setLong(1, timestamp);
        insertStatement.setInt(2, dbId);

        for(Map.Entry<Pair, TopData> entry: data.m_tops.m_map.entrySet()) {
            Pair pair = entry.getKey();
            insertStatement.setInt(3, pair.m_id);

            TopData top = entry.getValue();
            double bid = top.m_bid;
            double ask = top.m_ask;

            insertStatement.setDouble(4, bid);
            insertStatement.setDouble(5, ask);
            insertStatement.executeUpdate(); // execute insert SQL statement
        }
        data.m_counter += 1;
        data.clean();
    }

    private static enum State {
        START {
            public State process(String line, Data data, PreparedStatement insertStatement, PreparedStatement deleteStatement) {
                if (line.contains("==============================================")) {
                    Matcher matcher = DATE_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        String dateStr = matcher.group(1); // Wed Apr 23 14:38:57 EEST 2014
                        try {
                            Date date = DATE_FORMAT.parse(dateStr);
                            data.m_date = date;
                            return FIRST;
                        } catch (ParseException e) {
                            throw new RuntimeException("parse error: " + e, e);
                        }
                    }
                }
                return START;
            }
        },
        FIRST {
            public State process(String line, Data data, PreparedStatement insertStatement, PreparedStatement deleteStatement) throws SQLException {
                if (line.contains("loaded tops")) {
                    Matcher matcher = LINE_PATTERN_OLD.matcher(line);
                    if (matcher.matches()) {
                        match(matcher, 1, data);
                        match(matcher, 6, data);
                        match(matcher, 11, data);
                        match(matcher, 16, data);
                        match(matcher, 21, data);
                        match(matcher, 26, data);
                        if (data.m_tops.m_map.size() == 6) {
                            save(insertStatement, deleteStatement, data);
                        } else {
                            throw new RuntimeException("nor 6 exch topData loaded: " + line);
                        }
                        return START;
                    } else {
                        matcher = LINE_3_PATTERN.matcher(line);
                        if (matcher.matches()) {
                            match(matcher, 1, data);
                            match(matcher, 6, data);
                            match(matcher, 11, data);
                            return SECOND;
                        } else {
                            throw new RuntimeException("loaded FIRST line not parsed: " + line);
                        }
                    }
                }
                if (line.contains("==============================================")) {
                    throw new RuntimeException("iteration start before first line matched: " + line);
                }
                return FIRST;
            }
        },
        SECOND {
            public State process(String line, Data data, PreparedStatement insertStatement, PreparedStatement deleteStatement) throws SQLException {
                if (line.contains("=Top{bid=")) {
                    Matcher matcher = LINE_3_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        match(matcher, 1, data);
                        match(matcher, 6, data);
                        match(matcher, 11, data);
                        return THIRD;
                    } else {
                        throw new RuntimeException("loaded SECOND line not parsed: " + line);
                    }
                }
                if (line.contains("==============================================")) {
                    throw new RuntimeException("iteration start before second line matched: " + line);
                }
                throw new RuntimeException("error");
            }
        },
        THIRD {
            public State process(String line, Data data, PreparedStatement insertStatement, PreparedStatement deleteStatement) throws SQLException {
                if (line.contains("=Top{bid=")) {
                    Matcher matcher = LINE_3_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        match(matcher, 1, data);
                        match(matcher, 6, data);
                        match(matcher, 11, data);
                        return FORTH;
                    } else {
                        matcher = LINE_2_PATTERN.matcher(line);
                        if (matcher.matches()) {
                            match(matcher, 1, data);
                            match(matcher, 6, data);
                            if (data.m_tops.m_map.size() == 8) {
                                save(insertStatement, deleteStatement, data);
                            } else {
                                throw new RuntimeException("not 8 exch topData loaded: " + line);
                            }
                            return START;
                        } else {
                            throw new RuntimeException("loaded THIRD line not parsed: " + line);
                        }
                    }
                }
                if (line.contains("==============================================")) {
                    throw new RuntimeException("iteration start before third line matched: " + line);
                }
                throw new RuntimeException("error");
            }
        },
        FORTH {
            public State process(String line, Data data, PreparedStatement insertStatement, PreparedStatement deleteStatement) throws SQLException {
                if (line.contains("=Top{bid=")) {
                    Matcher matcher = LINE_1_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        match(matcher, 1, data);
                        if (data.m_tops.m_map.size() == 10) {
                            save(insertStatement, deleteStatement, data);
                        } else {
                            throw new RuntimeException("not 10 exch topData loaded: " + line);
                        }
                        return START;
                    } else {
                        throw new RuntimeException("loaded FORTH line not parsed: " + line);
                    }
                }
                if (line.contains("==============================================")) {
                    throw new RuntimeException("iteration start before forth line matched: " + line);
                }
                throw new RuntimeException("error");
            }
        }
        ;

        public State process(String line, Data data, PreparedStatement insertStatement, PreparedStatement deleteStatement) throws SQLException { return null; }
    }

    private static class Data {
        public Date m_date;
        public TopsData m_tops = new TopsData();
        public int m_counter;

        public void clean() {
            m_tops.m_map.clear();
        }
    }
}
