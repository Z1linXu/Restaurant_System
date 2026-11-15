package com.restaurant.system.allo;

import com.restaurant.system.common.auth.*;
import com.restaurant.system.common.response.ApiResponse;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/allo/stores/{storeId}/sales")
public class AlloSalesController {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> salesError(org.springframework.web.server.ResponseStatusException e) {
        return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(ApiResponse.failure(e.getReason(),e.getReason()));
    }
    private final AuthorizationService auth;
    private final AlloSalesService sales;
    private final AlloSalesReportService reports;
    private final com.restaurant.system.order.repository.OrderRepository orders;
    public AlloSalesController(AuthorizationService auth,AlloSalesService sales,AlloSalesReportService reports,
                               com.restaurant.system.order.repository.OrderRepository orders) {
        this.auth=auth;this.sales=sales;this.reports=reports;this.orders=orders;
    }
    private AuthenticatedUser access(Long store) {return auth.requireForStore(store,Capability.ORDER_VIEW_DETAIL);}
    private void scope(Long store,Long id) {
        access(store);auth.requireOrder(id,Capability.ORDER_VIEW_DETAIL);
        if(!store.equals(orders.findById(id).orElseThrow().store_id))throw new ForbiddenException("STORE_MISMATCH");
    }
    @GetMapping("/orders/{id}")
    public ApiResponse<?> detail(@PathVariable Long storeId,@PathVariable Long id) {scope(storeId,id);return ApiResponse.success(sales.detail(id));}
    @PostMapping("/orders/{id}/adjustments")
    public ApiResponse<?> adjust(@PathVariable Long storeId,@PathVariable Long id,@RequestBody AlloSalesService.Adjustment r) {
        scope(storeId,id);var u=auth.requireForStore(storeId,Capability.ORDER_MODIFY_SUBMITTED);
        return ApiResponse.success(sales.adjust(id,r,u.userId(),u.fullName()==null?u.username():u.fullName()));
    }
    @PostMapping("/orders/{id}/preview")
    public ApiResponse<?> preview(@PathVariable Long storeId,@PathVariable Long id,@RequestBody AlloSalesService.Adjustment r) {
        scope(storeId,id);auth.requireForStore(storeId,Capability.ORDER_MODIFY_SUBMITTED);return ApiResponse.success(sales.preview(id,r));
    }
    @GetMapping("/presets")
    public ApiResponse<?> presets(@PathVariable Long storeId) {access(storeId);return ApiResponse.success(sales.presets(storeId));}
    @PostMapping("/presets")
    public ApiResponse<?> savePreset(@PathVariable Long storeId,@RequestBody AlloSalesService.Preset p) {
        var u=auth.requireForStore(storeId,Capability.ORDER_MODIFY_SUBMITTED);return ApiResponse.success(sales.savePreset(storeId,p,u.userId()));
    }
    @GetMapping("/overview")
    public ApiResponse<?> overview(@PathVariable Long storeId,@RequestParam(required=false) LocalDate date) {
        access(storeId);return ApiResponse.success(reports.overview(storeId,date==null?sales.today(storeId):date));
    }
    @PostMapping("/reports")
    public ApiResponse<?> snapshot(@PathVariable Long storeId,@RequestParam(required=false) LocalDate date) {
        var u=access(storeId);return ApiResponse.success(reports.snapshot(storeId,date==null?sales.today(storeId):date,u.userId()));
    }
    @org.springframework.beans.factory.annotation.Autowired
    private com.restaurant.system.printing.service.PrintJobService printJobs;
    @GetMapping("/reports/jobs/{id}")
    public ApiResponse<?> reportJob(@PathVariable Long storeId,@PathVariable Long id) {
        access(storeId);sales.requireEnabled(storeId);var job=printJobs.requireJob(id);
        if(!storeId.equals(job.store_id)||!"SALES_OVERVIEW".equals(job.receipt_type))throw new ForbiddenException("REPORT_JOB_SCOPE_INVALID");
        return ApiResponse.success(printJobs.toResponse(job));
    }
    public record PrintRequest(UUID report_id,String request_key) {}
    @PostMapping("/reports/print")
    public ApiResponse<?> print(@PathVariable Long storeId,@RequestBody PrintRequest r) {
        var u=auth.requireForStore(storeId,Capability.ORDER_MODIFY_SUBMITTED);
        return ApiResponse.success(reports.print(storeId,r.report_id(),r.request_key(),u.userId()));
    }
}
