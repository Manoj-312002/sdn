package org.onosproject.scp;

import org.onlab.metrics.MetricsService;
import org.onlab.packet.Ethernet;
import org.onlab.packet.VlanId;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.statistic.PortStatisticsService;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.store.service.StorageService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.net.Path;

import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;

@Component(
    immediate = true
)
public class AppComponent {
    private final Logger log = getLogger(getClass());
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private PortStatisticsService portStatisticsService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MetricsService metricsService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected GroupService groupService;


    private ApplicationId appId;

    public static Listener ls;
    public static Graph gr;
    public static LPAllocater al;
    public static RLmodel rl;
    public static MultiPathInstaller mpi;
    public static CustomLogger customLogger;

    public static int nNode , nLink , STATE_D , FLOW_STATE_START;
    public static HashMap<Integer,Requests> requests;
    private ReactivePacketProcessor processor = new ReactivePacketProcessor();
    
    @Activate
    public void activate(ComponentContext context) {
        appId = coreService.registerApplication("org.onosproject.grp");
        packetService.addProcessor(processor, PacketProcessor.director(2));
        installLastFlows();
        log.info("Installed flows");
        requestIntercepts();
        customLogger = new CustomLogger();

        nNode = 14;
        nLink = 29;
        STATE_D = 2*nNode + 2*nLink;
        FLOW_STATE_START = 2*nLink;
        // calling packet processor
        requests = new HashMap<>();
        log.info("graph Init");
        gr = new Graph(nNode, nLink,getDevices(), topologyService, hostService);
    
        gr.updateEdgesToGraph();
        gr.storePaths();
        
        al = new LPAllocater();
        rl = new RLmodel(nNode, nLink);
        mpi = new MultiPathInstaller(deviceService, groupService, flowRuleService, appId);
        
        ls = new Listener();
        ls.start();
        log.info("Started");
    }

    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    
    public ArrayList<Device> getDevices(){
        ArrayList<Device> dev = new ArrayList<>();
        deviceService.getDevices().forEach(dev::add);

        Comparator<Device> comparator = new Comparator<Device>() {
            @Override
            public int compare(Device o1, Device o2) {
              return o1.id().toString().compareTo(o2.id().toString());
            }
          };
        dev.sort(comparator);
        return dev;
    }

    // Indicates whether this is a control packet, e.g. LLDP, BDDP
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

    // Sends a packet out the specified port.
    private void packetOut(PacketContext context, PortNumber portNumber ) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }

    private void flood(PacketContext context ) {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                                             context.inPacket().receivedFrom())) {
            packetOut(context, PortNumber.FLOOD);
        } else {
            context.block();
        }
    }
    
    private class ReactivePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {

            if (context.isHandled()) {
                return;
            }
            
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            
            if (ethPkt == null) {
                return;
            }
            
            if(isControlPacket(ethPkt)){
                return; 
            }
            
            HostId id = HostId.hostId(ethPkt.getDestinationMAC(), VlanId.vlanId(ethPkt.getVlanID()));
            if (id.mac().isLldp()) {
                return;
            }

            Host dst = hostService.getHost(id);
            if (dst == null) {
                flood(context);
                return;
            }
            
            Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                pkt.receivedFrom().deviceId(),
                dst.location().deviceId());

            if(paths.isEmpty()){
                return;
            }

            if (pkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
                if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                    packetOut(context, dst.location().port());
                }
                return;
            }

            for(Path pt: paths){
                if(!pt.src().port().equals(pkt.receivedFrom().port())){
                    AppComponent.customLogger.freePacket(pkt.receivedFrom().toString());
                    packetOut(context, pt.src().port());
                    return;
                }
            }
            AppComponent.customLogger.freePacket("Path Unfixed Node");
        }

    }

    public void installLastFlows(){
        for(Host hs : hostService.getHosts()){
            TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthDst(hs.mac())
                .build();

            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(hs.location().port())
                .build();
            
            FlowRule rule = DefaultFlowRule.builder()
                .forDevice(hs.location().deviceId())
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(5556)
                .fromApp(appId)
                .makePermanent()
                .build();

            flowRuleService.applyFlowRules(rule);
        }
    }
    
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    @Deactivate
    public void deactivate() {
        withdrawIntercepts();
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        log.info("Stopped");
    }
}