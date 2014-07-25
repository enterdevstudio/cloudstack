package com.globo.globodns.cloudstack.element;


import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Collections;

import javax.inject.Inject;

import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.test.utils.SpringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host.Type;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network;
import com.cloud.network.Network.Provider;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.resource.ResourceManager;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.utils.component.ComponentContext;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.ReservationContextImpl;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.globo.globodns.cloudstack.GloboDnsNetworkVO;
import com.globo.globodns.cloudstack.GloboDnsVirtualMachineVO;
import com.globo.globodns.cloudstack.commands.CreateRecordCommand;
import com.globo.globodns.cloudstack.commands.GetDomainInfoCommand;
import com.globo.globodns.cloudstack.commands.ListRecordCommand;
import com.globo.globodns.cloudstack.dao.GloboDnsNetworkDao;
import com.globo.globodns.cloudstack.dao.GloboDnsVirtualMachineDao;
import com.globo.globodns.client.model.Domain;
import com.globo.globodns.client.model.Record;
import com.globo.globodns.cloudstack.response.GloboDnsDomainResponse;
import com.globo.globodns.cloudstack.response.GloboDnsRecordListResponse;
import com.globo.globodns.cloudstack.response.GloboDnsRecordResponse;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
@DirtiesContext(classMode=ClassMode.AFTER_EACH_TEST_METHOD)
public class GloboDnsElementTest {

    private static long zoneId = 5L;
    private static long napiHostId = 7L;
    private static long domainId = 10L;
    private AccountVO acct = null;
	private UserVO user = null;
	
	@Inject
	DataCenterDao _datacenterDao;
	
	@Inject
	GloboDnsVirtualMachineDao _globodnsVMDao;
	
	@Inject
	GloboDnsElement _globodnsElement;
	
	@Inject
	GloboDnsNetworkDao _globodnsNetworkDao;
	
	@Inject
	HostDao _hostDao;
	
	@Inject
	AgentManager _agentMgr;

	@Inject
	AccountManager _acctMgr;

	@Before
	public void setUp() throws Exception {
        ComponentContext.initComponentsLifeCycle();
        
        // configure spy on GloboDnsElement
        this._globodnsElement = spy(this._globodnsElement);
        
        acct = new AccountVO(200L);
        acct.setType(Account.ACCOUNT_TYPE_NORMAL);
        acct.setAccountName("user");
        acct.setDomainId(domainId);

        user = new UserVO();
        user.setUsername("user");
        user.setAccountId(acct.getAccountId());

        CallContext.register(user, acct);
        when(_acctMgr.getSystemAccount()).thenReturn(this.acct);
        when(_acctMgr.getSystemUser()).thenReturn(this.user);
    }
    
    @After
	public void tearDown() throws Exception {
    	CallContext.unregister();
    	acct = null;
    }

    @Test
	public void testGetCapabilities() {
		//fail("Not yet implemented");
	}

	@Test(expected=InvalidParameterValueException.class)
	public void testUpperCaseCharactersAreNotAllowed() throws ConcurrentOperationException, ResourceUnavailableException, InsufficientCapacityException {
		Network network = mock(Network.class);
		when(network.getDataCenterId()).thenReturn(zoneId);
		when(network.getId()).thenReturn(1l);
		NicProfile nic = new NicProfile();
		VirtualMachineProfile vm = mock(VirtualMachineProfile.class);
		when(vm.getHostName()).thenReturn("UPPERCASENAME");
		when(vm.getType()).thenReturn(VirtualMachine.Type.User);
		when(_datacenterDao.findById(zoneId)).thenReturn(mock(DataCenterVO.class));
		when(_globodnsNetworkDao.findByNetworkId(network.getId())).thenReturn(new GloboDnsNetworkVO());
		DeployDestination dest = new DeployDestination();
		ReservationContext context = new ReservationContextImpl(null, null, user);
		_globodnsElement.prepare(network, nic, vm, dest, context);
	}

	@Test
	public void testprepareMethodCallDNSAPIToRegisterHostName() throws Exception {
		Network network = mock(Network.class);
		when(network.getDataCenterId()).thenReturn(zoneId);
		when(network.getId()).thenReturn(1l);
		NicProfile nic = new NicProfile();
		nic.setIp4Address("10.11.12.13");
		VirtualMachineProfile vm = mock(VirtualMachineProfile.class);
		when(vm.getHostName()).thenReturn("vm-name");
		when(vm.getType()).thenReturn(VirtualMachine.Type.User);
		DataCenterVO dataCenterVO = mock(DataCenterVO.class);
		when(dataCenterVO.getId()).thenReturn(zoneId);
		when(_datacenterDao.findById(zoneId)).thenReturn(dataCenterVO);
		when(_globodnsNetworkDao.findByNetworkId(network.getId())).thenReturn(new GloboDnsNetworkVO());
		DeployDestination dest = new DeployDestination();
		ReservationContext context = new ReservationContextImpl(null, null, user);
		
//		MockDNSAPI globodnsclient = new MockDNSAPI();
//		globodnsclient.registerFakeRequest(HttpMethod.GET, "/domains/0/records.json?query=vm-name", "{}");
		
//		final GloboDnsResource globodnsResource = new GloboDnsResource();
//		Map<String, Object> params = new HashMap<String, Object>();
//		params.put("globodnsclient", globodnsclient);
//		globodnsResource.configure("globodns", params);
		
		HostVO hostVO = mock(HostVO.class);
		when(hostVO.getId()).thenReturn(napiHostId);
		when(_hostDao.findByTypeNameAndZoneId(eq(zoneId), eq(Provider.GloboDns.getName()), eq(Type.L2Networking))).thenReturn(hostVO);

		when(_agentMgr.easySend(eq(napiHostId), isA(ListRecordCommand.class))).then(new org.mockito.stubbing.Answer<Answer>() {

			@Override
			public Answer answer(InvocationOnMock invocation) throws Throwable {
				Command cmd = (Command) invocation.getArguments()[1];
				return new GloboDnsRecordListResponse(cmd, Collections.<Record>emptyList());
			}
		});

		when(_agentMgr.easySend(eq(napiHostId), isA(CreateRecordCommand.class))).then(new org.mockito.stubbing.Answer<Answer>() {

			@Override
			public Answer answer(InvocationOnMock invocation) throws Throwable {
				Command cmd = (Command) invocation.getArguments()[1];
				Record record = new Record();
				record.getTypeARecordAttributes().setId(25L);
				return new GloboDnsRecordResponse(cmd, record);
			}
		});

		when(_agentMgr.easySend(eq(napiHostId), isA(GetDomainInfoCommand.class))).then(new org.mockito.stubbing.Answer<Answer>() {

			@Override
			public Answer answer(InvocationOnMock invocation) throws Throwable {
				Command cmd = (Command) invocation.getArguments()[1];
				Domain domain = new Domain();
				domain.getDomainAttributes().setId(24L);
				return new GloboDnsDomainResponse(cmd, domain);
			}
		});
		
//		when(_agentMgr.easySend(eq(napiHostId), any(GetDomainInfoCommand.class))).thenReturn(null);
//		when(_agentMgr.easySend(eq(napiHostId), any(Command.class))).then(new org.mockito.stubbing.Answer<Answer>() {
//
//			@Override
//			public Answer answer(InvocationOnMock invocation) throws Throwable {
//				return globodnsResource.executeRequest((Command) invocation.getArguments()[1]);
//			}
//			
//		});
		
		_globodnsElement.prepare(network, nic, vm, dest, context);
		verify(_globodnsVMDao, atLeastOnce()).persist(isA(GloboDnsVirtualMachineVO.class));
	}

    @Configuration
    @ComponentScan(basePackageClasses = {GloboDnsElement.class}, includeFilters = {@Filter(value = TestConfiguration.Library.class, type = FilterType.CUSTOM)}, useDefaultFilters = false)
    public static class TestConfiguration extends SpringUtils.CloudStackTestConfiguration {

    	@Bean
    	public GloboDnsNetworkDao globodnsNetworkDao() {
    		return mock(GloboDnsNetworkDao.class);
    	}
    	@Bean
    	public GloboDnsVirtualMachineDao dnsAPIVirtualMachineDao() {
    		return mock(GloboDnsVirtualMachineDao.class);
    	}
    	@Bean
    	public HostDao hostDao() {
    		return mock(HostDao.class);
    	}
    	@Bean
    	public DataCenterDao dataCenterDao() {
    		return mock(DataCenterDao.class);
    	}
    	@Bean
    	public PhysicalNetworkDao physicalNetworkDao() {
    		return mock(PhysicalNetworkDao.class);
    	}
    	@Bean
    	public NetworkDao networkDao() {
    		return mock(NetworkDao.class);
    	}
    	@Bean
    	public ConfigurationDao configurationDao() {
    		return mock(ConfigurationDao.class);
    	}
    	@Bean
    	public AgentManager agentManager() {
    		return mock(AgentManager.class);
    	}
    	@Bean
    	public ResourceManager resourceManager() {
    		return mock(ResourceManager.class);
    	}
    	@Bean
    	public AccountManager accountManager() {
    		return mock(AccountManager.class);
    	}
    
        public static class Library implements TypeFilter {
 
            @Override
            public boolean match(MetadataReader mdr, MetadataReaderFactory arg1) throws IOException {
                ComponentScan cs = TestConfiguration.class.getAnnotation(ComponentScan.class);
                return SpringUtils.includedInBasePackageClasses(mdr.getClassMetadata().getClassName(), cs);
            }
        }
    }
}