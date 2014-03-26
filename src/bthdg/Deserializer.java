package bthdg;

import bthdg.duplet.PairExchangeData;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

public class Deserializer {
    public static final int MAX_BUF_LEN = 128;

    private final PushbackReader m_reader;
    private final char[] m_buffer = new char[MAX_BUF_LEN];

    private Deserializer(String serialized) {
        StringReader stringReader = new StringReader(serialized);
        m_reader = new PushbackReader(stringReader, 100);
    }

    public static PairExchangeData deserialize(String serialized) throws IOException {
        Deserializer deserializer = new Deserializer(serialized);
        try {
            return deserializer.deserialize();
        } finally {
            deserializer.close();
        }
    }

    private void close() throws IOException {
        m_reader.close();
    }

    private PairExchangeData deserialize() throws IOException {
        PairExchangeData ret = PairExchangeData.deserialize(this);
        return ret;
    }

    public void readObjectStart(String str) throws IOException {
        readStr(str);
        char ch = (char) m_reader.read();
        if (ch != '[') {
            throw new IOException("Left square bracket expected. but have " + Character.toString(ch));
        }
    }

    public void readPropStart(String str) throws IOException {
        readStr(str);
        char ch = (char) m_reader.read();
        if (ch != '=') {
            throw new IOException("Equals sign expected. but have " + Character.toString(ch));
        }
    }

    public void readStr(String str) throws IOException {
        int length = str.length();
        int read = m_reader.read(m_buffer, 0, length);
        if(read != length) {
            throw new IOException("not enough bytes to read str '"+str+"'");
        }
        for( int i = 0; i < length; i++ ) {
            char ch1 = str.charAt(i);
            char ch2 = m_buffer[i];
            if(ch1 != ch2) {
                throw new IOException("Expected to read str '"+str+"', but got '"+new String(m_buffer, 0, length)+"'");
            }
        }
    }

    public boolean readIf(String str) throws IOException {
        int length = str.length();
        int read = m_reader.read(m_buffer, 0, length);
        if(read != length) {
            if(read > 0) {
                m_reader.unread(m_buffer, 0, read);
            }
            return false;
        }
        for( int i = 0; i < length; i++ ) {
            char ch1 = str.charAt(i);
            char ch2 = m_buffer[i];
            if(ch1 != ch2) {
                m_reader.unread(m_buffer, 0, read);
                return false;
            }
        }
        return true;
    }

    public String readTill(String str) throws IOException {
        int length = str.length();
        int read = m_reader.read(m_buffer, 0, length);
        if(read != length) {
            throw new IOException("not enough bytes to read till '"+str+"'");
        }
        int pos = 0;
        while(pos + length < MAX_BUF_LEN) {
            boolean matched = true;
            for( int i = 0; i < length; i++ ) {
                char ch1 = str.charAt(i);
                char ch2 = m_buffer[pos + i];
                if(ch1 != ch2) {
                    matched = false;
                    break;
                }
            }
            if(matched) {
                return new String(m_buffer, 0, pos);
            }
            char ch = (char) m_reader.read();
            m_buffer[pos+length] = ch;
            pos++;
        }
        throw new IOException("Not matched '"+str+"', but got '"+new String(m_buffer, 0, pos + length)+"'");
    }

    public void readObjectStart() throws IOException {
        readStr("[");
    }

    public void readObjectEnd() throws IOException {
        readStr("]");
    }

    public Map<String, String> readMap() throws IOException {
        // map=[1392823612790=624.9; 1392823670262=624.325; 1392823679748=624.325; ]
        readObjectStart();
        Map<String, String> ret = new HashMap<String, String>();
        while(true) {
            if(readIf("]")) {
                return ret;
            }
            String key = readTill("=");
            String value = readTill("; ");
            ret.put(key, value);
        }
    }
}
