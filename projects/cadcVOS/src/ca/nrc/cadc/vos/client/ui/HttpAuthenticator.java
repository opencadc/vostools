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


package ca.nrc.cadc.vos.client.ui;

import ca.nrc.cadc.net.NetrcFile;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.*;


/**
 * Simple Authenticator that pops up a custom dialog to get a username/password.
 * This implementation now caches the credentials in memory since OpenJDK does not
 * do so and users don't like seeing the same dialog box N times. If the auth is
 * HTTP BASIC this is not the biggest security problem, but is is less than optimal
 * so use with care.
 *
 * @author pdowler
 */
public class HttpAuthenticator extends Authenticator implements Runnable
{
    private static final Logger LOGGER =
            Logger.getLogger(HttpAuthenticator.class);

    private Component parent;
    private MyAuthDialog mad;

    // some recent JRE impls have tightened up the sync block so they can call
    // getPasswordAuthentication from multiple threads and we want to show the
    // minimal number of GUI dialogs: so we have to cache the uname/pword combo
    // inside a synchronized block as multiple threads doing http could be blocked
    // inside the getPasswordAuthentication method
    private Map<RequestingApp,PasswordAuthentication> authMap = new HashMap<RequestingApp,PasswordAuthentication>();

    // cache previous url -> credential look ups to detect when the creds were bad
    // weak: entries can be removed by the GC when the app is done with the URL
    private Map<URL,RequestingApp> prevAttempts = new WeakHashMap<URL,RequestingApp>();
    
    public HttpAuthenticator(final Component parent)
    { 
        super(); 
        this.parent = parent;

        LOGGER.debug("constructor");
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication()
    {
        synchronized(this) // http library does not have to synchronize usage
        {
            final RequestingApp ra = new RequestingApp();
            ra.host = getRequestingHost();
            ra.port = getRequestingPort();
            ra.prompt = getRequestingPrompt();
            ra.protocol = getRequestingProtocol();
            ra.scheme = getRequestingScheme();
            ra.url = getRequestingURL();

            LOGGER.debug("getPasswordAuthentication: " + ra);

            boolean retry = false;
            RequestingApp prev = prevAttempts.get(ra.url);
            if (prev != null) // already tried: assume creds were bad and clear cache
            {
                authMap.remove(prev);
                retry = true;
            }
            PasswordAuthentication pa = authMap.get(ra);
            if (pa == null)
            {
                pa = getCredentials(retry);
                if (pa != null)
                {
                    authMap.put(ra, pa);
                    // remove all old prevAttempts that refer to this RA
                    Iterator<Map.Entry<URL,RequestingApp>> iter = prevAttempts.entrySet().iterator();
                    while ( iter.hasNext() )
                    {
                        Map.Entry<URL,RequestingApp> me = iter.next();
                        if (me.getValue().equals(ra))
                        {
                            LOGGER.debug(
                                    "getPasswordAuthentication: removing " + me
                                            .getKey() + " , " + me.getValue());
                            iter.remove();
                        }
                    }
                }
            }

            if (pa == null)
                return null;

            prevAttempts.put(ra.url, ra);

            // return a deep copy in case the caller tries to clean up
            char[] pw = pa.getPassword();
            char[] pwcp = new char[pw.length];
            System.arraycopy(pw, 0, pwcp, 0, pw.length);
            LOGGER.debug("getPasswordAuthentication: ret = " + pa);
            return new PasswordAuthentication(pa.getUserName(), pwcp);
        }
    }

    private PasswordAuthentication lastResponse;
    private boolean doRetry;
    private PasswordAuthentication getCredentials(boolean retry)
    {
        this.doRetry = retry;
        if ( SwingUtilities.isEventDispatchThread() )
        {
            LOGGER.debug("getCredentials runs in the swing event thread");
            run();
        }
        else
            try
            {
                LOGGER.debug("getCredentials invokeAndWait in swing event thread");
                SwingUtilities.invokeAndWait(this);
            }
            catch(InterruptedException killed)
            {
                LOGGER.debug("getCredentials: interrupted");
                lastResponse = null;
            }
        catch(InvocationTargetException bug)
        {
            LOGGER.error("BUG: unexpected exception", bug);
            lastResponse = null;
        }

        PasswordAuthentication ret = lastResponse;
        this.lastResponse = null;
        this.doRetry = false;
        return ret;
    }

    public void run()
    {
        if (mad == null) // lazy init in case we never need it
            mad = new MyAuthDialog();
        mad.setRetry(doRetry);
        lastResponse = mad.getPasswordAuthentication(getRequestingHost(), getRequestingPrompt());
        mad.setRetry(false);
    }

    private class MyAuthDialog implements ActionListener
    {
        private int CANCEL = 1;
        private int OK = 2;
        private String RETRY_TEXT = "Credentials rejected by server, please try again";
        private String OK_TEXT = "OK";
        private String CANCEL_TEXT = "Cancel";
        private String NETRC_TEXT = "Read credentials from .netrc file";
        
        private int retval;
        private String host;
        private JLabel hostLabel, promptLabel, iconLabel, retryLabel;
        private JDialog dialog;
        private JTextField unField;
        private JPasswordField pwField;
        private JCheckBox netrcBox1;
        private JButton okButton, cancelButton;

        private boolean retry;

        MyAuthDialog() 
        {
            LOGGER.debug("constrcutor: MyAuthDialog");
        }

        public void setRetry(boolean retry) { this.retry = retry; }
        
        public PasswordAuthentication getPasswordAuthentication(String host, String prompt)
        {
            try
            {
                if ( dialog != null && netrcBox1.isSelected())
                {
                    LOGGER.debug(
                            "getPasswordAuthentication: calling findCredentials()");
                    NetrcFile f = new NetrcFile();
                    // since we are going to use directly, use strict hostname match
                    PasswordAuthentication pa = f.getCredentials(host, true);
                    if (pa != null)
                        return pa;
                }

                init(host, prompt);
                dialog.setVisible(true);

                if (retval == CANCEL)
                   return null;
                return new PasswordAuthentication(unField.getText(), pwField.getPassword());
            }
            finally
            {
                // for security reasons, we always want to clear traces of password text 
                // stored in memory; this appears to be the best we can do
                if (pwField != null)
                    pwField.setText(null);
            }
        }
              
        private void init(String host, String prompt)
        {
            if (dialog == null)
            {
                LOGGER.debug("MyAuthDialog.init: creating JDialog...");
                this.dialog = new JDialog(Util.findParentFrame(parent),
                                          "Authentication required", true);
                dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

                // the components
                this.iconLabel = new JLabel();
                this.hostLabel = new JLabel();
                this.promptLabel = new JLabel();
                this.retryLabel = new JLabel(RETRY_TEXT);
                retryLabel.setForeground(Color.red);

                // rescale font for prompt
                Font f = promptLabel.getFont();
                float sz = f.getSize2D();
                f = f.deriveFont(sz*1.5f);
                promptLabel.setFont(f);

                this.unField = new JTextField(12);
                unField.addActionListener(this);
                unField.setActionCommand(OK_TEXT);
                this.pwField = new JPasswordField(12);
                pwField.addActionListener(this);
                pwField.setActionCommand(OK_TEXT);
                this.netrcBox1 = new JCheckBox(NETRC_TEXT);
                netrcBox1.addActionListener(this);
                netrcBox1.setActionCommand(NETRC_TEXT);
                //netrcBox1.addChangeListener(this);
                //this.netrcBox2 = new JCheckBox("Save credentials to .netrc file");
                //netrcBox2.addChangeListener(this);
                this.okButton = new JButton(OK_TEXT);
                this.cancelButton = new JButton(CANCEL_TEXT);
                okButton.addActionListener(this);
                cancelButton.addActionListener(this);

                // main component
                JPanel mp = new JPanel(new BorderLayout());

                // top: info panel with logo and text
                JPanel info = new JPanel(new BorderLayout());
                info.add(iconLabel, BorderLayout.WEST);
                Box b = new Box(BoxLayout.Y_AXIS);
                b.add(Box.createGlue());
                b.add(promptLabel);
                b.add(hostLabel);
                b.add(retryLabel);
                b.add(Box.createGlue());
                b.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
                info.add(b, BorderLayout.CENTER);

                // input area
                Box input = new Box(BoxLayout.Y_AXIS);
                JPanel p;
                p = new JPanel();
                p.add(new JLabel("Username:"));
                p.add(unField);
                input.add(p);
                p = new JPanel();
                p.add(new JLabel("Password:"));
                p.add(pwField);
                input.add(p);
                input.add(netrcBox1);
                //input.add(netrcBox2);

                // button area
                JPanel buttons = new JPanel();
                buttons.add(okButton);
                buttons.add(cancelButton);

                mp.add(info, BorderLayout.NORTH);
                mp.add(input, BorderLayout.CENTER);
                mp.add(buttons, BorderLayout.SOUTH);

                mp.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
                Util.recursiveSetBackground(mp, Color.WHITE);

                dialog.getContentPane().add(mp);
            }
            // save server name and poke the netrc widget
            this.host = host;
            checkNetrc();

            // set the host/prompt labels if it is a CADC download
            try
            {
                // TODO: it would be nice to show a site-specific icon, but favicon.ico is not
                // supported by ImageIcon
                if (iconLabel.getIcon() == null)
                {
                    ImageIcon icon = new ImageIcon(Thread.currentThread().getContextClassLoader().getResource("images/cadc.jpg"));
                    iconLabel.setIcon(icon);
                    iconLabel.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
                }
            }
            catch(Throwable t) { LOGGER.debug("failed to load icon: " + t); }

            promptLabel.setText(prompt);
            hostLabel.setText("server: " + host);
            if (retry)
                retryLabel.setText(RETRY_TEXT);
            else
                retryLabel.setText(" ");

            // prepare to show dialog
            this.retval = CANCEL;

            Util.setPositionRelativeToParent(dialog, parent, 20, 20);
	}
        
        // this handles the OK and Cancel buttons and user pressing enter key in either 
        // text component, which is equivalent to OK
        public void actionPerformed(ActionEvent e)
        {
            if (OK_TEXT.equals(e.getActionCommand()))
            {
                this.retval = OK;
                dialog.setVisible(false);
            }
            else if (CANCEL_TEXT.equals(e.getActionCommand()))
            {
                this.retval = CANCEL;
                dialog.setVisible(false);
            }
            else if (NETRC_TEXT.equals(e.getActionCommand()))
            {
                checkNetrc();
            }
        }
        
        private void checkNetrc()
        {
            LOGGER.debug("checkNetrc...");
            if ( !netrcBox1.isSelected() )
                return;
            
            LOGGER.debug("creating NetrcFile");
            NetrcFile netrc = new NetrcFile();
            // since this is for http only, onyl strict hostname matching makes sense
            PasswordAuthentication pw = netrc.getCredentials(host, true);
            if (pw != null)
            {
                unField.setText(pw.getUserName());

                // TODO: SECURITY ISSUE
                // Doh! After all that work reading the .netrc and never making a password String, I have to 
                // convert it to a String and cannot blank it out; hopefully setText(null) above will be
                // enough to get rid of that String (eventually)
                pwField.setText(new String(pw.getPassword()));
            }
            else
            {
                LOGGER.debug("failed to find " + host + " in NetrcFile");
            }
        }
    }

    private class RequestingApp
    {
        String host;
        int port;
        String protocol;
        String prompt;
        String scheme;

        URL url;

        // gunk is the equality comparison part
        String gunk;

        RequestingApp() { }

        public String toString()
        {
            init();
            return "RequestingApp[" + gunk + "," + url + "]";
        }
        private void init()
        {
           if (gunk == null)
                gunk = protocol + "://" + host + ":"+port + "/" + prompt + "/" + scheme;
        }

        public boolean equals(Object o)
        {
            init();
            if (o instanceof RequestingApp)
            {
                RequestingApp ra = (RequestingApp) o;
                return gunk.equals(ra.gunk);
            }
            return false;
        }

        public int hashCode()
        {
            init();
            return gunk.hashCode();
        }
    }
}
