package com.sdn;
import ai.djl.*;

import java.nio.file.*;
import java.util.Arrays;

import ai.djl.inference.*;
import ai.djl.ndarray.*;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.*;
/**
 * Hello world!
 *
 */
public class App {
    public static void main( String[] args )throws Exception{
        Path modelDir = Paths.get("/home/manoj/sdn/onos-apps/gru_network/gru1.zip");
        Model model = Model.newInstance("gru");
        model.load(modelDir);

        Translator<Float[], float[]> translator = new Translator<Float[], float[]>(){
            @Override
            public NDList processInput(TranslatorContext ctx, Float[] input) {
                NDManager manager = ctx.getNDManager();
                NDArray array = manager.randomUniform(0, 1, new Shape(5,3));
                // NDArray array1 = manager.randomUniform(0, 1, new Shape(1,3));
                return new NDList (array);
            }
            
            @Override
            public float [] processOutput(TranslatorContext ctx, NDList list) {
                NDArray temp_arr = list.get(0);
                return temp_arr.toFloatArray();
            }
            
            @Override
            public Batchifier getBatchifier() {
                return Batchifier.STACK;
            }
        };
        
        Predictor<Float[], float[]> predictor = model.newPredictor(translator);
        Float [] a = new Float [2];
        float [] b = predictor.predict(a);
        System.out.println(Arrays.toString(b));
    }
}