package com.restaurant.system.menu.remove;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/menu/removes")
public class OwnerMenuRemoveController {
    private final StoreRemoveService service;
    private final AuthorizationService authorization;
    private final StoreModuleAccessEvaluator modules;
    private final AuditLogService audit;
    private StoreRemoveBaselineReconciler baseline;

    @org.springframework.beans.factory.annotation.Autowired
    public void setBaselineReconciler(StoreRemoveBaselineReconciler baseline) { this.baseline = baseline; }
    public OwnerMenuRemoveController(StoreRemoveService service,AuthorizationService authorization,
        StoreModuleAccessEvaluator modules,AuditLogService audit) {
        this.service=service;this.authorization=authorization;this.modules=modules;this.audit=audit;
    }
    @GetMapping
    public ApiResponse<StoreRemoveService.Catalog> list(@RequestParam("store_id") Long storeId) {
        require(storeId);return ApiResponse.success(service.getRemoves(storeId));
    }
    @PostMapping
    public ApiResponse<StoreRemoveService.Remove> create(@RequestBody StoreRemoveService.WriteRequest body,HttpServletRequest request) {
        if(body==null) throw new BusinessException("REMOVE_PAYLOAD_REQUIRED");
        var user=require(body.store_id);var result=service.create(body);
        record(result.store_id(),user,"MENU_REMOVE_CREATED",result.id(),request);return ApiResponse.success(result);
    }
    @PutMapping("/{id}")
    public ApiResponse<StoreRemoveService.Remove> update(@PathVariable Long id,@RequestParam(value="store_id",required=false) Long storeId,
        @RequestBody StoreRemoveService.WriteRequest body,HttpServletRequest request) {
        Long actual=service.storeIdForRemove(id);var user=require(actual);StoreRemoveService.checkScope(actual,storeId);
        var result=service.update(id,body);record(actual,user,"MENU_REMOVE_UPDATED",id,request);return ApiResponse.success(result);
    }
    @PutMapping("/reorder")
    public ApiResponse<List<StoreRemoveService.Remove>> reorder(@RequestBody StoreRemoveService.ReorderRequest body,HttpServletRequest request) {
        if(body==null) throw new BusinessException("REMOVE_REORDER_REQUIRED");
        var user=require(body.store_id);var result=service.reorder(body);
        record(body.store_id,user,"MENU_REMOVES_REORDERED",body.store_id,request);return ApiResponse.success(result);
    }
    @GetMapping("/{id}/applicability")
    public ApiResponse<StoreRemoveService.Applicability> applicability(@PathVariable Long id,@RequestParam(value="store_id",required=false) Long storeId) {
        Long actual=service.storeIdForRemove(id);require(actual);StoreRemoveService.checkScope(actual,storeId);
        return ApiResponse.success(service.getApplicability(id));
    }
    @PutMapping("/{id}/applicability")
    public ApiResponse<StoreRemoveService.Applicability> applicability(@PathVariable Long id,@RequestParam(value="store_id",required=false) Long storeId,
        @RequestBody StoreRemoveService.ApplicabilityRequest body,HttpServletRequest request) {
        Long actual=service.storeIdForRemove(id);var user=require(actual);StoreRemoveService.checkScope(actual,storeId);
        var result=service.setApplicability(id,body);record(actual,user,"MENU_REMOVE_APPLICABILITY_UPDATED",id,request);return ApiResponse.success(result);
    }
    @PostMapping("/reconcile")
    public ApiResponse<StoreRemoveService.Reconciliation> reconcile(@RequestBody StoreRemoveService.ReconcileRequest body,HttpServletRequest request) {
        if(body==null || body.dry_run==null) throw new BusinessException("REMOVE_RECONCILE_DRY_RUN_REQUIRED");
        var user=require(body.store_id);var result=service.reconcile(body.store_id,body.dry_run);
        if(!body.dry_run) record(body.store_id,user,"MENU_REMOVES_RECONCILED",body.store_id,request);
        return ApiResponse.success(result);
    }
    @PostMapping("/baseline-reconcile")
    public ApiResponse<StoreRemoveBaselineReconciler.Report> baselineReconcile(
        @RequestBody StoreRemoveBaselineReconciler.Request body
    ) {
        if (body == null || body.store_id == null || body.dry_run == null) throw new BusinessException("REMOVE_BASELINE_REQUEST_REQUIRED");
        var user = require(body.store_id);
        return ApiResponse.success(baseline.reconcile(body, user));
    }

    private AuthenticatedUser require(Long storeId) {
        var user=authorization.requireForStore(storeId,Capability.ADMIN_MENU_MANAGE);
        modules.requireCapability(storeId,ModuleKeys.MENU_MANAGEMENT);return user;
    }
    private void record(Long storeId,AuthenticatedUser user,String action,Long id,HttpServletRequest request) {
        audit.record(storeId,user,action,"STORE_REMOVE",id,"Updated Store Remove configuration",Map.of(),request);
    }
}
