/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2012.                         (c) 2012.
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
 * 11/14/12 - 3:12 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.client.ui;

import ca.nrc.cadc.util.StringUtil;

import javax.swing.*;
import java.awt.*;
import java.util.StringTokenizer;
import java.util.Vector;


public class WrappingLabel extends JTextArea
{
    private Vector<String> innerText;
    private int maxLength;


    public WrappingLabel(final int maxLength)
    {
        this(null, maxLength);
    }

    public WrappingLabel(final String text, final int maxLength)
    {
        this.maxLength = maxLength;
        setLineWrap(true);
        setWrapStyleWord(true);
        setText(text);
    }

    /**
     * Defines the single line of text this component will display.  If
     * the value of text is null or empty string, nothing is displayed.
     * <p/>
     * The default value of this property is null.
     * <p/>
     * This is a JavaBeans bound property.
     *
     * @beaninfo preferred: true
     * bound: true
     * attribute: visualUpdate true
     * description: Defines the single line of text this component will display.
     */
    @Override
    public void setText(final String text)
    {
        if (!StringUtil.hasText(text))
        {
            innerText = new Vector<String>();
        }
        else
        {
            final StringTokenizer st = new StringTokenizer(text, "\n");

            // we know we'll need at least this many lines
            innerText = new Vector<String>(st.countTokens());

            while (st.hasMoreTokens())
            {
                // make sure we don't exceed maximum length
                final String nextLine = st.nextToken();
                final StringBuilder buf = new StringBuilder();

                for (final StringTokenizer st2 =
                             new StringTokenizer(nextLine, " ", true);
                     st2.hasMoreTokens();)
                {
                    final String nextWord = st2.nextToken();

                    if ((buf.length() > 0)
                        && ((buf.length() + nextWord.length())
                            > getMaxLength()))
                    {
                        // We would exceed max length, set the buffer into the
                        // array & start a new buffer.
                        getInnerText().addElement(buf.toString());
                        buf.setLength(0);
                    }

                    buf.append(nextWord);
                }

                // catch the last part of a string
                getInnerText().addElement(buf.toString());
            }
        }
    }

    /**
     * Ensure that getFont does not returl null.
     * 
     * @return  Font instance.
     */
//    @Override
//    public Font getFont()
//    {
//        final Font f;
//
//        if (super.getFont() == null)
//        {
//            f = new Font("dialog", Font.PLAIN, 12);
//        }
//        else
//        {
//            f = super.getFont();
//        }
//
//        return f;
//    }

    /**
     * Make sure that the height and width are getting
     * sent using the multi lines we know about.
     *
     * @return      Dimension instance of the preferred size.
     */
    @Override
    public Dimension getPreferredSize()
    {
        FontMetrics fm = getFontMetrics(getFont());
        int maxWidth = 0;
        for (int i = 0; i < getInnerText().size(); ++i)
        {
            int mw = fm.stringWidth(getInnerText().elementAt(i));
            if (mw > maxWidth)
            {
                maxWidth = mw;
            }
        }

        final int height = fm.getHeight() * getInnerText().size();
        return new Dimension(maxWidth, height);
    }

    @Override
    public void paint(final Graphics g)
    {
        //
        // Start at ascent with offset in y.
        // Start at 0 for x.
        //
        super.paint(g);
        g.setColor(getForeground());

        final FontMetrics fm = g.getFontMetrics(getFont());
        int yLoc = fm.getAscent();
        final Dimension dim = getSize();

        for (final String element : getInnerText())
        {
            final int xLoc;

            if (getAlignmentX() == Label.CENTER)
            {
                xLoc = (dim.width - fm.stringWidth(element)) / 2;
            }
            else if (getAlignmentX() == Label.RIGHT)
            {
                xLoc = dim.width - fm.stringWidth(element);
            }
            else
            {
                xLoc = 0;
            }

            g.drawString(element, xLoc, yLoc);
            yLoc += fm.getHeight();
        }
    }

    public Vector<String> getInnerText()
    {
        return innerText;
    }

    public int getMaxLength()
    {
        return maxLength;
    }
}
