package org.onosproject.grp;

import org.onlab.metrics.MetricsService;
import org.onlab.packet.Data;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.UDP;
import org.onlab.packet.VlanId;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.DefaultOutboundPacket;
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
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;


import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.Set;
import java.util.TimerTask;


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


    private ReactivePacketProcessor processor = new ReactivePacketProcessor();
    private ApplicationId appId;
    
    private boolean packetOutOnly = false;
    private boolean matchDstMacOnly = true;
    private boolean matchIpv4Address = false;
    private boolean packetOutOfppTable = false;
    private Integer flowPriority = 10;
    private Integer flowTimeout = 10;

    public MetricUpdate mU;
    public Timer timer = new Timer();

    @Activate
    public void activate(ComponentContext context) {
        appId = coreService.registerApplication("org.onosproject.grp");
        packetService.addProcessor(processor, PacketProcessor.director(2));
        installInitRule();

        // calling packet processor
        requestIntercepts();
        timer.schedule(new Task(), 0, 7000);
        
        mU = new MetricUpdate(5);
    }

    // returns device name from ip address
    String getDeviceFromIp(String s){
        for(Host hs : hostService.getHostsByIp(Ip4Address.valueOf(s))){
            return hs.location().deviceId().toString();
        }
        return "";
    }
    
    void parsePacketData(String sender , String dt){
        String [] recv = dt.split(":");
        String [] metrics = recv[1].split(",");

        String s1 = getDeviceFromIp(sender);
        String s2 = getDeviceFromIp(recv[0]);
        mU.addEdge(s1 , s2 , 0 ,Float.valueOf(metrics[0]) );
        mU.addEdge(s1 , s2 , 1 ,Float.valueOf(metrics[1]) );
    }

    void getBandwidth(){
        for(Host hs : hostService.getHosts()){
            for(Link ls :linkService.getDeviceEgressLinks(hs.location().deviceId())){
                float v1 = portStatisticsService.load(ls.dst()).rate();
                float v2 = portStatisticsService.load(ls.src()).rate();

                mU.addEdge(ls.src().deviceId().toString(), 
                    ls.dst().deviceId().toString(), 2, (v1+v2)/2);
            }
        }
    }

    // ran every 7 seconds
    class Task extends TimerTask {
 
        @Override
        public void run() {
            sendInitPacket();
            try{
                Thread.sleep(2000);
            }catch(Exception e){
                e.printStackTrace();
            }
            getBandwidth();
            mU.predValues();
            log.info("Metrics Updated");
        }
    }
 
    // directs all packets with mac addrs 00:00:00:00:00:00 to controller
    public void installInitRule(){
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthDst(MacAddress.valueOf("00:00:00:00:00:00"));
        TrafficTreatment treatment;
        treatment = DefaultTrafficTreatment.builder()
        .setOutput(PortNumber.CONTROLLER)
        .build();
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(flowPriority)
                .fromApp(appId)
                .add();


        for( Host hs : hostService.getHosts() ){
            flowObjectiveService.forward( hs.location().deviceId() ,
                forwardingObjective);
        }
    }
    
    public String getNearbyNodes(Host hs){
        String ips = "";
        DeviceId hl = hs.location().deviceId();
        Set<Link> lks = linkService.getDeviceEgressLinks(hl);
        for(Link lk : lks){
            Set<Host> dvslk = hostService.getConnectedHosts(lk.dst().deviceId());
            for( Host dvs : dvslk ){
                Set<IpAddress> ads = dvs.ipAddresses();
                for( IpAddress ip : ads ){
                    ips += ip.toString() + ",";
                }
            }
        }
        return ips;

    }

    public void sendInitPacket(){
        for( Host hs : hostService.getHosts() ){
            String ipsS = getNearbyNodes(hs);
            // create an ethernet packet
            Ethernet ethInfo = new Ethernet();
            ethInfo.setDestinationMACAddress(hs.mac());
            ethInfo.setEtherType(Ethernet.TYPE_IPV4);
            ethInfo.setSourceMACAddress("00:00:00:00:00:00");

            // ip packet
            IPv4 ipInfo = new IPv4();
            ipInfo.setSourceAddress("10.0.0.0");
            ipInfo.setTtl((byte)64);

            Set<IpAddress> ips = hs.ipAddresses();
            for ( IpAddress ip : ips ){
                ipInfo.setDestinationAddress(ip.toString());
                break;
            }

            // udp packet
            UDP udpInfo = new UDP();
            udpInfo.setSourcePort(0xED88);
            udpInfo.setDestinationPort(0x1F40);
            
            // setting data 
            Data dt = new Data();
            // sending ip info
            byte[] bt = ipsS.getBytes();
            dt.setData(bt);
            udpInfo.setPayload( dt );
            ipInfo.setPayload(udpInfo);
            ethInfo.setPayload(ipInfo);

            TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
            builder.setOutput(hs.location().port());
            packetService.emit( new DefaultOutboundPacket(hs.location().deviceId(), 
                builder.build(), ByteBuffer.wrap(ethInfo.serialize())));
            
        }
    }
    
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    // Cancel request for packet in via packet service.
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }
    // Packet processor responsible for forwarding packets along their paths.
    private class ReactivePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.

            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            
            // Bail if this is deemed to be a control packet.
            if (isControlPacket(ethPkt)) {
                return;
            }
            
            // Skip IPv6 multicast packet when IPv6 forward is disabled.
            if (isIpv6Multicast(ethPkt)) {
                return;
            }

            if(ethPkt.getDestinationMAC().toString().equals("00:00:00:00:00:00")){
                IPv4 ip_pkt = (IPv4)ethPkt.getPayload();
                UDP udp_pkt = (UDP) ip_pkt.getPayload();
                
                parsePacketData(IPv4.fromIPv4Address(ip_pkt.getSourceAddress()), 
                    new String(udp_pkt.getPayload().serialize()) );

                context.block();
                return;
            }

            HostId id = HostId.hostId(ethPkt.getDestinationMAC(), VlanId.vlanId(ethPkt.getVlanID()));

            // Do not process LLDP MAC address in any way.
            if (id.mac().isLldp()) {
                return;
            }
            
            // Do not process IPv4 multicast packets, let mfwd handle them
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                if (id.mac().isMulticast()) {
                    return;
                }
            }

            // Do we know who this is for? If not, flood and bail.
            Host dst = hostService.getHost(id);
            if (dst == null) {
                flood(context);
                return;
            }

            // Are we on an edge switch that our destination is on? If so,
            // simply forward out to the destination and bail.
            if (pkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
                if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                    installRule(context, dst.location().port());
                }
                return;
            }

            // Otherwise, get a set of paths that lead from here to the
            // destination edge switch.
            Set<Path> paths =
                    topologyService.getPaths(topologyService.currentTopology(),
                                             pkt.receivedFrom().deviceId(),
                                             dst.location().deviceId());
            if (paths.isEmpty()) {
                // If there are no paths, flood and bail.
                flood(context);
                return;
            }
            // came from; if no such path, flood and bail.

            // Otherwise, pick a path that does not lead back to where we
            Path path = pickForwardPathIfPossible(paths, pkt.receivedFrom().port());
            if (path == null) {
                // log.warn("Don't know where to go from here {} for {} -> {}",
                //          pkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC());
                flood(context);
                return;
            }

            // Otherwise forward and be done with it.
            installRule(context, path.src().port());
        }

    }

    private boolean isIpv6Multicast(Ethernet eth) {
        return eth.getEtherType() == Ethernet.TYPE_IPV6;
    }

    // Indicates whether this is a control packet, e.g. LLDP, BDDP
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

    // Selects a path from the given set that does not lead back to the
    // specified port if possible.
    private Path pickForwardPathIfPossible(Set<Path> paths, PortNumber notToPort) {
        for (Path path : paths) {
            if (!path.src().port().equals(notToPort)) {
                return path;
            }
        }
        return null;
    }

    // Floods the specified packet if permissible.
    private void flood(PacketContext context ) {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                                             context.inPacket().receivedFrom())) {
            packetOut(context, PortNumber.FLOOD);
        } else {
            context.block();
        }
    }

    // Sends a packet out the specified port.
    private void packetOut(PacketContext context, PortNumber portNumber ) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }


    // Install a rule forwarding the packet to the specified port.
    private void installRule(PacketContext context, PortNumber portNumber ) {
        
        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();

        // If PacketOutOnly or ARP packet than forward directly to output port
        if (packetOutOnly || inPkt.getEtherType() == Ethernet.TYPE_ARP) {
            packetOut(context, portNumber);
            return;
        }

        if (matchDstMacOnly) {
            selectorBuilder.matchEthDst(inPkt.getDestinationMAC());
        } else {
            selectorBuilder.matchInPort(context.inPacket().receivedFrom().port())
                    .matchEthSrc(inPkt.getSourceMAC())
                    .matchEthDst(inPkt.getDestinationMAC());

            //
            // If configured and EtherType is IPv4 - Match IPv4 and
            // TCP/UDP/ICMP fields
            //
            if (matchIpv4Address && inPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipv4Packet = (IPv4) inPkt.getPayload();
                Ip4Prefix matchIp4SrcPrefix =
                        Ip4Prefix.valueOf(ipv4Packet.getSourceAddress(),
                                          Ip4Prefix.MAX_MASK_LENGTH);
                Ip4Prefix matchIp4DstPrefix =
                        Ip4Prefix.valueOf(ipv4Packet.getDestinationAddress(),
                                          Ip4Prefix.MAX_MASK_LENGTH);
                selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPSrc(matchIp4SrcPrefix)
                        .matchIPDst(matchIp4DstPrefix);

            }
        }

        TrafficTreatment treatment;
        
        treatment = DefaultTrafficTreatment.builder()
            .setOutput(portNumber)
            .build();
        
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(flowTimeout)
                .add();

        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(),
                                     forwardingObjective);
        //
        // If packetOutOfppTable
        //  Send packet back to the OpenFlow pipeline to match installed flow
        // Else
        //  Send packet direction on the appropriate port
        //
        if (packetOutOfppTable) {
            packetOut(context, PortNumber.TABLE);
        } else {
            packetOut(context, portNumber);
        }
    }



    @Deactivate
    public void deactivate() {
        withdrawIntercepts();
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        timer.cancel();
        timer = null;
        mU = null;       
        log.info("Stopped");
    }
}