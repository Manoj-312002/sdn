package org.onosproject.lrt;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;


import org.slf4j.LoggerFactory;

// import ai.djl.Model;
// import ai.djl.inference.Predictor;
// import ai.djl.ndarray.NDArray;
// import ai.djl.ndarray.NDList;
// import ai.djl.ndarray.NDManager;
// import ai.djl.translate.Batchifier;
// import ai.djl.translate.Translator;
// import ai.djl.translate.TranslatorContext;

import org.slf4j.Logger;

public class MetricUpdate {
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    public int nNode;
    
    public Map<String,Integer> mp;
    public Map<Integer,double[]> metrics;
    public Map<Integer,double[]> norm_metrics;
    public Map<Integer,double[]> pred_vals;
    public Map<Integer, LinkedList<double[]> > dt_buffer;
    // Translator<double[][], double[]> translator;
    // Predictor<double[][], double[]> predictor;
    
    int cNodes ,nMetrics ,nIter, bufferCount , nSeq;
    double SC = 0.000000001;

    Path modelDir;
    // Model model;
    /*/
        0 - delay 
        1 - jitter
        2 - bandwidth
    */
    
    MetricUpdate(int x){
        nNode = x;
        metrics = new HashMap<>();
        norm_metrics = new HashMap<>();
        dt_buffer = new HashMap<>();
        
        // map of all switch devices to the id
        mp = new HashMap<>();
        // number of switches
        cNodes = 0;
        nMetrics = 3; nIter = 0; bufferCount = 0; nSeq = 5;
        log.info("Init Module");
        initModel();
    }

    void initModel(){
        // loading deeplearning model
        // modelDir = Paths.get("/home/manoj/sdn/onos-apps/gru_network/gru1.zip");
        // model = Model.newInstance("gru");
        // try{
        //     model.load(modelDir);
        // }catch(Exception e){
        //     log.error("Error Loading model", e);
        // }

        // translator = new Translator<double[][], double[]>(){
        //     @Override
        //     public NDList processInput(TranslatorContext ctx, double[][] input) {
        //         NDManager manager = ctx.getNDManager();
        //         NDArray array = manager.create(input);
        //         return new NDList (array);
        //     }
            
        //     @Override
        //     public double [] processOutput(TranslatorContext ctx, NDList list) {
        //         NDArray temp_arr = list.get(0);
        //         return temp_arr.toDoubleArray();
        //     }
            
        //     @Override
        //     public Batchifier getBatchifier() {
        //         return Batchifier.STACK;
        //     }
        // };
        
        // predictor = model.newPredictor(translator);
    }

    int getId(String s){
        if(mp.containsKey(s)){
            dt_buffer.put( mp.get(s) , new LinkedList<>());
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
            norm_metrics.put(e1*nNode + e2, new double[nMetrics]);
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
                // square root normalization happens here
                norm_metrics.get(i)[j] = metrics.get(i)[j]/(normVal[j]+SC);
                entropy[j] *= (norm_metrics.get(i)[j] * Math.log(norm_metrics.get(i)[j] + SC));
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
                // entropy weighting 
                norm_metrics.get(i)[j] = norm_metrics.get(i)[j]*entropy[j];
            }
        }
    }

    void addToBuffer(){
        bufferCount += 1;
        
        for( int n : norm_metrics.keySet()){
            dt_buffer.get(n).add(norm_metrics.get(n));
        }

        if( bufferCount == nSeq  + 1){
            for( int n : dt_buffer.keySet()){
                dt_buffer.get(n).removeFirst();
            }
        }
    }

    void predict() throws Exception{
        double [][] temp = new double [5][];

        for( int n : dt_buffer.keySet()){
            int i = 4;
            for( double[] v : dt_buffer.get(n) ){
                temp[i] = v;
                i -= 1;
            }
            // double pred_val [] = predictor.predict(temp);
            // pred_vals.put(n, pred_val);
        }
    }

    void printMetric(){
        normalise();
        log.info("Norm Metrics");
        try{
            File dir = new File("/home/manoj/sdn/onos-apps/lstm/data.csv");
            FileWriter fstream =  new FileWriter(dir, true);
            BufferedWriter out = new BufferedWriter(fstream);
            
            for(int n : norm_metrics.keySet()){
                System.out.println(n + " " + Arrays.toString(norm_metrics.get(n)));
                out.write(n+","+nIter);
                for(double v : norm_metrics.get(n)){
                    out.write("," + v);
                }
                out.newLine();
            }
            nIter += 1;
            out.close();
        }catch(Exception e){
            log.error("Error Unable to read csv file" , e);
        }

        log.info("Metrics : ");
        for(int n : metrics.keySet()){
            System.out.println(n + " " + Arrays.toString(metrics.get(n)));
        }

        addToBuffer();

        try{
            predict();
        }catch(Exception e){
            log.error("Error predicting metric", e);
        }

        log.info("Predicted Metrics : ");
        for(int n : pred_vals.keySet()){
            System.out.println(n + " " + Arrays.toString(pred_vals.get(n)));
        }

        for(int n : metrics.keySet()){
            metrics.get(n)[0] = 100000d;
            metrics.get(n)[1] = 10000d;
        }
    }
}