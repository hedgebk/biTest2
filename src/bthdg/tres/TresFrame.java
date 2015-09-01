package bthdg.tres;

import bthdg.Log;
import bthdg.util.Snoozer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class TresFrame extends JFrame implements Runnable {
    private final Tres m_tres;
    private final Snoozer m_snoozer;
    private final JLabel m_label;
    private final TresCanvas m_canvas;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public TresFrame(Tres tres) throws java.awt.HeadlessException {
        m_tres = tres;
        m_snoozer = new Snoozer(500) {
            protected void wakeUp() {
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
        JPanel panel1 = new JPanel(new BorderLayout());
        {
            panel1.setBackground(Color.BLACK);
            JPanel topPanel = new JPanel(new FlowLayout());
            {
                topPanel.setBackground(Color.DARK_GRAY);
                m_label = new JLabel("...");
                topPanel.add(m_label);
                topPanel.add(new JButton("BTN") {
                    @Override protected void fireActionPerformed(ActionEvent event) {
                        super.fireActionPerformed(event);
                        log("BTN.fireActionPerformed: " + event);
                    }

                    @Override protected void fireItemStateChanged(ItemEvent event) {
                        super.fireItemStateChanged(event);
                        log("BTN.fireItemStateChanged: " + event);
                    }

                    @Override protected void fireStateChanged() {
                        super.fireStateChanged();
                        log("BTN.fireStateChanged");
                    }
                });
                topPanel.add(new JCheckBox("osc") {
                    @Override protected void fireStateChanged() {
                        super.fireStateChanged();
                        log("osc.fireStateChanged Selected=" + isSelected());
                    }

                    @Override protected void fireActionPerformed(ActionEvent event) {
                        super.fireActionPerformed(event);
                        log("osc.fireActionPerformed: " + event);
                    }

                    @Override protected void fireItemStateChanged(ItemEvent event) {
                        super.fireItemStateChanged(event);
                        log("osc.fireItemStateChanged: " + event);
                    }
                });
                topPanel.add(new JCheckBox("top"));
            }
            panel1.add(topPanel, BorderLayout.NORTH);
            m_canvas = new TresCanvas(m_tres);
            panel1.add(m_canvas, BorderLayout.CENTER);
        }
        add(panel1);
        pack();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                removeComponentListener(this);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override public void run() {
                        toFront();
                    }
                });
            }
        });
//        addWindowStateListener(new WindowAdapter() {
//            @Override public void windowOpened(WindowEvent e) {
//                removeWindowStateListener(this);
//                SwingUtilities.invokeLater(new Runnable() {
//                    @Override public void run() {
//                        toFront();
//                    }
//                });
//            }
//        });
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
        m_label.setText(m_tres.getState());
        m_canvas.repaint();
    }

}
