package org.onosproject.scp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;

import org.onlab.packet.Data;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;

import static org.slf4j.LoggerFactory.getLogger;

public class RLmodel {
    private final Logger log = getLogger(getClass());
    
    public int V , E;
    
    public static int EPOCHS = 20 , BATCH = 32;
    public static float  GAMMA = 0.8f;
    public static double LEARNING_RATE = 0.01;

    // rewards in current episode
    ArrayList<Float> rplBuffersquareR;
    // state in current episode
    ArrayList<ArrayList<Float>> rplBuffersquareS;
    
    public Timer timer = new Timer();
    public static String conf;

    ai.onnxruntime.OrtEnvironment env;
    OrtSession session;
    
    public RLmodel(int V,int E){
        this.V = V; this.E = E;
        this.updateModel();
        rplBuffersquareR = new ArrayList<>();
        rplBuffersquareS = new ArrayList<>();
    }

    public void updateModel(){
        //* onnx model initialization
        try{
            env = OrtEnvironment.getEnvironment();
            session = env.createSession("/home/manoj/sdn/onos-apps/schedule-app/dlmodel/scp_model.onnx",new OrtSession.SessionOptions());
            log.info("Model Loaded");
        }catch(Exception e){
            log.info("Error Loading model" + e.getMessage());
        }
    }

    public float[] predict(int bw , int src , int dst){
        log.info("Prediction start");
        float ar[][] = new float[AppComponent.gr.allPaths.get(src*V+dst).size()][AppComponent.STATE_D];
        
        for(int i = 0; i < AppComponent.gr.allPaths.get(src*V+dst).size(); i++ ){
            for(int j = 0; j < AppComponent.STATE_D; j++ ){
                ar[i][j] = AppComponent.gr.state.get(j);
            }
            
            int st = src;
            for( int sp : AppComponent.gr.allPaths.get(src*V+dst).get(i) ){
                int lc = AppComponent.gr.state_edge_map.get(st*V +sp);
                ar[i][lc] -= bw;
                st = sp;
            }
        }     

        log.info("Data Generated");
        String inputName = session.getInputNames().iterator().next();
        float op[][] = new float[AppComponent.gr.allPaths.get(src*V+dst).size()][1];
        float opt[] = new float[AppComponent.gr.allPaths.get(src*V+dst).size()];
        try{
            OnnxTensor test = OnnxTensor.createTensor(env, ar);
            Result output = session.run(Collections.singletonMap(inputName, test));
            op = (float [][])output.get(0).getValue();
            for(int i = 0; i < AppComponent.gr.allPaths.get(src*V+dst).size(); i++) opt[i] = op[i][0];
        }catch(Exception e){
            log.info("Error in prediction " + e.getMessage() );
        }

        return opt;
    }
    
    // with sequence of rewards calculated
    public void finishEpisode(){
        float rw = 0;
        int sz = rplBuffersquareR.size();
        log.info("Finishing episode");

        for(int i = sz-1; i >= 0; i--){
            rw = rplBuffersquareR.get(i) + GAMMA*rw;
            String wdata = "";
            for(Float f : rplBuffersquareS.get(i)) wdata += Float.toString(f) + ",";
            wdata += Float.toString(rw);
            AppComponent.customLogger.dataCollector(wdata);
        }
        rplBuffersquareR.clear();
        rplBuffersquareS.clear();
    }

    // TODO calculate reward given , use request (AppComponent.requests) and state (AppComponent.gr.state)
    public float calculateReward(){
        float rw = 0;
        for(Requests rq : AppComponent.requests.values()){
            for( Requests.path pth : rq.paths ){
                rw += pth.bw / rq.reqBw;
            }
        }
        return rw;
    }

    // this sequence should be added to all open requests
    // a new episode with request id should also be created
    public void addSequence(){
        rplBuffersquareR.add(calculateReward());   
        rplBuffersquareS.add(AppComponent.gr.state);
    }
}
