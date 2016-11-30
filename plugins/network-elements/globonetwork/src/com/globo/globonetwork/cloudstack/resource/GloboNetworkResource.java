/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.globo.globonetwork.cloudstack.resource;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.lb.LoadBalancingRule;
import com.globo.globonetwork.client.api.ExpectHealthcheckAPI;
import com.globo.globonetwork.client.api.GloboNetworkAPI;
import com.globo.globonetwork.client.api.PoolAPI;
import com.globo.globonetwork.client.model.healthcheck.ExpectHealthcheck;
import com.globo.globonetwork.client.model.pool.PoolV3;
import com.globo.globonetwork.cloudstack.commands.GetPoolLBByIdCommand;
import com.globo.globonetwork.cloudstack.commands.ListExpectedHealthchecksCommand;
import com.globo.globonetwork.cloudstack.commands.ListPoolLBCommand;
import com.globo.globonetwork.cloudstack.commands.UpdatePoolCommand;
import com.globo.globonetwork.cloudstack.manager.HealthCheckHelper;
import com.globo.globonetwork.cloudstack.response.GloboNetworkExpectHealthcheckResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.naming.ConfigurationException;

import com.globo.globonetwork.client.model.OptionVip;
import com.globo.globonetwork.client.model.Pool;
import com.globo.globonetwork.client.model.PoolOption;
import com.globo.globonetwork.client.model.VipPoolMap;
import com.globo.globonetwork.cloudstack.commands.ListGloboNetworkLBCacheGroupsCommand;
import com.globo.globonetwork.cloudstack.commands.ListPoolOptionsCommand;
import com.globo.globonetwork.cloudstack.response.GloboNetworkCacheGroupsResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolOptionResponse;
import org.apache.cloudstack.framework.config.dao.ConfigurationDaoImpl;
import org.apache.log4j.Logger;

import com.cloud.agent.IAgentControl;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.MaintainAnswer;
import com.cloud.agent.api.MaintainCommand;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.ReadyAnswer;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.resource.ServerResource;
import com.cloud.utils.component.ManagerBase;
import com.globo.globonetwork.client.exception.GloboNetworkErrorCodeException;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import com.globo.globonetwork.client.model.Environment;
import com.globo.globonetwork.client.model.Equipment;
import com.globo.globonetwork.client.model.Ip;
import com.globo.globonetwork.client.model.Network;
import com.globo.globonetwork.client.model.Real.RealIP;
import com.globo.globonetwork.client.model.Vip;
import com.globo.globonetwork.client.model.VipEnvironment;
import com.globo.globonetwork.client.model.Vlan;
import com.globo.globonetwork.cloudstack.commands.AcquireNewIpForLbCommand;
import com.globo.globonetwork.cloudstack.commands.ActivateNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.AddAndEnableRealInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.ApplyVipInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.CreateNewVlanInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.DeallocateVlanFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.DisableAndRemoveRealInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GenerateUrlForEditingVipCommand;
import com.globo.globonetwork.cloudstack.commands.GetNetworkFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GetVipInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GetVlanInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GloboNetworkErrorAnswer;
import com.globo.globonetwork.cloudstack.commands.ListAllEnvironmentsFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RegisterEquipmentAndIpInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.ReleaseIpFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveNetworkInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveVipFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.UnregisterEquipmentAndIpInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAllEnvironmentResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAndIPResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse.Real;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVlanResponse;
import sun.net.util.IPAddressUtil;

public class GloboNetworkResource extends ManagerBase implements ServerResource {
    private String _zoneId;

    private String _guid;

    private String _name;

    private String _username;

    private String _url;

    private String _password;

    protected GloboNetworkAPI _globoNetworkApi;

    private static final Logger s_logger = Logger.getLogger(GloboNetworkResource.class);

    private static final long NETWORK_TYPE = 6; // Rede invalida de equipamentos

    private static final Long EQUIPMENT_TYPE = 10L;

    private static final Integer DEFAULT_REALS_PRIORITY = 0;

    private static final Integer DEFAULT_REAL_WEIGHT = 0;

    private static final Integer DEFAULT_MAX_CONN = 0;
    private static final int DEFAULT_REAL_STATUS = 7;

    protected enum LbAlgorithm {
        RoundRobin("round-robin"), LeastConn("least-conn");

        String globoNetworkBalMethod;

        LbAlgorithm(String globoNetworkBalMethod) {
            this.globoNetworkBalMethod = globoNetworkBalMethod;
        }

        public String getGloboNetworkBalMethod() {
            return globoNetworkBalMethod;
        }
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {

        try {
            _zoneId = (String)params.get("zoneId");
            if (_zoneId == null) {
                throw new ConfigurationException("Unable to find zone");
            }

            _guid = (String)params.get("guid");
            if (_guid == null) {
                throw new ConfigurationException("Unable to find guid");
            }

            _name = (String)params.get("name");
            if (_name == null) {
                throw new ConfigurationException("Unable to find name");
            }

            _url = (String)params.get("url");
            if (_url == null) {
                throw new ConfigurationException("Unable to find url");
            }

            _username = (String)params.get("username");
            if (_username == null) {
                throw new ConfigurationException("Unable to find username");
            }

            _password = (String)params.get("password");
            if (_password == null) {
                throw new ConfigurationException("Unable to find password");
            }

            _globoNetworkApi = new GloboNetworkAPI(_url, _username, _password);

            if (params.containsKey("readTimeout")) {
                _globoNetworkApi.setReadTimeout(Integer.valueOf((String)params.get("readTimeout")));
            }

            if (params.containsKey("connectTimeout")) {
                _globoNetworkApi.setConnectTimeout(Integer.valueOf((String)params.get("connectTimeout")));
            }

            if (params.containsKey("numberOfRetries")) {
                _globoNetworkApi.setNumberOfRetries(Integer.valueOf((String)params.get("numberOfRetries")));
            }

            return true;
        } catch (NumberFormatException e) {
            s_logger.error("Invalid number in configuration parameters", e);
            throw new ConfigurationException("Invalid number in configuration parameters: " + e);
        }
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public Type getType() {
        return Host.Type.L2Networking;
    }

    @Override
    public StartupCommand[] initialize() {
        StartupCommand cmd = new StartupCommand(getType());
        cmd.setName(_name);
        cmd.setGuid(_guid);
        cmd.setDataCenter(_zoneId);
        cmd.setPod("");
        cmd.setPrivateIpAddress("");
        cmd.setStorageIpAddress("");
        cmd.setVersion(GloboNetworkResource.class.getPackage().getImplementationVersion());

        return new StartupCommand[] {cmd};
    }

    @Override
    public PingCommand getCurrentStatus(long id) {
        return new PingCommand(getType(), id);
    }

    @Override
    public void disconnected() {
    }

    @Override
    public IAgentControl getAgentControl() {
        return null;
    }

    @Override
    public void setAgentControl(IAgentControl agentControl) {
    }

    @Override
    public Answer executeRequest(Command cmd) {
        if (cmd instanceof ReadyCommand) {
            return new ReadyAnswer((ReadyCommand)cmd);
        } else if (cmd instanceof MaintainCommand) {
            return new MaintainAnswer((MaintainCommand)cmd);
        } else if (cmd instanceof GetVlanInfoFromGloboNetworkCommand) {
            return execute((GetVlanInfoFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof CreateNewVlanInGloboNetworkCommand) {
            return execute((CreateNewVlanInGloboNetworkCommand)cmd);
        } else if (cmd instanceof ActivateNetworkCommand) {
            return execute((ActivateNetworkCommand)cmd);
        } else if (cmd instanceof ListAllEnvironmentsFromGloboNetworkCommand) {
            return execute((ListAllEnvironmentsFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof RemoveNetworkInGloboNetworkCommand) {
            return execute((RemoveNetworkInGloboNetworkCommand)cmd);
        } else if (cmd instanceof DeallocateVlanFromGloboNetworkCommand) {
            return execute((DeallocateVlanFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof RegisterEquipmentAndIpInGloboNetworkCommand) {
            return execute((RegisterEquipmentAndIpInGloboNetworkCommand)cmd);
        } else if (cmd instanceof UnregisterEquipmentAndIpInGloboNetworkCommand) {
            return execute((UnregisterEquipmentAndIpInGloboNetworkCommand)cmd);
        } else if (cmd instanceof GetVipInfoFromGloboNetworkCommand) {
            return execute((GetVipInfoFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof AddAndEnableRealInGloboNetworkCommand) {
            return execute((AddAndEnableRealInGloboNetworkCommand)cmd);
        } else if (cmd instanceof DisableAndRemoveRealInGloboNetworkCommand) {
            return execute((DisableAndRemoveRealInGloboNetworkCommand)cmd);
        } else if (cmd instanceof GenerateUrlForEditingVipCommand) {
            return execute((GenerateUrlForEditingVipCommand)cmd);
        } else if (cmd instanceof RemoveVipFromGloboNetworkCommand) {
            return execute((RemoveVipFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof AcquireNewIpForLbCommand) {
            return execute((AcquireNewIpForLbCommand)cmd);
        } else if (cmd instanceof ReleaseIpFromGloboNetworkCommand) {
            return execute((ReleaseIpFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof ApplyVipInGloboNetworkCommand) {
            return execute((ApplyVipInGloboNetworkCommand)cmd);
        } else if (cmd instanceof GetNetworkFromGloboNetworkCommand) {
            return execute((GetNetworkFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof ListGloboNetworkLBCacheGroupsCommand) {
            return execute((ListGloboNetworkLBCacheGroupsCommand) cmd);
        }else if (cmd instanceof ListPoolOptionsCommand){
            return execute((ListPoolOptionsCommand) cmd);
        }else if (cmd instanceof ListPoolLBCommand) {
            return execute((ListPoolLBCommand) cmd);
        }else if (cmd instanceof GetPoolLBByIdCommand) {
            return execute((GetPoolLBByIdCommand) cmd);
        }else if (cmd instanceof ListExpectedHealthchecksCommand) {
            return execute((ListExpectedHealthchecksCommand) cmd);
        }else if (cmd instanceof UpdatePoolCommand) {
            return execute((UpdatePoolCommand) cmd);
        }
        return Answer.createUnsupportedCommandAnswer(cmd);
    }

    private Answer execute(ListExpectedHealthchecksCommand cmd) {
        try{
            ExpectHealthcheckAPI api = _globoNetworkApi.getExpectHealthcheckAPI();

            List<ExpectHealthcheck> expectHealthchecks = api.listHealthcheck();

            List<GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck> result = new ArrayList<GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck>();

            for (ExpectHealthcheck expectHealthcheck : expectHealthchecks){
                result.add(new GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck(expectHealthcheck.getId(), expectHealthcheck.getExpect()));
            }

            GloboNetworkExpectHealthcheckResponse response = new GloboNetworkExpectHealthcheckResponse(result);
            return response;
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        } catch (Exception e) {
            s_logger.error("Generic error accessing GloboNetwork while update pool", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    private Answer execute(UpdatePoolCommand cmd) {
        try {
            PoolAPI poolAPI = _globoNetworkApi.getPoolAPI();

            List<GloboNetworkPoolResponse.Pool> pools = new ArrayList<GloboNetworkPoolResponse.Pool>();
            List<PoolV3> poolsV3 = poolAPI.getByIdsV3(cmd.getPoolIds());
            for (PoolV3 poolv3 : poolsV3) {

                PoolV3.Healthcheck healthCheck = poolv3.getHealthcheck();
                healthCheck.setHealthcheck(cmd.getHealthcheckType(), cmd.getHealthcheck(), cmd.getExpectedHealthcheck() );

                poolv3.setMaxconn(cmd.getMaxConn());
            }

            if ( poolsV3.size() > 0 ) {
                if (poolsV3.get(0).isPoolCreated()) {
                    poolAPI.updateDeployAll(poolsV3);
                } else {
                    poolAPI.updateAll(poolsV3);
                }
            }

            for (PoolV3 poolv3 : poolsV3) {
                pools.add(poolV3FromNetworkApi(poolv3));
            }

            GloboNetworkPoolResponse answer = new GloboNetworkPoolResponse(cmd, pools, true, "");

            return answer;
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        } catch (Exception e) {
            s_logger.error("Generic error accessing GloboNetwork while update pool", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }


    private Answer execute(GetPoolLBByIdCommand cmd) {
        try {
            Pool.PoolResponse poolResponse = _globoNetworkApi.getPoolAPI().getByPk(cmd.getPoolId());

            GloboNetworkPoolResponse.Pool poolCS = poolFromNetworkApi(poolResponse.getPool());
            GloboNetworkPoolResponse answer = new GloboNetworkPoolResponse(cmd, true, "", poolCS);

            return answer;
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        } catch (Exception e) {
            s_logger.error("Generic error accessing GloboNetwork while getPoolById", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    private Answer execute(ListPoolLBCommand cmd) {
        try {
            List<Pool> poolsNetworkApi= _globoNetworkApi.getPoolAPI().listAllByReqVip(cmd.getVipId());

            List<GloboNetworkPoolResponse.Pool> pools = new ArrayList<GloboNetworkPoolResponse.Pool>();
            for (Pool pool : poolsNetworkApi) {
                GloboNetworkPoolResponse.Pool poolCS = poolFromNetworkApi(pool);
                pools.add(poolCS);
            }

            GloboNetworkPoolResponse answer = new GloboNetworkPoolResponse(cmd, true, "", pools);
            return answer;
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        } catch (Exception e) {
            s_logger.error("Generic error accessing GloboNetwork", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    private static GloboNetworkPoolResponse.Pool poolFromNetworkApi(Pool poolNetworkApi) throws GloboNetworkException {
        GloboNetworkPoolResponse.Pool pool = new GloboNetworkPoolResponse.Pool();

        pool.setId(poolNetworkApi.getId());
        pool.setIdentifier(poolNetworkApi.getIdentifier());
        pool.setPort(poolNetworkApi.getDefaultPort());
        pool.setLbMethod(poolNetworkApi.getLbMethod());
        pool.setMaxconn(poolNetworkApi.getMaxconn());
        pool.setVipPort(poolNetworkApi.getVipPort());


        Pool.Healthcheck healthcheck = poolNetworkApi.getHealthcheck();

        if ( healthcheck != null ) {
            pool.setHealthcheck(healthcheck.getHealthcheckRequest());
            pool.setHealthcheckType(healthcheck.getHealthcheckType());
            pool.setExpectedHealthcheck(healthcheck.getExpectedHealthcheck());
        }

        return pool;
    }
    private static GloboNetworkPoolResponse.Pool poolV3FromNetworkApi(PoolV3 poolNetworkApi) throws GloboNetworkException {
        GloboNetworkPoolResponse.Pool pool = new GloboNetworkPoolResponse.Pool();

        pool.setId(poolNetworkApi.getId());
        pool.setIdentifier(poolNetworkApi.getIdentifier());
        pool.setPort(poolNetworkApi.getDefaultPort());
        pool.setLbMethod(poolNetworkApi.getLbMethod());
        pool.setMaxconn(poolNetworkApi.getMaxconn());

        PoolV3.Healthcheck healthcheck = poolNetworkApi.getHealthcheck();

        if ( healthcheck != null ) {
            pool.setHealthcheck(healthcheck.getHealthcheckRequest());
            pool.setHealthcheckType(healthcheck.getHealthcheckType());
            pool.setExpectedHealthcheck(healthcheck.getExpectedHealthcheck());
        }

        return pool;
    }

    private Answer execute(ListPoolOptionsCommand cmd) {
        try {
            Network network = _globoNetworkApi.getNetworkJsonAPI().listVipNetworks(cmd.getGloboNetworkLBEnvironmentId(), false).get(0);
            Vlan vlan = _globoNetworkApi.getVlanAPI().getById(network.getVlanId());
            List<PoolOption> poolOptions = _globoNetworkApi.getPoolAPI().listPoolOptions(vlan.getEnvironment(), cmd.getType());

            List<GloboNetworkPoolOptionResponse.PoolOption> options = new ArrayList<>();
            for(PoolOption option : poolOptions){
                options.add(new GloboNetworkPoolOptionResponse.PoolOption(option.getId(), option.getName()));
            }
            return new GloboNetworkPoolOptionResponse(cmd, options);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        } catch (IOException e) {
            s_logger.error("Generic error accessing GloboNetwork", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    private Answer execute(ListGloboNetworkLBCacheGroupsCommand cmd) {
        try {
            List<OptionVip> optionVips = _globoNetworkApi.getOptionVipAPI().listCacheGroups(cmd.getLBEnvironmentId());
            List<String> cacheGroups = new ArrayList<String>();
            if (optionVips != null) {
                for(OptionVip optionVip : optionVips) {
                    cacheGroups.add(optionVip.getCacheGroup());
                }
            }
            // if optionVips is null, then an empty list will be returned
            return new GloboNetworkCacheGroupsResponse(cmd, cacheGroups);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    private Answer execute(GetNetworkFromGloboNetworkCommand cmd) {
        try {
            if (cmd.getNetworkId() == null) {
                return new Answer(cmd, false, "Invalid network ID");
            }

            Network network = _globoNetworkApi.getNetworkAPI().getNetwork(cmd.getNetworkId(), cmd.isv6());
            if (network == null) {
                return new Answer(cmd, false, "Network with ID " + cmd.getNetworkId() + " not found in GloboNetwork");
            }

            return this.createNetworkResponse(network, cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    private Answer handleGloboNetworkException(Command cmd, GloboNetworkException e) {
        if (e instanceof GloboNetworkErrorCodeException) {
            GloboNetworkErrorCodeException ex = (GloboNetworkErrorCodeException)e;
            s_logger.error("Error accessing GloboNetwork: " + ex.getCode() + " - " + ex.getDescription(), ex);
            return new GloboNetworkErrorAnswer(cmd, ex.getCode(), ex.getDescription());
        } else {
            s_logger.error("Generic error accessing GloboNetwork", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    public Answer execute(AddAndEnableRealInGloboNetworkCommand cmd) {
        try {
            Vip vip = _globoNetworkApi.getVipAPI().getById(cmd.getVipId());
            if (vip == null || !cmd.getVipId().equals(vip.getId())) {
                return new Answer(cmd, false, "Vip request " + cmd.getVipId() + " not found in GloboNetwork");
            }

            Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(cmd.getEquipName());
            if (equipment == null) {
                // Equipment doesn't exist
                return new Answer(cmd, false, "Equipment " + cmd.getEquipName() + " doesn't exist in GloboNetwork");
            }

            List<Ip> ips = _globoNetworkApi.getIpAPI().findIpsByEquipment(equipment.getId());
            Ip ip = null;
            for (Ip equipIp : ips) {
                String equipIpString = equipIp.getIpString();
                if (equipIpString.equals(cmd.getIp())) {
                    ip = equipIp;
                }
            }

            if (ip == null) {
                return new Answer(cmd, false, "IP doesn't exist in this GloboNetwork environment");
            }

            if (!vip.getValidated()) {
                _globoNetworkApi.getVipAPI().validate(cmd.getVipId());
            }

            if (!vip.getCreated()) {
                s_logger.info("Requesting GloboNetwork to create vip " + vip.getId());
                _globoNetworkApi.getVipAPI().create(cmd.getVipId());
            }

            if (vip.getRealsIp() != null) {
                for (RealIP realIp : vip.getRealsIp()) {
                    if (ip.getId().equals(realIp.getIpId())) {
                        // real already added. Only ensure is enabled
                        _globoNetworkApi.getVipAPI().enableReal(cmd.getVipId(), ip.getId(), equipment.getId(), null, null);
                        return new Answer(cmd, true, "Real enabled successfully");
                    }
                }
            }

            // added reals are always enabled by default
            _globoNetworkApi.getVipAPI().addReal(cmd.getVipId(), ip.getId(), equipment.getId(), null, null);
            return new Answer(cmd, true, "Real added and enabled successfully");

        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(DisableAndRemoveRealInGloboNetworkCommand cmd) {
        try {
            Vip vip = _globoNetworkApi.getVipAPI().getById(cmd.getVipId());
            if (vip == null || !cmd.getVipId().equals(vip.getId())) {
                return new Answer(cmd, false, "Vip request " + cmd.getVipId() + " not found in GloboNetwork");
            }

            Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(cmd.getEquipName());
            if (equipment == null) {
                // Equipment doesn't exist. So, there is no Vip either.
                return new Answer(cmd, true, "Equipment " + cmd.getEquipName() + " doesn't exist in GloboNetwork");
            }

            if (vip.getRealsIp() != null) {
                for (RealIP realIp : vip.getRealsIp()) {
                    if (cmd.getIp().equals(realIp.getRealIp())) {
                        // real exists in vip. Remove it.
                        _globoNetworkApi.getVipAPI().removeReal(cmd.getVipId(), realIp.getIpId(), equipment.getId(), realIp.getVipPort(), realIp.getRealPort());
                        return new Answer(cmd, true, "Real removed successfully");
                    }
                }
            }
            return new Answer(cmd, true, "Real not in vipId " + cmd.getVipId());

        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(RemoveVipFromGloboNetworkCommand cmd) {
        if (cmd.getVipId() == null) {
            return new Answer(cmd, true, "Vip request was previously removed from GloboNetwork");
        }
        try {
            VipApiAdapter vipApiAdapter = this.createVipApiAdapter(cmd.getVipId());
            if (!vipApiAdapter.hasVip()) {
                return new Answer(cmd, true, "Vip request " + cmd.getVipId() + " was previously removed from GloboNetwork");
            }

            vipApiAdapter.undeploy();

            s_logger.info("Requesting GloboNetwork to delete vip vip_id=" + cmd.getVipId() + " keepIp=" + cmd.isKeepIp());
            vipApiAdapter.delete(cmd.isKeepIp());

            return new Answer(cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(GetVlanInfoFromGloboNetworkCommand cmd) {
        try {
            Vlan vlan = _globoNetworkApi.getVlanAPI().getById(cmd.getVlanId());
            return createResponse(vlan, cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(CreateNewVlanInGloboNetworkCommand cmd) {
        Vlan vlan = null;
        try {
            vlan = _globoNetworkApi.getVlanAPI().allocateWithoutNetwork(cmd.getGloboNetworkEnvironmentId(), cmd.getVlanName(), cmd.getVlanDescription());

            /*Network network = */_globoNetworkApi.getNetworkAPI().addNetwork(vlan.getId(), Long.valueOf(NETWORK_TYPE), null, cmd.isIpv6(), cmd.getSubnet());

            // Bug in GloboNetworkApi: I need to have a second call to get networkid
            vlan = _globoNetworkApi.getVlanAPI().getById(vlan.getId());
            return createResponse(vlan, cmd);
        } catch (GloboNetworkException e) {
            if (vlan != null) {
                try {
                    _globoNetworkApi.getVlanAPI().deallocate(vlan.getId());
                } catch (GloboNetworkException ex) {
                    s_logger.error("Error deallocating vlan " + vlan.getId() + "from GloboNetwork.");
                }
            }
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ActivateNetworkCommand cmd) {
        try {
            _globoNetworkApi.getNetworkJsonAPI().createNetworks(cmd.getNetworkId(), cmd.isv6());
            return new Answer(cmd, true, "Network created");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ListAllEnvironmentsFromGloboNetworkCommand cmd) {
        try {
            List<Environment> apiEnvironmentList = _globoNetworkApi.getEnvironmentAPI().listAll();

            List<GloboNetworkAllEnvironmentResponse.Environment> environmentList = new ArrayList<GloboNetworkAllEnvironmentResponse.Environment>(apiEnvironmentList.size());
            for (Environment apiEnvironment : apiEnvironmentList) {
                GloboNetworkAllEnvironmentResponse.Environment environment = new GloboNetworkAllEnvironmentResponse.Environment();
                environment.setId(apiEnvironment.getId());
                environment.setDcDivisionName(apiEnvironment.getDcDivisionName());
                environment.setL3GroupName(apiEnvironment.getL3GroupName());
                environment.setLogicalEnvironmentName(apiEnvironment.getLogicalEnvironmentName());
                environmentList.add(environment);
            }

            return new GloboNetworkAllEnvironmentResponse(cmd, environmentList);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(RemoveNetworkInGloboNetworkCommand cmd) {
        try {
            _globoNetworkApi.getNetworkJsonAPI().removeNetwork(cmd.getNetworkId(), cmd.isIpv6());
            return new Answer(cmd, true, "Network removed");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(DeallocateVlanFromGloboNetworkCommand cmd) {
        try {
            _globoNetworkApi.getVlanAPI().deallocate(cmd.getVlanId());
            return new Answer(cmd, true, "Vlan deallocated");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(RegisterEquipmentAndIpInGloboNetworkCommand cmd) {
        try {
            Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(cmd.getVmName());
            if (equipment == null) {
                s_logger.info("Registering virtualmachine " + cmd.getVmName() + " in GloboNetwork");
                // Equipment (VM) does not exist, create it
                equipment = _globoNetworkApi.getEquipmentAPI().insert(cmd.getVmName(), EQUIPMENT_TYPE, cmd.getEquipmentModelId(), cmd.getEquipmentGroupId());
            }

            Vlan vlan = _globoNetworkApi.getVlanAPI().getById(cmd.getVlanId());

            // Make sure this vlan has only one IPv4/IPv6 network associated to it
            if (vlan.getNetworks().size() == 0) {
                return new Answer(cmd, false, "No IPv4/IPv6 networks in this vlan");
            } else if (vlan.getNetworks().size() > 1) {
                return new Answer(cmd, false, "Multiple IPv4/IPv6 networks in this vlan");
            }
            Network network = vlan.getNetworks().get(0);

            Ip ip = _globoNetworkApi.getIpAPI().findByIpAndEnvironment(cmd.getNicIp(), cmd.getEnvironmentId(), network.isv6());
            if (ip == null) {
                // Doesn't exist, create it
                ip = _globoNetworkApi.getIpAPI().saveIp(cmd.getNicIp(), equipment.getId(), cmd.getNicDescription(), network.getId(), network.isv6());
            } else {
                ip = _globoNetworkApi.getIpAPI().getIp(ip.getId(), false);
                if (!ip.getEquipments().contains(cmd.getVmName())) {
                    _globoNetworkApi.getIpAPI().assocIp(ip.getId(), equipment.getId(), network.getId(), network.isv6(), cmd.getNicDescription());
                }
            }

            if (ip == null) {
                return new Answer(cmd, false, "Could not register NIC in GloboNetwork");
            }

            return new Answer(cmd, true, "NIC " + cmd.getNicIp() + " registered successfully in GloboNetwork");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(UnregisterEquipmentAndIpInGloboNetworkCommand cmd) {
        try {
            Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(cmd.getVmName());
            if (equipment == null) {
                s_logger.warn("VM was removed from GloboNetwork before being destroyed in Cloudstack. This is not critical, logging inconsistency: VM UUID " + cmd.getVmName());
                return new Answer(cmd);
            }

            if (cmd.getEnvironmentId() != null && cmd.getNicIp() != null) {
                Ip ip = _globoNetworkApi.getIpAPI().findByIpAndEnvironment(cmd.getNicIp(), cmd.getEnvironmentId(), cmd.isv6());
                if (ip == null) {
                    // Doesn't exist, ignore
                    s_logger.warn("IP was removed from GloboNetwork before being destroyed in Cloudstack. This is not critical, logging inconsistency: IP " + cmd.getNicIp());
                } else {
                    _globoNetworkApi.getEquipmentAPI().removeIP(equipment.getId(), ip.getId(), cmd.isv6());
                }
            }

            // if there are no more IPs in equipment, remove it.
            List<Ip> ipList = _globoNetworkApi.getIpAPI().findIpsByEquipment(equipment.getId());
            if (ipList.size() == 0) {
                _globoNetworkApi.getEquipmentAPI().delete(equipment.getId());
            }

            return new Answer(cmd, true, "NIC " + cmd.getNicIp() + " deregistered successfully in GloboNetwork");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ReleaseIpFromGloboNetworkCommand cmd) {
        try {
            Ip ip = _globoNetworkApi.getIpAPI().checkVipIp(cmd.getIp(), cmd.getVipEnvironmentId(), cmd.isv6());
            if (ip == null) {
                // Doesn't exist, ignore
                s_logger.warn("IP was removed from GloboNetwork before being destroyed in Cloudstack. This is not critical.");
            } else {
                _globoNetworkApi.getIpAPI().deleteIp(ip.getId(), cmd.isv6());
            }
            return new Answer(cmd, true, "IP deleted successfully from GloboNetwork");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(GetVipInfoFromGloboNetworkCommand cmd) {
        try {
            Vip vip = null;
            if (cmd.getVipId() != null) {
                long vipId = cmd.getVipId();
                vip = _globoNetworkApi.getVipAPI().getById(vipId);
            } else {
                Ip ip = _globoNetworkApi.getIpAPI().checkVipIp(cmd.getIp(), cmd.getVipEnvironmentId(), cmd.isv6());
                if (ip != null) {
                    List<Vip> vips = _globoNetworkApi.getVipAPI().getByIp(ip.getIpString());
                    if (!vips.isEmpty()) {
                        vip = vips.get(0);
                    }
                }
            }

            if (vip == null) {
                return new Answer(cmd, false, null);
            }
            return this.createVipResponse(vip, cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(GenerateUrlForEditingVipCommand cmd) {

        try {
            String url = _globoNetworkApi.getVipAPI().generateVipEditingUrl(cmd.getVipId(), cmd.getVipServerUrl());
            return new Answer(cmd, true, url);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(AcquireNewIpForLbCommand cmd) {
        try {
            long globoNetworkLBEnvironmentId = cmd.getGloboNetworkLBEnvironmentId();
            Ip globoIp = _globoNetworkApi.getIpAPI().getAvailableIpForVip(globoNetworkLBEnvironmentId, cmd.getDescription(), cmd.isv6());
            if (globoIp == null) {
                return new Answer(cmd, false, "No available ip address for load balancer environment network " + globoNetworkLBEnvironmentId);
            }

            // get network information
            Long networkId = globoIp.getNetworkId();
            Network network = _globoNetworkApi.getNetworkAPI().getNetwork(networkId, cmd.isv6());
            if (network == null) {
                return new Answer(cmd, false, "Network with id " + networkId + " not found");
            }

            GloboNetworkAndIPResponse answer = (GloboNetworkAndIPResponse)this.createNetworkResponse(network, cmd);

            // ip information
            answer.setIp(globoIp.getIpString());
            answer.setIpId(globoIp.getId());

            return answer;
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ApplyVipInGloboNetworkCommand cmd) {
        List<VipPoolMap> vipPoolMapping = null;
        VipApiAdapter vipApiAdapter = null;

        try {
            s_logger.debug("[ApplyVip_" + cmd.getHost() + "] Vip_id: " + cmd.getVipId() + " ip: " + cmd.getIpv4() + " envId: " + cmd.getVipEnvironmentId());
            Long start = new Date().getTime();

            vipApiAdapter = this.createVipApiAdapter(cmd.getVipId());
            VipInfoHelper vipInfo = getVipInfos(cmd.getVipEnvironmentId(), cmd.getIpv4());
            List<PoolV3.PoolMember> poolMembers = buildPoolMembers(cmd.getRealList());
            vipPoolMapping = savePools(vipApiAdapter.getPoolIds(), vipInfo, poolMembers, cmd);

            if (!vipApiAdapter.hasVip()) {
                vipApiAdapter.save(cmd, cmd.getHost(), vipInfo.vipEnvironment, vipInfo.vipIp, vipPoolMapping);
            } else {
                vipApiAdapter.update(cmd, vipInfo.vipIp, vipPoolMapping);
            }

            vipApiAdapter.validate(vipInfo.vipIp).deploy();

            Answer vipResponse = vipApiAdapter.createVipResponse(cmd);
            Long time = new Date().getTime() - start;

            s_logger.debug("[ApplyVip END] Vip: " + cmd.getHost() + ", ip: " + cmd.getIpv4() +", Operation time: " + time + " ms");
            return vipResponse;
        }catch (GloboNetworkException e) {
            rollbackVipCreation(cmd, vipApiAdapter, vipPoolMapping);
            return handleGloboNetworkException(cmd, e);
        } catch (InvalidParameterValueException e){
            s_logger.error("Error", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    private void rollbackVipCreation(ApplyVipInGloboNetworkCommand cmd, VipApiAdapter adapter, List<VipPoolMap> poolMappings){
        if(adapter != null && !adapter.hasVip() && cmd.getVipId() == null && poolMappings != null){
            List<Long> ids = new ArrayList<>();
            for(VipPoolMap vipPoolMap : poolMappings){
                ids.add(vipPoolMap.getPoolId());
            }
            try {
                _globoNetworkApi.getPoolAPI().delete(ids);
            } catch (GloboNetworkException e) {
                s_logger.error("It was not possible to cleanup pools after failed vip creation", e);
            }
        }
    }

    protected String buildPoolName(String region, String host, Integer vipPort, Integer realport) {
        return "ACS_POOL_" + region + "_" + host + "_" + vipPort +  "_" + realport;
    }

    protected LbAlgorithm getBalancingAlgorithm(String methodBal) {
        LbAlgorithm lbAlgorithm;
        if ("roundrobin".equals(methodBal)) {
            lbAlgorithm = LbAlgorithm.RoundRobin;
        } else if ("leastconn".equals(methodBal)) {
            lbAlgorithm = LbAlgorithm.LeastConn;
        } else {
            throw new InvalidParameterValueException("Invalid balancing method provided.");
        }
        return lbAlgorithm;
    }

    protected PoolV3 findPoolByPort(Integer port, List<PoolV3> pools) {
        if(pools != null) {
            for (PoolV3 pool : pools) {
                if (port.equals(pool.getDefaultPort())) {
                    return pool;
                }
            }
        }
        return null;
    }

    protected Answer createVipResponse(Vip vip, Command cmd) {
        if (vip == null || vip.getId() == null) {
            return new Answer(cmd, false, "Vip request was not created in GloboNetwork");
        }

        try {
            // Using a map rather than a list because different ports come in different objects
            // even though they have the same ID
            // Example
            // {
            //    "id_ip": "33713",
            //    "port_real": "8180",
            //    "port_vip": "80",
            //    "real_ip": "10.20.30.40",
            //    "real_name": "MACHINE01"
            // },
            // {
            //    "id_ip": "33713",
            //    "port_real": "8280",
            //    "port_vip": "80",
            //    "real_ip": "10.20.30.40",
            //    "real_name": "MACHINE01"
            // },

            Map<Long, Real> reals = new HashMap<Long, Real>();
            for (RealIP real : vip.getRealsIp()) {
                Real realResponse = reals.get(real.getIpId());
                if (realResponse == null) {
                    // Doesn't exist yet, first time iterating, so add IP parameter and add to list
                    realResponse = new Real();
                    realResponse.setIp(real.getRealIp());
                    realResponse.setVmName(real.getName());
                    reals.put(real.getIpId(), realResponse);
                }
                realResponse.getPorts().addAll(vip.getServicePorts());
            }

            VipEnvironment vipEnvironment = _globoNetworkApi.getVipEnvironmentAPI().search(null, vip.getFinality(), vip.getClient(), vip.getEnvironment());
            if (vipEnvironment == null) {
                throw new GloboNetworkException("Vip Environment not found for vip " + vip.getId());
            }

            Ip ip = _globoNetworkApi.getIpAPI().checkVipIp(vip.getIps().get(0), vipEnvironment.getId(), false);
            if (ip == null) {
                throw new GloboNetworkException("Vip IP not found for vip " + vip.getId());
            }

            return new GloboNetworkVipResponse(
                    cmd, vip.getId(),
                    vip.getHost(),
                    vip.getIps().size() == 1 ? vip.getIps().get(0) : vip.getIps().toString(),
                    ip.getId(),
                    vipEnvironment.getId(), null,
                    vip.getCache(),
                    vip.getMethod(),
                    vip.getPersistence(),
                    vip.getHealthcheckType(),
                    vip.getHealthcheck(),
                    vip.getMaxConn(),
                    vip.getServicePorts(), reals.values(), vip.getCreated()
            );
        } catch (GloboNetworkException e) {
            return new Answer(cmd, e);
        }

    }

    private Answer createResponse(Vlan vlan, Command cmd) {

        if (vlan.getIpv4Networks().isEmpty() && vlan.getIpv6Networks().isEmpty()) {
            // Error code 116 from GloboNetwork: 116 : VlanNaoExisteError,
            return new GloboNetworkErrorAnswer(cmd, 116, "No networks in this VLAN");
        }

        String vlanName = vlan.getName();
        String vlanDescription = vlan.getDescription();
        Long vlanId = vlan.getId();
        Long vlanNum = vlan.getVlanNum();
        Network network = vlan.getNetworks().get(0);

        return new GloboNetworkVlanResponse(cmd, vlanId, vlanName, vlanDescription, vlanNum, network.getNetworkAddressAsString(),
                network.getMaskAsString(), network.getId(), network.getActive(), network.getBlock(), network.isv6());
    }

    private Answer createNetworkResponse(Network network, Command cmd) throws GloboNetworkException {
        GloboNetworkAndIPResponse answer = new GloboNetworkAndIPResponse(cmd);
        answer.setNetworkId(network.getId());
        answer.setVipEnvironmentId(network.getVipEnvironmentId());
        answer.setNetworkAddress(network.getNetworkAddressAsString());
        answer.setNetworkMask(network.getMaskAsString());
        answer.setActive(Boolean.TRUE.equals(network.getActive()));
        answer.setNetworkCidr(network.getNetworkAddressAsString() + "/" + network.getBlock());
        answer.setIsv6(network.isv6());

        // get vlan information
        Long vlanId = network.getVlanId();
        Vlan vlan = _globoNetworkApi.getVlanAPI().getById(vlanId);
        if (vlan == null) {
            return new Answer(cmd, false, "Vlan with id " + vlanId + " not found");
        }
        answer.setVlanId(vlanId);
        answer.setVlanName(vlan.getName());
        answer.setVlanDescription(vlan.getDescription());
        answer.setVlanNum(vlan.getVlanNum().intValue());
        return answer;
    }

    public enum PersistenceMethod {
        NONE("(nenhum)", "None"),
        COOKIE("cookie", "Cookie"),
        SOURCE_IP("source-ip", "Source-ip"),
        SOURCE_IP_PERSISTENCE_PORTS("source-ip com persist. entre portas", "Source-ip with persistence between ports"),
        PRIORITY_FAILOVER("Priority_Failover", "Priority Failover");


        private String networkAPICode;
        private String description;
        private  PersistenceMethod(String networkAPICode, String description){
            this.networkAPICode = networkAPICode;
            this.description = description;
        }
        public static String fromPersistencePolicy(LoadBalancingRule.LbStickinessPolicy policy) {
            if (policy == null) {
                return PersistenceMethod.NONE.networkAPICode;
            }

            for (PersistenceMethod persistenceMethod : PersistenceMethod.values()) {
                if (persistenceMethod.description.equals(policy.getMethodName())) {
                    return persistenceMethod.networkAPICode;
                }
            }

            throw new InvalidParameterValueException("Invalid persistence policy provided. value: " + policy.getMethodName());
        }
    }

    protected List<VipPoolMap> savePools(List<Long> poolIds, VipInfoHelper vipInfos, List<PoolV3.PoolMember> poolMembers, ApplyVipInGloboNetworkCommand cmd) throws GloboNetworkException {
        Map<String, VipPoolMap> vipPoolMaps = new LinkedHashMap<>(); //just for test order

        PoolAPI poolAPI = _globoNetworkApi.getPoolAPI();

        for (String port : cmd.getPorts()) {
            String[] splittedPorts = port.split(":");
            Integer vipPort = Integer.valueOf(splittedPorts[0]);
            Integer realPort = Integer.valueOf(splittedPorts[1]);

            List<PoolV3> pools = new ArrayList<>();
            if(poolIds != null && !poolIds.isEmpty()){
                pools = poolAPI.getByIdsV3(poolIds);
            }
            PoolV3 poolV3 = findPoolByPort(realPort, pools);

            //case when user add or remove real, does not need to update pool settings, only reals
            if (poolV3 != null) {
                List<PoolV3.PoolMember> poolMembersFinal = new ArrayList<PoolV3.PoolMember>();

                for (PoolV3.PoolMember poolMember : poolMembers) {
                    PoolV3.PoolMember poolMemberAlreadyExists = findExistsRealInPool(poolMember, poolV3.getPoolMembers());

                    if (poolMemberAlreadyExists == null) {
                        poolMember.setPortReal(realPort);// for new reals
                        poolMembersFinal.add(poolMember);
                    } else {
                        poolMemberAlreadyExists.setEquipment(poolMember.getEquipment());
                        poolMemberAlreadyExists.setIp(poolMember.getIp());
                        poolMembersFinal.add(poolMemberAlreadyExists);
                    }
                }
                poolV3.setPoolMembers(poolMembersFinal);
            } else {
                // new pool
                poolV3 = new PoolV3();
                poolV3.setMaxconn(DEFAULT_MAX_CONN);
                poolV3.setIdentifier(buildPoolName(cmd.getRegion(), cmd.getHost(), vipPort, realPort));
                poolV3.setDefaultPort(realPort);
                poolV3.setEnvironment(vipInfos.getEnvironment());

                PoolV3.Healthcheck healthcheck = poolV3.getHealthcheck();
                healthcheck.setDestination(cmd.getHealthCheckDestination());
                if (forceSupportOldPoolVersion(cmd.getHealthcheckType(), realPort)) {
                    healthcheck.setHealthcheck("TCP", "", "");
                } else {
                    healthcheck.setHealthcheck(cmd.getHealthcheckType(), cmd.getHealthcheck(), cmd.getExpectedHealthcheck());
                }

                LbAlgorithm lbAlgorithm = getBalancingAlgorithm(cmd.getMethodBal());
                poolV3.setLbMethod(lbAlgorithm.getGloboNetworkBalMethod());

                PoolV3.ServiceDownAction serviceDownAction = new PoolV3.ServiceDownAction();
                serviceDownAction.setName(cmd.getServiceDownAction());
                poolV3.setServiceDownAction(serviceDownAction);

                for (PoolV3.PoolMember poolMember : poolMembers) {
                    poolMember.setPortReal(realPort);
                    poolV3.getPoolMembers().add(poolMember);
                }
            }

            poolV3 = poolAPI.save(poolV3);

            vipPoolMaps.put(port, new VipPoolMap(poolV3.getId(), vipPort));
        }

        return new ArrayList(vipPoolMaps.values());
    }

    protected boolean forceSupportOldPoolVersion(String healthcheckType, Integer realPort) {
        return (!healthcheckType.equals(HealthCheckHelper.HealthCheckType.HTTPS.name())
                &&(realPort == 443 ||  realPort == 8443));
    }

    private PoolV3.PoolMember findExistsRealInPool(PoolV3.PoolMember poolMember, List<PoolV3.PoolMember> poolMembersFromNetworkApi) {

        for (PoolV3.PoolMember poolMemberCreated : poolMembersFromNetworkApi) {
            if (poolMemberCreated.getIpFormated().equals(poolMember.getIpFormated())) {
                return poolMemberCreated;
            }
        }
        return null;
    }

    protected List<PoolV3.PoolMember> buildPoolMembers(List<Real> realList) throws GloboNetworkException {
        List<PoolV3.PoolMember> poolMembers = new ArrayList<PoolV3.PoolMember>();

        for (Real real : realList) {
            if (real.isRevoked()) {
                continue;
            }
            Ip equipmentIp = _globoNetworkApi.getIpAPI().findByIpAndEnvironment(real.getIp(), real.getEnvironmentId(), false);
            if (equipmentIp == null) {
                throw new InvalidParameterValueException("Could not get information by real IP: " + real.getIp());
            }
            Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(real.getVmName());

            PoolV3.PoolMember poolMember = new PoolV3.PoolMember();

            poolMember.setPriority(DEFAULT_REALS_PRIORITY);
            poolMember.setWeight(DEFAULT_REAL_WEIGHT);
            poolMember.setMemberStatus(DEFAULT_REAL_STATUS);

            poolMember.setEquipmentId(equipment.getId());
            poolMember.setEquipmentName(equipment.getName());

            PoolV3.Ip ipReal = new PoolV3.Ip();
            ipReal.setIpFormated(real.getIp());
            ipReal.setId(equipmentIp.getId());


            boolean isIPv6 = IPAddressUtil.isIPv6LiteralAddress(real.getIp());
            if (isIPv6) {
                poolMember.setIpv6(ipReal);
            } else {
                poolMember.setIp(ipReal);
            }

            poolMembers.add(poolMember);
        }

        return poolMembers;
    }

    protected VipInfoHelper getVipInfos(Long vipEnvironmentId, String ipv4) throws GloboNetworkException {
        VipEnvironment environmentVip = _globoNetworkApi.getVipEnvironmentAPI().search(vipEnvironmentId, null, null, null);
        if (environmentVip == null) {
            throw new InvalidParameterValueException("Could not find VIP environment " + vipEnvironmentId);
        }

        Ip vipIp = _globoNetworkApi.getIpAPI().checkVipIp(ipv4, vipEnvironmentId, false);
        if (vipIp == null) {
            throw new InvalidParameterValueException("IP " + ipv4 + " doesn't exist in VIP environment " + vipEnvironmentId);
        }

        Network network = _globoNetworkApi.getNetworkAPI().getNetwork(vipIp.getNetworkId(), false);
        if (network == null) {
            throw new InvalidParameterValueException("Network " + vipIp.getNetworkId() + " was not found in GloboNetwork");
        }
        Vlan vlan = _globoNetworkApi.getVlanAPI().getById(network.getVlanId());
        if (vlan == null) {
            throw new InvalidParameterValueException("Vlan " + network.getVlanId() + " was not found in GloboNetwork");
        }

        return new VipInfoHelper(vlan.getEnvironment(), environmentVip, vipIp, network);
    }

    public static class VipInfoHelper {

        private final Network vipNetwork;
        private final Ip vipIp;
        private Long environment;
        private VipEnvironment vipEnvironment;

        public VipInfoHelper(Long environment, VipEnvironment vipEnv, Ip vipIp, Network vipNetwork) {
            this.environment = environment;
            this.vipEnvironment = vipEnv;
            this.vipIp = vipIp;
            this.vipNetwork = vipNetwork;

        }

        public Long getEnvironment() {
            return environment;
        }
    }

    protected VipApiAdapter createVipApiAdapter(Long vipId) throws GloboNetworkException {
        return new VipApiAdapter(vipId, _globoNetworkApi, getVipApiVersion());
    }

    private String getVipApiVersion() {
        return new ConfigurationDaoImpl().getValue("globonetwork.vip.apiversion");
    }
}
