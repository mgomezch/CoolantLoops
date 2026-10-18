package com.gtnewhorizons.coolantloops.common.gui.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphData.GraphEdge;
import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphData.GraphNode;

/**
 * Pure Java implementation of the Sugiyama / Gansner et al. (DOT) hierarchical DAG layout.
 *
 * Implements the 4 classic phases:
 * 1. Layer/Rank assignment (longest path with dummy node waypoint insertion for multi-span edges).
 * 2. Crossing minimization (two-layer sweep with barycenter heuristic).
 * 3. Left-to-right coordinate assignment with straight-line relaxation.
 * 4. Polyline edge routing with directional flow indicator chevrons.
 */
public class SugiyamaLayout {

    public static final double DEFAULT_NODE_WIDTH = 64.0;
    public static final double DEFAULT_NODE_HEIGHT = 32.0;
    public static final double LAYER_SPACING_X = 130.0;
    public static final double NODE_SPACING_Y = 56.0;
    public static final double PADDING_X = 24.0;
    public static final double PADDING_Y = 24.0;

    public static class Point2D {

        public double x, y;

        public Point2D(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class LayoutNode {

        public final String id;
        public final boolean isDummy;
        public final GraphNode data;
        public int layer = 0;
        public int orderInLayer = 0;
        public double x = 0.0;
        public double y = 0.0;
        public double width = DEFAULT_NODE_WIDTH;
        public double height = DEFAULT_NODE_HEIGHT;

        public LayoutNode(String id, boolean isDummy, GraphNode data) {
            this.id = id;
            this.isDummy = isDummy;
            this.data = data;
            if (isDummy) {
                this.width = 0.0;
                this.height = 0.0;
            }
        }
    }

    public static class LayoutEdge {

        public final String id;
        public final String fromNodeId;
        public final String toNodeId;
        public final GraphEdge data;
        public final List<Point2D> waypoints = new ArrayList<>();
        public final List<Point2D> arrowPoints = new ArrayList<>(); // tip, left wing, right wing
        public final List<String> dummyNodeIds = new ArrayList<>();

        public LayoutEdge(String id, String fromNodeId, String toNodeId, GraphEdge data) {
            this.id = id;
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
            this.data = data;
        }
    }

    private final List<LayoutNode> nodes = new ArrayList<>();
    private final List<LayoutEdge> edges = new ArrayList<>();
    private final Map<String, LayoutNode> nodeMap = new LinkedHashMap<>();
    private double boundsWidth = 200.0;
    private double boundsHeight = 150.0;

    public List<LayoutNode> getNodes() {
        return nodes;
    }

    public List<LayoutEdge> getEdges() {
        return edges;
    }

    public double getBoundsWidth() {
        return boundsWidth;
    }

    public double getBoundsHeight() {
        return boundsHeight;
    }

    public LayoutNode getNode(String id) {
        return nodeMap.get(id);
    }

    /**
     * Executes the complete Sugiyama hierarchical layout on the provided graph data.
     */
    public void computeLayout(CoolantLoopGraphData graphData) {
        nodes.clear();
        edges.clear();
        nodeMap.clear();

        if (graphData == null || graphData.nodes == null || graphData.nodes.isEmpty()) {
            boundsWidth = 200.0;
            boundsHeight = 150.0;
            return;
        }

        // 1. Initialize real nodes
        for (GraphNode gn : graphData.nodes) {
            LayoutNode ln = new LayoutNode(gn.id, false, gn);
            nodes.add(ln);
            nodeMap.put(gn.id, ln);
        }

        // Build adjacency lists
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, List<String>> revAdj = new HashMap<>();
        for (LayoutNode ln : nodes) {
            adj.put(ln.id, new ArrayList<>());
            revAdj.put(ln.id, new ArrayList<>());
        }

        List<GraphEdge> rawEdges = graphData.edges != null ? graphData.edges : Collections.emptyList();
        for (GraphEdge ge : rawEdges) {
            if (nodeMap.containsKey(ge.fromNodeId) && nodeMap.containsKey(ge.toNodeId)) {
                adj.get(ge.fromNodeId)
                    .add(ge.toNodeId);
                revAdj.get(ge.toNodeId)
                    .add(ge.fromNodeId);
            }
        }

        // PHASE 1: Layer Assignment (Longest Path Ranking)
        assignLayers(adj, revAdj);

        // Sub-phase: Insert Dummy Nodes for long edges (span > 1)
        List<LayoutEdge> finalEdges = new ArrayList<>();
        int dummyCounter = 0;

        for (GraphEdge ge : rawEdges) {
            LayoutNode from = nodeMap.get(ge.fromNodeId);
            LayoutNode to = nodeMap.get(ge.toNodeId);
            if (from == null || to == null) continue;

            int span = to.layer - from.layer;
            LayoutEdge layoutEdge = new LayoutEdge(ge.id, ge.fromNodeId, ge.toNodeId, ge);

            if (span > 1) {
                // Remove direct edge from adj/revAdj
                List<String> fromAdj = adj.get(from.id);
                if (fromAdj != null) fromAdj.remove(to.id);
                List<String> toRev = revAdj.get(to.id);
                if (toRev != null) toRev.remove(from.id);

                // Insert dummy nodes
                LayoutNode prev = from;
                for (int i = 1; i < span; i++) {
                    String dummyId = "_dummy_" + (++dummyCounter);
                    LayoutNode dummy = new LayoutNode(dummyId, true, null);
                    dummy.layer = from.layer + i;
                    nodes.add(dummy);
                    nodeMap.put(dummyId, dummy);

                    layoutEdge.dummyNodeIds.add(dummyId);

                    adj.computeIfAbsent(prev.id, k -> new ArrayList<>())
                        .add(dummy.id);
                    revAdj.computeIfAbsent(dummy.id, k -> new ArrayList<>())
                        .add(prev.id);
                    prev = dummy;
                }
                adj.computeIfAbsent(prev.id, k -> new ArrayList<>())
                    .add(to.id);
                revAdj.computeIfAbsent(to.id, k -> new ArrayList<>())
                    .add(prev.id);
            }

            finalEdges.add(layoutEdge);
        }

        // Group nodes into layers
        int maxLayer = 0;
        for (LayoutNode ln : nodes) {
            if (ln.layer > maxLayer) maxLayer = ln.layer;
        }

        List<List<LayoutNode>> layers = new ArrayList<>(maxLayer + 1);
        for (int l = 0; l <= maxLayer; l++) {
            layers.add(new ArrayList<>());
        }
        for (LayoutNode ln : nodes) {
            layers.get(ln.layer)
                .add(ln);
        }

        // PHASE 2: Crossing Minimization (Barycenter Heuristic)
        minimizeCrossings(layers, adj, revAdj);

        // PHASE 3: Coordinate Assignment (Left-to-Right)
        assignCoordinates(layers, maxLayer, adj, revAdj);

        // PHASE 4: Edge Route Geometry Generation
        for (LayoutEdge le : finalEdges) {
            routeEdge(le, adj, rawEdges);
            edges.add(le);
        }
    }

    private void assignLayers(Map<String, List<String>> adj, Map<String, List<String>> revAdj) {
        // Find roots (in-degree 0)
        List<LayoutNode> sources = new ArrayList<>();
        for (LayoutNode ln : nodes) {
            if (revAdj.get(ln.id)
                .isEmpty() || (ln.data != null && "SOURCE".equals(ln.data.nodeType))) {
                sources.add(ln);
                ln.layer = 0;
            }
        }
        if (sources.isEmpty() && !nodes.isEmpty()) {
            sources.add(nodes.get(0));
            nodes.get(0).layer = 0;
        }

        // Topological longest path ranking
        Set<String> visited = new HashSet<>();
        List<LayoutNode> topoOrder = new ArrayList<>();
        for (LayoutNode src : sources) {
            dfsTopo(src.id, adj, visited, topoOrder);
        }
        for (LayoutNode ln : nodes) {
            if (!visited.contains(ln.id)) {
                dfsTopo(ln.id, adj, visited, topoOrder);
            }
        }
        Collections.reverse(topoOrder);

        for (LayoutNode u : topoOrder) {
            for (String vId : adj.get(u.id)) {
                LayoutNode v = nodeMap.get(vId);
                if (v != null && v.layer <= u.layer) {
                    v.layer = u.layer + 1;
                }
            }
        }

        // Ensure SINK is at the final layer
        int maxNonSinkLayer = 0;
        LayoutNode sinkNode = null;
        for (LayoutNode ln : nodes) {
            if (ln.data != null && "SINK".equals(ln.data.nodeType)) {
                sinkNode = ln;
            } else if (ln.layer > maxNonSinkLayer) {
                maxNonSinkLayer = ln.layer;
            }
        }
        if (sinkNode != null && sinkNode.layer <= maxNonSinkLayer) {
            sinkNode.layer = maxNonSinkLayer + 1;
        }
    }

    private void dfsTopo(String curr, Map<String, List<String>> adj, Set<String> visited, List<LayoutNode> order) {
        if (!visited.add(curr)) return;
        List<String> nexts = adj.get(curr);
        if (nexts != null) {
            for (String nxt : nexts) {
                dfsTopo(nxt, adj, visited, order);
            }
        }
        order.add(nodeMap.get(curr));
    }

    private void minimizeCrossings(List<List<LayoutNode>> layers, Map<String, List<String>> adj,
        Map<String, List<String>> revAdj) {
        int maxLayer = layers.size() - 1;
        int iterations = 8;

        for (int iter = 0; iter < iterations; iter++) {
            // Downward sweep
            for (int l = 1; l <= maxLayer; l++) {
                final List<LayoutNode> prevLayer = layers.get(l - 1);
                Map<String, Integer> prevPos = new HashMap<>();
                for (int i = 0; i < prevLayer.size(); i++) {
                    prevPos.put(prevLayer.get(i).id, i);
                }

                List<LayoutNode> currLayer = layers.get(l);
                for (LayoutNode node : currLayer) {
                    List<String> preds = revAdj.get(node.id);
                    double sum = 0.0;
                    int count = 0;
                    if (preds != null) {
                        for (String pId : preds) {
                            Integer pPos = prevPos.get(pId);
                            if (pPos != null) {
                                sum += pPos;
                                count++;
                            }
                        }
                    }
                    double bary = count > 0 ? (sum / count) : currLayer.indexOf(node);
                    node.orderInLayer = (int) (bary * 1000.0);
                }

                currLayer.sort(Comparator.comparingInt(n -> n.orderInLayer));
            }

            // Upward sweep
            for (int l = maxLayer - 1; l >= 0; l--) {
                final List<LayoutNode> nextLayer = layers.get(l + 1);
                Map<String, Integer> nextPos = new HashMap<>();
                for (int i = 0; i < nextLayer.size(); i++) {
                    nextPos.put(nextLayer.get(i).id, i);
                }

                List<LayoutNode> currLayer = layers.get(l);
                for (LayoutNode node : currLayer) {
                    List<String> succs = adj.get(node.id);
                    double sum = 0.0;
                    int count = 0;
                    if (succs != null) {
                        for (String sId : succs) {
                            Integer sPos = nextPos.get(sId);
                            if (sPos != null) {
                                sum += sPos;
                                count++;
                            }
                        }
                    }
                    double bary = count > 0 ? (sum / count) : currLayer.indexOf(node);
                    node.orderInLayer = (int) (bary * 1000.0);
                }

                currLayer.sort(Comparator.comparingInt(n -> n.orderInLayer));
            }
        }

        // Set final order index
        for (List<LayoutNode> layer : layers) {
            for (int i = 0; i < layer.size(); i++) {
                layer.get(i).orderInLayer = i;
            }
        }
    }

    private void assignCoordinates(List<List<LayoutNode>> layers, int maxLayer, Map<String, List<String>> adj,
        Map<String, List<String>> revAdj) {
        int maxNodesInLayer = 1;
        for (List<LayoutNode> layer : layers) {
            if (layer.size() > maxNodesInLayer) maxNodesInLayer = layer.size();
        }

        double totalGraphH = Math.max(160.0, maxNodesInLayer * NODE_SPACING_Y + PADDING_Y * 2);

        // 1. Initial layer-based positions
        for (int l = 0; l <= maxLayer; l++) {
            List<LayoutNode> layer = layers.get(l);
            double layerX = PADDING_X + l * LAYER_SPACING_X;
            int count = layer.size();
            double layerH = count * NODE_SPACING_Y;
            double startY = (totalGraphH - layerH) / 2.0;

            for (int i = 0; i < count; i++) {
                LayoutNode node = layer.get(i);
                node.x = layerX;
                node.y = startY + i * NODE_SPACING_Y + (NODE_SPACING_Y - node.height) / 2.0;
            }
        }

        // 2. Fine-tuning relaxation (aligning nodes with neighbor average Y for straight lines)
        for (int pass = 0; pass < 4; pass++) {
            for (int l = 1; l < maxLayer; l++) {
                List<LayoutNode> layer = layers.get(l);
                for (int i = 0; i < layer.size(); i++) {
                    LayoutNode node = layer.get(i);
                    double sumY = 0.0;
                    int count = 0;

                    List<String> preds = revAdj.get(node.id);
                    if (preds != null) {
                        for (String pId : preds) {
                            LayoutNode p = nodeMap.get(pId);
                            if (p != null) {
                                sumY += (p.y + p.height / 2.0);
                                count++;
                            }
                        }
                    }
                    List<String> succs = adj.get(node.id);
                    if (succs != null) {
                        for (String sId : succs) {
                            LayoutNode s = nodeMap.get(sId);
                            if (s != null) {
                                sumY += (s.y + s.height / 2.0);
                                count++;
                            }
                        }
                    }

                    if (count > 0) {
                        double targetY = (sumY / count) - (node.height / 2.0);
                        node.y = 0.5 * node.y + 0.5 * targetY;
                    }
                }

                // Enforce minimum separation
                for (int i = 0; i < layer.size() - 1; i++) {
                    LayoutNode curr = layer.get(i);
                    LayoutNode next = layer.get(i + 1);
                    double minDist = curr.height + 12.0;
                    if (next.y < curr.y + minDist) {
                        next.y = curr.y + minDist;
                    }
                }
            }
        }

        // Compute overall bounding box
        double maxX = 0.0;
        double maxY = 0.0;
        for (LayoutNode ln : nodes) {
            double rx = ln.x + ln.width;
            double ry = ln.y + ln.height;
            if (rx > maxX) maxX = rx;
            if (ry > maxY) maxY = ry;
        }

        boundsWidth = maxX + PADDING_X;
        boundsHeight = Math.max(totalGraphH, maxY + PADDING_Y);
    }

    private void routeEdge(LayoutEdge edge, Map<String, List<String>> adj, List<GraphEdge> rawEdges) {
        LayoutNode startNode = nodeMap.get(edge.fromNodeId);
        LayoutNode endNode = nodeMap.get(edge.toNodeId);
        if (startNode == null || endNode == null) return;

        edge.waypoints.clear();
        edge.arrowPoints.clear();

        // Start point: right center of startNode
        Point2D startPt = new Point2D(startNode.x + startNode.width, startNode.y + startNode.height / 2.0);
        edge.waypoints.add(startPt);

        // Intermediate dummy waypoints
        for (String dummyId : edge.dummyNodeIds) {
            LayoutNode dummy = nodeMap.get(dummyId);
            if (dummy != null) {
                edge.waypoints.add(new Point2D(dummy.x, dummy.y + DEFAULT_NODE_HEIGHT / 2.0));
            }
        }

        // End point: left center of endNode
        Point2D endPt = new Point2D(endNode.x, endNode.y + endNode.height / 2.0);
        edge.waypoints.add(endPt);

        // Generate directional chevron arrow at the midpoint of the longest segment
        generateDirectionalArrow(edge);
    }

    private void generateDirectionalArrow(LayoutEdge edge) {
        if (edge.waypoints.size() < 2) return;

        // Find longest line segment in waypoints
        double maxLen = -1.0;
        int bestIdx = 0;
        for (int i = 0; i < edge.waypoints.size() - 1; i++) {
            Point2D p1 = edge.waypoints.get(i);
            Point2D p2 = edge.waypoints.get(i + 1);
            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            double len = Math.hypot(dx, dy);
            if (len > maxLen) {
                maxLen = len;
                bestIdx = i;
            }
        }

        Point2D p1 = edge.waypoints.get(bestIdx);
        Point2D p2 = edge.waypoints.get(bestIdx + 1);
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double len = Math.hypot(dx, dy);
        if (len < 8.0) return;

        double dirX = dx / len;
        double dirY = dy / len;
        double normX = -dirY;
        double normY = dirX;

        double midX = (p1.x + p2.x) / 2.0;
        double midY = (p1.y + p2.y) / 2.0;

        double arrowLen = 5.0;
        double arrowHalfW = 3.5;

        Point2D tip = new Point2D(midX + dirX * 2.0, midY + dirY * 2.0);
        Point2D leftWing = new Point2D(
            midX - dirX * arrowLen + normX * arrowHalfW,
            midY - dirY * arrowLen + normY * arrowHalfW);
        Point2D rightWing = new Point2D(
            midX - dirX * arrowLen - normX * arrowHalfW,
            midY - dirY * arrowLen - normY * arrowHalfW);

        edge.arrowPoints.add(tip);
        edge.arrowPoints.add(leftWing);
        edge.arrowPoints.add(rightWing);
    }

    /**
     * Hit tests for a device node at coordinates (x, y).
     */
    public LayoutNode findNodeAt(double x, double y) {
        for (LayoutNode ln : nodes) {
            if (ln.isDummy) continue;
            if (x >= ln.x && x <= ln.x + ln.width && y >= ln.y && y <= ln.y + ln.height) {
                return ln;
            }
        }
        return null;
    }

    /**
     * Hit tests for an edge (pipe segment) at coordinates (x, y) within tolerance.
     */
    public LayoutEdge findEdgeAt(double x, double y, double tolerance) {
        for (LayoutEdge le : edges) {
            for (int i = 0; i < le.waypoints.size() - 1; i++) {
                Point2D p1 = le.waypoints.get(i);
                Point2D p2 = le.waypoints.get(i + 1);
                if (distancePointToSegment(x, y, p1.x, p1.y, p2.x, p2.y) <= tolerance) {
                    return le;
                }
            }
        }
        return null;
    }

    public static double distancePointToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double l2 = dx * dx + dy * dy;
        if (l2 == 0.0) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0.0, Math.min(1.0, ((px - x1) * dx + (py - y1) * dy) / l2));
        double projX = x1 + t * dx;
        double projY = y1 + t * dy;
        return Math.hypot(px - projX, py - projY);
    }
}
