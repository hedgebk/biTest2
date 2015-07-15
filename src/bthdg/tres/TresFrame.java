package bthdg.tres;

import bthdg.Log;
import bthdg.util.Snoozer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class TresFrame extends JFrame implements Runnable {
    private final Tres m_tres;
    private final Snoozer m_snoozer;
    private final JLabel m_label;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public TresFrame(Tres tres) throws java.awt.HeadlessException {
        m_tres = tres;
        m_snoozer = new Snoozer(500) {
            protected void wakeUp() {
log("wakeUp");
                updateUI();
            }
        };
        setTitle("Tres");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                m_snoozer.stop();
            }
        });
        JPanel panel = new JPanel(new FlowLayout());
        {
            m_label = new JLabel("...");
            panel.add(m_label);
            panel.add(new JButton("BTN"));
        }
        add(panel);
        pack();
        toFront();
    }

    public void fireUpdated() { m_snoozer.update(); }

    public void stop() {
        m_snoozer.stop();
        dispose();
    }

    private void updateUI() {
        SwingUtilities.invokeLater(this);
    }

    @Override public void run() {
log("run ui update");
        m_label.setText(m_tres.getState());
    }
}
