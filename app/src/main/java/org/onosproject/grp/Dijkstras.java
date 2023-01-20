package org.onosproject.grp;

/*
 * Implements dijkstras by accepting weights of predicted output
*/

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.lang.Math;
 
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;

public class Dijkstras {
    private final Logger log = getLogger(getClass());

    private float dist[];
    private int pr[];
    private PriorityQueue<Node> pq;
    private boolean vis[];
    List<Map<Integer,Float> > adj;
    private int nNode;
 
    
    Dijkstras(int v){
        // this.v = v;
        dist = new float[v];
        pr = new int[v];
        adj = new ArrayList<>(v);
        pq = new PriorityQueue<Node>(new nodeComparator());
        vis = new boolean[v];
        
        for(int i = 0; i < v; i++ ){
            adj.add(new HashMap<>());
        }

        nNode = v;
        reset();
        log.info("Dijsktra started");
    }
    
    // TODO : Does not reset all
    
    public void reset(){
        for(int i = 0; i < nNode; i++ ){
            pr[i] = -1;
            vis[i] = false;
        }
    }
    
    public void shortestPath(int s , int d){

        log.info(s + " " + d);
        reset();
        Arrays.fill(dist, Integer.MAX_VALUE);
        pq.add(new Node(s , 0));
        dist[s] = 0;

        int q = 0;
        for( Map<Integer,Float> i : adj){
            log.info(q + " " + i.toString());
            q += 1;
        }

        while(!pq.isEmpty()){
            Node n = pq.poll();
            int u = n.node;
            // checks if node is unvisited
            if( vis[u] ) continue;
            vis[u] = true;

            for( int v : adj.get(u).keySet() ){
                float w = adj.get(u).get(v);
                // if the distance to that node is less than what it can be reachced through u
                if( dist[v] > dist[u] + w ){
                    dist[v] = dist[u] + w;
                    pq.add(new Node(v, dist[v]));
                    pr[v] = u;
                }
            }
        }

        log.info("Distance Matrix " + Arrays.toString(dist));
        int st = d;
        List <Integer> pths = new ArrayList<>();
        while(st != s && st != -1){
            pths.add(st);
            st = pr[st];
        }
        pths.add(s);
        Collections.reverse(pths);
        log.info(pths.toString());

        // TODO return as onos object
    }

    public void setWeight(int u , int v , float vl ){
        adj.get(u).put(v, Math.abs(vl));
    }
}
