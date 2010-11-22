/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2007.                            (c) 2007.
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
 * 18-May-2007 - 9:12:20 AM
 *
 * 
 * 
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.client;

import java.text.DecimalFormat;
import java.text.NumberFormat;


/**
 * Enumerated type to represent a FileSizeType.  This will lay out the size of
 * any one type, be it a Byte, Kilobyte, Megabyte, Gigabyte, or Terrabyte.
 */
public enum FileSizeType
{
    BYTE(1l),
    KILOBYTE(BYTE.getSize() * 1024l),
    MEGABYTE(KILOBYTE.getSize() * 1024l),
    GIGABYTE(MEGABYTE.getSize() * 1024l),
    TERRABYTE(GIGABYTE.getSize() * 1024l);

    private final static String DECIMAL_FORMAT = "0.00"; 

    private long size;


    FileSizeType(final long size)
    {
        this.size = size;
    }


    /**
     * Obtain the size in Bytes.
     *
     * @return      Long bytes.
     */
    public long getSize()
    {
        return size;
    }

    /**
     * Produce the Human Readable file size.  This is useful for display.
     *
     * @param size  The size of the item to be displayed.
     * @return      String human readable (i.e. 10MB).
     */
    public static String getHumanReadableSize(final long size)
    {
        final NumberFormat formatter = new DecimalFormat(DECIMAL_FORMAT);

        if (size < KILOBYTE.getSize())
        {
            return size + "B";
        }
        else if (size < MEGABYTE.getSize())
        {
            return formatter.format(
                    (size / (KILOBYTE.getSize() * 1.0d))) + "KB";
        }
        else if (size < GIGABYTE.getSize())
        {
            return formatter.format(
                    (size / (MEGABYTE.getSize() * 1.0d))) + "MB";
        }
        else if (size < TERRABYTE.getSize())
        {
            return formatter.format(
                    (size / (GIGABYTE.getSize() * 1.0d))) + "GB";
        }
        else
        {
            return formatter.format(
                    (size / (TERRABYTE.getSize() * 1.0d))) + "TB";
        }
    }
}
