// Created on 30-Jan-07

package ca.onfire.swing;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

public class JSystemPropDisplay extends JPanel
{
    public JSystemPropDisplay()
    {
        super(new BorderLayout());
        init();
    }
    
    private void init()
    {
        JTextArea textWidget = new JTextArea();
        textWidget.setEditable(false);
        this.add(new JScrollPane(textWidget), BorderLayout.CENTER);
        textWidget.setText(getSystemProperties());
    }
    
    private String getSystemProperties()
    {
        StringBuffer sb = new StringBuffer();

        ArrayList props = new ArrayList();
        Iterator i = System.getProperties().entrySet().iterator();
        while( i.hasNext() )
        {
            Map.Entry me = (Map.Entry)i.next();
            String s = me.getKey() + ":  " + me.getValue();
            props.add(s);
        }
        Collections.sort(props);
        for (int ii=0; ii<props.size(); ii++)
            sb.append( ((String) props.get(ii)) + "\n");
        return sb.toString();
    }
}
