package bthdg;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;

public class DbReady {

    private static final String DELETE_TICKS_BETWEEN_SQL = "DELETE FROM Ticks WHERE src = ? AND stamp > ? AND stamp < ?";
    public static final int IMPORT_DAYS = 1 * 30;
    private static final int LOG_IMPORT_STAT_DELAY = 15000; // log import stat each X ms
    private static final String INSERT_TICKS_SQL = "INSERT INTO Ticks ( src, stamp, price, volume ) VALUES (?,?,?,?)";

    public static void main(String[] args) {
        System.out.println("Started");
        long millis = logTimeMemory();

//        startAndInitDb();
//        dropTicks(Exchange.BTCE,     "0", "-13d");
//        dropTicks(Exchange.BITSTAMP, "0", "-100d");
        importFromFiles();

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    public static long logTimeMemory() {
        long millis = System.currentTimeMillis();
        System.out.println("timeMills: " + millis);
        long maxMemory = Runtime.getRuntime().maxMemory();
        System.out.println("maxMemory: " + maxMemory + ", k:" + (maxMemory /= 1024) + ": m:" + (maxMemory /= 1024));
        return millis;
    }

    public static void startAndInitDb() {
        IDbRunnable runnable = new IDbRunnable() {
            public void run(Connection connection) throws SQLException {
                System.out.println("-- Creating Tables (if needed) --");
                Statement statement = connection.createStatement();
                try {
                    int ret;
                    ret = statement.executeUpdate("DROP TABLE Ticks");
                    System.out.println("--- DROP TABLE Ticks  returns " + ret);

                    ret = statement.executeUpdate(
                            "CREATE TABLE IF NOT EXISTS Ticks ( " +
                            " src       INTEGER NOT NULL, " +
                            " stamp     BIGINT NOT NULL, " +
                            " price     DOUBLE," +
                            " volume    DOUBLE )");
                    System.out.println("--- CREATE TABLE Ticks  returns " + ret);

                    ret = statement.executeUpdate("CREATE INDEX srst on Ticks (src, stamp)");
                    System.out.println("--- CREATE INDEX srst on Ticks  returns " + ret);

                    //------------------------------------------------------------------

//                    ret = statement.executeUpdate("DROP TABLE Trace");
//                    System.out.println("--- DROP TABLE Trace  returns " + ret);

//                    ret = statement.executeUpdate(
//                            "CREATE TABLE IF NOT EXISTS Trace ( " +
//                            " stamp BIGINT NOT NULL, " +
//                            " bid1  DOUBLE, " +
//                            " ask1  DOUBLE, " +
//                            " bid2  DOUBLE, " +
//                            " ask2  DOUBLE, " +
//                            " fork  BIGINT NOT NULL, " +
//                            " buy1  DOUBLE, " +
//                            " sell1 DOUBLE, " +
//                            " buy2  DOUBLE, " +
//                            " sell2 DOUBLE" +
//                            ")");
//                    System.out.println("--- CREATE TABLE Trace  returns " + ret);

                    //------------------------------------------------------------------

//                    ret = statement.executeUpdate("DROP TABLE TraceTrade");
//                    System.out.println("--- DROP TABLE TraceTrade  returns " + ret);

//                    ret = statement.executeUpdate(
//                            "CREATE TABLE IF NOT EXISTS TraceTrade ( " +
//                            " stamp   BIGINT NOT NULL, " +
//                            " exch    INTEGER NOT NULL, " +
//                            " side    VARCHAR(4), " +
//                            " price   DOUBLE, " +
//                            " amount  DOUBLE, " +
//                            " crossId BIGINT, " +
//                            " forkId  BIGINT" +
//                            ")");
//
//                    System.out.println("--- CREATE TABLE TraceTrade  returns " + ret);

                    // CREATE INDEX srst on sakila.Ticks (src, stamp);
                    // DROP INDEX srst ON sakila.Ticks;

// mysqlshow -u root -p
// mysql -u root -p

// ALTER TABLE tbl_name MAX_ROWS=1000000000 AVG_ROW_LENGTH=nnn;
//  You have to specify AVG_ROW_LENGTH only for tables with BLOB or TEXT columns;

// You can check the maximum data and index sizes by using this statement:
// SHOW TABLE STATUS FROM db_name LIKE 'tbl_name';
//  SHOW TABLE STATUS FROM sakila LIKE 'Ticks'

                } finally {
                    statement.close();
                }
                System.out.println("--- Complete ---");
            }
        };
        if (goWithDb(runnable)) {
            return;
        }
        System.out.println("Finished");
    }

    public static void importFromFiles() {
        IDbRunnable runnable = new IDbRunnable() {
            public void run(Connection connection) throws SQLException {
                System.out.println("-- importFromFiles --");
                importFromFiles(connection);
                System.out.println("--- Complete ---");
            }
        };
        if (goWithDb(runnable)) {
            return;
        }
        System.out.println("Finished");
    }

    private static Connection createConnection() {
        Connection connection;
        try {
            // move params to config file
            String username = "root";
            String password = "bitcoin";
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/sakila", username, password); // profileSQL=true
        } catch (SQLException e) {
            System.out.println("error: " + e);
            e.printStackTrace();
            connection = null;
        }
        return connection;
    }

    public static boolean goWithDb(IDbRunnable runnable) {
        if (init()) {
            return true;
        }
        Connection connection = createConnection();
        if(connection == null) {
            return true;
        }
        try {
            try {
                runnable.run(connection);
            } finally {
                connection.close();
            }
        } catch (SQLException e) {
            System.out.println("goWithDb error: " + e);
            e.printStackTrace();
            return true;
        }
        return false;
    }

    private static boolean init() {
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
            return true;
        }
        return false;
    }

    protected static void dropTicks(final Exchange exchange, final String from, final String to) {
        final long fromMillis = Utils.toMillis(from);
        final long toMillis = Utils.toMillis(to);

        goWithDb(new IDbRunnable() {
            public void run(Connection connection) throws SQLException {
                dropTicks(connection, exchange, fromMillis, toMillis);
            }
        });
    }

    private static void dropTicks(Connection connection, Exchange exchange, long fromMillis, long toMillis) throws SQLException {
        long from = fromMillis;
        long to = toMillis;
        if( from > to ) {
            long swap = from;
            from = to;
            to = swap;
        }
        PreparedStatement pStatement = connection.prepareStatement(DELETE_TICKS_BETWEEN_SQL);
        try {
            System.out.println("deleting last ticks from '" + new java.util.Date(from) + "' to '" + new java.util.Date(to) + "' on "+exchange);
            pStatement.setInt(1, exchange.m_databaseId); // src
            pStatement.setLong(2, from);
            pStatement.setLong(3, to);
            int deleted = pStatement.executeUpdate();
            System.out.println("deleted " + deleted);
        } finally {
            pStatement.close();
        }
    }

    private static void importFromFiles(Connection connection) {
        try {
            connection.setAutoCommit(false); // for fast inserts/updates
//            importExchange(connection, Exchange.BTCE);
            importExchange(connection, Exchange.BITSTAMP);
//            importExchange(connection, Exchange.BITFINEX);
//            importExchange(connection, Exchange.CAMPBX);
//            importExchange(connection, Exchange.HITBTC);
//            importExchange(connection, Exchange.LAKEBTC);
//            importExchange(connection, Exchange.ITBIT);
//            importExchange(connection, Exchange.BTCN);
//            importExchange(connection, Exchange.OKCOIN);
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
    }

    private static void importExchange(Connection connection, Exchange exchange) throws SQLException, IOException, ParseException {
        System.out.println("importExchange: " + exchange.m_name);
        InputStream is = new FileInputStream(exchange.m_bitcoinchartsSymbol + ".csv");
        int available = is.available();
        // returns CSV: unixtime,price,amount
        BufferedInputStream bis = new BufferedInputStream(is);
        try {
            long start = System.currentTimeMillis();
            long oldestTickTime = start - IMPORT_DAYS * Utils.ONE_DAY_IN_MILLIS;
            readData(connection, exchange, available, bis, oldestTickTime);
        } finally {
            bis.close();
        }
        System.out.println("importExchange DONE: " + exchange.m_name);
    }

    static int readData(Connection connection, Exchange exchange, int available, BufferedInputStream bis, long oldestTickTime) throws SQLException, IOException, ParseException {
        PreparedStatement statement = connection.prepareStatement(INSERT_TICKS_SQL);
        //Statement statement = connection.createStatement();
        try {
            return readData(bis, available, connection, statement, exchange, oldestTickTime);
        } finally {
            System.out.println(" committing...");
            connection.commit();
            statement.close();
        }
    }

    private static int readData(BufferedInputStream bis, long totalBytes,
                                Connection connection, PreparedStatement preparedStatement,
                                Exchange exchange, long importFromTick) throws IOException, ParseException, SQLException {
        StringBuffer sb = new StringBuffer();
        int exchangeId = exchange.m_databaseId;

        DecimalFormat format = new DecimalFormat();
        DecimalFormatSymbols dfs = format.getDecimalFormatSymbols();
        dfs.setDecimalSeparator('.');
        format.setDecimalFormatSymbols(dfs);

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        int counter = 0;
        int totalCounter = 0;
        int totalSaved = 0;
        long dataRead = 0;
        long firstRecordDataRead = 0;
        long start = System.currentTimeMillis();
        long lastStat = System.currentTimeMillis();
        while (true) {
            String token = readToken(bis, sb);
            if (token.length() == 0) {
                long millis = System.currentTimeMillis();
                logStat(totalBytes, counter, totalCounter, totalSaved, dataRead, firstRecordDataRead, start, millis);

                System.out.println("Was readData " + counter + " ticks");
                System.out.println(" min time=" + min + "; max time=" + max + "; now=" + millis);
                java.util.Date minDate = new java.util.Date(min);
                java.util.Date maxDate = new java.util.Date(max);
                java.util.Date nowDate = new java.util.Date(millis);
                System.out.println(" minDate=" + minDate + "; maxDate=" + maxDate + "; nowDate=" + nowDate);
                break;
            }
            dataRead += token.length() + 1;
            long unixtime = Long.parseLong(token);
            unixtime *= 1000;
            if (unixtime > max) {
                max = unixtime;
            }
            if (unixtime < min) {
                min = unixtime;
            }

            token = readToken(bis, sb); // "804.000000000000"
            dataRead += token.length() + 1;
            double price = parseDouble(format, token);

            token = readToken(bis, sb); // "1.234"
            dataRead += token.length() + 1;
            double volume = parseDouble(format, token);

            if (unixtime >= importFromTick) { // save only ticks after start point
                if (totalSaved == 0) { // the first Tick recording
                    start = System.currentTimeMillis(); // save time
                    firstRecordDataRead = dataRead; // save byte start
                    System.out.println("Saving the first tick: bytes offset:" + dataRead + ", ticks skipped: " + totalCounter);
                    System.out.println(" tick timestamp: " + unixtime);
                }
//                statement.executeQuery(
//                                "INSERT INTO Ticks ( src, stamp, price, volume ) VALUES " +
//                                "      ( "+exchangeId+", "+unixtime+", "+price+", "+volume+" )");

                preparedStatement.setInt(1, exchangeId);
                preparedStatement.setLong(2, unixtime);
                preparedStatement.setDouble(3, price);
                preparedStatement.setDouble(4, volume);
                preparedStatement.executeUpdate(); // execute insert SQL statement

                totalSaved++;
            }

//            System.out.println("Readed: unixtime="+unixtime+", price="+price+", volume="+volume);
            counter++;
            totalCounter++;

            long millis = System.currentTimeMillis();
            if (millis - lastStat > LOG_IMPORT_STAT_DELAY) {
                logStat(totalBytes, counter, totalCounter, totalSaved, dataRead, firstRecordDataRead, start, millis);
                counter = 0;

                System.out.print(" committing...");
                connection.commit();
                long millis2 = System.currentTimeMillis();
                System.out.println(" commit done in " + Utils.millisToDHMSStr(millis2 - millis));

                lastStat = millis2;
            }
        }
        return totalSaved;
    }

    private static double parseDouble(DecimalFormat format, String token) throws ParseException {
        try {
            return format.parse(token).doubleValue();
        } catch (ParseException e) {
            System.out.println("Error: " + e);
            return 0.0;
        }
    }

    private static void logStat(long totalBytes, int counter, int totalCounter, int totalSaved, long dataRead,
                                long firstRecordDataRead, long start, long millis) {
        double percent = dataRead * 100.0 / totalBytes;
        long dataRemainedToRead = totalBytes - dataRead;
        long dataReadAfterFirstRecord = dataRead - firstRecordDataRead + 1;
        long storingDataTime = millis - start;
        long remainedTime = storingDataTime * dataRemainedToRead / dataReadAfterFirstRecord;
        System.out.println("Was read " + counter + " ticks, " +
                "total read "+totalCounter+" ticks. " +
                "total saved "+totalSaved+" ticks. " +
                percent + "% done. " +
                "remained " + Utils.millisToDHMSStr(remainedTime) );
    }

    private static String readToken(BufferedInputStream bis, StringBuffer sb) throws IOException {
        while(true) {
            int read = bis.read();
            if(read == -1) {
                break; // EOF
            }
            if((read == '\n') || (read == ',')){
                break;
            }
            sb.append((char)read);
        }
        String ret = sb.toString();
        sb.setLength(0); // clear buffer for further usage
        return ret;
    }

    public interface IDbRunnable {
        void run(Connection connection) throws SQLException;
    }

}
