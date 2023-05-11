package org.onosproject.scp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.onlab.packet.Ethernet;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.GroupId;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.group.DefaultGroupBucket;
import org.onosproject.net.group.DefaultGroupDescription;
import org.onosproject.net.group.DefaultGroupKey;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupKey;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.topology.TopologyEdge;

import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;

public class MultiPathInstaller {
    private final Logger log = getLogger(getClass());
    class DeviceRuleStore {

        protected HashMap<Set<Criterion>, GroupKey> groupKeyStore;
        protected HashMap<Set<Criterion>, FlowRule> flowRuleStore;
        DeviceId id;

        public DeviceRuleStore(DeviceId id) {
            this.groupKeyStore = new HashMap<>();
            this.flowRuleStore = new HashMap<>();
            this.id = id;
        }
    }

    private DeviceService deviceService;
    private GroupService groupService;
    private FlowRuleService flowRuleService;
    private ApplicationId appId;
    private HashMap<DeviceId, DeviceRuleStore> ruleStore;
    private static int groupId = 0;
    
    public MultiPathInstaller(DeviceService deviceService, GroupService groupService, FlowRuleService flowRuleService, ApplicationId appId){
        this.deviceService = deviceService;
        this.groupService = groupService;
        this.flowRuleService = flowRuleService;
        this.appId = appId;
        this.ruleStore = new HashMap<>();   
    }

    public void installPaths(){
        // iterate through all the Requests
        for(Requests rq : AppComponent.requests.values()){
            // group tables should be inserted per device per request
            for(Device device : deviceService.getDevices()){
                // used to identify old group table and delete it
                groupId++;
                
                // * Create a list of buckets per request per device
                ArrayList<GroupBucket> buckets = new ArrayList<>();   
                // for each path check if this device is involved and add it to the bucket
                for(Requests.path pt : rq.paths ){
                    short weight = (short) (pt.bw*1000);

                    if(weight == 0) continue;
                    for( TopologyEdge ed : pt.edges ){
                        Link l = ed.link();
                        if( l.src().deviceId().equals(device.id())){
                            TrafficTreatment.Builder tBuilder = DefaultTrafficTreatment.builder();
                            tBuilder.setOutput(l.src().port());
                            GroupBucket bucket = DefaultGroupBucket.createSelectGroupBucket(tBuilder.build(), weight);
                            buckets.add(bucket);
                            break;
                        }
                    }                    
                }
                // TODO check if this should be moved below remove all the previous flows and table
                if( buckets.isEmpty()) continue;

                // * select the traffic for which the group table should work
                TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPSrc(rq.src)
                    .matchIPDst(rq.dst)
                    .build();
                
                log.info("Selector done");
                Set<Criterion> criteria = selector.criteria();


                // * remove old group table and flow service
                // stores the ncessary information required for deleting old group table and flow
                DeviceRuleStore deviceRuleStore;

                if (ruleStore.containsKey(device.id())) deviceRuleStore = ruleStore.get(device.id());
                else {
                    deviceRuleStore = new DeviceRuleStore(device.id());
                    ruleStore.put(device.id(), deviceRuleStore);
                }
                
                if(deviceRuleStore.groupKeyStore.containsKey(criteria)){
                    GroupKey lastKey = deviceRuleStore.groupKeyStore.get(criteria);
                    groupService.removeGroup(device.id(), lastKey, appId);
                    flowRuleService.removeFlowRules(deviceRuleStore.flowRuleStore.get(criteria));      
                }
                log.info("Old info removed");
                // * create a new group table and insert it
                ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                buffer.putInt(groupId);                
                GroupKey newKey = new DefaultGroupKey(buffer.array());
                GroupDescription groupDescription = 
                    new DefaultGroupDescription(device.id(),
                        GroupDescription.Type.SELECT,
                        new GroupBuckets(buckets),
                        newKey, groupId, appId );
                
                groupService.addGroup(groupDescription);
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .group(new GroupId(groupId))
                        .build();
                log.info("Treatment generated");
                FlowRule rule = DefaultFlowRule.builder()
                        .forDevice(device.id())
                        .withSelector(selector)
                        .withTreatment(treatment)
                        .withPriority(55555)
                        .fromApp(appId)
                        .makePermanent()
                        .build();
                log.info("Flow rule generated");
                deviceRuleStore.flowRuleStore.put(criteria, rule);
                flowRuleService.applyFlowRules(rule);
                log.info("comp");
            }
        }
    }
}
