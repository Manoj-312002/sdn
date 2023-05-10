package com.example;

import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        INDArray zeros = Nd4j.zeros(2, 2);
        System.out.println(zeros.toString());
        MultiLayerConfiguration cfg = new NeuralNetConfiguration.Builder()
        .seed(123)
        .weightInit(WeightInit.UNIFORM)
        .updater(new Nesterovs(0.01, 0.9))
        .list()
        .layer(0, new DenseLayer.Builder()
          .nIn(86)
          .nOut(10)
          .activation(Activation.RELU)
          .build())
        .layer(1, new DenseLayer.Builder()
          .nIn(10)
          .nOut(5)
          .activation(Activation.RELU)
          .build()) 
        .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
          .activation(Activation.RELU)
          .nIn(5)
          .nOut(1)
          .build())
        .build();

        System.out.println(cfg.toString());
    }
}
