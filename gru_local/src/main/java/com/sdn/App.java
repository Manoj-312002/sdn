package com.sdn;

import java.util.Arrays;
import java.util.Collections;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.NonNegativeConstraint;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
/**
 * Hello world!
 *
 */
import java.util.*;
public class App {

    static ai.onnxruntime.OrtEnvironment env;
    static OrtSession session;
    static FileWriter dataCollector;
    static BufferedWriter dataCollectorOut;
    public static void main( String[] args )throws Exception{
        // gru_onnx();
        
        dataCollector = new FileWriter("/home/manoj/sdn/onos-apps/gru_local/data.csv");
        dataCollectorOut = new BufferedWriter(dataCollector);

        for(int nLinks = 20; nLinks < 100; nLinks++ ){
            int nNodes = nLinks/2;
            maximiseBandwidth(6, nLinks + 6);        
            env = OrtEnvironment.getEnvironment();
            session = env.createSession("/home/manoj/sdn/onos-apps/gru_local/models/scp_model_" + Integer.toString((nLinks+nNodes)*2) + ".onnx", new OrtSession.SessionOptions());
            predict((nLinks+nNodes)*2);
            Dijkstras dj = new Dijkstras(nNodes, nLinks);
            dj.shortestPath(5, 9);
        }

        dataCollectorOut.flush();
    }

    public static void dataCollector(String data){    
        try{
            dataCollectorOut.write(data);
        }catch(Exception e){
            
        }
    }

    public static void gru_onnx() throws Exception{
        var env = OrtEnvironment.getEnvironment();
        var session = env.createSession("/home/manoj/sdn/onos-apps/network_data/models/v1/gru_model_v7.onnx",new OrtSession.SessionOptions());
        
        Scanner sc = new Scanner(new File("/home/manoj/sdn/onos-apps/network_data/models/v1/d_star_10000_1_5_u.csv"));
        // sc.useDelimiter(",");
        sc.next();
        
        float[][][] testData = new float[10][5][3];
        
        int ct = 0;
        int j = 4;
        int st = (10+1)*10;
        
        while(sc.hasNext()){
            sc.next();
            st--;
            if( st == 0) break;
        }
        
        while (sc.hasNext()){  
            String [] ar =sc.next().split(",");
            Integer.parseInt(ar[1]);
            testData[ct%10][j][0] = Float.parseFloat(ar[2])/10000;
            testData[ct%10][j][1] = Float.parseFloat(ar[3])/10000;
            testData[ct%10][j][2] = Float.parseFloat(ar[4])/10000000;
            ct++;

            if( ct%10 == 0 ) j--;
            if(j==-1) break;
        }
        
        sc.close();
        
        String inputName = session.getInputNames().iterator().next();
        OnnxTensor test = OnnxTensor.createTensor(env, testData);
        
        Result output = session.run(Collections.singletonMap(inputName, test));
        float op[][] = (float [][])output.get(0).getValue();

        for(int i = 0; i < 9; i++){
            System.out.println(Arrays.toString(op[i]));
       }
    }

    public static void maximiseBandwidth(int index , int nConstraints){
        long begin = System.nanoTime();

        Random rd = new Random();
        double[] objectiveCoefficient = new double[index+1];
        
        // setting priority
        for(int i = 0; i < index; i++){
            objectiveCoefficient[i] = rd.nextInt() % 50;
        }
        
        ArrayList<LinearConstraint> constraints = new ArrayList<>();

        for( int i = 0; i < nConstraints; i++ ){
            double[] linkConstraintCoeff = new double[index+1];
            for(int j = 0; j < index; j++ ){
                linkConstraintCoeff[j] = rd.nextInt() % 2;
            }
            constraints.add(new LinearConstraint(linkConstraintCoeff, Relationship.LEQ, rd.nextInt(50) ) );
        }
        
        LinearObjectiveFunction func = new LinearObjectiveFunction(objectiveCoefficient, 0);
        PointValuePair solution = new SimplexSolver().optimize(
            new MaxIter(100) , 
            func, 
            new LinearConstraintSet(constraints), 
            GoalType.MAXIMIZE, 
            new NonNegativeConstraint(true));

        
        long end = System.nanoTime();      
        long time = end-begin;
        dataCollector(time + ",");
        // System.out.println(time);
        solution.getPoint();
    }

    public static void predict(int nStates) throws Exception{
        long begin = System.nanoTime();

        float ar[][] = new float[10][nStates];

        String inputName = session.getInputNames().iterator().next();
        // float op[][] = new float[AppComponent.gr.allPaths.get(src*V+dst).size()][1];
        
        OnnxTensor test = OnnxTensor.createTensor(env, ar);
        Result output = session.run(Collections.singletonMap(inputName, test));
        
        long end = System.nanoTime();      
        long time = end-begin;
        dataCollector(time + ",");

        output.close();
    }

}