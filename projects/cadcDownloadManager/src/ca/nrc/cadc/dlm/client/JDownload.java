/*****************************************************************************
 *  
 *  Copyright (C) 2009				Copyright (C) 2009
 *  National Research Council		Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6			Ottawa, Canada, K1A 0R6
 *  All rights reserved				Tous droits reserves
 *  					
 *  NRC disclaims any warranties,	Le CNRC denie toute garantie
 *  expressed, implied, or statu-	enoncee, implicite ou legale,
 *  tory, of any kind with respect	de quelque nature que se soit,
 *  to the software, including		concernant le logiciel, y com-
 *  without limitation any war-		pris sans restriction toute
 *  ranty of merchantability or		garantie de valeur marchande
 *  fitness for a particular pur-	ou de pertinence pour un usage
 *  pose.  NRC shall not be liable	particulier.  Le CNRC ne
 *  in any event for any damages,	pourra en aucun cas etre tenu
 *  whether direct or indirect,		responsable de tout dommage,
 *  special or general, consequen-	direct ou indirect, particul-
 *  tial or incidental, arising		ier ou general, accessoire ou
 *  from the use of the software.	fortuit, resultant de l'utili-
 *  								sation du logiciel.
 *  
 *  
 *  This file is part of cadcDownloadManager.
 *  
 *  CadcDownloadManager is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  CadcDownloadManager is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcDownloadManager.  If not, see <http://www.gnu.org/licenses/>.			
 *  
 *****************************************************************************/

package ca.nrc.cadc.dlm.client;

import ca.nrc.cadc.dlm.client.event.ProgressListener;
import ca.nrc.cadc.dlm.client.event.DownloadEvent;
import ca.nrc.cadc.dlm.client.event.DownloadListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.text.DecimalFormat;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;

/**
 * Simple UI to show progress of a Download task.
 *
 * @author pdowler
 */
public class JDownload extends JPanel implements DownloadListener, ProgressListener, Runnable
{
    private boolean debug = false;
    private boolean showBorder = false;
    
    private static Color COMPLETED_COLOR = Color.BLACK;
    private static Color FAILED_COLOR = Color.RED;
    private static Color CANCELLED_COLOR = Color.GRAY;
    
    private Download dl;
        
    //private String status;
    private JTextArea textPane; // main result display so user can copy and paste it
    private JLabel transLabel;  // transient label for rapid update of content during download
    private JProgressBar progress;
    private JButton cancelButton;
    private Border spaceBorder;
    
    private String totalStr;
    private long startTime = 0;
    private long lastUpdate = 0;
    private int newBytes;
    private int totalBytes;
    private int startingPos;
    
    // for instananeous rate
    private long dtSinceLastUpdate;
    private int bytesSinceLastUpdate;
    private long dtSinceLastUpdate2;
    private int bytesSinceLastUpdate2;
    
    private DownloadEvent lastEvent;

    public JDownload(Download dl) 
    { 
        super(new BorderLayout());
        this.dl = dl;
        dl.setProgressListener(this);

        if (dl.label == null && dl.url != null)
            dl.label = dl.url.toString();
 
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        this.transLabel = new JLabel();
        transLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        this.textPane = new JTextArea();
        textPane.setEditable(false);
        textPane.setFont(transLabel.getFont()); // keep same font
        this.progress = new JProgressBar();
        progress.setMinimum(0);
        progress.setMaximum(100);
        
        content.add(textPane, BorderLayout.CENTER);
        
        // put progress bar in a subpanel so it doesn't stretch to same width as the transLabel/textPane
        JPanel dynContent = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dynContent.add(progress, BorderLayout.WEST);
        dynContent.add(transLabel, BorderLayout.CENTER);
        content.add(dynContent, BorderLayout.SOUTH);

        // need subpanel so button takes a natural size inside it
        JPanel cb = new JPanel();
        this.cancelButton = new JButton(new CancelAction());
        cb.add(cancelButton);
        
        this.add(cb, BorderLayout.WEST);
        this.add(content, BorderLayout.CENTER);

        this.spaceBorder = BorderFactory.createEmptyBorder(4, 4, 4, 4);
        if (showBorder)
        {
            LineBorder lineBorder = new LineBorder(Color.BLACK, 1, true);
            this.setBorder(BorderFactory.createCompoundBorder(spaceBorder, lineBorder)); 
        }
        else
            this.setBorder(spaceBorder);
        
        // starting content
        textPane.setText(dl.label);
        transLabel.setText("Queued");
    }
    
    public void setDebug(boolean debug) { this.debug = debug; }
    
    public DownloadEvent getLastEvent() { return lastEvent; }
    
    public void cancel()
    {
        if (dl != null)
            dl.terminate();
    }

    // DownloadListener
    public void downloadEvent(DownloadEvent e)
    {
        msg("downloadEvent: " + e);
        this.lastEvent = e;
        SwingUtilities.invokeLater(new UpdateUI(e));
    }
    
    public String getEventHeader() { return null; }
    
    private class UpdateUI implements Runnable
    {
        DownloadEvent e;
        UpdateUI(DownloadEvent e) { this.e = e; }
        public void run()
        {
            msg("UpdateUI: " + e);
            switch(e.getState())
            {
                case DownloadEvent.CONNECTING:
                    //status = "Connecting...";
                    transLabel.setText("Connecting...");
                    progress.setIndeterminate(true);
                    break;
                    
                case DownloadEvent.CONNECTED:
                    //status = "Connected";
                    transLabel.setText("Connected");
                    break;
                    
                case DownloadEvent.DOWNLOADING:
                case DownloadEvent.DECOMPRESSING:
                    JDownload.this.startingPos = e.getStartingPosition();
                    if (e.getState() == DownloadEvent.DECOMPRESSING)
                        textPane.setText("decompressing " + dl.label);
                    
                    transLabel.setText("");
                    if (dl.getSize() > 0)
                    {
                        totalStr = " of " + sizeToString(dl.getSize(), 0);
                        progress.setIndeterminate(false);
                        progress.setMinimum(0);
                        progress.setMaximum(dl.getSize());
                    }
                    else
                        progress.setIndeterminate(true);
                    break;
                    
                case DownloadEvent.FAILED:
                case DownloadEvent.CANCELLED:
                    if (e.getState() == DownloadEvent.FAILED)
                    {
                        String status = "failed: " + e.getError().getMessage();

                        // switch to textPane so displayed result is selectable
                        textPane.setText(dl.label + "\n" + status);
                        textPane.setForeground(FAILED_COLOR);
                    }
                    else
                    {
                        //status = "cancelled";
                        
                        // switch to textPane so displayed result is selectable
                        textPane.setText(dl.label + "\ncancelled");
                        textPane.setForeground(CANCELLED_COLOR);
                        textPane.setFont(transLabel.getFont()); // keep same font
                        if (showBorder)
                        {
                            LineBorder lineBorder = new LineBorder(Color.GRAY, 1, true);
                            setBorder(BorderFactory.createCompoundBorder(spaceBorder, lineBorder));
                        }
                    }
                    validate();
                    break;
                case DownloadEvent.COMPLETED:
                    double dt = (System.currentTimeMillis() - startTime) / 1000.0; // seconds
                    if (dt == 0.0)
                        dt = 1.0;
                    String name = dl.label + " -> " + e.getFile();
                    String status = "completed " + sizeToString(totalBytes, 0) + " in " + dt + "sec (" + sizeToString((totalBytes-startingPos), dt) + ")";
                    
                    // switch to textPane so displayed result is selectable
                    textPane.setForeground(COMPLETED_COLOR);
                    textPane.setText(name + "\n" + status);
                    textPane.setFont(transLabel.getFont()); // keep same font
                    validate();
                    break;
            }
            // handle button and progress bar separately
            switch(e.getState())
            {
                case DownloadEvent.FAILED:
                case DownloadEvent.CANCELLED:
                case DownloadEvent.COMPLETED:
                    if (cancelButton != null)
                    {
                        // disable and remove
                        cancelButton.setEnabled(false);
                        cancelButton.setVisible(false);
                        cancelButton.getParent().remove(cancelButton);
                        cancelButton = null;
                    }
                    if (progress != null)
                    {
                        // disable animation and remove
                        progress.setIndeterminate(false);
                        progress.setVisible(false);
                        progress.getParent().remove(progress);
                        progress = null;
                    }
                    if (transLabel != null)
                    {
                        // disable and remove
                        transLabel.setVisible(false);
                        transLabel.getParent().remove(transLabel);
                        transLabel = null;
                    }
                    validate();
            }
            repaint();
        }
    }
    
    // ProgressListener
    public void update(int newBytes, int totalBytes)
    {
        this.newBytes = newBytes;
        this.totalBytes = totalBytes;
        if (startTime == 0)
            startTime = System.currentTimeMillis();
            
        // try to not fire too many UI updates into the EventQueue
        long t = System.currentTimeMillis();
        this.dtSinceLastUpdate = t - lastUpdate;
        this.bytesSinceLastUpdate += newBytes;
        if (dtSinceLastUpdate > 1000) // milliseconds
        {
            this.dtSinceLastUpdate2 = this.dtSinceLastUpdate; // copy values to "back-buffer" for rendering in run()
            this.bytesSinceLastUpdate2 = this.bytesSinceLastUpdate;
            this.bytesSinceLastUpdate = 0;
            this.lastUpdate = t;
            SwingUtilities.invokeLater(this);
        }
    }
    
    // for use in SwingUtilities.invokeLater from update()
    public void run()
    {
        if (transLabel == null)
            return; // already failed/cancelled by the time this update ran

        double dt = (System.currentTimeMillis() - startTime) / 1000.0; // seconds
        if (dt == 0.0)
            dt = 1.0;
        
        if (progress != null && !progress.isIndeterminate() && totalBytes > 0)
            progress.setValue(totalBytes);
        
        String s = sizeToString(totalBytes, 0);
        if (totalStr != null)
            s += totalStr;
        
        // average rate
        //String rate = sizeToString((totalBytes-startingPos), dt);
        
        // instantaneous rate
        String rate = sizeToString(bytesSinceLastUpdate2, dtSinceLastUpdate2/1000.0);
        
        s += " (" + rate + ")";
        //status = s;
        transLabel.setText(s);
        validate();
    }
        
    private static String  b = "bytes";
    private static String kb = "KB";
    private static String mb = "MB";
    private static String gb = "GB";
    private static DecimalFormat format = new DecimalFormat("#.#");
    
    // convert number of bytes to a nicer size, optionally a rate if dt > 0.
    // val in bytes
    // dt in seconds
    private String sizeToString(double val, double dt)
    {
        String units = b;
        if (val > 1024.0)
        {
            units = kb;
            val /= 1024.0;
        }
        if (val > 1024.0)
        {
            units = mb;
            val /= 1024.0;
        }
        // no one gets downloads fast enough to warrant GB progress indicator
        //if (val > 1024.0)
        //{
        //    units = gb;
        //    val /= 1024.0;
        //}
        
        // convert to a rate
        if (dt > 0)
        {
            val /= dt;
            units = units + "/sec";
        }

        return format.format(val) + units;
    }
        
    public class CancelAction extends AbstractAction
    {
        public CancelAction() { super("Cancel"); }

        public void actionPerformed(ActionEvent e) { cancel(); }
    }
    
    // debug messages
    private void msg(String s)
    {
        if (debug) System.out.println("[JDownload] " + s);
    }
}
