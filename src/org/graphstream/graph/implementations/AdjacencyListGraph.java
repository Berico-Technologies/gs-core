/*
 * This file is part of GraphStream.
 * 
 * GraphStream is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * GraphStream is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with GraphStream.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright 2006 - 2009
 * 	Julien Baudry
 * 	Antoine Dutot
 * 	Yoann Pigné
 * 	Guilhelm Savin
 */

package org.graphstream.graph.implementations;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import org.graphstream.graph.Edge;
import org.graphstream.graph.EdgeFactory;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.NodeFactory;
import org.graphstream.graph.ElementNotFoundException;
import org.graphstream.graph.IdAlreadyInUseException;
import org.graphstream.oldUi.GraphViewer;
import org.graphstream.oldUi.GraphViewerRemote;
import org.graphstream.stream.AttributeSink;
import org.graphstream.stream.ElementSink;
import org.graphstream.stream.Sink;
import org.graphstream.stream.GraphParseException;
import org.graphstream.stream.Pipe;
import org.graphstream.stream.SourceBase;
import org.graphstream.stream.SourceBase.ElementType;
import org.graphstream.stream.file.FileSink;
import org.graphstream.stream.file.FileSinkFactory;
import org.graphstream.stream.file.FileSource;
import org.graphstream.stream.file.FileSourceFactory;
import org.graphstream.stream.sync.SinkTime;
import org.graphstream.ui2.layout.Layout;
import org.graphstream.ui2.swingViewer.GraphRenderer;
import org.graphstream.ui2.swingViewer.Viewer;
import org.graphstream.ui2.swingViewer.basicRenderer.SwingBasicGraphRenderer;

/**
 * <p>
 * A light graph class intended to allow the construction of big graphs
 * (millions of elements).
 * </p>
 * 
 * <p>
 * The main purpose here is to minimise memory consumption even if the
 * management of such a graph implies more CPU consuming. See the
 * <code>complexity</code> tags on each method so as to figure out the impact
 * on the CPU.
 * </p>
 */
public class AdjacencyListGraph extends AbstractElement implements Graph
{
	public class EdgeIterator implements Iterator<Edge>
	{
		Iterator<Edge> edgeIterator;
		
		public EdgeIterator()
		{
			edgeIterator = edges.values().iterator();
		}
		
		public boolean hasNext()
		{
			return edgeIterator.hasNext();
		}

		public Edge next()
		{
			return edgeIterator.next();
		}

		public void remove()
		{
			throw new UnsupportedOperationException( "this iterator does not allow removing" );
		}
	}

	
	public class NodeIterator implements Iterator<Node>
	{
		Iterator<Node> nodeIterator;
		
		public NodeIterator()
		{
			nodeIterator = nodes.values().iterator();
		}

		public boolean hasNext()
		{
			return (nodeIterator.hasNext());
		}

		public Node next()
		{
			return  nodeIterator.next();
		}

		public void remove()
		{
			throw new UnsupportedOperationException( "this iterator does not allow removing" );
		}
	}

	/**
	 * All the nodes.
	 */
	protected HashMap<String,Node> nodes = new HashMap<String, Node>();

	/**
	 * All the edges.
	 */
	protected HashMap<String,Edge> edges = new HashMap<String, Edge>();
	
	/**
	 * Verify name space conflicts, removal of non-existing elements, use of
	 * non-existing elements.
	 */
	protected boolean strictChecking = true;

	/**
	 * Automatically create missing elements. For example, if an edge is created
	 * between two non-existing nodes, create the nodes.
	 */
	protected boolean autoCreate = false;

	
	/**
	 *  Help full class that dynamically instantiate nodes according to a given class name.
	 */
	protected NodeFactory nodeFactory;
	
	/**
	 *  Help full class that dynamically instantiate edges according to a given class name.
	 */
	protected EdgeFactory edgeFactory;
	
	/**
	 * The current step.
	 */
	protected double step;

	/**
	 * The set of listeners.
	 */
	protected GraphListeners listeners;
	
// Constructors

	/**
	 * New empty graph, with the empty string as default identifier.
	 * @see #AdjacencyListGraph(String)
	 * @see #AdjacencyListGraph(boolean, boolean)
	 * @see #AdjacencyListGraph(String, boolean, boolean) 
	 */
	@Deprecated
	public AdjacencyListGraph()
	{
		this( "AdjListGraph" );
	}
	
	/**
	 * New empty graph.
	 * @param id Unique identifier of the graph.
	 * @see #AdjacencyListGraph(boolean, boolean)
	 * @see #AdjacencyListGraph(String, boolean, boolean)
	 */
	public AdjacencyListGraph( String id )
	{
		this( id, true, false );
	}

	/**
	 * New empty graph, with the empty string as default identifier.
	 * @param strictChecking If true any non-fatal error throws an exception.
	 * @param autoCreate If true (and strict checking is false), nodes are
	 *        automatically created when referenced when creating a edge, even
	 *        if not yet inserted in the graph.
	 * @see #AdjacencyListGraph(String, boolean, boolean)
	 * @see #setStrict(boolean)
	 * @see #setAutoCreate(boolean)
	 */
	@Deprecated
	public AdjacencyListGraph( boolean strictChecking, boolean autoCreate )
	{
		this( "", strictChecking, autoCreate );
	}
	
	/**
	 * New empty graph.
	 * @param id Unique identifier of this graph.
	 * @param strictChecking If true any non-fatal error throws an exception.
	 * @param autoCreate If true (and strict checking is false), nodes are
	 *        automatically created when referenced when creating a edge, even
	 *        if not yet inserted in the graph.
	 * @see #setStrict(boolean)
	 * @see #setAutoCreate(boolean)
	 */
	public AdjacencyListGraph( String id, boolean strictChecking, boolean autoCreate )
	{
		super( id );
		setStrict( strictChecking );
		setAutoCreate( autoCreate );
		
		listeners  = new GraphListeners();
		
		nodeFactory = new NodeFactory()
		{
			public Node newInstance( String id, Graph graph )
			{
				return new AdjacencyListNode(graph,id);
			}
		};
		edgeFactory = new EdgeFactory()
		{
			public Edge newInstance( String id, Node src, Node trg, boolean directed )
			{
				return new AdjacencyListEdge(id,src,trg,directed);
			}
		};
	}
	
	@Override
	protected String myGraphId()		// XXX
	{
		return getId();
	}
	
	@Override
	protected long newEvent()			// XXX
	{
		return listeners.newEvent();
	}

	public EdgeFactory edgeFactory()
	{
		return edgeFactory;
	}
	
	public void setEdgeFactory( EdgeFactory ef )
	{
		this.edgeFactory = ef;
	}

	public NodeFactory nodeFactory()
	{
		return nodeFactory;
	}
	
	public void setNodeFactory( NodeFactory nf )
	{
		this.nodeFactory = nf;
	}

	public Edge addEdge( String id, String node1, String node2 ) throws IdAlreadyInUseException, ElementNotFoundException
	{
		return addEdge( id, node1, node2, false );
	}

	protected Edge addEdge_( String sourceId, long timeId, String edgeId, String from, String to, boolean directed ) throws IdAlreadyInUseException, ElementNotFoundException
	{
		Node src;
		Node trg;

		src =  lookForNode( from );
		trg =  lookForNode( to );

		if( src == null )
		{
			if( strictChecking )
			{
				throw new ElementNotFoundException( "cannot make edge from '" + from + "' to '" + to + "' since node '" + from
						+ "' is not part of this graph" );
			}
			else if( autoCreate )
			{
				src = addNode( from );
			}
		}

		if( trg == null )
		{
			if( strictChecking )
			{
				throw new ElementNotFoundException( "cannot make edge from '" + from + "' to '" + to + "' since node '" + to
						+ "' is not part of this graph" );
			}
			else if( autoCreate )
			{
				trg = addNode( to );
			}
		}

		if( src != null && trg != null )
		{
			Edge edge = null;
			Edge old = lookForEdge( edgeId );
			if( old != null )
			{
				if( strictChecking )
				{
					throw new IdAlreadyInUseException( "id '" + edgeId + "' already used, cannot add edge" );
				}
				else
				{
					edge = old;
				}
			}
			else
			{
				if( ( (AdjacencyListNode) src ).hasEdgeToward( trg ) != null )
				{
					throw new IdAlreadyInUseException( "Cannot add edge between " + from + " and " + to + ". A link already exists." );
				}
				else
				{
					edge = edgeFactory.newInstance(edgeId,src,trg,directed);
					//edge.setDirected( directed );
					
					edges.put( edgeId,edge );
					((AdjacencyListNode)src).edges.add( edge );
					((AdjacencyListNode)trg).edges.add( edge );
					listeners.sendEdgeAdded( sourceId, timeId, edgeId, from, to, directed );
				}
			}
			return edge;
		}

		return null;
	}

	/**
	 * @complexity O(log(n)) with n be the number of edges in the graph.
	 */
	public Edge addEdge( String id, String from, String to, boolean directed ) throws IdAlreadyInUseException, ElementNotFoundException
	{
		Edge e = addEdge_( getId(), newEvent(), id, from, to, directed );
		return e;
	}

	/**
	 * @complexity O(log(n)) with n be the number of nodes in the graph.
	 */
	public Node addNode( String id ) throws IdAlreadyInUseException
	{
		Node n = addNode_( getId(), newEvent(), id );
		
		return n;
	}

	protected Node addNode_( String sourceId, long timeId, String nodeId ) throws IdAlreadyInUseException
	{
		Node node;
		Node old = lookForNode( nodeId );
		if( old != null )
		{
			if( strictChecking )
			{
				throw new IdAlreadyInUseException( "id '" + nodeId + "' already used, cannot add node" );
			}
			else
			{
				node = old;
			}
		}
		else
		{
			node = nodeFactory.newInstance(nodeId,this);
			
			nodes.put(nodeId, node );
			listeners.sendNodeAdded( sourceId, timeId, nodeId );
		}

		return node;
	}

	/**
	 * @complexity O(1).
	 */
	public void clear()
	{
		clear_( getId(), newEvent() );
	}
	
	protected void clear_( String sourceId, long timeId )
	{
		listeners.sendGraphCleared( sourceId, timeId );
		nodes.clear();
		edges.clear();
	}

	/**
	 * @complexity O(1).
	 */
	public void clearSinks()
	{
		listeners.clearSinks();
	}
	
	public void clearAttributeSinks()
	{
		listeners.clearAttributeSinks();
	}
	
	public void clearElementSinks()
	{
		listeners.clearElementSinks();
	}

	/**
	 * @complexity O(log(n)) with n be the number of edges in the graph.
	 */
	public Edge getEdge( String id )
	{
		return lookForEdge(id);
	}

	/**
	 * @complexity constant
	 */
	public int getEdgeCount()
	{
		return edges.size();
	}

	/**
	 * @complexity constant
	 */
	public Iterator<Edge> getEdgeIterator()
	{
		return new EdgeIterator();
	}

	/**
	 * @complexity Constant.
	 */
	public Iterable<Edge> edgeSet()
	{
		return edges.values();
	}

	/**
	 * @complexity O(log(n)) with n be the number of nodes in the graph.
	 */
	public Node getNode( String id )
	{
		return lookForNode( id );
	}

	/**
	 * @complexity Constant.
	 */
	public int getNodeCount()
	{
		return nodes.size();
	}

	/**
	 * @complexity Constant.
	 */
	public Iterator<Node> getNodeIterator()
	{
		return new NodeIterator();
	}
	
	public Iterator<Node> iterator()
	{
		return new NodeIterator();
	}

	/**
	 * @complexity Constant.
	 */
	public Iterable<Node> nodeSet()
	{
		return nodes.values();
	}

	public boolean isAutoCreationEnabled()
	{
		return autoCreate;
	}

	public boolean isStrict()
	{
		return strictChecking;
	}

	public Iterable<AttributeSink> attributeSinks()
	{
		return listeners.attributeSinks();
	}
	
	public Iterable<ElementSink> elementSinks()
	{
		return listeners.elementSinks();
	}
	
	public double getStep()
	{
		return step;
	}

	/**
	 * @complexity O( 2*log(n)+log(m) ) with n the number of nodes and m the number of edges in the graph.
	 */
	public Edge removeEdge( String from, String to ) throws ElementNotFoundException
	{
		return removeEdge_( getId(), newEvent(), from, to );
	}
	
	protected Edge removeEdge_( String sourceId, long timeId, String from, String to )
	{
		Node n0 = lookForNode( from );
		Node n1 = lookForNode( to );

		if( n0 != null && n1 != null )
		{
			Edge e = ( (AdjacencyListNode) n0 ).hasEdgeToward( n1 );
			if( e != null )
			{
				return removeEdge_( sourceId, timeId, e);
			}
			else
			{
				e = ( (AdjacencyListNode) n0 ).hasEdgeToward( n1 );
				if( e != null )
				{
					return removeEdge_( sourceId, timeId, e);
				}
			}
		}
		return null;
	}

	/**
	 * @complexity O( 2*log(m) ) with  m the number of edges in the graph.
	 */
	public Edge removeEdge( String id ) throws ElementNotFoundException
	{
		Edge edge = lookForEdge( id );
		
		if( edge != null )
			removeEdge_( getId(), newEvent(), edge );
		
		return edge;
	}

	/**
	 * Removes an edge from a given reference to it.
	 * @param edge The reference of the edge to remove.
	 * @complexity O( log(m) ) with  m the number of edges in the graph.
	 */
	public Edge removeEdge( Edge edge ) throws ElementNotFoundException
	{
		return removeEdge_( getId(), newEvent(), edge );
	}
	
	protected Edge removeEdge_( String sourceId, long timeId, Edge edge )
	{
		listeners.sendEdgeRemoved( sourceId, timeId, edge.getId() );
		
		Node n0 = edge.getSourceNode();
		Node n1 = edge.getTargetNode();
		
		( (AdjacencyListNode) n0 ).edges.remove( edge );
		( (AdjacencyListNode) n1 ).edges.remove( edge );
		edges.remove( edge.getId() );
	
		return edge;
	}

	/**
	 * @complexity 0( 2*log(n) ) with n the number of nodes in the graph.
	 */
	public Node removeNode( String id ) throws ElementNotFoundException
	{
		Node node = lookForNode( id );
		if( node != null )
		{
//System.err.printf( "%s.removeNode( %s )%n", getId(), id );
			return removeNode_( getId(), newEvent(), node );
		}
		return null;
	}

	/**
	 * Remove a node form a given reference of it.
	 * @param node The reference of the node to be removed.
	 * @complexity 0( log(n) ) with n the number of nodes in the graph.
	 */
	public Node removeNode( Node node ) throws ElementNotFoundException
	{
		return removeNode_( getId(), newEvent(), node );
	}
	
	protected Node removeNode_( String sourceId, long timeId, Node node )
	{
		if( node != null )
		{
//System.err.printf( "%s.removeNode_(%s, %d, %s)%n", getId(), sourceId, timeId, node.getId() );
			listeners.sendNodeRemoved( sourceId, timeId, node.getId() );
			disconnectEdges( node );
			nodes.remove( node.getId() );

			return node;
		}

		if( strictChecking )
			throw new ElementNotFoundException( "node not found, cannot remove" );

		return null;
	}

	public void stepBegins( double step )
	{
		stepBegins_( getId(), newEvent(), step );
	}
	
	protected void stepBegins_( String sourceId, long timeId, double step )
	{
		this.step = step;
		
		listeners.sendStepBegins( sourceId, timeId, step );
	}
	
	/**
	 * When a node is unregistered from a graph, it must not keep edges
	 * connected to nodes still in the graph. This methods unbind all edges
	 * connected to this node (this also unregister them from the graph).
	 */
	protected void disconnectEdges( Node node ) throws IllegalStateException
	{
		int n = node.getDegree();

		// We cannot use a "for" since unbinding an edge removes this edge from
		// the node. The number of edges will change continuously.

		while( n > 0 )
		{
			Edge e = ((AdjacencyListNode)node).edges.get( 0 );
			removeEdge( e );
			n = node.getDegree(); //edges.size(); ???
		}
	}

	public void setAutoCreate( boolean on )
	{
		autoCreate = on;
	}

	public void setStrict( boolean on )
	{
		strictChecking = on;
	}

	/**
	 * Tries to retrieve a node in the internal structure identified by the given string.
	 * @param id The string identifier of the seek node.
	 * @complexity 0( log(n) ), with n the number of nodes;
	 */
	protected Node lookForNode( String id )
	{
		return nodes.get( id );
	}

	/**
	 * 
	 * Tries to retrieve an edge in the internal structure identified by the given string.
	 * @param id The string identifier of the seek edges.
	 * @complexity 0( log(m) ), with m the number of edges;
	 */
	protected Edge lookForEdge( String id )
	{
		return edges.get( id );
	}

	
// Events

	public void addSink( Sink listener )
	{
		listeners.addSink( listener );
	}
	
	public void addAttributeSink( AttributeSink listener )
	{
		listeners.addAttributeSink( listener );
	}
	
	public void addElementSink( ElementSink listener )
	{
		listeners.addElementSink( listener );
	}
	
	public void removeSink( Sink listener )
	{
		listeners.removeSink( listener );
	}
	
	public void removeAttributeSink( AttributeSink listener )
	{
		listeners.removeAttributeSink( listener );
	}
	
	public void removeElementSink( ElementSink listener )
	{
		listeners.removeElementSink( listener );
	}

	@Override
	protected void attributeChanged( String sourceId, long timeId, String attribute, AttributeChangeEvent event, Object oldValue, Object newValue )
	{
		listeners.sendAttributeChangedEvent( sourceId, timeId, getId(),
				ElementType.GRAPH, attribute, event, oldValue, newValue );
	}
/*
	public void graphCleared()
    {
		clear_( getId(), newEvent() );
    }
*/
// Commands -- Utility

	public void read( FileSource input, String filename ) throws IOException, GraphParseException
    {
		input.readAll( filename );
    }

	public void read( String filename )
		throws IOException, GraphParseException, ElementNotFoundException
	{
		FileSource input = FileSourceFactory.sourceFor( filename );
		input.addSink( this );
		read( input, filename );
	}
	
	public void write( FileSink output, String filename ) throws IOException
    {
		output.writeAll( this, filename );
    }
	
	public void write( String filename )
		throws IOException
	{
		FileSink output = FileSinkFactory.sinkFor( filename );
		write( output, filename );
	}	

	public GraphViewerRemote oldDisplay()
	{
		return oldDisplay( true );
	}

	public GraphViewerRemote oldDisplay( boolean autoLayout )
	{
		try
        {
			Class<?> clazz = Class.forName( "org.miv.graphstream.ui.swing.SwingGraphViewer" );
	        Object object = clazz.newInstance();
	        
	        if( object instanceof GraphViewer )
	        {
	        	GraphViewer gv = (GraphViewer) object;
	        	
	        	gv.open(  this, autoLayout );
	        	
	        	return gv.newViewerRemote();
	        }
	        else
	        {
	        	System.err.printf( "not a GraphViewer\n", object == null ? "" : object.getClass().getName() );
	        }
        }
        catch( ClassNotFoundException e )
        {
	        e.printStackTrace();
        	System.err.printf( "Cannot display graph, GraphViewer class not found : " + e.getMessage() );
        }
        catch( IllegalAccessException e )
        {
        	e.printStackTrace();
        }
        catch( InstantiationException e )
        {
        	e.printStackTrace();
        }
        
        return null;
	}

	public Viewer display()
	{
		return display( true );
	}
	
	public Viewer display( boolean autoLayout )
	{
		Viewer        viewer   = new Viewer( this, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD );
		GraphRenderer renderer = newGraphRenderer();
		
		viewer.addView( String.format( "defaultView_%d", (long)(Math.random()*10000) ), renderer );
	
		if( autoLayout )
		{
			Layout layout = newLayoutAlgorithm();
			viewer.enableAutoLayout( layout );
		}
		
		return viewer;
	}
	
	protected static Layout newLayoutAlgorithm()
	{
		String layoutClassName = System.getProperty( "gs.ui.layout" );
		
		if( layoutClassName == null )
			return new org.graphstream.ui2.layout.springbox.SpringBox( false );
		
		try
        {
	        Class<?> c      = Class.forName( layoutClassName );
	        Object   object = c.newInstance();
	        
	        if( object instanceof Layout )
	        {
	        	return (Layout) object;
	        }
	        else
	        {
	        	System.err.printf( "class '%s' is not a 'GraphRenderer'%n", object );
	        }
        }
        catch( ClassNotFoundException e )
        {
	        e.printStackTrace();
        	System.err.printf( "Cannot create layout, 'GraphRenderer' class not found : " + e.getMessage() );
        }
        catch( InstantiationException e )
        {
            e.printStackTrace();
        	System.err.printf( "Cannot create layout, class '"+layoutClassName+"' error : " + e.getMessage() );
        }
        catch( IllegalAccessException e )
        {
            e.printStackTrace();
        	System.err.printf( "Cannot create layout, class '"+layoutClassName+"' illegal access : " + e.getMessage() );
        }

		return new org.graphstream.ui2.layout.springbox.SpringBox( false );
	}
	
	protected static GraphRenderer newGraphRenderer()
	{
		String rendererClassName = System.getProperty( "gs.ui.renderer" );
		
		if( rendererClassName == null )
			return new SwingBasicGraphRenderer();
		
		try
        {
	        Class<?> c      = Class.forName( rendererClassName );
	        Object   object = c.newInstance();
	        
	        if( object instanceof GraphRenderer )
	        {
	        	return (GraphRenderer) object;
	        }
	        else
	        {
	        	System.err.printf( "class '%s' is not a 'GraphRenderer'%n", object );
	        }
        }
        catch( ClassNotFoundException e )
        {
	        e.printStackTrace();
        	System.err.printf( "Cannot create graph renderer, 'GraphRenderer' class not found : " + e.getMessage() );
        }
        catch( InstantiationException e )
        {
            e.printStackTrace();
        	System.err.printf( "Cannot create graph renderer, class '"+rendererClassName+"' error : " + e.getMessage() );
        }
        catch( IllegalAccessException e )
        {
            e.printStackTrace();
        	System.err.printf( "Cannot create graph renderer, class '"+rendererClassName+"' illegal access : " + e.getMessage() );
        }

    	return new SwingBasicGraphRenderer();
	}

// Sink

	public void edgeAdded( String sourceId, long timeId, String edgeId, String fromNodeId, String toNodeId,
            boolean directed )
    {
		listeners.edgeAdded(sourceId, timeId, edgeId, fromNodeId, toNodeId, directed);
    }

	public void edgeRemoved( String sourceId, long timeId, String edgeId )
    {
		listeners.edgeRemoved(sourceId, timeId, edgeId);
    }

	public void graphCleared( String sourceId, long timeId )
    {
		listeners.graphCleared(sourceId, timeId);
    }

	public void nodeAdded( String sourceId, long timeId, String nodeId )
    {
		listeners.nodeAdded(sourceId, timeId, nodeId);
    }

	public void nodeRemoved( String sourceId, long timeId, String nodeId )
    {
		listeners.nodeRemoved(sourceId, timeId, nodeId);
    }

	public void stepBegins( String sourceId, long timeId, double step )
    {
		listeners.stepBegins(sourceId, timeId, step);
    }

	public void edgeAttributeAdded( String sourceId, long timeId, String edgeId, String attribute, Object value )
    {
		listeners.edgeAttributeAdded(sourceId, timeId, edgeId, attribute, value);
    }

	public void edgeAttributeChanged( String sourceId, long timeId, String edgeId, String attribute,
            Object oldValue, Object newValue )
    {
		listeners.edgeAttributeChanged(sourceId, timeId, edgeId, attribute, oldValue, newValue);
    }

	public void edgeAttributeRemoved( String sourceId, long timeId, String edgeId, String attribute )
    {
		listeners.edgeAttributeRemoved(sourceId, timeId, edgeId, attribute);
    }

	public void graphAttributeAdded( String sourceId, long timeId, String attribute, Object value )
    {
		listeners.graphAttributeAdded(sourceId, timeId, attribute, value);
    }

	public void graphAttributeChanged( String sourceId, long timeId, String attribute, Object oldValue,
            Object newValue )
    {
		listeners.graphAttributeChanged(sourceId, timeId, attribute, oldValue, newValue);
    }

	public void graphAttributeRemoved( String sourceId, long timeId, String attribute )
    {
		listeners.graphAttributeRemoved(sourceId, timeId, attribute);
    }

	public void nodeAttributeAdded( String sourceId, long timeId, String nodeId, String attribute, Object value )
    {
		listeners.nodeAttributeAdded(sourceId, timeId, nodeId, attribute, value);
    }

	public void nodeAttributeChanged( String sourceId, long timeId, String nodeId, String attribute,
            Object oldValue, Object newValue )
    {
		listeners.nodeAttributeChanged(sourceId, timeId, nodeId, attribute, oldValue, newValue);
    }

	public void nodeAttributeRemoved( String sourceId, long timeId, String nodeId, String attribute )
    {
		listeners.nodeAttributeRemoved(sourceId, timeId, nodeId, attribute);
    }

	// Handling the listeners -- We use the IO2 InputBase for this.
		
	class GraphListeners
		extends SourceBase
		implements Pipe
	{
		SinkTime sinkTime;

		public GraphListeners()
		{
			super( getId() );

			sinkTime = new SinkTime();
			sourceTime.setSinkTime(sinkTime);
		}
		
		protected long newEvent()
		{
			return sourceTime.newEvent();
		}

		public void edgeAttributeAdded(String sourceId, long timeId,
				String edgeId, String attribute, Object value) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				Edge edge = getEdge( edgeId );
				
				if( edge != null )
					((AdjacencyListEdge)edge).addAttribute_( sourceId, timeId, attribute, value );
			}
		}

		public void edgeAttributeChanged(String sourceId, long timeId,
				String edgeId, String attribute, Object oldValue,
				Object newValue) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				Edge edge = getEdge( edgeId );
				
				if( edge != null )
					((AdjacencyListEdge)edge).changeAttribute_( sourceId, timeId, attribute, newValue );
			}
		}

		public void edgeAttributeRemoved(String sourceId, long timeId,
				String edgeId, String attribute) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				Edge edge = getEdge( edgeId );
				
				if( edge != null )
					((AdjacencyListEdge)edge).removeAttribute_( sourceId, timeId, attribute );
			}
		}

		public void graphAttributeAdded(String sourceId, long timeId,
				String attribute, Object value) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				addAttribute_( sourceId, timeId, attribute, value );
			}
		}

		public void graphAttributeChanged(String sourceId, long timeId,
				String attribute, Object oldValue, Object newValue) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				changeAttribute_( sourceId, timeId, attribute, newValue );
			}
		}

		public void graphAttributeRemoved(String sourceId, long timeId,
				String attribute) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				removeAttribute_( sourceId, timeId, attribute );
			}
		}

		public void nodeAttributeAdded(String sourceId, long timeId,
				String nodeId, String attribute, Object value) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				Node node = getNode( nodeId );
				
				if( node != null )
					((AdjacencyListNode)node).addAttribute_( sourceId, timeId, attribute, value );
			}
		}

		public void nodeAttributeChanged(String sourceId, long timeId,
				String nodeId, String attribute, Object oldValue,
				Object newValue) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				Node node = getNode( nodeId );
				
				if( node != null )
					((AdjacencyListNode)node).changeAttribute_( sourceId, timeId, attribute, newValue );
			}
		}

		public void nodeAttributeRemoved(String sourceId, long timeId,
				String nodeId, String attribute) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				Node node = getNode( nodeId );

				if( node != null )
					((AdjacencyListNode)node).removeAttribute_( sourceId, timeId, attribute );
			}
		}

		public void edgeAdded(String sourceId, long timeId, String edgeId,
				String fromNodeId, String toNodeId, boolean directed) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				addEdge_( sourceId, timeId, edgeId, fromNodeId, toNodeId, directed );
			}
		}

		public void edgeRemoved(String sourceId, long timeId, String edgeId) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				Edge e = getEdge( edgeId );
				
				if( e != null )
					removeEdge_( sourceId, timeId, getEdge( edgeId ) );
			}
		}

		public void graphCleared(String sourceId, long timeId) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				clear_( sourceId, timeId );
			}
		}

		public void nodeAdded(String sourceId, long timeId, String nodeId) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				addNode_( sourceId, timeId, nodeId );
			}
		}

		public void nodeRemoved(String sourceId, long timeId, String nodeId) {
//System.err.printf( "%s.nodeRemoved( %s, %d, %s ) => ", getId(), sourceId, timeId, nodeId );
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				Node n = getNode( nodeId );
				
				if( n != null )
				{
//System.err.printf( "=> removed%n" );
					removeNode_( sourceId, timeId, n );
				}
			}
//else System.err.printf( "=> ignored%n" );
		}

		public void stepBegins(String sourceId, long timeId, double step) {
			if( sinkTime.isNewEvent(sourceId, timeId) )
			{
				stepBegins_( sourceId, timeId, step );
			}
		}
	}
}