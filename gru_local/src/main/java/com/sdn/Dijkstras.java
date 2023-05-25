package com.sdn;

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
import java.util.Random;
import java.lang.Math;

public class Dijkstras {

    private float dist[];
    private int pr[];
    private PriorityQueue<Node> pq;
    private boolean vis[];
    List<Map<Integer,Float> > adj;
    private int nNode;


    Dijkstras(int v, int n){
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

        Random rd = new Random();
        for(int i = 0; i < n; i++){
            this.setWeight(rd.nextInt(v), rd.nextInt(v), 1);
        }
    }

    // TODO : Does not reset all

    public void reset(){
        for(int i = 0; i < nNode; i++ ){
            pr[i] = -1;
            vis[i] = false;
        }
    }

    public void shortestPath(int s , int d){
        long begin =System.nanoTime();;
        reset();
        Arrays.fill(dist, Integer.MAX_VALUE);
        pq.add(new Node(s , 0));
        dist[s] = 0;

        // int q = 0;
        // for( Map<Integer,Float> i : adj){
        //     q += 1;
        // }

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

        int st = d;
        List <Integer> pths = new ArrayList<>();
        while(st != s && st != -1){
            pths.add(st);
            st = pr[st];
        }
        pths.add(s);
        Collections.reverse(pths);
        
        long end =System.nanoTime();;      
        long time = (end-begin)*3;
        App.dataCollector(time + "\n");
    }

    public void setWeight(int u , int v , float vl ){
        adj.get(u).put(v, Math.abs(vl));
    }
}