/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2013.                          (c) 2013.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 *
 * @author goliaths
 * @version $ Revision: $
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */


package ca.nrc.cadc.exec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import ca.nrc.cadc.util.ArrayUtil;

/**
 * Cut and paste of the OutputGrabber class from javaUtil, except for the part
 * where the Runtime.exec has been replaced with ProcessBuilder.
 */
public class BuilderOutputGrabber
{
    private static Logger log = Logger.getLogger(BuilderOutputGrabber.class);

    private final StringBuilder stdout = new StringBuilder();
    private final StringBuilder stderr = new StringBuilder();
    private int exit_value;


    public BuilderOutputGrabber()
    {
    }


    // example of capturing the stdout of a program, waiting for the
    // program to finish, and getting the exit value
    public void captureOutput(String cmd)
    {
        try
        {
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            log.debug("exec: " + cmd);
            Process p = processBuilder.start();
            grabOutput(p);
        }
        catch(IOException ioex)
        {
            getStderr().append("IOException: ").append(ioex);
            exit_value = -1;
        }
        catch(InterruptedException irex)
        {
            getStderr().append("InterruptedException: ").append(irex);
            exit_value = -1;
        }
        catch(Exception ex)
        {
            getStderr().append("Exception: ").append(ex);
            exit_value = -1;
        }
    }

    public void captureOutput(String[] cmd)
    {
        try
        {
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            log.debug("exec: " + concat(cmd));
            Process p = processBuilder.start();
            grabOutput(p);
        }
        catch(IOException ioex)
        {
            getStderr().append("IOException: ").append(ioex);
            exit_value = -1;
        }
        catch(InterruptedException irex)
        {
            getStderr().append("InterruptedException: ").append(irex);
            exit_value = -1;
        }
        catch(Exception ex)
        {
            getStderr().append("Exception: ").append(ex);
            exit_value = -1;
        }
    }

    public void captureOutput(String[] cmd, Map<String, String> env)
    {
        try
        {
            List<String> parameters = new ArrayList<String>();
            parameters.addAll(Arrays.asList(cmd));
            ProcessBuilder processBuilder = new ProcessBuilder(parameters);
            if (env != null)
            {
                Map<String, String> environment = processBuilder.environment();
                environment.clear();
                environment.putAll(env);
            }
            log.debug("exec: " + concat(cmd) + "\nenv: " + concat(env));
            Process p = processBuilder.start();
            grabOutput(p);
        }
        catch(IOException ioex)
        {
            getStderr().append("IOException: ").append(ioex);
            exit_value = -1;
        }
        catch(InterruptedException irex)
        {
            getStderr().append("InterruptedException: ").append(irex);
            exit_value = -1;
        }
        catch(Exception ex)
        {
            getStderr().append("Exception: ").append(ex);
            exit_value = -1;
        }
    }

    public void captureOutput(String[] cmd, Map<String, String> env, File dir)
    {
        try
        {
            List<String> parameters = new ArrayList<String>();
            parameters.addAll(Arrays.asList(cmd));
            ProcessBuilder processBuilder = new ProcessBuilder(parameters);
            if (env != null)
            {
                Map<String, String> environment = processBuilder.environment();
                environment.clear();
                environment.putAll(env);
            }
            log.debug("exec: " + concat(cmd) + "\nenv: " + concat(
                    env) + "\ndir: " + dir);
            processBuilder.directory(dir);
            Process p = processBuilder.start();
            grabOutput(p);
        }
        catch(IOException ioex)
        {
            getStderr().append("IOException: ").append(ioex);
            exit_value = -1;
        }
        catch(InterruptedException irex)
        {
            getStderr().append("InterruptedException: ").append(irex);
            exit_value = -1;
        }
        catch(Exception ex)
        {
            getStderr().append("Exception: ").append(ex);
            exit_value = -1;
        }
    }

    protected void grabOutput(Process p)
            throws IOException, InterruptedException
    {
        InputStream pi = null;
        InputStream pe = null;
        OutputStream po = null;

        try
        {
            pi = p.getInputStream();
            pe = p.getErrorStream();
            po = p.getOutputStream();

            final ReaderThread out = new ReaderThread(pi, getStdout());
            out.start();

            final ReaderThread err = new ReaderThread(pe, getStderr());
            err.start();

            // should suffice to let the threads run and wait for the process to exit
            out.join();
            err.join();
            exit_value = p.waitFor(); // block

            if (out.ex != null)
            {
                getStderr().append("exception while reading command output:\n")
                        .append(out.ex.toString());
            }

            if (err.ex != null)
            {
                getStderr().append(
                        "exception while reading command error output:\n")
                        .append(err.ex.toString());
            }
        }
        finally
        {
            if (pi != null)
            {
                try
                {
                    pi.close();
                }
                catch(IOException ignore)
                {
                }
            }

            if (pe != null)
            {
                try
                {
                    pe.close();
                }
                catch(IOException ignore)
                {
                }
            }

            if (po != null)
            {
                try
                {
                    po.close();
                }
                catch(IOException ignore)
                {
                }
            }
        }
    }

    public String getOutput(boolean doTrim)
    {
        if (doTrim)
        {
            return getStdout().toString().trim();
        }
        else
        {
            return getStdout().toString();
        }
    }

    public String getErrorOutput(boolean doTrim)
    {
        if (doTrim)
        {
            return getStderr().toString().trim();
        }
        else
        {
            return getStderr().toString();
        }
    }

    public String getOutput()
    {
        return getOutput(true);
    }

    public String getErrorOutput()
    {
        return getErrorOutput(true);
    }

    public StringBuilder getStdout()
    {
        return stdout;
    }

    public StringBuilder getStderr()
    {
        return stderr;
    }

    public int getExitValue()
    {
        return exit_value;
    }

    private String concat(String[] s)
    {
        if (ArrayUtil.isEmpty(s))
        {
            return null;
        }
        else if (s.length == 1)
        {
            return s[0];
        }

        final StringBuilder sb = new StringBuilder();

        for (final String value : s)
        {
            sb.append(value).append(" ");
        }

        return sb.toString();
    }

    private String concat(Map<String, String> map)
    {
        if (map == null)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String key : map.keySet())
        {
            sb.append(key);
            sb.append(' ');
            sb.append(map.get(key));
            sb.append(' ');
        }
        return sb.toString();
    }

    private class ReaderThread extends Thread
    {
        public Exception ex;
        private StringBuilder sb;
        private LineNumberReader reader;

        public ReaderThread(InputStream istream, StringBuilder sb)
        {
            this.reader = new LineNumberReader(new InputStreamReader(istream));
            this.sb = sb;
        }

        public void run()
        {
            try
            {
                String s;
                while ((s = reader.readLine()) != null)
                {
                    sb.append(s).append("\n");
                }
            }
            catch (Exception iex)
            {
                this.ex = iex;
            }
        }
    }
}
