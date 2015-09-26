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
            @Override protected void wakeUp() {
                updateUI();
            }
        };
        m_canvas = new TresCanvas(m_tres);
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
                topPanel.add(new JButton("B") {
                    @Override protected void fireActionPerformed(ActionEvent event) {
                        super.fireActionPerformed(event);
                        log("BTN.fireActionPerformed: " + event);
                    }
                });
//                topPanel.add(new JCheckBox("top", true));
                topPanel.add(new JCheckBox("sym", false) {
                    @Override protected void fireItemStateChanged(ItemEvent event) {
                        super.fireItemStateChanged(event);
                        TresCanvas.m_paintSym = (event.getStateChange() == ItemEvent.SELECTED);
                        m_canvas.repaint();
                    }
                });
                topPanel.add(new JCheckBox("osc", false) {
                    @Override protected void fireItemStateChanged(ItemEvent event) {
                        super.fireItemStateChanged(event);
                        TresCanvas.m_paintOsc = (event.getStateChange() == ItemEvent.SELECTED);
                        m_canvas.repaint();
                    }
                });
                topPanel.add(new JCheckBox("coppock", false) {
                    @Override protected void fireItemStateChanged(ItemEvent event) {
                        super.fireItemStateChanged(event);
                        TresCanvas.m_paintCoppock = (event.getStateChange() == ItemEvent.SELECTED);
                        m_canvas.repaint();
                    }
                });
                topPanel.add(new JCheckBox("cci", false) {
                    @Override protected void fireItemStateChanged(ItemEvent event) {
                        super.fireItemStateChanged(event);
                        TresCanvas.m_paintCci = (event.getStateChange() == ItemEvent.SELECTED);
                        m_canvas.repaint();
                    }
                });
                tres.addControllers(topPanel, m_canvas);
            }
            panel1.add(topPanel, BorderLayout.NORTH);
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
