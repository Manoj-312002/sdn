package org.onosproject.scp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.NonNegativeConstraint;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;

import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;


// requires information about graph
// and information about which flow is fixed to which path
// linear programming
public class LPAllocater {
    private final Logger log = getLogger(getClass());
    public LPAllocater(){
    }
    /*
     * Constraints
        - sum of flow bw for each link < capacity (each link)
        - sum of bw for flow < requirement (each flow)
        - minimise priority * req per flow - priority * sum of allorted per flow
    */
    int n_var;
    Integer []keyList;
    // for every link the flows it has
    HashMap<Integer,ArrayList<Integer>> flow_g_links;       // links represented by link number
    HashMap<Integer,ArrayList<Integer>> flows_g_request;    // request id

    public void maximiseBandwidth(){
        log.info("Maximising bw");
        Integer[] keyList = AppComponent.requests.keySet().toArray(new Integer[AppComponent.requests.keySet().size()]);
        
        flow_g_links = new HashMap<>();
        flows_g_request = new HashMap<>();
        for(int i : AppComponent.gr.edges.keySet()) flow_g_links.put(i, new ArrayList<>());

        int index = 0;

        for(Integer i : keyList){
            int src = AppComponent.requests.get(i).srci;
            flows_g_request.put(i , new ArrayList<>());

            for(Requests.path pt : AppComponent.requests.get(i).paths ){
                int st = src;
                flows_g_request.get(i).add(index);
                for(int sp : pt.edg){
                    flow_g_links.get(st*AppComponent.nNode + sp).add(index);
                    st = sp;
                }
                index++;
            }
        }

        double[] objectiveCoefficient = new double[index+1];
        for(Integer i : keyList){
            for( Integer j : flows_g_request.get(i) ){
                objectiveCoefficient[j] = Integer.valueOf(AppComponent.requests.get(i).priority).doubleValue();
            }
        }
        log.info("Coefficient generated");
        ArrayList<LinearConstraint> constraints = new ArrayList<>();

        for( Integer lkId : flow_g_links.keySet() ){
            double[] linkConstraintCoeff = new double[index+1];
            for(Integer fls : flow_g_links.get(lkId) ){
                linkConstraintCoeff[fls] = 1;
            }
            constraints.add(new LinearConstraint(linkConstraintCoeff, Relationship.LEQ, AppComponent.gr.edges.get(lkId) ) );
        }
        log.info("C1 added");
        for( Integer reqId : keyList ){
            double[] reqConstraintCoeff = new double[index+1];   
            for(Integer fls : flows_g_request.get(reqId) ){
                reqConstraintCoeff[fls] = 1;
            }
            constraints.add(new LinearConstraint(reqConstraintCoeff, Relationship.LEQ, AppComponent.requests.get(reqId).reqBw ) );
        }
        
        log.info("C2 added");
        LinearObjectiveFunction func = new LinearObjectiveFunction(objectiveCoefficient, 0);
        PointValuePair solution = new SimplexSolver().optimize(
            new MaxIter(100) , 
            func, 
            new LinearConstraintSet(constraints), 
            GoalType.MAXIMIZE, 
            new NonNegativeConstraint(true));
        
        log.info("Sol ran");
        int assignVal = 0;
        for(Integer reqId : keyList){
            for(Requests.path pt : AppComponent.requests.get(reqId).paths){
                pt.bw = (float)solution.getPoint()[assignVal];
                AppComponent.customLogger.splitting("Assigning bandwidth : " + reqId + " " + pt.edg.toString() + " " + pt.bw );
                assignVal++;
            }
        }

        log.info("Assigned");
    }
}
