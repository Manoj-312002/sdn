package org.onosproject.grp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;


import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;

import java.util.Collections;

import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;

public class MetricUpdate {
    private final Logger log = getLogger(getClass());
    
    public int nNode;
    // ip to device id to switch number (assigned)
    public Map<String,Integer> mp;
    // switch number to current metric
    public Map<Integer,float[]> metrics;
    public Map<Integer,float[]> norm_metrics;
    public Map<Integer,float[]> pred_vals;
    // switch id to sequence of metrics
    // public Map<Integer, LinkedList<float[]> > dt_buffer;
    
    // linked list of data sequence,( nNode *2 x nMetrics ) (10 x 3)
    public LinkedList<float[][]> dt_buffer;
    public ArrayList<Integer> sorted_list;
    public boolean first_event;

    int cNodes ,nMetrics ,nIter, bufferCount , nSeq;
    float SC = 0.0000001f;

    ai.onnxruntime.OrtEnvironment env;
    OrtSession session;
    /*/
        0 - delay 
        1 - jitter
        2 - bandwidth
    */
    
    MetricUpdate(int x){
        nNode = x;
        metrics = new HashMap<>();
        norm_metrics = new HashMap<>();
        pred_vals = new HashMap<>();
        dt_buffer = new LinkedList<>();
        
        // map of all switch devices to the id
        mp = new HashMap<>();
        
        // number of switches
        cNodes = 0;
        nMetrics = 3; nIter = 0; bufferCount = 0; nSeq = 5;
        first_event = true;

        try{
            env = OrtEnvironment.getEnvironment();
            session = env.createSession("/home/manoj/sdn/onos-apps/gru_local/gru_model.onnx",new OrtSession.SessionOptions());
        }catch(Exception e){
            log.info("Error Loading model" + e.getMessage());
        }

        log.info("Module Init");
    }

    int getId(String s){
        if(mp.containsKey(s)){
            // dt_buffer.put( mp.get(s) , new LinkedList<>());
            return mp.get(s);
        }else{
            mp.put(s, cNodes);
            cNodes += 1;
            return cNodes - 1;
        }
    }

    void addEdge(String s1 , String s2 , int metricType , float val){
        int e1 = getId(s1);
        int e2 = getId(s2);
       
        if(!metrics.containsKey(e1*nNode +e2)){
            metrics.put(e1*nNode + e2, new float[nMetrics]);
            norm_metrics.put(e1*nNode + e2, new float[nMetrics]);
        }

        metrics.get(e1*nNode +e2)[metricType] = val;
    }

    void normalise(){
        float normVal[] = new float[nMetrics];
        
        for(int i : metrics.keySet()){
            for(int j = 0; j < nMetrics; j++){
                normVal[j] += Math.pow(metrics.get(i)[j], 2);
            }
        }

        for(int i = 0; i < nMetrics; i++){
            normVal[i] = (float) Math.sqrt(normVal[i]);
        }
        log.info(Arrays.toString(normVal));
        
        float entropy[] = new float[nMetrics];
        int ct = metrics.size();
        Arrays.fill(entropy, -1/(float) Math.log(ct));

        for(int i : metrics.keySet()){
            for(int j = 0; j < nMetrics; j++){
                // square root normalization happens here
                norm_metrics.get(i)[j] = metrics.get(i)[j]/(normVal[j]+SC);
                entropy[j] *= (norm_metrics.get(i)[j] * Math.log(norm_metrics.get(i)[j] + SC));
            }
        }
        log.info(Arrays.toString(entropy));

        float sm = 0;
        for(int i = 0; i < nMetrics; i++){
            entropy[i] = 1-entropy[i];
            sm += entropy[i];
        }
        for(int i = 0; i < nMetrics; i++){
            entropy[i] /= sm;
        }

        for(int i : metrics.keySet()){
            for(int j = 0; j < nMetrics; j++){
                // entropy weighting 
                norm_metrics.get(i)[j] = norm_metrics.get(i)[j]*entropy[j];
            }
        }
    }

    void addToBuffer(){
        float [][] temp = new float[nNode*2][];
        
        int i = 0;
        for( int n : sorted_list){
            temp[i] = norm_metrics.get(n);
            i += 1;
        }
        dt_buffer.add(temp);

        if( bufferCount == nSeq ){
            dt_buffer.removeFirst();
        }else{
            bufferCount += 1;
        }
    }

    void predict() throws Exception{
        float [][][] temp = new float [nNode*2][nSeq][];
        // newly added data should have sq = 0
        int sq = 0;
        
        for( int j = nSeq - 1; j >= 0; j-- ){
            for( int i = 0; i < 10; i++){
                temp[i][sq] = dt_buffer.get(j)[i];
            }
            sq += 1;
        }
        
        String inputName = session.getInputNames().iterator().next();
        OnnxTensor test = OnnxTensor.createTensor(env, temp);

        Result output = session.run(Collections.singletonMap(inputName, test));
        // 10 x 3
        float op[][] = (float [][])output.get(0).getValue();
        
        int bt = 0;
        for(int n : sorted_list ){
            pred_vals.put(n, op[bt]);
            bt += 1;
        }

    }

    void printMetrics(boolean norm ){
        Map<Integer,float[]> mt;
        if(norm){ mt = norm_metrics;}
        else{ mt = metrics; }
        try{
            File dir;
            if (norm){ dir = new File("/home/manoj/sdn/onos-apps/app/norm_data.csv");}
            else{ dir = new File("/home/manoj/sdn/onos-apps/app/data.csv"); }

            FileWriter fstream =  new FileWriter(dir, true);
            BufferedWriter out = new BufferedWriter(fstream);
            
            for(int n : mt.keySet()){
                log.info(n + " " + Arrays.toString(mt.get(n)));
                out.write(n + "," + nIter);
                for(float v : mt.get(n)){
                    out.write("," + v);
                }
                out.newLine();
            }
            if(!norm) nIter += 1;
            out.close();
        }catch(Exception e){
            log.info("Unable to write to CSV file" + e.getMessage().toString());
        }
    }

    void predValues(){
        normalise();

        if(first_event){
            sorted_list = new ArrayList<>(norm_metrics.keySet());
            Collections.sort(sorted_list);
            first_event = false;
        }

        log.info("Norm Metrics");
        printMetrics(true);

        log.info("Metrics : ");
        printMetrics(false);
        
        try{
            addToBuffer();
        }catch(Exception e){
            log.info("Error adding to buffer : " + e.getMessage() );
        }

        try{
            predict();
        }catch(Exception e){
            log.info("Error Predicting" + e.getMessage().toString());
        }

        log.info("Predicted Metrics : ");
        for(int n : pred_vals.keySet()){
            log.info(n + " " + Arrays.toString(pred_vals.get(n)));
        }

        // if the metrics is not getting updated in the next iteration it 
        // should have maximum value
        for(int n : metrics.keySet()){
            metrics.get(n)[0] = 100000f;
            metrics.get(n)[1] = 10000f;
        }
    }
}