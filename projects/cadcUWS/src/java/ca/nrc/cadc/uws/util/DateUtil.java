/******************************************************************************
 *
 *  Copyright (C) 2009                          Copyright (C) 2009
 *  National Research Council           Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6                     Ottawa, Canada, K1A 0R6
 *  All rights reserved                         Tous droits reserves
 *
 *  NRC disclaims any warranties,       Le CNRC denie toute garantie
 *  expressed, implied, or statu-       enoncee, implicite ou legale,
 *  tory, of any kind with respect      de quelque nature que se soit,
 *  to the software, including          concernant le logiciel, y com-
 *  without limitation any war-         pris sans restriction toute
 *  ranty of merchantability or         garantie de valeur marchande
 *  fitness for a particular pur-       ou de pertinence pour un usage
 *  pose.  NRC shall not be liable      particulier.  Le CNRC ne
 *  in any event for any damages,       pourra en aucun cas etre tenu
 *  whether direct or indirect,         responsable de tout dommage,
 *  special or general, consequen-      direct ou indirect, particul-
 *  tial or incidental, arising         ier ou general, accessoire ou
 *  from the use of the software.       fortuit, resultant de l'utili-
 *                                                              sation du logiciel.
 *
 *
 *  This file is part of cadcUWS.
 *
 *  cadcUWS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  cadcUWS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcUWS.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package ca.nrc.cadc.uws.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Date conversion utility.
 * </p><p>
 * WARNING: The underlying SimpleDateFormat instances are NOT thread safe.
 *
 * @version $Version$
 * @author pdowler
 */
public class DateUtil
{
    public static String ISO_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    public static String ISO_DATE_FORMAT_TZ = "yyyy-MM-dd HH:mm:ss.SSSZ";
    public static String ISO8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";


    public static TimeZone UTC = TimeZone.getTimeZone("UTC");
    public static TimeZone LOCAL = TimeZone.getDefault();

    /**
     * A pre-configured ISO_DATE_FORMAT/LOCAL time zone formatter.
     *
     */
    public static DateFormat isoDateFormat =
            new SimpleDateFormat(ISO_DATE_FORMAT);

    private static HashMap<String, DateFormat> formats =
            new HashMap<String, DateFormat>();

    /**
     * Create a new DateFormat object with the specified format and timezone.
     * If the format is null it defaults to ISO format (without required TZ).
     * If the time zone is null it defaults to local (default) time zone.
     * </p><p>
     * WARNING: The underlying SimpleDateFormat instance is NOT thread safe.
     *
     * @param format
     * @param tz
     * @return
     */
    public static DateFormat getDateFormat(String format, TimeZone tz)
    {
        if (format == null)
            format = ISO_DATE_FORMAT;

        SimpleDateFormat ret = new SimpleDateFormat(format);
        ret.setLenient(false);
        if (tz != null)
            ret.setTimeZone(tz);
        return ret;
    }

    /**
     * Get a shared DateFormat object with the specified format and timezone.
     * If the format is null it defaults to ISO format (without required TZ).
     * If the time zone is null it defaults to local (default) time zone.
     * </p><p>
     * WARNING: The underlying SimpleDateFormat instance is NOT thread safe.
     *
     * @param format
     * @param tz
     * @return
     */
    public static synchronized DateFormat getSharedDateFormat(String format, TimeZone tz)
    {
        if (format == null)
            format = ISO_DATE_FORMAT;
        if (tz == null)
            tz = LOCAL;

        DateFormat fmt = null;
        String key = format.hashCode() + "/" + tz.hashCode();

        Object obj = formats.get(key);
        if (obj != null)
        {
            fmt = (DateFormat) obj;
        }
        else
        {
            fmt = getDateFormat(format, tz);
            formats.put(key, fmt);
        }

        return fmt;
    }

    /**
     * Convert the argument date string to a Date using the default format
     * and TimeZone.
     *
     * @param s representation of the date
     * @return a Date
     * @throws java.text.ParseException   If the String is unparseable.
     */
    public static Date toDate(String s)
            throws ParseException
    {
        return toDate(s, null, null);
    }

    /**
     * Parse all the given formats in order and return the first one that can
     * be processed into a Date.  This is intended to be a little sloppy.
     *
     * @param source        The date string source.
     * @param formats       The array of formats desired.
     *
     * @return              Date instance.
     * @throws java.text.ParseException       If the given formats or source is null or
     *                              unreadable, or if none of the formats can
     *                              be used.
     */
    public static Date toDate(final String source, final String[] formats)
            throws ParseException
    {
        if (!StringUtil.hasText(source) || (formats == null))
        {
            throw new ParseException(
                    "No usable formats or source date is null", -1);
        }

        for (final String format : formats)
        {
            if (!StringUtil.hasLength(format))
            {
                continue;
            }

            try
            {
                return toDate(source.trim(), format);
            }
            catch (ParseException e)
            {
                // We're just ignoring it as we just want the first one to
                // match!
            }
        }

        throw new ParseException("No usable formats found in >> "
                                 + Arrays.toString(formats), -1);
    }

    /**
     * Convert the argument date string to a Date using the supplied format and
     * default (LOCAL) TimeZone.
     *
     * @see java.text.SimpleDateFormat for format specification
     * @param s string representation of the date
     * @param format format string to use (null: default)
     * @return the Date
     * @throws java.text.ParseException  If the String is unparseable.
     */
    public static Date toDate(String s, String format)
            throws ParseException
    {
        return toDate(s, format, null);
    }

    /**
     * Convert the argument date string to a Date using the supplied format and
     * TimeZone.
     *
     * If initial parsing fails, this method will try to append a
     * time string (0:0:0) and parse, which will work if only a date is supplied.
     * If that also fails, the original ParseException is thrown.
     * </p><p>
     * NOTE: Formatters created are kept and re-used in future calls to this
     * method. THIS IS NOT THREAD SAFE. Use getDateFormat(...) if you need to be
     * thread safe.
     *
     * @param s string representation of the date
     * @param format format string to use (null: default)
     * @param tz timezone to assume (null: default)
     * @return the Date
     * @throws java.text.ParseException  If the String is unparseable.
     */
    public static Date toDate(String s, String format, TimeZone tz)
            throws ParseException
    {
        DateFormat fmt = getSharedDateFormat(format, tz);
        return flexToDate(s, fmt);
    }

    /**
     * Sloppy parsing. This method makes several attempts to parse the supplied
     * date string before giving up. if the initial parse fails, it tries to append
     * 0 milliseconds to then time, then a time of 0:0:0, then 0:0:0.0, and the it
     * tries setting the DateFormat to lenient.
     *
     * @param s string representation of the date
     * @param fmt the DateFormat to use
     * @return the Date
     * @throws java.text.ParseException   If the String is unparseable.
     */
    public static Date flexToDate(String s, DateFormat fmt)
            throws ParseException
    {
        // try to parse
        ParseException orig = null;
        NumberFormatException origN = null;
        try
        {
            return fmt.parse(s);
        }
        catch (ParseException pex)
        {
            orig = pex;
        }
        catch(NumberFormatException nex)
        {
            origN = nex;
        }

        // missing milliseconds?
        try
        {
            return fmt.parse(s + ".0");

        }
        catch (ParseException ignore) { }
        catch (NumberFormatException ignore) { }

        // missing time?
        try
        {
            return fmt.parse(s + " 0:0:0.0");
        }
        catch (ParseException ignore) { }
        catch (NumberFormatException ignore) { }

        // more lenient?
	/* temporary HACK: need to fix recursion before enabling this
	try
        {
            fmt.setLenient(true);
            return flexToDate(s, fmt);
        }
        catch (ParseException ignore) { }
        catch (NumberFormatException ignore) { }
        finally
        {
            fmt.setLenient(false);
        }
	*/
        if (orig != null)
            throw orig;
        throw new ParseException("failed to parse '" + s + "': " + origN, 0);
    }

    /**
     * Convert the given Date using the supplied format. The assumed time zone
     * is local.
     *
     * @param d
     * @param format
     * @return Formatted Date in String format.
     */
    public static String toString(Date d, String format)
    {
        return toString(d, format, LOCAL);
    }

    /**
     * Convert the given Date using the supplied format in the specified timezone.
     *
     * @param d
     * @param format
     * @param tz
     * @return Formatted Date in String format.
     */
    public static String toString(Date d, String format, TimeZone tz)
    {
        DateFormat fmt = getSharedDateFormat(format, tz);
        return fmt.format(d);
    }

    /**
     * Convert the argument date string to a vanilla java.util.Date. The input
     * object can be a java.sql.Date (or subclass) or java.sql.Timestamp
     * (or subclass).
     *
     * @param date the original date
     * @return a Date
     * @throws UnsupportedOperationException
     */
    public static Date toDate(Object date)
    {
        if (date == null)
            return null;
        if (date instanceof java.sql.Timestamp)
        {
            java.sql.Timestamp ts = (java.sql.Timestamp) date;
            long millis = ts.getTime();

            // NOTE: On DB2 the millis is only to the second and all the fractional
            // part is in the nanos
            //int nanos = ts.getNanos();
            //millis += (long) (nanos / 1000000);

            return new Date(millis);
        }
        if (date instanceof java.sql.Date)
        {
            java.sql.Date sd = (java.sql.Date) date;
            return new Date(sd.getTime());
        }
        if (date instanceof Date)
        {
            return (Date) date;
        }
        throw new UnsupportedOperationException("failed to convert " + date.getClass().getName() + " to java.util.Date");
    }

    /**
     * Convert a date in the UTC timezone to Modified Julian Date. Note that the
     * double datatype for MJD does not have enough digits for successful
     * round-trip conversuion of all possible Date values (i.e. it has less
     * that microsecond precision).
     *
     * @param date a date in the UTC timezone
     * @return the MJD value
     */
    public static double toModifiedJulianDate(Date date)
    {
        return toModifiedJulianDate(date, UTC);
    }

    /**
     * Convert a date in the specified timezone to Modified Julian Date.
     *
     * @param date
     * @param timezone
     * @return number of days
     */
    public static double toModifiedJulianDate(Date date, TimeZone timezone)
    {
        Calendar cal = Calendar.getInstance(timezone);
        cal.clear();
        cal.setTime(date);
        int yr = cal.get(Calendar.YEAR);
        int mo = cal.get(Calendar.MONTH) + 1; // Calendar is 0-based

        int dy = cal.get(Calendar.DAY_OF_MONTH);
        double days = slaCldj(yr, mo, dy);

        int hh = cal.get(Calendar.HOUR_OF_DAY);
        int mm = cal.get(Calendar.MINUTE);
        int ss = cal.get(Calendar.SECOND);
        int ms = cal.get(Calendar.MILLISECOND);
        double seconds = hh * 3600.0 + mm * 60.0 + ss + ms / 1000.0;

        return days + seconds / 86400.0;
    }

    /**
     * Convert a Modified Julian Date to a date in the UTC timezone.
     *
     * @param mjd the MJD value
     * @return a Date in the UTC timezone
     */
    public static Date toDate(double mjd)
    {
        int[] ymd = slaDjcl(mjd);

        // fraction of a day
        double frac = mjd - ((double) (long) mjd);
        int hh = (int) (frac * 24);
        // fraction of an hour
        frac = frac * 24.0 - hh;
        int mm = (int) (frac * 60);
        // fraction of a minute
        frac = frac * 60.0 - mm;
        int ss = (int) (frac * 60);
        // fraction of a second
        frac = frac * 60.0 - ss;
        int ms = (int) (frac * 1000);
        //frac = frac*1000.0 - ms;

        Calendar cal = Calendar.getInstance(UTC);
        cal.set(Calendar.YEAR, ymd[0]);
        cal.set(Calendar.MONTH, ymd[1] - 1); // Calendar is 0-based

        cal.set(Calendar.DAY_OF_MONTH, ymd[2]);
        cal.set(Calendar.HOUR_OF_DAY, hh);
        cal.set(Calendar.MINUTE, mm);
        cal.set(Calendar.SECOND, ss);
        cal.set(Calendar.MILLISECOND, ms);

        return cal.getTime();
    }
    /* Month lengths in days */
    private static int mtab[] =
    {
        31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
    };

    //private static double slaCldj( int iy, int im, int id, double *djm, int *j )
    private static double slaCldj(int iy, int im, int id)
    /*
     **  - - - - - - - -
     **   s l a C l d j
     **  - - - - - - - -
     **
     **  Gregorian calendar to Modified Julian Date.
     **
     **  Given:
     **     iy,im,id     int    year, month, day in Gregorian calendar
     **
     **  Returned:
     **     *djm         double Modified Julian Date (JD-2400000.5) for 0 hrs
     **     *j           int    status:
     **                           0 = OK
     **                           1 = bad year   (MJD not computed)
     **                           2 = bad month  (MJD not computed)
     **                           3 = bad day    (MJD computed)
     **
     **  The year must be -4699 (i.e. 4700BC) or later.
     **
     **  The algorithm is derived from that of Hatcher 1984 (QJRAS 25, 53-55).
     **
     **  Last revision:   29 August 1994
     **
     **  Copyright P.T.Wallace.  All rights reserved.
     */
    {
        //System.out.println("[slaCldj] " + iy + ", " + im + ", " + id);

        long iyL, imL;

        /* Validate year */
        //if ( iy < -4699 ) { *j = 1; return; }
        if (iy < -4699)
        {
            throw new IllegalArgumentException("bad year");
        }

        /* Validate month */
        //if ( ( im < 1 ) || ( im > 12 ) ) { *j = 2; return; }
        if ((im < 1) || (im > 12))
        {
            throw new IllegalArgumentException("bad month");
        }

        /* Allow for leap year */
        mtab[1] = (((iy % 4) == 0) &&
                (((iy % 100) != 0) || ((iy % 400) == 0))) ? 29 : 28;

        /* Validate day */
        //*j = ( id < 1 || id > mtab[im-1] ) ? 3 : 0;
        if (id < 1 || id > mtab[im - 1])
        {
            throw new IllegalArgumentException("bad day");

        /* Lengthen year and month numbers to avoid overflow */
        }
        iyL = (long) iy;
        imL = (long) im;

        /* Perform the conversion */
        return (double) ((1461L * (iyL - (12L - imL) / 10L + 4712L)) / 4L + (306L * ((imL + 9L) % 12L) + 5L) / 10L - (3L * ((iyL - (12L - imL) / 10L + 4900L) / 100L)) / 4L + (long) id - 2399904L);
    }

    //void slaDjcl ( double djm, int *iy, int *im, int *id, double *fd, int *j)
    private static int[] slaDjcl(double djm)
    /*
     **  - - - - - - - -
     **   s l a D j c l
     **  - - - - - - - -
     **
     **  Modified Julian Date to Gregorian year, month, day,
     **  and fraction of a day.
     **
     **  Given:
     **     djm      double     Modified Julian Date (JD-2400000.5)
     **
     **  Returned:
     **     *iy      int        year
     **     *im      int        month
     **     *id      int        day
     **     *fd      double     fraction of day
     **     *j       int        status:
     **                      -1 = unacceptable date (before 4701BC March 1)
     **
     **  The algorithm is derived from that of Hatcher 1984 (QJRAS 25, 53-55).
     **
     **  Defined in slamac.h:  dmod
     **
     **  Last revision:   12 March 1998
     **
     **  Copyright P.T.Wallace.  All rights reserved.
     */
    {
        //System.out.println("[slaDjcl] " + djm);
        //double f, d;
        //double f;
        long ld, jd, n4, nd10;

        /* Check if date is acceptable */
        if ((djm <= -2395520.0) || (djm >= 1e9))
        //{
        //*j = -1;
        //return;
        {
            throw new IllegalArgumentException("MJD out of valid range");
        //}
        //else
        //{
        //	*j = 0;

        /* Separate day and fraction */
        //f = dmod ( djm, 1.0 );
        //if ( f < 0.0 ) f += 1.0;
        //d = djm - f;
        //d = dnint ( d );
        }
        ld = (long) djm;

        /* Express day in Gregorian calendar */
        //jd = (long) dnint ( d ) + 2400001;
        jd = ld + 2400001L;
        n4 = 4L * (jd + ((6L * ((4L * jd - 17918L) / 146097L)) / 4L + 1L) / 2L - 37L);
        nd10 = 10L * (((n4 - 237L) % 1461L) / 4L) + 5L;
        //*iy = (int) (n4/1461L-4712L);
        //*im = (int) (((nd10/306L+2L)%12L)+1L);
        //*id = (int) ((nd10%306L)/10L+1L);
        //*fd = f;
        int[] ret = new int[3];
        ret[0] = (int) (n4 / 1461L - 4712L);
        ret[1] = (int) (((nd10 / 306L + 2L) % 12L) + 1L);
        ret[2] = (int) ((nd10 % 306L) / 10L + 1L);

        //*j = 0;
        return ret;
    //}
    }

    /**
     * Obtain the Date from the given DMF seconds.
     *
     * @param dmfSeconds        The DMF Seconds value.
     * @return Date object from the dmf Seconds.
     */
    public static Date toDate(final long dmfSeconds)
    {
        return new Date((dmfSeconds * 1000L) + getDMFEpoch().getTime());
    }

    /**
     * Obtain the Date object representing the DMF Epoch of January 1st, 1980.
     *
     * @return Date object.
     */
    public static Date getDMFEpoch()
    {
        final Calendar cal = Calendar.getInstance(UTC);
        cal.set(1980, Calendar.JANUARY, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);

        return cal.getTime();
    }
}