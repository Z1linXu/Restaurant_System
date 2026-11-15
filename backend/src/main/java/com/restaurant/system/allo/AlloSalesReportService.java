package com.restaurant.system.allo;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.printing.entity.*;
import com.restaurant.system.printing.repository.*;
import com.restaurant.system.printing.service.PrintJobService;
import com.restaurant.system.delivery.ReceiptArtifact;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AlloSalesReportService {
    private final AlloSalesService sales;
    private final OrderRepository orders;
    private final JdbcTemplate db;
    private final StoreRepository stores;
    private final PrintJobRepository jobs;
    private final PrintJobService printing;
    private final PrinterAssignmentRepository assignments;
    private final PrinterConfigRepository printers;
    public AlloSalesReportService(AlloSalesService sales,OrderRepository orders,JdbcTemplate db,StoreRepository stores,
        PrintJobRepository jobs,PrintJobService printing,PrinterAssignmentRepository assignments,PrinterConfigRepository printers) {
        this.sales=sales;this.orders=orders;this.db=db;this.stores=stores;this.jobs=jobs;this.printing=printing;this.assignments=assignments;this.printers=printers;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> overview(Long store,LocalDate date) {sales.requireEnabled(store);return collect(store,date);}
    private Map<String,Object> collect(Long store,LocalDate date) {
        var eligible=orders.findAllByStoreId(store).stream().filter(o->"completed".equals(o.status)
            && !o.kitchenMirror() && o.external_source==null && date.equals(AlloSalesService.businessDate(o))).toList();
        BigDecimal gross=BigDecimal.ZERO,discount=BigDecimal.ZERO,net=BigDecimal.ZERO,tax=BigDecimal.ZERO,total=BigDecimal.ZERO;
        BigDecimal dine=BigDecimal.ZERO,takeout=BigDecimal.ZERO;int dineCount=0,takeoutCount=0;
        for(var o:eligible) {
            var m=AlloMoney.determined(o);gross=gross.add(m.gross());discount=discount.add(m.discount());net=net.add(m.net());tax=tax.add(m.tax());total=total.add(m.total());
            if("dine_in".equals(o.order_type)){dine=dine.add(m.total());dineCount++;}else{takeout=takeout.add(m.total());takeoutCount++;}
        }
        var result=new LinkedHashMap<String,Object>();result.put("store_id",store);result.put("business_date",date);
        result.put("timezone",stores.findById(store).orElseThrow().timezone);result.put("generated_at",Instant.now().toString());
        result.put("gross",AlloMoney.cents(gross));result.put("discount",AlloMoney.cents(discount));result.put("net",AlloMoney.cents(net));
        result.put("tax",AlloMoney.cents(tax));result.put("total",AlloMoney.cents(total));result.put("collected",AlloMoney.cents(total));result.put("order_count",eligible.size());
        result.put("collected_note","按门店规则记账的已收金额，未按现金/银行卡核对");
        result.put("dine_in",Map.of("count",dineCount,"total",AlloMoney.cents(dine)));
        result.put("takeout",Map.of("count",takeoutCount,"total",AlloMoney.cents(takeout)));result.put("uber_eats","未接入");
        result.put("adjustments",db.queryForList("select a.*,o.order_no,o.table_no,o.pickup_no,o.order_type from allo_order_adjustments a join orders o on o.id=a.order_id where a.store_id=? and a.business_date=? order by a.id",store,date));
        var voids=db.queryForMap("""
            select coalesce(sum((a.before_snapshot::jsonb->>'gross')::numeric-(a.after_snapshot::jsonb->>'gross')::numeric),0) as gross,
            coalesce(sum((select sum((v->>'quantity')::int) from jsonb_array_elements(a.item_changes::jsonb) v)),0) as quantity
            from allo_order_adjustments a where a.store_id=? and a.business_date=? and a.action in ('VOID_ORDER','VOID_ITEMS')
            """,store,date);
        result.put("voided_quantity",voids.get("quantity"));result.put("voided_gross",voids.get("gross"));
        result.put("voided_orders",db.queryForObject("select count(distinct a.order_id) from allo_order_adjustments a join orders o on o.id=a.order_id where a.store_id=? and a.business_date=? and o.status='cancelled'",Long.class,store,date));
        return result;
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> snapshot(Long store,LocalDate date,Long actor) {
        sales.requireEnabled(store);var data=collect(store,date);UUID id=UUID.randomUUID();data.put("report_id",id);
        String text=render(data);db.update("insert into allo_sales_reports(id,store_id,business_date,snapshot,rendered_text,created_by,created_at) values(?,?,?,?,?,?,?)",id,store,date,sales.write(data),text,actor,LocalDateTime.now());
        data.put("rendered_text",text);return data;
    }
    @Transactional
    public com.restaurant.system.printing.dto.PrintJobResponse print(Long store,UUID report,String key,Long actor) {
        sales.requireEnabled(store);
        if(report==null || key==null || !key.matches("[A-Za-z0-9_-]{16,100}"))throw new BusinessException("REPORT_PRINT_REQUEST_INVALID");
        // Serializes duplicate requests without order_id or a fake business order.
        var s=stores.findById(store).orElseThrow();
        var rows=db.queryForList("select * from allo_sales_reports where id=? and store_id=? for update",report,store);
        if(rows.isEmpty())throw new BusinessException("REPORT_NOT_FOUND");
        String source="sales-report:"+report+":"+key;
        var old=jobs.findByDispatchSourceKey(source);if(old.isPresent())return printing.toResponse(old.get());
        var assignment=assignments.findByStoreIdAndModuleCode(store,"FRONTDESK_RECEIPT").orElseThrow(()->new BusinessException("ASSIGNMENT_MISSING"));
        if(!Boolean.TRUE.equals(assignment.enabled)||assignment.printer_id==null)throw new BusinessException("ASSIGNMENT_DISABLED");
        var printer=printers.findById(assignment.printer_id).orElseThrow(()->new BusinessException("PRINTER_NOT_FOUND"));
        if(!store.equals(printer.store_id)||!Boolean.TRUE.equals(printer.enabled))throw new BusinessException("PRINTER_SCOPE_INVALID");
        String mode=s.printing_mode;
        if(!Set.of("PAD_DIRECT","MOCK").contains(mode))throw new BusinessException("PRINTING_DISABLED");
        var payload=new LinkedHashMap<String,Object>();payload.put("store_id",store);payload.put("module_code","FRONTDESK_RECEIPT");
        payload.put("report_id",report);payload.put("report_snapshot",rows.get(0).get("snapshot"));
        payload.put("report_intent",Map.of("version","ALLO_SALES_REPORT_V1","execution_mode",mode));
        var job=printing.createPendingJob(s.organization_id,store,null,null,printer.id,"FRONTDESK_RECEIPT","SALES_OVERVIEW",actor,sales.write(payload),source);
        String text=rows.get(0).get("rendered_text").toString().lines().map(com.restaurant.system.printing.renderer.PrintMarkup::doubleHeight).collect(java.util.stream.Collectors.joining("\n"));
        job=printing.attachRenderedContent(job,printer.id,text);
        String font=assignment.font_size==null?printer.font_size:assignment.font_size;
        font=AlloReceiptSupport.font(font);
        if("PAD_DIRECT".equals(mode))job=printing.markPadDirectQueued(job,printer,font);
        else {job=printing.markPrinting(job,printer);job=printing.markPrinted(job,printer,"MOCK report; no physical paper");}
        return printing.toResponse(job);
    }
    private static String changes(String text) {
        try {var nodes=new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);var b=new StringBuilder();
            for(var n:nodes)b.append(n.path("name").asText()).append(" x").append(n.path("quantity").asInt()).append("\n");return b.toString();
        }catch(Exception e){throw new BusinessException("REPORT_ADJUSTMENT_INVALID");}
    }
    private static String action(String code) {return switch(code){case "VOID_ORDER"->"作废整单";case "VOID_ITEMS"->"作废菜品";case "DISCOUNT"->"应用折扣";case "REMOVE_DISCOUNT"->"取消折扣";case "TAKEOUT_REPAIR"->"外带完成修复";default->code;};}
    @SuppressWarnings("unchecked")
    public static String render(Map<String,Object> d) {
        var b=new StringBuilder("Sales Overview / 销售汇总\nAllo Nouilles\n");
        b.append(d.get("business_date")).append(" / ").append(d.get("timezone")).append("\n");
        b.append("Report: ").append(d.get("report_id")).append("\nGenerated: ").append(d.get("generated_at")).append("\n--------------------------------\n");
        for(String[] field:new String[][]{{"Gross Sales","gross"},{"Discounts","discount"},{"Net Sales","net"},{"Taxes","tax"},{"Total incl. Tax","total"},{"Amount Collected","collected"}})
            b.append(field[0]).append(": $").append(d.get(field[1])).append("\n");
        b.append("按门店规则记账，未按现金/银行卡核对\n");
        for(String channel:List.of("dine_in","takeout")){var c=(Map<String,Object>)d.get(channel);b.append(channel).append(": ").append(c.get("count")).append(" / $").append(c.get("total")).append("\n");}
        b.append("Uber Eats: 未接入\nVoided orders: ").append(d.get("voided_orders")).append("\n");
        b.append("Voided quantity: ").append(d.get("voided_quantity")).append("\nVoided gross: $").append(d.get("voided_gross")).append("\n");
        b.append("--------------------------------\n调整/作废明细（单独披露，不重复扣减）\n");
        for(var a:(List<Map<String,Object>>)d.get("adjustments")) b.append(a.get("created_at")).append(" / ").append(a.get("actor_name")).append("\n")
            .append(a.get("order_no")).append(" / ").append(a.get("order_type")).append(" / ").append(a.get("table_no")==null?a.get("pickup_no"):a.get("table_no")).append("\n")
            .append(action(a.get("action").toString())).append("\n").append(changes(a.get("item_changes").toString())).append("\n已收调整: $").append(a.get("collected_delta")).append("\n");
        return b.toString();
    }
}
