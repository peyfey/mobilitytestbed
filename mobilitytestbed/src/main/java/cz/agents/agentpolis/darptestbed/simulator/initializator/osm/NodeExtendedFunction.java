package cz.agents.agentpolis.darptestbed.simulator.initializator.osm;

import java.util.Map;

import net.sf.javaml.core.kdtree.KDTree;

import com.vividsolutions.jts.geom.Coordinate;

import eu.superhub.wp4.simulator.initializator.osm.init.WGS84Convertor;

public class NodeExtendedFunction {

	private final Map<Long, Coordinate> projectedNodeCoordinats;
	private final KDTree kdTreeForAllNodes;
	private WGS84Convertor wgs84Convertor;

	public NodeExtendedFunction(Map<Long, Coordinate> projectedNodeCoordinats, KDTree kdTreeForAllNodes,
			WGS84Convertor wgs84Convertor) {
		super();
		this.projectedNodeCoordinats = projectedNodeCoordinats;
		this.kdTreeForAllNodes = kdTreeForAllNodes;
		this.wgs84Convertor = wgs84Convertor;
	}

	public long getNearestNodeByNodeId(double longitude, double latitude) {
		Coordinate coordinate = wgs84Convertor.convert(longitude, latitude);
		return (Long) kdTreeForAllNodes.nearest(new double[] { coordinate.x, coordinate.y });

	}

	public double computeDistanceBetweenNodes(long fromNodeId, long toNodeId) {
		Coordinate from = projectedNodeCoordinats.get(fromNodeId);
		Coordinate to = projectedNodeCoordinats.get(toNodeId);
		return from.distance(to);
	}

}