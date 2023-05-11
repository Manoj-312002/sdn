package org.onosproject.scp;

import java.util.ArrayList;

import org.onlab.packet.IpPrefix;
import org.onosproject.net.topology.TopologyEdge;

public class Requests {
    public class path{
        float bw;
        ArrayList<TopologyEdge> edges;
        ArrayList<Integer> edg;
    }
    int priority , reqBw , srci ,dsti;
    ArrayList<path> paths;
    IpPrefix src , dst;
    
    public void addPath(ArrayList<Integer> ipath , ArrayList<TopologyEdge> tpath){
        path p = new path();
        p.edges = tpath;
        p.edg = ipath;
        paths.add(p);
    }
}

