package org.onosproject.grp;

/*
 * the value of metrics is updated as map( switch number to array of 3 values ) 
 *      [addEdge - called when ] new metric is received by the reactive processor
 * normalization is done
 * the predvals functions is called each 5 sec by the timing task at app component
 * all the predicted values are updated in pred_vals
*/

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
    
    public int nNode , nLink;
    // ip to device id to switch number (assigned)
    public Map<String,Integer> mp;
    // switch number to current metric
    public Map<Integer,float[]> metrics;
    public Map<Integer,float[]> norm_metrics;
    public Map<Integer,float[]> pred_vals;
    
    // linked list of data sequence,( nNode *2 x nMetrics ) (10 x 3)
    public LinkedList<float[][]> dt_buffer;
    // the order in which predicted values are accessed
    public ArrayList<Integer> sorted_list;

    int cNodes ,nMetrics ,nIter, bufferCount , nSeq;
    float SC = 0.0000001f;

    ai.onnxruntime.OrtEnvironment env;
    OrtSession session;

    BufferedWriter metaOut;
    /*
     * Metric indexes
        * 0 - delay 
        * 1 - jitter
        * 2 - bandwidth
    */
    
    MetricUpdate(int x , int y){
        nNode = x;
        nLink = y;
        metrics = new HashMap<>();
        norm_metrics = new HashMap<>();
        pred_vals = new HashMap<>();
        dt_buffer = new LinkedList<>();
        
        // map of all switch devices to the id
        mp = new HashMap<>();
        
        // number of switches
        cNodes = 0;
        nMetrics = 3; nIter = 0; bufferCount = 0; nSeq = 5;

        //* onnx model initialization
        try{
            env = OrtEnvironment.getEnvironment();
            session = env.createSession("/home/manoj/sdn/onos-apps/gru_local/gru_model.onnx",new OrtSession.SessionOptions());
        }catch(Exception e){
            log.info("Error Loading model" + e.getMessage());
        }
        log.info(cNodes+"");
        log.info("Module Init");
    }

    // converts switch id to a continuos number
    int getId(String s){
        if(mp.containsKey(s)){
            return mp.get(s);
        }else{
            mp.put(s, cNodes);
            
            try{
                File dir;
                dir = new File("/home/manoj/sdn/onos-apps/app/meta.txt");
                FileWriter fstream =  new FileWriter(dir, true);
                metaOut = new BufferedWriter(fstream);
                metaOut.write(s + ":" + cNodes );
                metaOut.newLine();
                metaOut.close();
            }catch(Exception e){
                log.info("Unablle to write meta data");
            }

            cNodes += 1;
            return cNodes - 1;
        }
    }
    
    // input - two switch ids , metric type ( 0,1,2 ) and the actual value
    void addEdge(String s1 , String s2 , int metricType , float val){
        int e1 = getId(s1);
        int e2 = getId(s2);
       
        if(!metrics.containsKey(e1*nNode +e2)){
            metrics.put(e1*nNode + e2, new float[nMetrics]);
            norm_metrics.put(e1*nNode + e2, new float[nMetrics]);
        }

        metrics.get(e1*nNode +e2)[metricType] = val;
    }

    /*
     * entropy normalization
     *  - square root normalization is applied to each metric
     *  - entropy is calculated for each metric -1/ln(ct) * sumof( vij * ln(vij) ) 
     *      - ct is number of points in here it is number of connections
     *      - vij is the normalized value for ith connection and jth metric
     *  - degree of deviation = 1 - entropy
     *  - each degre of deviation is normalized with combined sum
     */
    void normalise(){
        // TODO : Also include features to normalize pred_vals
        float normVal[] = new float[nMetrics];
        
        for(int i : metrics.keySet()){
            for(int j = 0; j < nMetrics; j++){
                normVal[j] += Math.pow(metrics.get(i)[j], 2);
            }
        }

        for(int i = 0; i < nMetrics; i++){
            normVal[i] = (float) Math.sqrt(normVal[i]);
        }
        // log.info("Norm Value " + Arrays.toString(normVal));
        
        float entropy[] = new float[nMetrics];
        int ct = metrics.size();
        Arrays.fill(entropy, -1/(float) Math.log(ct));

        for(int i : metrics.keySet()){
            for(int j = 0; j < nMetrics; j++){
                // square root normalization happens here
                norm_metrics.get(i)[j] = metrics.get(i)[j]/(normVal[j]+SC);
                // entropy calculation
                entropy[j] *= (norm_metrics.get(i)[j] * Math.log(norm_metrics.get(i)[j] + SC));
            }
        }
        // log.info("Entropy Value" + Arrays.toString(entropy));

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

    /*
        * this will be called by predict function 
        * it adds contents into dt_buffer
        * check the order in which it is added to buffer
    */   
    void addToBuffer(){
        float [][] temp = new float[nLink*2][];
        
        int i = 0;

        for( int n : sorted_list){
            temp[i] = norm_metrics.get(n);
            // TODO : Normalize the ordinary data
            i += 1;
        }
        dt_buffer.add(temp);
        
        if( bufferCount == nSeq ){
            dt_buffer.removeFirst();
        }else{
            bufferCount += 1;
        }
        log.info("Buffer count : " + bufferCount);
    }

    /*
     * called be scheduler predvals function
     * checks if there is enough size in dt_buffer 
     * converts output in proper format and updates in pred_vals 
    */
    void predict() throws Exception{
        if( bufferCount < nSeq ){
            return;
        }

        float [][][] temp = new float [nLink*2][nSeq][];
        // newly added data should have sq = 0
        int sq = 0;
        
        for( int j = nSeq - 1; j >= 0; j-- ){
            for( int i = 0; i < nLink*2; i++){
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
        // TODO : Normalize pred_vals
    }
    
    // auxillary function to print metrics into file
    void printMetrics(Map<Integer,float[]> mt , String fn ){
        try{
            File dir;
            dir = new File(fn);

            FileWriter fstream =  new FileWriter(dir, true);
            BufferedWriter out = new BufferedWriter(fstream);
            
            for(int n : mt.keySet()){
                out.write(n + "," + nIter);
                for(float v : mt.get(n)){
                    out.write("," + v);
                }
                out.newLine();
            }

            out.close();
        }catch(Exception e){
            log.info("Unable to write to CSV file" + e.getMessage().toString());
        }
    }

    // called by timer function
    void predValues(){
        normalise();
        
        sorted_list = new ArrayList<>(norm_metrics.keySet());
        Collections.sort(sorted_list);

        //** printing metrics to file
        log.info("Norm Metrics");
        printMetrics(norm_metrics , "/home/manoj/sdn/onos-apps/app/norm_data.csv");

        log.info("Metrics : ");
        printMetrics(metrics , "/home/manoj/sdn/onos-apps/app/metric_data.csv");
        
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

        nIter += 1;

        log.info("Predicted Metrics : ");
        printMetrics(pred_vals, "/home/manoj/sdn/onos-apps/app/pred_data.csv");

        // if the metrics is not getting updated in the next iteration it 
        // should have maximum value
        for(int n : metrics.keySet()){
            metrics.get(n)[0] = 100000f;
            metrics.get(n)[1] = 10000f;
        }
    }
}