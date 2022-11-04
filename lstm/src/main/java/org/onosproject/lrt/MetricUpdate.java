package org.onosproject.lrt;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class MetricUpdate {
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    public int nNode;
    
    public Map<String,Integer> mp;
    public Map<Integer,double[]> metrics;
    int cNodes ,nMetrics ,nIter;
    double SC = 0.000000001;

    /*
        0 - delay 
        1 - jitter
        2 - bandwidth
    */
    
    MetricUpdate(int x){
        nNode = x;
        metrics = new HashMap<>();
        mp = new HashMap<>();
        cNodes = 0;
        nMetrics = 3;
        nIter = 0;
    }

    int getId(String s){
        if(mp.containsKey(s)){
            return mp.get(s);
        }else{
            mp.put(s, cNodes);
            cNodes += 1;
            return cNodes - 1;
        }
    }

    void addEdge(String s1 , String s2 , int metricType , Double val){
        int e1 = getId(s1);
        int e2 = getId(s2);
       
        if(!metrics.containsKey(e1*nNode +e2)){
            metrics.put(e1*nNode + e2, new double[nMetrics]);
        }

        metrics.get(e1*nNode +e2)[metricType] = val;
    }

    void normalise(){
        double normVal[] = new double[nMetrics];
        
        for(int i : metrics.keySet()){
            for(int j = 0; j < nMetrics; j++){
                normVal[j] += Math.pow(metrics.get(i)[j], 2);
            }
        }

        for(int i = 0; i < 3; i++){
            normVal[i] = Math.sqrt(normVal[i]);
        }
        log.info(Arrays.toString(normVal));
        
        double entropy[] = new double[nMetrics];
        int ct = metrics.size();
        Arrays.fill(entropy, -1/Math.log(ct));

        for(int i : metrics.keySet()){
            for(int j = 0; j < nMetrics; j++){
                metrics.get(i)[j] = metrics.get(i)[j]/(normVal[j]+SC);
                entropy[j] *= (metrics.get(i)[j] * Math.log(metrics.get(i)[j] + SC));
            }
        }
        log.info(Arrays.toString(entropy));

        double sm = 0;
        for(int i = 0; i < nMetrics; i++){
            entropy[i] = 1-entropy[i];
            sm += entropy[i];
        }
        for(int i = 0; i < nMetrics; i++){
            entropy[i] /= sm;
        }

        for(int i : metrics.keySet()){
            for(int j = 0; j < nMetrics; j++){
                metrics.get(i)[j] = metrics.get(i)[j]*entropy[j];
            }
        }
    }

    void printMetric(){
        normalise();
        log.info("Network Metrics : ");

        try{
            File dir = new File("/home/manoj/sdn/onos-apps/lstm/data.csv");
            FileWriter fstream =  new FileWriter(dir, true);
            BufferedWriter out = new BufferedWriter(fstream);
            
            for(int n : metrics.keySet()){
                System.out.println(n + " " + Arrays.toString(metrics.get(n)));
                out.write(n+","+nIter);
                for(double v : metrics.get(n)){
                    out.write("," + v);
                }
                out.newLine();
            }
            nIter += 1;
            out.close();
        }catch(Exception e){
            log.info("Error");
            log.warn(e.toString());
        }

    }
}
