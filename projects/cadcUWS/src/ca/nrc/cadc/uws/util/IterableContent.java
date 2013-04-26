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

package ca.nrc.cadc.uws.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.apache.log4j.Logger;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * Extension of a jdom content that allows the child content to be
 * generated on-the-fly from an iterator.  Instances of this object
 * can be added directly to a jdom document.  Child elements will
 * be generated using the data from the iterator and converted with
 * the supplied ContentConverter.
 * 
 * @author majorb
 *
 * @param <E> The type of jdom content
 * @param <T> The type of object being iterated
 */
public class IterableContent<E extends Content, T> extends Element
{
    private static Logger log = Logger.getLogger(IterableContent.class);
    
    private static final long serialVersionUID = -3057526491224781782L;
    private Iterator<T> iterator;
    private ContentConverter<E, T> contentConverter;
    private MaxIterations maxIterations;
    
    /**
     * Construct the iterable content.
     * @param name The name of the content
     * @param ns The content namespace
     * @param iterator The iterator holding the child data
     * @param contentConverter The object that converts child data to jdom content
     */
    public IterableContent(String name, Namespace ns, Iterator<T> iterator, ContentConverter<E, T> contentConverter)
    {
        super(name, ns);
        log.debug("IterableContent contructed.");
        this.iterator = iterator;
        this.contentConverter = contentConverter;
    }
    
    /**
     * Construct the iterable content.
     * @param name The name of the content
     * @param ns The content namespace
     * @param iterator The iterator holding the child data
     * @param contentConverter The object that converts child data to jdom content
     * @param maxIterations Defines the behaviour in limiting the iterations.
     */
    public IterableContent(String name, Namespace ns, Iterator<T> iterator,
            ContentConverter<E, T> contentConverter, MaxIterations maxIterations)
    {
        super(name, ns);
        log.debug("IterableContent contructed.");
        this.iterator = iterator;
        this.contentConverter = contentConverter;
        this.maxIterations = maxIterations;
    }
    
    @Override
    public List<Content> getContent()
    {
        log.debug("Get content called.");
        IterableList<Content, T> iterableList = new IterableList(iterator, contentConverter, maxIterations);
        return iterableList;
    }
    
    private class IterableList<C extends Content, D> extends ArrayList<C>
    {
        
        private static final long serialVersionUID = 1932716563665349508L;
        private ContentConversionIterator<C, D> iterator;
        
        public IterableList(Iterator<C> iterator, ContentConverter<C, D> contentConverter, MaxIterations maxIterations)
        {
            super();
            log.debug("IterableList contructed.");
            this.iterator = new ContentConversionIterator(contentConverter, iterator, maxIterations);
        }
        
        @Override
        public boolean isEmpty()
        {
            log.debug("IterableList.isEmpty() called.");
            return (iterator.isEmpty());
        }
        
        @Override
        public Iterator<C> iterator()
        {
            log.debug("IterableList.iterator() called.");
            return iterator;
        }
        
        // Report an unsupported operation if any of these other list methods are called
        @Override public boolean add(C c) { throw new UnsupportedOperationException(); }
        @Override public void add(int i, C c) { throw new UnsupportedOperationException(); }
        @Override public boolean addAll(Collection<? extends C> c) { throw new UnsupportedOperationException(); }
        @Override public boolean addAll(int i, Collection<? extends C> c) { throw new UnsupportedOperationException(); }
        @Override public void clear() { throw new UnsupportedOperationException(); }
        @Override public boolean contains(Object o) { throw new UnsupportedOperationException(); }
        @Override public boolean containsAll(Collection<?> c) { throw new UnsupportedOperationException(); }
        @Override public boolean equals(Object o) { throw new UnsupportedOperationException(); }
        @Override public C get(int i) { throw new UnsupportedOperationException(); }
        @Override public int indexOf(Object o) { throw new UnsupportedOperationException(); }
        @Override public int lastIndexOf(Object o) { throw new UnsupportedOperationException(); }
        @Override public ListIterator<C> listIterator() { throw new UnsupportedOperationException(); }
        @Override public C remove(int i) { throw new UnsupportedOperationException(); }
        @Override public boolean remove(Object o) { throw new UnsupportedOperationException(); }
        @Override public boolean removeAll(Collection<?> c) { throw new UnsupportedOperationException(); }
        @Override public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException(); }
        @Override public C set(int i, C c) { throw new UnsupportedOperationException(); }
        @Override public int size() { throw new UnsupportedOperationException(); }
        @Override public List<C> subList(int i, int j) { throw new UnsupportedOperationException(); }
        @Override public Object[] toArray() { throw new UnsupportedOperationException(); }
        @Override public <T> T[] toArray(T[] a) { throw new UnsupportedOperationException(); }
        
    }
    
    private class ContentConversionIterator<A extends Content, B> implements Iterator<A>
    {
        
        private ContentConverter<A, B> contentConverter;
        private MaxIterations maxIterations;
        private Iterator<B> iterator;
        private B next;
        private long rowCount = 0;
        private boolean maxIterationsReached = false;
        private boolean maxIterationsReachedCalled = false;
        
        ContentConversionIterator(ContentConverter<A, B> contentConverter,
                Iterator<B> iterator, MaxIterations maxIterations)
        {
            log.debug("ContentConversionIterator contructed.");
            this.contentConverter = contentConverter;
            this.iterator = iterator;
            this.maxIterations = maxIterations;
            
            // get the first element up front
            advance();
        }
        
        private void advance()
        {
            next = null;

            if (this.iterator.hasNext())
            {
                next = this.iterator.next();
            }
            
            if (maxIterations != null)
            {
                if (rowCount >= maxIterations.getMaxIterations())
                {
                    maxIterationsReached = true;
                    next = null;
                }
            }
            
            rowCount++;
        }
        
        boolean isEmpty()
        {
            return (next == null);
        }

        @Override
        public boolean hasNext()
        {
            if (maxIterationsReached && !maxIterationsReachedCalled)
            {
                maxIterations.maxIterationsReached();
                maxIterationsReachedCalled = true;
            }
            
            return (next != null);
        }

        @Override
        public A next()
        {
            log.debug("ContentConversionIterator.next");
            
            if (next == null)
                throw new NoSuchElementException();

            // convert the object
            A nextContent = contentConverter.convert(next);

            // advance the iterator
            advance();
            
            return nextContent;
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException();
        }
        
    }
    
}
