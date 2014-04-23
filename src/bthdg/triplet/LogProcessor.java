package bthdg.triplet;

import bthdg.DbReady;
import bthdg.Exchange;
import bthdg.Utils;
import bthdg.exch.Pair;
import bthdg.exch.TopData;
import bthdg.exch.TopsData;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogProcessor extends DbReady {
    private static final String LOG_FILE = "C:\\Botya\\Projects\\biTest\\logs\\triplet.103.log";

    private static Pattern DATE_PATTERN = Pattern.compile(".*iteration.*date=(.*)");
    private static Pattern LINE_PATTERN = Pattern.compile(".*?=Top\\{bid=(\\d+\\.\\d+),\\sask=(\\d+\\.\\d+).*?=Top\\{bid=(\\d+\\.\\d+),\\sask=(\\d+\\.\\d+).*?=Top\\{bid=(\\d+\\.\\d+),\\sask=(\\d+\\.\\d+).*");

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

    public static void main(String[] args) {
        System.out.println("LogProcessor Started.");
        long millis = logTimeMemory();

        updateFromLog();

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private static void updateFromLog() {
//        updateFromLog(null);

        goWithDb(new DbReady.IDbRunnable() {
            public void run(Connection connection) throws SQLException {
//                creaTetableIfNeeded(connection);
                updateFromLog(connection);
            }
        });
    }

    private static void creaTetableIfNeeded(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            int ret;
            ret = statement.executeUpdate(CREATE_TOPS_SQL);
            System.out.println("--- CREATE TABLE Tops  returns " + ret);

            ret = statement.executeUpdate(CREATE_TOPS_INDEX_SQL);
            System.out.println("--- CREATE INDEX srsttops on Tops  returns " + ret);
        } finally {
            statement.close();
        }
    }

    private static void updateFromLog(Connection connection) {
        long start = System.currentTimeMillis();
        long millis = start;
        int counter = 0;
        try {
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

    private static TopData match(Matcher matcher, int indx) {
        String bidStr = matcher.group(indx);
        String askStr = matcher.group(indx + 1);
        double bid = Double.parseDouble(bidStr);
        double ask = Double.parseDouble(askStr);
        return new TopData(bid, ask);
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
                        DateFormat df = new SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy");
                        Date date;
                        try {
                            date = df.parse(dateStr);
                        } catch (ParseException e) {
                            throw new RuntimeException("parse error: " + e, e);
                        }
                        data.m_date = date;
                        return FIRST;
                    }
                }
                return START;
            }
        },
        FIRST {
            public State process(String line, Data data, PreparedStatement insertStatement, PreparedStatement deleteStatement) {
                if (line.contains("loaded tops")) {
                    Matcher matcher = LINE_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        data.m_tops.put(Pair.BTC_EUR, match(matcher, 1));
                        data.m_tops.put(Pair.EUR_USD, match(matcher, 3));
                        data.m_tops.put(Pair.LTC_BTC, match(matcher, 5));
                        return SECOND;
                    }
                }
                return FIRST;
            }
        },
        SECOND {
            public State process(String line, Data data, PreparedStatement insertStatement, PreparedStatement deleteStatement) throws SQLException {
                if (line.contains("LTC_USD=Top{bid=")) {
                    Matcher matcher = LINE_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        data.m_tops.put(Pair.LTC_USD, match(matcher, 1));
                        data.m_tops.put(Pair.BTC_USD, match(matcher, 3));
                        data.m_tops.put(Pair.LTC_EUR, match(matcher, 5));
                        save(insertStatement, deleteStatement,  data);
                        return START;
                    }
                }
                throw new RuntimeException("error");
            }
        };

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
