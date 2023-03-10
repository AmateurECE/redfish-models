package com.twardyece.dmtf.api;

import com.twardyece.dmtf.RustType;
import io.swagger.v3.oas.models.PathItem;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.Graphs;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;

import java.util.*;
import java.util.stream.Collectors;

public class PathMap {
    private Graph<TraitContext, DefaultEdge> graph;
    private TraitContext root;
    private TraitContextFactory factory;
    private static final PathNameTranslator pathName = new PathNameTranslator();

    public PathMap(HashMap<String, PathItem> paths, TraitContextFactory factory) {
        this.graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        this.factory = factory;

        List<String> sorted = paths.keySet().stream().sorted().collect(Collectors.toList());
        Map<String, ApiEndpoint> endpoints = new HashMap<>();
        Graph<ApiEndpoint, DefaultEdge> endpointGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        ApiEndpoint root = null;
        for (String path : sorted) {
            ApiEndpoint endpoint = addEndpoint(endpointGraph, endpoints, path, paths.get(path));
            if (null != endpoint) {
                root = endpoint;
            }
        }

        normalizeEndpoints(endpointGraph, root);
        this.transformToTraitContextGraph(endpointGraph, root);
    }

    private ApiEndpoint addEndpoint(Graph<ApiEndpoint, DefaultEdge> graph, Map<String, ApiEndpoint> endpoints,
                             String path, PathItem pathItem) {
        int endIndex = path.length();
        if ('/' == path.charAt(path.length() - 1)) {
            endIndex = path.length() - 1;
        }

        path = path.substring(0, endIndex);

        String name = pathName.translate(path, pathItem);
        // Use the last component of the path as the "summary".
        String[] components = path.split("/");
        ApiEndpoint current = new ApiEndpoint(name, components[components.length - 1], pathItem);
        graph.addVertex(current);

        ApiEndpoint root = null;
        if (!endpoints.isEmpty()) {
            String maxSubstring = "";
            for (String endpoint : endpoints.keySet()) {
                if (path.contains(endpoint) && endpoint.length() > maxSubstring.length() && !path.equals(endpoint)) {
                    maxSubstring = endpoint;
                }
            }

            ApiEndpoint previous = endpoints.get(maxSubstring);
            if (previous != null && !previous.equals(current)) {
                graph.addEdge(previous, current);
            }
        } else {
            root = current;
        }

        endpoints.put(path, current);
        return root;
    }

    private static void normalizeEndpoints(Graph<ApiEndpoint, DefaultEdge> graph, ApiEndpoint root) {
        List<ApiEndpoint> mountedEndpoints = new ArrayList<>();
        UnmountVertexTransformation transformation = new UnmountVertexTransformation();
        Iterator<ApiEndpoint> iterator = new DepthFirstIterator<>(graph, root);
        while (iterator.hasNext()) {
            ApiEndpoint endpoint = iterator.next();
            if (transformation.check(graph, endpoint)) {
                mountedEndpoints.add(endpoint);
            }
        }

        for (ApiEndpoint endpoint : mountedEndpoints) {
            transformation.perform(graph, endpoint, root);
        }
    }

    private void transformToTraitContextGraph(Graph<ApiEndpoint, DefaultEdge> endpointGraph, ApiEndpoint root) {
        Map<RustType, TraitContext> traits = new HashMap<>();
        this.graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        AllDirectedPaths<ApiEndpoint, DefaultEdge> pathFinder = new AllDirectedPaths<>(endpointGraph);
        Iterator<ApiEndpoint> iterator = new BreadthFirstIterator<>(endpointGraph, root);
        while (iterator.hasNext()) {
            ApiEndpoint endpoint = iterator.next();
            List<String> path = this.getPath(root, endpoint, pathFinder);
            TraitContext trait = this.factory.makeTraitContext(path, endpoint.getPathItem());
            traits.put(trait.rustType, trait);
            this.graph.addVertex(trait);
            if (null == this.root) {
                this.root = trait;
            }

            Graphs.predecessorListOf(endpointGraph, endpoint).stream()
                    .map((v) -> {
                        List<String> predecessor = this.getPath(root, v, pathFinder);
                        return this.factory.getRustType(predecessor);
                    }).forEach((p) -> this.graph.addEdge(traits.get(p), trait));
        }
    }

    private List<String> getPath(ApiEndpoint root, ApiEndpoint endpoint, AllDirectedPaths<ApiEndpoint, DefaultEdge> pathFinder) {
        List<GraphPath<ApiEndpoint, DefaultEdge>> paths = pathFinder.getAllPaths(root, endpoint, true, null);
        List<List<String>> apiPaths = paths.stream()
                .map((p) -> p.getVertexList()
                        .stream()
                        .map(ApiEndpoint::getSummary)
                        .collect(Collectors.toList()))
                .toList();
        if (apiPaths.size() > 1) {
            throw new RuntimeException("Normalization failed. Endpoint " + endpoint + " has multiple paths from root.");
        }

        return apiPaths.get(0);
    }

    public Graph<TraitContext, DefaultEdge> borrowGraph() { return this.graph; }
    public TraitContext getRoot() { return this.root; }

    public List<TraitContext> getTraits() {
        Map<String, TraitContext> traits = new HashMap<>();

        Iterator<TraitContext> iterator = new DepthFirstIterator<>(this.graph, this.root);
        while (iterator.hasNext()) {
            TraitContext trait = iterator.next();
            traits.put(trait.toString(), trait);

            // Add this trait as a submodule to the preceding traits
            Graphs.predecessorListOf(this.graph, trait)
                    .forEach((e) -> traits.get(e.toString()).moduleContext.addNamedSubmodule(trait.moduleContext.path.getLastComponent()));
        }

        return traits.values().stream().toList();
    }
}
