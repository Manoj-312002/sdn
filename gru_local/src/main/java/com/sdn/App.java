package com.sdn;

import java.util.Arrays;
import java.util.Collections;

// import ai.djl.*;
// import java.nio.file.*;
// import ai.djl.inference.*;
// import ai.djl.ndarray.*;
// import ai.djl.translate.*;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
/**
 * Hello world!
 *
 */
public class App {
    public static void main( String[] args )throws Exception{
        // Path modelDir = Paths.get("/home/manoj/sdn/onos-apps/gru_network/gru_star_10000_1_5_5.zip");
        // Model model = Model.newInstance("gru");
        // model.load(modelDir);

        // Translator<double[][], double[]> translator = new Translator<double[][], double[]>(){
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
        
        // Predictor<double[][], double[]> predictor = model.newPredictor(translator);
        // double [][] a = new double [5][3];

        // double [] b = predictor.predict(a);
        // System.out.println(Arrays.toString(b));

        var env = OrtEnvironment.getEnvironment();
        var session = env.createSession("/home/manoj/sdn/onos-apps/grusdn/gru_model.onnx",new OrtSession.SessionOptions());


        float[][][] testData = new float[5][9][3];
        
        String inputName = session.getInputNames().iterator().next();
        OnnxTensor test = OnnxTensor.createTensor(env, testData);
        
        Result output = session.run(Collections.singletonMap(inputName, test));
        float op[][] = (float [][])output.get(0).getValue();

        for(int i = 0; i < 9; i++){
            System.out.println(Arrays.toString(op[i]));
        }
        
    }
}