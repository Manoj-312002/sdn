/*
 * Copyright 2014-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.lrt;

import com.google.common.collect.ImmutableSet;

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
import org.onosproject.event.Event;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.statistic.PortStatisticsService;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.store.service.StorageService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;

/**
 * Sample reactive forwarding application.
 */
@Component(
    immediate = true
)
public class AppComponent {
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    
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
    private final TopologyListener topologyListener = new InternalTopologyListener();
    private ExecutorService blackHoleExecutor;

    // private boolean ignoreIPv4Multicast = true;
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
        blackHoleExecutor = newSingleThreadExecutor(
            groupedThreads("onos/app/lstm", "black-hole-fixer",
            log));

        // cfgService.registerProperties(getClass());
        
        appId = coreService.registerApplication("org.onosproject.lrt");

        packetService.addProcessor(processor, PacketProcessor.director(2));
        topologyService.addListener(topologyListener);
        
        log.info("Debug Mode");

        mU = new MetricUpdate(5);
        
        installInitRule();
        requestIntercepts();
        timer.schedule(new Task(), 0, 7000);

        log.info("Started", appId.id());
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

        mU.addEdge(s1 , s2 , 0 ,Double.valueOf(metrics[0]) );
        mU.addEdge(s1 , s2 , 1 ,Double.valueOf(metrics[1]) );
    }

    void getBandwidth(){
        for(Host hs :hostService.getHosts()){
            for(Link ls :linkService.getDeviceEgressLinks(hs.location().deviceId())){
                double v1 = portStatisticsService.load(ls.dst()).rate();
                double v2 = portStatisticsService.load(ls.src()).rate();

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
            mU.printMetric();
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
    
    
    @Deactivate
    public void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        withdrawIntercepts();
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        topologyService.removeListener(topologyListener);
        blackHoleExecutor.shutdown();
        blackHoleExecutor = null;
        processor = null;
        timer.cancel();
        timer = null;
        mU = null;
        
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        requestIntercepts();
    }

    //Request packet in via packet service.
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
                log.warn("Don't know where to go from here {} for {} -> {}",
                         pkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC());
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

    private class InternalTopologyListener implements TopologyListener {
        @Override
        public void event(TopologyEvent event) {
            @SuppressWarnings("rawtypes")
            List<Event> reasons = event.reasons();
            if (reasons != null) {
                reasons.forEach(re -> {
                    if (re instanceof LinkEvent) {
                        LinkEvent le = (LinkEvent) re;
                        if (le.type() == LinkEvent.Type.LINK_REMOVED && blackHoleExecutor != null) {
                            blackHoleExecutor.submit(() -> fixBlackhole(le.subject().src()));
                        }
                    }
                });
            }
        }
    }

    private void fixBlackhole(ConnectPoint egress) {
        Set<FlowEntry> rules = getFlowRulesFrom(egress);
        Set<SrcDstPair> pairs = findSrcDstPairs(rules);

        Map<DeviceId, Set<Path>> srcPaths = new HashMap<>();

        for (SrcDstPair sd : pairs) {
            // get the edge deviceID for the src host
            Host srcHost = hostService.getHost(HostId.hostId(sd.src));
            Host dstHost = hostService.getHost(HostId.hostId(sd.dst));
            if (srcHost != null && dstHost != null) {
                DeviceId srcId = srcHost.location().deviceId();
                DeviceId dstId = dstHost.location().deviceId();
                log.trace("SRC ID is {}, DST ID is {}", srcId, dstId);

                cleanFlowRules(sd, egress.deviceId());

                Set<Path> shortestPaths = srcPaths.get(srcId);
                if (shortestPaths == null) {
                    shortestPaths = topologyService.getPaths(topologyService.currentTopology(),
                            egress.deviceId(), srcId);
                    srcPaths.put(srcId, shortestPaths);
                }
                backTrackBadNodes(shortestPaths, dstId, sd);
            }
        }
    }

    // Backtracks from link down event to remove flows that lead to blackhole
    private void backTrackBadNodes(Set<Path> shortestPaths, DeviceId dstId, SrcDstPair sd) {
        for (Path p : shortestPaths) {
            List<Link> pathLinks = p.links();
            for (int i = 0; i < pathLinks.size(); i = i + 1) {
                Link curLink = pathLinks.get(i);
                DeviceId curDevice = curLink.src().deviceId();

                // skipping the first link because this link's src has already been pruned beforehand
                if (i != 0) {
                    cleanFlowRules(sd, curDevice);
                }

                Set<Path> pathsFromCurDevice =
                        topologyService.getPaths(topologyService.currentTopology(),
                                                 curDevice, dstId);
                if (pickForwardPathIfPossible(pathsFromCurDevice, curLink.src().port()) != null) {
                    break;
                } else {
                    if (i + 1 == pathLinks.size()) {
                        cleanFlowRules(sd, curLink.dst().deviceId());
                    }
                }
            }
        }
    }

    // Removes flow rules off specified device with specific SrcDstPair
    private void cleanFlowRules(SrcDstPair pair, DeviceId id) {
        log.trace("Searching for flow rules to remove from: {}", id);
        log.trace("Removing flows w/ SRC={}, DST={}", pair.src, pair.dst);
        for (FlowEntry r : flowRuleService.getFlowEntries(id)) {
            boolean matchesSrc = false, matchesDst = false;
            for (Instruction i : r.treatment().allInstructions()) {
                if (i.type() == Instruction.Type.OUTPUT) {
                    // if the flow has matching src and dst
                    for (Criterion cr : r.selector().criteria()) {
                        if (cr.type() == Criterion.Type.ETH_DST) {
                            if (((EthCriterion) cr).mac().equals(pair.dst)) {
                                matchesDst = true;
                            }
                        } else if (cr.type() == Criterion.Type.ETH_SRC) {
                            if (((EthCriterion) cr).mac().equals(pair.src)) {
                                matchesSrc = true;
                            }
                        }
                    }
                }
            }
            if (matchesDst && matchesSrc) {
                log.trace("Removed flow rule from device: {}", id);
                flowRuleService.removeFlowRules((FlowRule) r);
            }
        }

    }

    // Returns a set of src/dst MAC pairs extracted from the specified set of flow entries
    private Set<SrcDstPair> findSrcDstPairs(Set<FlowEntry> rules) {
        ImmutableSet.Builder<SrcDstPair> builder = ImmutableSet.builder();
        for (FlowEntry r : rules) {
            MacAddress src = null, dst = null;
            for (Criterion cr : r.selector().criteria()) {
                if (cr.type() == Criterion.Type.ETH_DST) {
                    dst = ((EthCriterion) cr).mac();
                } else if (cr.type() == Criterion.Type.ETH_SRC) {
                    src = ((EthCriterion) cr).mac();
                }
            }
            builder.add(new SrcDstPair(src, dst));
        }
        return builder.build();
    }

    private Set<FlowEntry> getFlowRulesFrom(ConnectPoint egress) {
        ImmutableSet.Builder<FlowEntry> builder = ImmutableSet.builder();
        flowRuleService.getFlowEntries(egress.deviceId()).forEach(r -> {
            if (r.appId() == appId.id()) {
                r.treatment().allInstructions().forEach(i -> {
                    if (i.type() == Instruction.Type.OUTPUT) {
                        if (((Instructions.OutputInstruction) i).port().equals(egress.port())) {
                            builder.add(r);
                        }
                    }
                });
            }
        });

        return builder.build();
    }

    // Wrapper class for a source and destination pair of MAC addresses
    private final class SrcDstPair {
        final MacAddress src;
        final MacAddress dst;

        private SrcDstPair(MacAddress src, MacAddress dst) {
            this.src = src;
            this.dst = dst;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SrcDstPair that = (SrcDstPair) o;
            return Objects.equals(src, that.src) &&
                    Objects.equals(dst, that.dst);
        }

        @Override
        public int hashCode() {
            return Objects.hash(src, dst);
        }
    }


}
