package org.onosproject.scp;

import java.io.File;
import java.util.*;
import java.util.stream.Stream;

import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.net.Device;
import org.onosproject.net.Host;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;


public class Graph {
    private final Logger log = getLogger(getClass());

    public class Edge {
        public int bw , src , dst;
        public Edge(int src,int dst, int  bw ){
            this.bw = bw;
            this.src = src;
            this.dst = dst;
        }
    }
    
    public int V , E ;
    public ArrayList<Device> dev;                   // all the switches
    public ArrayList<ArrayList<Edge>> adjLists;     // adjacency list 
    
    TopologyService topologyService;
    HostService hostService;
    TopologyGraph topoGraph;
    // TODO can also add amount of data for each flow
    public ArrayList<Float> state;
    public static int currentLink = 0;              // calculate state edge map
    
    // link id (src*V + dst) to path
    HashMap<Integer,ArrayList<ArrayList<Integer>> > allPaths;
    // link number to bw
    public HashMap<Integer,Integer> edges;
    // link number to topo edge
    public HashMap<Integer,TopologyEdge> topoEdges;
    // link number to index
    public HashMap<Integer,Integer> state_edge_map;
    // device id to index
    public HashMap<String,Integer> dev_id_map;

    Random rd;
    public Graph(int V , int E , ArrayList<Device> dev , TopologyService tp, HostService hs){
        this.V = V; this.E = E; this.dev = dev;
        this.topologyService = tp;
        this.hostService = hs;    
        
        adjLists = new ArrayList<ArrayList<Edge>>();
        state = new ArrayList<>();

        allPaths = new HashMap<>();
        state_edge_map = new HashMap<>();
        edges = new HashMap<>();
        topoEdges = new HashMap<>();
        dev_id_map = new HashMap<>();

        rd = new Random();

        for(int i = 0; i < V; i++ ) adjLists.add( i, new ArrayList<Edge>());
        for(int i = 0; i < AppComponent.STATE_D; i++ ) state.add( i, 0f);
        
        int ct = 0;
        for(Device d : dev ){ dev_id_map.put(d.id().toString(), ct); ct++; }
        topoGraph = topologyService.getGraph(topologyService.currentTopology());
    }


    // * read from file, bandwidth should be stored properly and converted 
    // * to notation of onos
    public void updateEdgesToGraph(){
        // populating topoEdges

        for(TopologyEdge te : topoGraph.getEdges()){
            // String src = te.src().deviceId().toString();
            // int sr = Integer.decode("0x" + src.substring(src.length() -1)) -1;
            // String dst = te.dst().deviceId().toString();
            // int ds = Integer.decode("0x" + dst.substring(dst.length() -1)) -1;
            int sr = dev_id_map.get(te.src().deviceId().toString());
            int ds = dev_id_map.get(te.dst().deviceId().toString());
            topoEdges.put(sr*V + ds, te);
        }
        // add edge - state edge map , (Link) state , adjacency list and edges
        try{
            File file = new File("/home/manoj/sdn/onos-apps/topo/json/nycbw.txt");
            Scanner sc = new Scanner(file);
            while (sc.hasNextLine()){
                String s = sc.nextLine();
                String []dt = s.split(",");
                int src = Integer.parseInt(dt[0]) - 1;
                int dst = Integer.parseInt(dt[1]) - 1;
                int bw = Integer.parseInt(dt[2]);
                addEdge(src, dst, bw);
            }
            sc.close();
        }catch(Exception e){
            log.error("Cannot read bw file");
        }
    }

    public void addEdge(int src , int dst , int bw){
        state_edge_map.put(src*V + dst, currentLink);
        state_edge_map.put(dst*V + src, currentLink+1);

        this.state.set(currentLink , Integer.valueOf(bw).floatValue());
        this.state.set(currentLink+1 , Integer.valueOf(bw).floatValue());
        currentLink+=2;
        
        this.edges.put(dst*V + src , bw);
        this.edges.put(src*V + dst , bw);

        this.adjLists.get(src).add(new Edge(src, dst, bw ));
        this.adjLists.get(dst).add(new Edge(dst, src, bw ));
    }

    /*
     * state composition    
        - 2*e values of residual bw (upstream and downstream)
        - v values representing src demand
        - v values sink demand
    */
    

    // should consider all possible paths of particular src dst

    public void addRequest(int reqId, int priority, int bw , int src , int dst){
        AppComponent.customLogger.reqManger("Add " + Integer.toString(reqId) + " " + Integer.toString(priority) + " " + Integer.toString(bw) + " " + Integer.toString(src) + " " + Integer.toString(dst));
        Requests rq = new Requests();
        rq.srci = src; rq.dsti = dst; rq.priority = priority; rq.reqBw = bw;
        
        for(Host hs : hostService.getConnectedHosts(dev.get(src).id())){
            for(IpAddress adr : hs.ipAddresses()){
                rq.src = IpPrefix.valueOf(adr, 32);
            }
        }
        for(Host hs : hostService.getConnectedHosts(dev.get(dst).id())){
            for(IpAddress adr : hs.ipAddresses()){
                rq.dst = IpPrefix.valueOf(adr, 32);
            }
        }

        state.set(AppComponent.FLOW_STATE_START + src , state.get(AppComponent.FLOW_STATE_START + src) + Integer.valueOf(bw).floatValue());
        state.set(AppComponent.FLOW_STATE_START + V + dst , state.get(AppComponent.FLOW_STATE_START + V + dst) + Integer.valueOf(bw).floatValue());
        AppComponent.requests.put(reqId, rq);
    }

    public void removeRequest(int reqId){
        Requests rq = AppComponent.requests.get(reqId);
        state.set(AppComponent.FLOW_STATE_START + rq.srci , state.get(AppComponent.FLOW_STATE_START + rq.srci) - rq.reqBw );
        state.set(AppComponent.FLOW_STATE_START + V + rq.dsti , state.get(AppComponent.FLOW_STATE_START + V + rq.dsti) - rq.reqBw );
        
        AppComponent.requests.remove(reqId);
        AppComponent.customLogger.reqManger("Remove " + Integer.toString(reqId));
    }

    // all possible combination of src and dst
    public void storePaths(){
        try{
            for(int i = 0; i < V; i++ ){
                for(int j = 0; j < V; j++){
                    Stream<Path> pths = topologyService.getKShortestPaths(
                        topologyService.currentTopology() , 
                        dev.get(i).id() , dev.get(j).id());
                    
                    ArrayList<ArrayList<Integer>> psd = new ArrayList<>();
                    
                    pths.limit(5).forEach( (Path p) -> {
                        ArrayList<Integer> ps = new ArrayList<>();
                        for(Link l : p.links()){
                            // String dsts = l.dst().deviceId().toString();
                            // dsts = dsts.substring(dsts.length() - 1);
                            // ps.add(Integer.decode("0x" + dsts ) - 1);
                            ps.add(dev_id_map.get(l.dst().deviceId().toString()));
                        }
                        psd.add(ps);
                    });

                    allPaths.put(i*V + j , psd);
                }
            }
            log.info("Paths generated " + allPaths.size());
        }catch(Exception e ){
            log.error(e.getMessage());
        }
        
    }

    // set the paths in a variable based on maximum score
    public void fixPath(int reqId , float[] scores){
        // TODO choose multiple paths based on maximising scores
        int i1 = 0, i2 = 1;
        for(int i = 1; i < scores.length; i++ ){
            if( scores[i] >= scores[i1] ){
                i2 = i1;
                i1 = i;
            }else if( scores[i] > scores[i2] ){
                i2 = i;
            }
        }
        
        Requests rq = AppComponent.requests.get(reqId);
        rq.paths = new ArrayList<>();
        ArrayList<ArrayList<Integer>> pths = allPaths.get(rq.srci*V + rq.dsti);

        // implemening epsilon greedy
        if( rd.nextFloat() < 0.8 ){
            AppComponent.customLogger.splitting("Maximise " + Integer.toString(reqId) + " " + pths.get(i1).toString());
            AppComponent.customLogger.splitting("Maximise " + Integer.toString(reqId) + " " + pths.get(i2).toString());

            rq.addPath( pths.get(i1) , convertPath(pths.get(i1), rq.srci));
            rq.addPath( pths.get(i2) , convertPath(pths.get(i2), rq.srci));
        }else{
            i1 = rd.nextInt(scores.length); i2 = rd.nextInt(scores.length);

            AppComponent.customLogger.splitting("Maximise " + Integer.toString(reqId) + " " + pths.get(i1).toString());
            AppComponent.customLogger.splitting("Maximise " + Integer.toString(reqId) + " " + pths.get(i2).toString());

            rq.addPath( pths.get(i1) , convertPath(pths.get(i1), rq.srci));
            rq.addPath( pths.get(i2) , convertPath(pths.get(i2), rq.srci));
        }
        log.info(Integer.toString(rq.paths.size()));
    }

    public ArrayList<TopologyEdge> convertPath(ArrayList<Integer> pth , int src){
        ArrayList<TopologyEdge> pt = new ArrayList<>();
        int st = src;
        for(int sp : pth){
            pt.add( topoEdges.get(st*V+sp) );
            st = sp;
        }
        return pt;
    }

    public void updateState(){
        for(Integer lkId : edges.keySet()){
            state.set( state_edge_map.get(lkId) , edges.get(lkId).floatValue() );
        }
        AppComponent.customLogger.splitting(state.toString());
        for(Integer reqId : AppComponent.requests.keySet()){
            int src = AppComponent.requests.get(reqId).srci;
            for(Requests.path pt : AppComponent.requests.get(reqId).paths){
                int st = src;
                for( int sp : pt.edg ){
                    int lc = state_edge_map.get(st*V+sp);
                    state.set(lc , state.get(lc) - pt.bw);
                    st = sp;
                }
            }
        }
        AppComponent.customLogger.splitting(state.toString());
    }
}
