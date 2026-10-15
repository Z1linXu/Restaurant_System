package com.restaurant.system.platform.controller;

import com.restaurant.system.common.auth.*;
import com.restaurant.system.menu.entity.*;
import com.restaurant.system.menu.repository.*;
import com.restaurant.system.platform.entity.*;
import com.restaurant.system.platform.repository.*;
import com.restaurant.system.station.entity.*;
import com.restaurant.system.station.repository.*;
import com.restaurant.system.user.entity.*;
import com.restaurant.system.user.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Checks the persisted owner before an untrusted body can overwrite it. */
@Service
public class PlatformResourceAuthorization {
    private final AuthorizationService auth;
    private final RequestUserContextService context;
    private final StoreAccessService access;
    private final OwnerOrganizationAuthorizationService owners;
    private final StoreRepository stores;
    private final RestaurantTemplateRepository templates;
    private final StationRepository stations;
    private final DiningTableRepository tables;
    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;
    private final MenuItemOptionRepository options;
    private final StoreKdsDisplayConfigRepository kds;
    private final UserRepository users;
    private final RoleRepository roles;

    public PlatformResourceAuthorization(AuthorizationService auth, RequestUserContextService context,
        StoreAccessService access, OwnerOrganizationAuthorizationService owners, StoreRepository stores,
        RestaurantTemplateRepository templates, StationRepository stations, DiningTableRepository tables,
        MenuCategoryRepository categories, MenuItemRepository items, MenuItemOptionRepository options,
        StoreKdsDisplayConfigRepository kds, UserRepository users, RoleRepository roles) {
        this.auth=auth; this.context=context; this.access=access; this.owners=owners; this.stores=stores;
        this.templates=templates; this.stations=stations; this.tables=tables; this.categories=categories;
        this.items=items; this.options=options; this.kds=kds; this.users=users; this.roles=roles;
    }
    private ForbiddenException denied() { return new ForbiddenException("Access denied for resource scope"); }
    private void same(Long actual, Long requested) {
        if (actual == null || requested == null || !actual.equals(requested)) throw denied();
    }
    public List<Store> stores() {
        var actor=context.getRequiredUser();
        return access.accessibleStores(actor).stream().filter(store -> access.hasCapabilityForStore(actor,store,Capability.ADMIN_STORE_CONFIG)).toList();
    }
    public List<Organization> organizations() {
        var actor=context.getRequiredUser();
        return access.accessibleOrganizations(actor).stream()
            .filter(org -> "OWNER".equals(access.roleCodeForOrganization(actor,org.id)) || stores().stream().anyMatch(store -> org.id.equals(store.organization_id))).toList();
    }
    public void organization(Long id) { owners.requireActiveOwnerMembership(context.getRequiredUser(), id); }
    public List<RestaurantTemplate> templates() {
        Set<Long> ids = new HashSet<>();
        organizations().forEach(org -> ids.add(org.id));
        return templates.findAllByOrderByIdAsc().stream().filter(t -> ids.contains(t.organization_id))
            .filter(t -> t.source_store_id == null || access.canAccessStore(context.getRequiredUser(), t.source_store_id)).toList();
    }
    public void template(Long id, RestaurantTemplate body) {
        organization(body.organization_id);
        if (id != null) same(templates.findById(id).orElseThrow(this::denied).organization_id, body.organization_id);
        if (body.source_store_id != null) {
            Store source = stores.findById(body.source_store_id).orElseThrow(this::denied);
            same(source.organization_id, body.organization_id);
            auth.requireForStore(source.id, Capability.ADMIN_STORE_CONFIG);
        }
    }
    public void store(Long id, Store body) {
        if (id == null) throw denied(); // Store creation uses the reviewed Owner provisioning API.
        Store actual=stores.findById(id).orElseThrow(this::denied);
        same(actual.organization_id, body.organization_id);
        auth.requireManagerOrOwnerForStore(id, Capability.ADMIN_STORE_CONFIG);
    }
    public void station(Long id, Station body) {
        auth.requireForStore(body.store_id, Capability.ADMIN_STORE_CONFIG);
        if (id != null) same(stations.findById(id).orElseThrow(this::denied).store_id, body.store_id);
    }
    public void table(Long id, DiningTable body) {
        auth.requireForStore(body.store_id, Capability.ADMIN_STORE_CONFIG);
        if (id != null) same(tables.findById(id).orElseThrow(this::denied).store_id, body.store_id);
    }
    public void category(Long id, MenuCategory body) {
        auth.requireForStore(body.store_id, Capability.ADMIN_STORE_CONFIG);
        if (id != null) same(categories.findById(id).orElseThrow(this::denied).store_id, body.store_id);
    }
    public void item(Long id, MenuItem body) {
        auth.requireForStore(body.store_id, Capability.ADMIN_MENU_MANAGE, Capability.ADMIN_STORE_CONFIG);
        if (id != null) same(items.findById(id).orElseThrow(this::denied).store_id, body.store_id);
        if (body.category_id != null) same(categories.findById(body.category_id).orElseThrow(this::denied).store_id, body.store_id);
        if (body.station_id != null) same(stations.findById(body.station_id).orElseThrow(this::denied).store_id, body.store_id);
    }
    public void option(Long id, MenuItemOption body) {
        if (body.menu_item_id == null) throw denied();
        MenuItem item=items.findById(body.menu_item_id).orElseThrow(this::denied);
        auth.requireForStore(item.store_id, Capability.ADMIN_STORE_CONFIG);
        if (id != null) {
            MenuItemOption actual=options.findById(id).orElseThrow(this::denied);
            same(actual.menu_item_id, body.menu_item_id);
        }
        if (body.parent_option_id != null) {
            MenuItemOption parent=options.findById(body.parent_option_id).orElseThrow(this::denied);
            same(parent.menu_item_id, body.menu_item_id);
        }
    }
    public void kds(Long id, StoreKdsDisplayConfig body) {
        auth.requireForStore(body.store_id, Capability.ADMIN_STORE_CONFIG);
        if (id != null) same(kds.findById(id).orElseThrow(this::denied).store_id, body.store_id);
    }
    public void user(Long id, User body) {
        AuthenticatedUser actor=auth.requireStaffManageForStore(body.getStore_id());
        if (id != null) {
            User actual=users.findById(id).orElseThrow(this::denied);
            same(actual.getStore_id(),body.getStore_id());
            allowedRole(actor,actual.getRole_id());
            String actualRole=roles.findById(actual.getRole_id()).orElseThrow(this::denied).getCode();
            var identity=new AuthenticatedUser(actual.getId(),actual.getStore_id(),actual.getRole_id(),actual.getUsername(),actual.getFull_name(),actualRole);
            for (Store assigned : access.accessibleStores(identity)) if (!access.canAccessStore(actor,assigned.id)) throw denied();
        }
        allowedRole(actor,body.getRole_id());
    }
    private void allowedRole(AuthenticatedUser actor, Long roleId) {
        if (roleId == null) throw denied();
        String role=roles.findById(roleId).orElseThrow(this::denied).getCode();
        if (!Set.of("OWNER","MANAGER","FRONTDESK","HOT_KITCHEN","NOODLE_VIEW","PASS").contains(role)) throw denied();
        if (!auth.isOwner(actor) && !"FRONTDESK".equals(role)) throw denied();
    }
}
