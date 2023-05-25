package org.onosproject.scp;

import java.net.*;
import java.time.LocalDateTime;
import java.util.Arrays;

import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;

public class Listener extends Thread {
    private final Logger log = getLogger(getClass());
    private DatagramSocket socket;
    private byte[] buf = new byte[256];
    
    public Listener() {
        try{
            socket = new DatagramSocket(4445);
        }catch(Exception e){
            log.error(e.getMessage(), e);
        }
    }

    public void run() {
        try{
            AppComponent.customLogger.splitting("Start " + LocalDateTime.now());
            while (true) {
                DatagramPacket packet  = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String data = new String(packet.getData(), 0, packet.getLength());
                String vals[] = data.split(",");
                
                
                if(vals[0].equals("start")){
                    // reqId, priority , bw , src , dst
                    int reqId = Integer.parseInt(vals[1]);
                    int priority = Integer.parseInt(vals[2]);
                    int bw = Integer.parseInt(vals[3]);
                    int src = Integer.parseInt(vals[4]);
                    int dst = Integer.parseInt(vals[5]);
                    
                    log.info("Requst" + Arrays.toString(vals));
                    AppComponent.gr.addRequest(reqId ,priority ,bw ,src, dst); 
                    float[] scores = AppComponent.rl.predict(bw , src, dst);
                    log.info("Scores " + Arrays.toString(scores));
                    
                    AppComponent.gr.fixPath(reqId, scores);
                    AppComponent.al.maximiseBandwidth();
                    AppComponent.mpi.installPaths();
                    log.info("Paths Installed");
                    
                    // TODO run in separate thread
                    AppComponent.gr.updateState();
                    AppComponent.rl.addSequence();
                    
                }else if(vals[0].equals("stop")){
                    // reqId
                    int reqId = Integer.parseInt(vals[1]);
                    log.info("Remove" + Arrays.toString(vals));
                    
                    AppComponent.gr.removeRequest(reqId);
                    AppComponent.al.maximiseBandwidth();
                    AppComponent.mpi.installPaths();
                    AppComponent.gr.updateState();
                    // TODO check if add sequence is required
                
                }else if(vals[0].equals("update")){
                    AppComponent.rl.updateModel();
                    AppComponent.customLogger.reqManger("Update Model");
                }else{
                    // finish episode
                    // TODO run in separate thread
                    AppComponent.customLogger.splitting("Done " + LocalDateTime.now());
                    AppComponent.rl.finishEpisode();
                }
                log.info(data);
            }
        }catch(Exception e){
            log.info(e.getMessage());
            socket.close();
        }
        
    }
}