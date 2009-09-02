/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2009.                            (c) 2009.
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
 * @author jenkinsd
 * Aug 6, 2009 - 9:52:16 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws;

import java.net.URL;


/**
 * Result Object.
 */
public class Result
{
    private String name;
    private URL url;


    public Result(final String name, final URL url)
    {
        this.name = name;
        this.url = url;
    }
    

    public String getName()
    {
        return name;
    }

    public void setName(final String name)
    {
        this.name = name;
    }

    public URL getURL()
    {
        return url;
    }

    public void setURL(final URL url)
    {
        this.url = url;
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }

        if ((o == null) || (getClass() != o.getClass()))
        {
            return false;
        }

        final Result result = (Result) o;

        return !((name != null)
                 ? !name.equals(result.name)
                 : (result.name != null))
               && !((url != null)
                    ? !url.equals(result.url)
                    : (result.url != null));

    }

    @Override
    public int hashCode()
    {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (url != null ? url.hashCode() : 0);
        return result;
    }
}
