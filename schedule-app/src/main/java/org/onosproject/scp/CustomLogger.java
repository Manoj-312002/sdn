package org.onosproject.scp;

import java.io.BufferedWriter;
import java.io.FileWriter;

import static org.slf4j.LoggerFactory.getLogger;
import org.slf4j.Logger;

public class CustomLogger {
    private final Logger log = getLogger(getClass());
    FileWriter dataCollector, freePacket ,reqManger, splitting; 
    BufferedWriter dataCollectorOut , freePacketOut, reqMangerOut, splittingOut;

    public CustomLogger() {
        try{
            dataCollector = new FileWriter("/home/manoj/sdn/onos-apps/schedule-app/dlmodel/data.csv",true);
            dataCollectorOut = new BufferedWriter(dataCollector);

            freePacket = new FileWriter("/home/manoj/sdn/onos-apps/schedule-app/dlmodel/freePacket.txt");
            freePacketOut = new BufferedWriter(freePacket);

            reqManger = new FileWriter("/home/manoj/sdn/onos-apps/schedule-app/dlmodel/reqManger.txt");
            reqMangerOut = new BufferedWriter(reqManger);

            splitting = new FileWriter("/home/manoj/sdn/onos-apps/schedule-app/dlmodel/splitting.txt");
            splittingOut = new BufferedWriter(splitting);

        }catch(Exception e){
            log.info("File Creation " + e.getMessage());
        }

    }
    public void dataCollector(String data){    
        try{
            dataCollectorOut.write(data);
            dataCollectorOut.newLine();
            dataCollectorOut.flush();
        }catch(Exception e){
            log.info("Data collector " + e.getMessage());
        }
    }

    public void freePacket(String data){    
        try{
            freePacketOut.write(data);
            freePacketOut.newLine();
            freePacketOut.flush();
        }catch(Exception e){
            log.info("Free packet " + e.getMessage());
        }
    }

    public void reqManger(String data){    
        try{
            reqMangerOut.write(data);
            reqMangerOut.newLine();
            reqMangerOut.flush();
        }catch(Exception e){
            log.info("Request Manager " + e.getMessage());
        }
    }

    public void splitting(String data){    
        try{
            splittingOut.write(data);
            splittingOut.newLine();
            splittingOut.flush();
        }catch(Exception e){
            log.info("Splitting " + e.getMessage());
        }
    }
    
}
