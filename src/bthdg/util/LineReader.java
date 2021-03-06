package bthdg.util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class LineReader {
    private static final int MAX_HOLD_BACK_LINES_NUM = 50;


    private final InputStream m_is;
    private final BufferedReader m_bis;
    private final List<String> m_buffer = new ArrayList<String>();
    public int m_linesReaded = 0;

    public LineReader(String logFile) throws FileNotFoundException {
        this(new File(logFile));
    }

    public LineReader(File file) throws FileNotFoundException {
        this(new FileInputStream(file));
    }

    public LineReader(InputStream is) {
        m_is = is;
        m_bis = new BufferedReader(new InputStreamReader(is));
    }

    public LineReader(File file, int bufferSize) throws FileNotFoundException {
        this(new FileInputStream(file), bufferSize);
    }

    public LineReader(InputStream is, int bufferSize) {
        m_is = is;
        m_bis = new BufferedReader(new InputStreamReader(is), bufferSize);
    }

    public void close() throws IOException {
        m_is.close();
        m_bis.close();
    }

    public synchronized String getLine(int index) throws IOException {
        if (index < m_buffer.size()) {
            return m_buffer.get(index);
        }
        return readLine();
    }

    private String readLine() throws IOException {
        String line = m_bis.readLine();
        if (line != null) {
            m_buffer.add(line);
            m_linesReaded++;

            if(m_linesReaded > MAX_HOLD_BACK_LINES_NUM) {
                int i = m_linesReaded - MAX_HOLD_BACK_LINES_NUM;
                m_buffer.set(i, null); // do not hold too much back lines
            }
        }
        return line;
    }
}
