package com.restaurant.system.common.auth;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.auth.service.impl.TokenServiceImpl;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.user.entity.*;
import com.restaurant.system.user.repository.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.*;

/** Synthetic identities only; two unrelated organizations and a same-org sibling Store. */
public class CommercialSecurityFixture {
    public final OrganizationRepository organizations=mock(OrganizationRepository.class,withSettings().lenient());
    public final StoreRepository stores=mock(StoreRepository.class,withSettings().lenient());
    public final OrganizationMembershipRepository orgMembers=mock(OrganizationMembershipRepository.class,withSettings().lenient());
    public final StoreMembershipRepository storeMembers=mock(StoreMembershipRepository.class,withSettings().lenient());
    public final UserRepository users=mock(UserRepository.class,withSettings().lenient());
    public final RoleRepository roles=mock(RoleRepository.class,withSettings().lenient());
    public final Map<Long, User> userRows=new ConcurrentHashMap<>();
    public final Map<Long, Store> storeRows=new ConcurrentHashMap<>();
    public final Map<String, StoreMembership> membershipRows=new ConcurrentHashMap<>();
    public final Map<String, OrganizationMembership> organizationMembershipRows=new ConcurrentHashMap<>();
    public final Map<Long, Role> roleRows=new ConcurrentHashMap<>();
    public final Map<Long, Organization> organizationRows=new ConcurrentHashMap<>();
    public final RoleCapabilityRegistry capabilities=new RoleCapabilityRegistry();
    public final StoreAccessService access=new StoreAccessService(capabilities,organizations,stores,orgMembers,storeMembers);
    public final TokenServiceImpl tokens=new TokenServiceImpl(new ObjectMapper(),"synthetic-test-signing-key-never-a-runtime-secret",900);
    public final BearerUserResolver identities=new BearerUserResolver(tokens,users,roles);
    public final RequestUserContextService context;
    public CommercialSecurityFixture() {
        for (long id : new long[]{100,200}) { Organization org=new Organization(); org.id=id;org.code="TEST_ORG_"+id;org.status="active";organizationRows.put(id,org); }
        store(10,100);store(11,100);store(20,200);
        String[] codes={"OWNER","MANAGER","FRONTDESK","ADMIN","HOT_KITCHEN","NOODLE_VIEW","PASS"};
        for (int i=0;i<codes.length;i++) { Role role=new Role();role.setId((long)i+1);role.setCode(codes[i]);roleRows.put(role.getId(),role); }
        user(1,10,"OWNER");user(2,20,"OWNER");user(3,10,"MANAGER");user(4,10,"FRONTDESK");user(5,10,"ADMIN");
        user(6,10,"FRONTDESK");user(7,11,"OWNER");user(8,10,"FRONTDESK");user(9,10,"FRONTDESK");user(10,10,"HOT_KITCHEN");
        orgMember(1,100,"OWNER",true);orgMember(2,200,"OWNER",true);
        member(3,10,"MANAGER",true);member(4,10,"FRONTDESK",true);member(7,11,"FRONTDESK",true);
        member(8,10,"OWNER",true);member(9,10,"FRONTDESK",false);member(10,10,"HOT_KITCHEN",true);
        when(stores.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(storeRows.get(i.getArgument(0))));
        when(stores.findAll()).thenAnswer(i -> new ArrayList<>(storeRows.values()));
        when(stores.findAllByOrganizationIdOrderByIdAsc(anyLong())).thenAnswer(i -> storeRows.values().stream().filter(s -> s.organization_id.equals(i.getArgument(0))).toList());
        when(organizations.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(organizationRows.get(i.getArgument(0))));
        when(users.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(userRows.get(i.getArgument(0))));
        when(users.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(userRows.get(i.getArgument(0))));
        when(roles.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(roleRows.get(i.getArgument(0))));
        when(roles.findAll()).thenAnswer(i -> new ArrayList<>(roleRows.values()));
        when(storeMembers.findFirstByUserIdAndStoreId(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(membershipRows.get(key(i.getArgument(0),i.getArgument(1)))));
        when(orgMembers.findFirstByUserIdAndOrganizationId(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(organizationMembershipRows.get(key(i.getArgument(0),i.getArgument(1)))));
        when(storeMembers.existsByUserId(anyLong())).thenAnswer(i -> membershipRows.values().stream().anyMatch(m -> m.userId.equals(i.getArgument(0))));
        when(orgMembers.existsByUserId(anyLong())).thenAnswer(i -> organizationMembershipRows.values().stream().anyMatch(m -> m.userId.equals(i.getArgument(0))));
        when(storeMembers.findAllByUserIdAndIsActiveTrueOrderByStoreIdAsc(anyLong())).thenAnswer(i -> membershipRows.values().stream().filter(m -> m.userId.equals(i.getArgument(0)) && Boolean.TRUE.equals(m.isActive)).toList());
        when(orgMembers.findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(anyLong())).thenAnswer(i -> organizationMembershipRows.values().stream().filter(m -> m.userId.equals(i.getArgument(0)) && Boolean.TRUE.equals(m.isActive)).toList());
        when(users.findAllByStore_id(anyLong())).thenAnswer(i -> userRows.values().stream().filter(u -> u.getStore_id().equals(i.getArgument(0))).toList());
        @SuppressWarnings("unchecked") ObjectProvider<HttpServletRequest> requests=mock(ObjectProvider.class,withSettings().lenient());
        when(requests.getIfAvailable()).thenAnswer(i -> RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null);
        context=new RequestUserContextService(requests,users,roles,false);
    }
    private String key(Long user,Long scope) { return user+":"+scope; }
    public AuthenticatedUser actor(long id) { User u=userRows.get(id);return new AuthenticatedUser(id,u.getStore_id(),u.getRole_id(),u.getUsername(),u.getFull_name(),roleRows.get(u.getRole_id()).getCode()); }
    public String bearer(long userId) { AuthenticatedUser actor=actor(userId);return "Bearer "+tokens.createAccessToken(actor,storeRows.get(actor.storeId()).organization_id); }
    public void member(long user,long store,String role,boolean active) { StoreMembership m=new StoreMembership();m.userId=user;m.storeId=store;m.organizationId=storeRows.get(store).organization_id;m.roleCode=role;m.isActive=active;membershipRows.put(key(user,store),m); }
    public void orgMember(long user,long org,String role,boolean active) { OrganizationMembership m=new OrganizationMembership();m.userId=user;m.organizationId=org;m.roleCode=role;m.isActive=active;organizationMembershipRows.put(key(user,org),m); }
    private void store(long id,long org) { Store s=new Store();s.id=id;s.organization_id=org;s.status="active";s.code="SYNTHETIC_"+id;s.name=s.code;storeRows.put(id,s); }
    private void user(long id,long store,String role) { User u=new User();u.setId(id);u.setStore_id(store);u.setRole_id(roleRows.values().stream().filter(r -> role.equals(r.getCode())).findFirst().orElseThrow().getId());u.setUsername("synthetic_user_"+id);u.setStatus("active");userRows.put(id,u); }
}
