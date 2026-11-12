package com.restaurant.system.printing.routing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.menu.template.*;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.*;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.dto.MenuRevisionResponse;
import com.restaurant.system.modules.*;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.printing.entity.*;
import com.restaurant.system.printing.repository.*;
import com.restaurant.system.printing.service.*;
import com.restaurant.system.printing.routing.TemplateRoutingModels.*;
import com.restaurant.system.printing.service.impl.OrderDispatchOutboxProcessor;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

class TemplatePrintingBoundaryTest {
 private final TemplateMenuService menus=mock(TemplateMenuService.class);
 private final PrintJobService jobs=mock(PrintJobService.class);
 private final PrintJobRepository jobRepo=mock(PrintJobRepository.class);
 private final PrinterConfigRepository printers=mock(PrinterConfigRepository.class);
 private final PrinterConfigService printing=mock(PrinterConfigService.class);
 private final StoreModuleAccessEvaluator modules=mock(StoreModuleAccessEvaluator.class);
 private final com.restaurant.system.printing.AlloProductionPrintingPolicy printingPolicy=mock(com.restaurant.system.printing.AlloProductionPrintingPolicy.class);
 private final TemplateKitchenDispatchHandler handler=new TemplateKitchenDispatchHandler(menus,jobs,jobRepo,printers,printing,modules,printingPolicy);
 private final FrozenIntent intent=new FrozenIntent();
 private OrderDispatchOutbox event(){
  var e=new OrderDispatchOutbox();e.id=1L;e.storeId=2L;e.organizationId=3L;e.orderId=4L;e.eventType=TemplateRoutingService.EVENT;
  e.moduleCode=TemplateRoutingService.EVENT;e.sourceKey="template:submit:4:group:A";e.routingContext="frozen";e.attemptCount=0;e.nextAttemptAt=LocalDateTime.now();
  intent.group_code="A";intent.printer_id=5L;intent.rendered_text="Frozen ticket";
  when(menus.read("frozen",FrozenIntent.class)).thenReturn(intent);
  when(jobRepo.findByDispatchSourceKey(e.sourceKey)).thenReturn(Optional.empty());
  when(printing.getStorePrintingMode(2L)).thenReturn("MOCK");
  var evaluation=mock(StoreModuleAccessEvaluation.class);
  when(modules.evaluateCapability(2L,ModuleKeys.PRINTING)).thenReturn(evaluation);
  var printer=new PrinterConfig();printer.id=5L;printer.store_id=2L;printer.enabled=true;
  when(printers.findByIdAndStoreId(5L,2L)).thenReturn(Optional.of(printer));
  return e;
 }
 @Test void disabledPrintingModuleCannotCreateOrMarkAnyJob(){
  var e=event();var evaluation=modules.evaluateCapability(2L,ModuleKeys.PRINTING);
  doThrow(new com.restaurant.system.common.exception.BusinessException("MODULE_DISABLED")).when(evaluation).requireAllowed();
  assertEquals(PrintDispatchOutcome.POLICY_BLOCKED,handler.dispatch(e));
  assertEquals("MODULE_DISABLED",e.lastError);verifyNoInteractions(jobs);
 }
 @Test void nonMockModeIsInertAndCannotCreateAnyJob(){
  var e=event();when(printing.getStorePrintingMode(2L)).thenReturn("PAD_DIRECT");
  assertEquals(PrintDispatchOutcome.POLICY_BLOCKED,handler.dispatch(e));verifyNoInteractions(jobs);
 }
 @Test void knownPreJobFailureDoesNotInventPrintedJob(){
  var e=event();intent.reason="Printer C is missing";
  assertEquals(PrintDispatchOutcome.FAILED,handler.dispatch(e));assertEquals(intent.reason,e.lastError);verifyNoInteractions(jobs);
 }
 @Test void absentOrForeignPrinterCannotCreateJob(){
  var e=event();when(printers.findByIdAndStoreId(5L,2L)).thenReturn(Optional.empty());
  assertEquals(PrintDispatchOutcome.FAILED,handler.dispatch(e));verifyNoInteractions(jobs);
 }
 @Test void terminalUncertainResultIsNeverReopenedOrReattached(){
  var e=event();var old=new PrintJob();old.status="FAILED";old.error_code="PRINT_RESULT_UNCERTAIN";old.error_message="MOCK uncertain";
  when(jobRepo.findByDispatchSourceKey(e.sourceKey)).thenReturn(Optional.of(old));
  assertEquals(PrintDispatchOutcome.FAILED,handler.dispatch(e));verifyNoInteractions(jobs);assertEquals(old.error_message,e.lastError);
 }
 @Test void successfulMockUsesFrozenTextAndExistingStateMethods(){
  var e=event();var job=new PrintJob();job.id=44L;job.status="PENDING";
  when(jobs.createPendingJob(any(),any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(job);
  when(jobs.attachRenderedContent(job,5L,"Frozen ticket")).thenReturn(job);
  when(jobs.markPrinting(eq(job),any())).thenReturn(job);
  assertEquals(PrintDispatchOutcome.MOCK_RENDERED,handler.dispatch(e));
  assertEquals("MOCK",job.executionMode);
  verify(jobs).markPrinted(eq(job),any(),eq("MOCK: inert transport, no printer bytes sent"));
  verify(jobs,never()).markPadDirectQueued(any(),any());
 }
 @Test void controlledAlloPadUsesExistingQueueWithoutMockOrPhysicalExecution(){
  var e=event();when(printing.getStorePrintingMode(2L)).thenReturn("PAD_DIRECT");when(printingPolicy.allowsPadDirect(2L)).thenReturn(true);
  intent.execution_mode="PAD_DIRECT";
  var job=new PrintJob();job.id=44L;job.status="PENDING";
  when(jobs.createPendingJob(any(),any(),any(),any(),any(),any(),any(),any(),any(),eq(e.sourceKey))).thenReturn(job);
  when(jobs.attachRenderedContent(job,5L,"Frozen ticket")).thenReturn(job);when(jobs.markPadDirectQueued(eq(job),any())).thenReturn(job);
  // Old MOCK-only failure injection must never become a physical transport operation.
  intent.mock_failure="UNCERTAIN";
  assertEquals(PrintDispatchOutcome.DISPATCHED,handler.dispatch(e));assertEquals("PAD_DIRECT",job.executionMode);
  verify(jobs).markPadDirectQueued(eq(job),any());verify(jobs,never()).markPrinting(any(),any());verify(jobs,never()).markPrinted(any(),any(),any());verify(jobs,never()).markFailed(any(),any(),any(),any());
  when(jobRepo.findByDispatchSourceKey(e.sourceKey)).thenReturn(Optional.of(job));
  assertEquals(PrintDispatchOutcome.DISPATCHED,handler.dispatch(e));
  verify(jobs,times(1)).createPendingJob(any(),any(),any(),any(),any(),any(),any(),any(),any(),eq(e.sourceKey));
  verify(jobs,times(1)).markPadDirectQueued(eq(job),any());
 }
 @Test void historicalMissingOrMockIntentsCannotBecomePhysicalAfterStoreSwitchesToPad(){
  var e=event();when(printing.getStorePrintingMode(2L)).thenReturn("PAD_DIRECT");when(printingPolicy.allowsPadDirect(2L)).thenReturn(true);
  for(String capturedMode:Arrays.asList(null,"MOCK","DISABLED")){
   intent.execution_mode=capturedMode;
   assertEquals(PrintDispatchOutcome.POLICY_BLOCKED,handler.dispatch(e));
   assertTrue(e.lastError.startsWith("TEMPLATE_PAD_INTENT_REQUIRED"));verifyNoInteractions(jobs);
  }
 }
 @Test void replayOfAnExistingPadJobCannotCreateAnotherJobOrReopenIt(){
  var e=event();var existing=new PrintJob();existing.executionMode="PAD_DIRECT";existing.status="PRINTING";
  when(jobRepo.findByDispatchSourceKey(e.sourceKey)).thenReturn(Optional.of(existing));
  assertEquals(PrintDispatchOutcome.DISPATCHED,handler.dispatch(e));verifyNoInteractions(jobs);
 }
 @Test void routingSaveCanUsePadOnlyForTheExplicitAlloRuntime(){
  var configs=mock(TemplateStoreConfigurationRepository.class);var revisions=mock(MenuRevisionService.class);
  var routing=new TemplateRoutingService(menus,configs,mock(MenuItemRepository.class),mock(MenuItemOptionRepository.class),mock(MenuCategoryRepository.class),printers,revisions,mock(OrderDispatchOutboxRepository.class),printing,printingPolicy);
  var config=new Configuration();when(printing.getStorePrintingMode(2L)).thenReturn("PAD_DIRECT");
  assertThrows(com.restaurant.system.common.exception.BusinessException.class,()->routing.save(2L,config));
  when(printingPolicy.allowsPadDirect(2L)).thenReturn(true);assertDoesNotThrow(()->routing.save(2L,config));
  var group=new Group();group.group_code="A";group.name="Kitchen";group.mock_failure="FAILED";config.groups.add(group);
  assertThrows(com.restaurant.system.common.exception.BusinessException.class,()->routing.save(2L,config));
 }
 @Test void diagnosticsReportActualPadProvenanceAndKeepUnknownHistoryMockOnly(){
  var e=event();var events=mock(OrderDispatchOutboxRepository.class);
  when(events.findTemplateIntents(eq(2L),isNull(),isNull(),any())).thenReturn(List.of(e));
  var diagnostics=new TemplatePrintingDiagnostics(events,jobRepo,menus);
  intent.execution_mode="PAD_DIRECT";
  var rows=(List<Map<String,Object>>)diagnostics.list(2L,null,null).get("tickets");assertEquals("PAD_DIRECT",rows.get(0).get("execution_mode"));
  intent.execution_mode=null;rows=(List<Map<String,Object>>)diagnostics.list(2L,null,null).get("tickets");assertEquals("MOCK",rows.get(0).get("execution_mode"));
 }
 @Test void genericProcessorBranchDoesNotCallLegacyDispatcherOrAssignmentQuery(){
  var e=event();var repo=mock(OrderDispatchOutboxRepository.class);var assignments=mock(PrinterAssignmentRepository.class);var legacy=mock(PrintDispatcherService.class);
  var isolated=mock(TemplateKitchenDispatchHandler.class);when(isolated.handles(e)).thenReturn(true);when(isolated.serializationKey(e)).thenReturn("store:2|printer:5");
  when(isolated.dispatch(e)).thenAnswer(v->{e.lastError="pre-job persistent reason";return PrintDispatchOutcome.FAILED;});
  when(repo.findDueForDispatch(any(),any())).thenReturn(List.of(e));when(repo.claimDueForProcessing(eq(1L),any(),any())).thenReturn(1);
  when(repo.findById(1L)).thenReturn(Optional.of(e));
  PlatformTransactionManager tx=new PlatformTransactionManager(){
   public TransactionStatus getTransaction(TransactionDefinition d){return new SimpleTransactionStatus();}
   public void commit(TransactionStatus s){} public void rollback(TransactionStatus s){}
  };
  var processor=new OrderDispatchOutboxProcessor(repo,assignments,legacy,Runnable::run,new TransactionTemplate(tx));processor.setTemplateHandler(isolated);processor.processDueEvents();
  verifyNoInteractions(legacy,assignments);assertEquals("FAILED",e.status);assertEquals("pre-job persistent reason",e.lastError);
 }
 @Test void customerGroupNamedUnassignedCannotCollideWithSystemDiagnostic() throws Exception {
  when(printing.getStorePrintingMode(2L)).thenReturn("PAD_DIRECT");
  var configs=mock(TemplateStoreConfigurationRepository.class);var itemRepo=mock(MenuItemRepository.class);
  var optionRepo=mock(MenuItemOptionRepository.class);var categoryRepo=mock(MenuCategoryRepository.class);
  var revisions=mock(MenuRevisionService.class);var events=mock(OrderDispatchOutboxRepository.class);
  var config=new Configuration();config.enabled=true;var g=new Group();g.group_code="UNASSIGNED";g.name="Customer named group";
  g.category_ids=List.of(10L);g.logical_target="A";g.printer_id=5L;config.groups.add(g);
  var stored=new TemplateStoreConfiguration();stored.store_id=2L;stored.routing_json="config";
  when(configs.findById(2L)).thenReturn(Optional.of(stored));when(menus.read("config",Configuration.class)).thenReturn(config);
  when(revisions.getRevision(2L)).thenReturn(new MenuRevisionResponse(2L,3L,1L,LocalDateTime.now(),"v","t","e"));
  var itemA=new MenuItem();itemA.id=11L;itemA.category_id=10L;var itemB=new MenuItem();itemB.id=12L;itemB.category_id=20L;
  when(itemRepo.findById(11L)).thenReturn(Optional.of(itemA));when(itemRepo.findById(12L)).thenReturn(Optional.of(itemB));
  var printer=new PrinterConfig();printer.id=5L;printer.enabled=true;when(printers.findByIdAndStoreId(5L,2L)).thenReturn(Optional.of(printer));
  when(events.insertIfAbsent(any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
  when(events.findBySourceKey(any())).thenAnswer(v->{var e=new OrderDispatchOutbox();e.sourceKey=v.getArgument(0);return Optional.of(e);});
  var mapper=new ObjectMapper();when(menus.write(any())).thenAnswer(v->mapper.writeValueAsString(v.getArgument(0)));
  var routing=new TemplateRoutingService(menus,configs,itemRepo,optionRepo,categoryRepo,printers,revisions,events,printing,printingPolicy);
  var order=new Order();order.id=9L;order.store_id=2L;order.order_no="SYNTHETIC";
  var lineA=new OrderItem();lineA.id=21L;lineA.menu_item_id=11L;lineA.item_name_snapshot_zh="A";lineA.quantity=1;
  var lineB=new OrderItem();lineB.id=22L;lineB.menu_item_id=12L;lineB.item_name_snapshot_zh="B";lineB.quantity=1;
  try(var origin=com.restaurant.system.printing.security.PrintOriginContext.dispatch(66L,2L,3L)){
   routing.enqueue(order,null,List.of(lineA,lineB),List.of());
  }
  var keys=ArgumentCaptor.forClass(String.class);
  verify(events,times(2)).insertIfAbsent(any(),any(),any(),any(),any(),any(),keys.capture(),any(),any());
  assertEquals(Set.of("template:submit:9:group:UNASSIGNED","template:submit:9:diagnostic:UNASSIGNED"),new HashSet<>(keys.getAllValues()));
  verify(events,times(2)).insertIfAbsent(eq(3L),eq(2L),eq(9L),isNull(),any(),any(),any(),any(),eq(66L));
  var frozen=ArgumentCaptor.forClass(OrderDispatchOutbox.class);verify(events,times(2)).save(frozen.capture());
  for(var event:frozen.getAllValues())assertEquals("PAD_DIRECT",mapper.readTree(event.routingContext).path("execution_mode").asText());
 }
}
