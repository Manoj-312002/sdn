package org.onosproject.lrt;

import java.util.HashMap;
import java.util.Map;


import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class metricUpdate {
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    public int nNode;
    float [][][] metrics;
    public Map<String,Integer> mp;
    int cNodes;

    /*
        0 - delay 
        1 - jitter
    */
    
    metricUpdate(int x){
        nNode = x;
        metrics = new float[3][x][x];
        mp = new HashMap<>();
        cNodes = 0;
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

    // switch string
    void addEdge(String s1 , String s2 , int metricType , float val){
        int e1 = getId(s1);
        int e2 = getId(s2);
        metrics[metricType][e1][e2] = val;
    }

    void printMetric(){
        int i = 0;
        for (float[][] array2D: metrics) {
            log.info("Count " + i);
            i += 1;
            for (float[] array1D: array2D) {
                for(float item: array1D) {
                    System.out.print(item + " ");
                }
                System.out.println("\n");
            }
        }
    }

    
}
