/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*                                       
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*                                       
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*                                       
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*                                       
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*                                       
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/


package ca.nrc.cadc.dlm.client;

import ca.nrc.cadc.net.HttpDownload;
import ca.nrc.cadc.net.event.ProgressListener;
import ca.nrc.cadc.net.event.TransferEvent;
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
import org.apache.log4j.Logger;

/**
 * Simple UI to show progress of a Download task.
 *
 * @author pdowler
 */
public class JDownload extends JPanel implements ProgressListener, Runnable
{
    private static final long serialVersionUID = 201008051500L;
    private static Logger log = Logger.getLogger(JDownload.class);
    
    private boolean showBorder = false;
    
    private static Color COMPLETED_COLOR = Color.BLACK;
    private static Color FAILED_COLOR = Color.RED;
    private static Color CANCELLED_COLOR = Color.GRAY;
    
    private HttpDownload dl;
        
    //private String status;
    private JTextArea textPane; // main result display so user can copy and paste it
    private JLabel transLabel;  // transient label for rapid update of content during download
    private JProgressBar progress;
    private JButton cancelButton;
    private Border spaceBorder;
    
    private String totalStr;
    private long startTime = 0;
    private long lastUpdate = 0;
    private long totalBytes;
    private long startingPos;
    private long totalSize = -1;
    
    // for instananeous rate
    private long dtSinceLastUpdate;
    private long bytesSinceLastUpdate;
    private long dtSinceLastUpdate2;
    private long bytesSinceLastUpdate2;
    
    private TransferEvent lastEvent;

    public JDownload(HttpDownload dl)
    { 
        super(new BorderLayout());
        this.dl = dl;
        dl.setProgressListener(this);

        //if (dl.label == null && dl.url != null)
        //    dl.label = dl.url.toString();
 
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
        textPane.setText(dl.getURL().toString());
        transLabel.setText("Queued");
    }
    
    public TransferEvent getLastEvent() { return lastEvent; }
    
    public void cancel()
    {
        if (dl != null)
            dl.terminate();
    }

    // TransferListener
    public void transferEvent(TransferEvent e)
    {
        this.lastEvent = e;
        SwingUtilities.invokeLater(new UpdateUI(e));
    }
    
    public String getEventHeader() { return null; }
    
    private class UpdateUI implements Runnable
    {
        TransferEvent e;
        UpdateUI(TransferEvent e) { this.e = e; }
        public void run()
        {
            //log.debug("UpdateUI: " + e);
            switch(e.getState())
            {
                case TransferEvent.CONNECTING:
                    //status = "Connecting...";
                    transLabel.setText("Connecting...");
                    progress.setIndeterminate(true);
                    break;
                    
                case TransferEvent.CONNECTED:
                    //status = "Connected";
                    transLabel.setText("Connected");
                    break;

                case TransferEvent.RETRYING:
                    transLabel.setText("Server busy, sleeping...");
                    break;

                case TransferEvent.TRANSFERING:
                case TransferEvent.DECOMPRESSING:
                    JDownload.this.startingPos = e.getStartingPosition();
                    if (e.getState() == TransferEvent.TRANSFERING)
                    {
                        StringBuffer sb = new StringBuffer();
                        sb.append("downloading");
                        //sb.append(dl.getURL().toString());
                        sb.append(" -> ");
                        sb.append(e.getFile().getAbsolutePath());
                        textPane.setText(sb.toString());
                        totalSize = dl.getContentLength();
                    }
                    else
                    {
                        textPane.setText("decompressing -> " + e.getFile().getAbsolutePath());
                        totalSize = dl.getSize();
                    }
                    if (totalSize > 0)
                    {
                        totalStr = " of " + sizeToString(dl.getSize(), 0);
                        progress.setIndeterminate(false);
                        progress.setMinimum(0);
                        progress.setMaximum(100);
                    }
                    else
                        progress.setIndeterminate(true);
                    validate();
                    break;
                    
                case TransferEvent.FAILED:
                    // switch to textPane so displayed result is selectable
                    textPane.setText(dl.getURL() + "\nfailed: " +  e.getError().getMessage());
                    textPane.setForeground(FAILED_COLOR);
                    validate();
                    break;

                case TransferEvent.CANCELLED:
                    transLabel.setText("");
                    textPane.setText(dl.getURL() + "\ncancelled");
                    textPane.setForeground(CANCELLED_COLOR);
                    textPane.setFont(transLabel.getFont()); // keep same font
                    if (showBorder)
                    {
                        LineBorder lineBorder = new LineBorder(Color.GRAY, 1, true);
                        setBorder(BorderFactory.createCompoundBorder(spaceBorder, lineBorder));
                    }
                    validate();
                    break;
                    
                case TransferEvent.COMPLETED:
                    double dt = (System.currentTimeMillis() - startTime) / 1000.0; // seconds
                    if (dt == 0.0)
                        dt = 1.0;
                    StringBuffer sb = new StringBuffer();
                    sb.append(dl.getURL().toString());
                    sb.append(" -> ");
                    sb.append(e.getFile().getAbsolutePath());
                    sb.append("\ncompleted ");
                    sb.append(sizeToString(totalBytes, 0));
                    sb.append(" in ");
                    sb.append(Double.toString(dt));
                    sb.append("sec (");
                    sb.append(sizeToString((totalBytes-startingPos), dt));
                    sb.append(")");
                    transLabel.setText("");
                    textPane.setForeground(COMPLETED_COLOR);
                    textPane.setText(sb.toString());
                    textPane.setFont(transLabel.getFont()); // keep same font
                    validate();
                    break;

                default:
                    log.warn("unexpected event: " + e.getStateLabel());
            }
            // handle button and progress bar separately
            switch(e.getState())
            {
                case TransferEvent.FAILED:
                case TransferEvent.CANCELLED:
                case TransferEvent.COMPLETED:
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
    public void update(long newBytes, long totalBytes)
    {
        this.totalBytes = totalBytes;
        if (startTime == 0)
            startTime = System.currentTimeMillis();
            
        // try to not fire too many UI updates into the EventQueue
        long t = System.currentTimeMillis();
        this.dtSinceLastUpdate = t - lastUpdate;
        this.bytesSinceLastUpdate += newBytes;
        if (dtSinceLastUpdate > 333) // milliseconds
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
        {
            int percentComplete = (int) (100*totalBytes / totalSize);
            progress.setValue(percentComplete);
        }
        
        String s = sizeToString(totalBytes, 0);
        if (totalStr != null)
            s += totalStr;
        
        // average rate
        //String rate = sizeToString((totalBytes-startingPos), dt);
        
        // instantaneous rate
        String rate = sizeToString(bytesSinceLastUpdate2, dtSinceLastUpdate2/1000.0);

        if (totalBytes == totalSize) // complete
            rate = sizeToString(totalBytes, dt);
        
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
        if (dt > 0)
            val /= dt;
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
            //val /= dt;
            units = units + "/sec";
        }

        return format.format(val) + units;
    }
        
    public class CancelAction extends AbstractAction
    {
        public CancelAction() { super("Cancel"); }

        public void actionPerformed(ActionEvent e) { cancel(); }
    }
}
