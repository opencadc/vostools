/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.nrc.cadc.conformance.vos;

import ca.nrc.cadc.vos.Node;

/**
 *
 * @author jburke
 */
public class TestNode
{
    public Node sampleNode;
    public Node sampleNodeWithLink;

    TestNode(Node sampleNode, Node sampleNodeWithLink)
    {
        this.sampleNode = sampleNode;
        this.sampleNodeWithLink = sampleNodeWithLink;
    }

}
