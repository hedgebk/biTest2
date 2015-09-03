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
                        // BTN.fireActionPerformed: java.awt.event.ActionEvent[ACTION_PERFORMED,cmd=null,when=1441149624139,modifiers=Button1] on javax.swing.DefaultButtonModel@59b23128

                    }
                });
                topPanel.add(new JCheckBox("osc") {
                    @Override protected void fireItemStateChanged(ItemEvent event) {
                        super.fireItemStateChanged(event);
                        log("osc.fireItemStateChanged: " + event);
                        // osc.fireItemStateChanged: java.awt.event.ItemEvent[ITEM_STATE_CHANGED,item=javax.swing.JToggleButton$ToggleButtonModel@47738e30,stateChange=SELECTED] on javax.swing.JToggleButton$ToggleButtonModel@47738e30
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
