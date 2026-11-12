package com.restaurant.system.delivery;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.repository.*;
import com.restaurant.system.printing.service.impl.PrintJobServiceImpl;
import java.util.*;
import org.junit.jupiter.api.Test;
class ReceiptArtifactTest {
 PrintJobRepository jobs=mock(PrintJobRepository.class);
 com.restaurant.system.printing.AlloProductionPrintingPolicy printingPolicy=mock(com.restaurant.system.printing.AlloProductionPrintingPolicy.class);
 PrintJobServiceImpl service=new PrintJobServiceImpl(jobs,mock(PrintJobAttemptRepository.class),mock(PrinterConfigRepository.class),printingPolicy);
 PrintJob job(long id){var j=new PrintJob();j.id=id;j.store_id=1L;j.module_code="GRAB";j.status="PENDING";j.payload_snapshot="{\"receipt_context\":{\"version\":\"ALLO_RECEIPT_V1\"}}";when(jobs.findById(id)).thenReturn(Optional.of(j));when(jobs.save(any())).thenAnswer(i->i.getArgument(0));return j;}
 PrintJob padJob(long id){var j=job(id);j.payload_snapshot="{\"receipt_context\":{\"version\":\"ALLO_RECEIPT_V1\",\"execution_mode\":\"PAD_DIRECT\"}}";return j;}
 @Test void mockProofFreezesExactUtf8AndHashesWithoutChangingState() throws Exception {
  var j=job(1);service.attachRenderedContent(j,null,"合成 Épicé x2\n");assertEquals("PENDING",j.status);
  assertNotNull(j.escposPayloadBase64);assertTrue(new String(Base64.getDecoder().decode(j.escposPayloadBase64),java.nio.charset.StandardCharsets.UTF_8).contains("合成 Épicé x2"));
  var a=new com.fasterxml.jackson.databind.ObjectMapper().readTree(j.payload_snapshot).path("receipt_artifact");assertEquals("UTF-8",a.path("encoding").asText());assertEquals(com.restaurant.system.printing.transport.EscPosFontSizeMode.DEFAULT_CODE,a.path("font").asText());assertEquals(64,a.path("bytes_sha256").asText().length());
 }
 @Test void manualCopyPreservesTextBytesAndContextExactly(){var source=job(1);service.attachRenderedContent(source,null,"合成 Plat synthétique\n");var copy=job(2);copy.payload_snapshot=source.payload_snapshot;copy.reprintSourceJobId=1L;
  service.attachRenderedContent(copy,null,source.rendered_text_snapshot);assertEquals(source.rendered_text_snapshot,copy.rendered_text_snapshot);assertEquals(source.escposPayloadBase64,copy.escposPayloadBase64);assertEquals(source.payload_snapshot,copy.payload_snapshot);}
 @Test void missingOrCorruptedOriginalCannotBeReconstructedSilently(){var source=job(1);service.attachRenderedContent(source,null,"Original");source.escposPayloadBase64="YWx0ZXJlZA==";var copy=job(2);copy.payload_snapshot=source.payload_snapshot;copy.reprintSourceJobId=1L;assertThrows(RuntimeException.class,()->service.attachRenderedContent(copy,null,"Original"));}
 @Test void mockProofCannotEnterPadQueue(){var j=job(1);service.attachRenderedContent(j,null,"Original");assertThrows(RuntimeException.class,()->service.markPadDirectQueued(j,null));assertEquals("PENDING",j.status);}
 @Test void malformedUnicodeFailsInsteadOfReplacement(){var j=job(1);assertThrows(RuntimeException.class,()->service.attachRenderedContent(j,null,"Broken \uD800"));}
 @Test void legacyAttachmentDoesNotGenerateNewArtifacts(){var j=job(1);j.payload_snapshot="{}";service.attachRenderedContent(j,null,"Legacy");assertNull(j.escposPayloadBase64);assertEquals("{}",j.payload_snapshot);}
 com.restaurant.system.printing.entity.PrinterConfig printer(){var p=new com.restaurant.system.printing.entity.PrinterConfig();p.id=5L;p.store_id=1L;p.enabled=true;p.ip_address="192.0.2.10";p.port=9100;p.text_encoding="UTF-8";p.escpos_code_page=17;p.font_size="SMALL";p.paper_width_mm=80;return p;}
 @Test void controlledPadBuildsFreshPhysicalArtifactWithActualPrinterEncodingAndEscpos() throws Exception {
  when(printingPolicy.padDirectActive(1L)).thenReturn(true);var j=padJob(1);var p=printer();
  service.attachRenderedContent(j,p.id,"合成 Épicé x2\n");assertNull(j.escposPayloadBase64);assertFalse(ReceiptArtifact.present(j));
  service.markPadDirectQueued(j,p,"SMALL");assertEquals("PAD_DIRECT",j.executionMode);assertEquals("PENDING",j.status);
  var artifact=ReceiptArtifact.validate(j);assertEquals(ReceiptArtifact.PAD_TRANSPORT,artifact.path("transport").asText());assertEquals("SMALL",artifact.path("font").asText());assertEquals(17,artifact.path("escpos_code_page").asInt());
  var bytes=Base64.getDecoder().decode(j.escposPayloadBase64);assertArrayEquals(new byte[]{0x1B,0x40,0x1B,0x74,17},Arrays.copyOf(bytes,5));
  assertTrue(new String(bytes,java.nio.charset.StandardCharsets.UTF_8).contains("合成 Épicé x2"));
 }
 @Test void historicalMockArtifactCannotBePromotedAfterRuntimeOpens(){var j=job(1);service.attachRenderedContent(j,null,"Frozen MOCK");String payload=j.payload_snapshot,bytes=j.escposPayloadBase64;
  when(printingPolicy.padDirectActive(1L)).thenReturn(true);assertThrows(RuntimeException.class,()->service.markPadDirectQueued(j,printer()));
  assertEquals(payload,j.payload_snapshot);assertEquals(bytes,j.escposPayloadBase64);assertEquals("PENDING",j.status);
 }
 @Test void newPadOriginalCopyPreservesExactTextBytesAndProvenance(){when(printingPolicy.padDirectActive(1L)).thenReturn(true);var source=padJob(1);var p=printer();service.attachRenderedContent(source,p.id,"Frozen physical\n");service.markPadDirectQueued(source,p);source.status="PRINTED";
  var copy=job(2);copy.payload_snapshot=source.payload_snapshot;copy.reprintSourceJobId=source.id;
  service.attachRenderedContent(copy,p.id,source.rendered_text_snapshot);service.markPadDirectQueued(copy,p);
  assertEquals(source.escposPayloadBase64,copy.escposPayloadBase64);assertEquals(source.payload_snapshot,copy.payload_snapshot);assertEquals("PRINTED",source.status);assertEquals("PENDING",copy.status);
 }
 @Test void originalPadBytesCannotBeRecodedForChangedPrinter(){when(printingPolicy.padDirectActive(1L)).thenReturn(true);var source=padJob(1);var p=printer();service.attachRenderedContent(source,p.id,"Frozen physical\n");service.markPadDirectQueued(source,p);
  var copy=job(2);copy.payload_snapshot=source.payload_snapshot;copy.reprintSourceJobId=source.id;service.attachRenderedContent(copy,p.id,source.rendered_text_snapshot);
  p.escpos_code_page=18;assertThrows(RuntimeException.class,()->service.markPadDirectQueued(copy,p));assertEquals(source.escposPayloadBase64,copy.escposPayloadBase64);
 }
 @Test void strictPhysicalEncodingFailsInsteadOfReplacingCharacters(){when(printingPolicy.padDirectActive(1L)).thenReturn(true);var j=padJob(1);var p=printer();p.text_encoding="US-ASCII";service.attachRenderedContent(j,p.id,"合成菜");
  assertThrows(RuntimeException.class,()->service.markPadDirectQueued(j,p));assertNull(j.escposPayloadBase64);assertFalse(ReceiptArtifact.present(j));
 }
 @Test void newPhysicalArtifactRejectsForeignStorePrinter(){when(printingPolicy.padDirectActive(1L)).thenReturn(true);var j=padJob(1);var p=printer();p.store_id=2L;service.attachRenderedContent(j,p.id,"Original");assertThrows(RuntimeException.class,()->service.markPadDirectQueued(j,p));assertNull(j.escposPayloadBase64);}
 @Test void frozenArtifactCannotBeOverwrittenOrRequeued(){when(printingPolicy.padDirectActive(1L)).thenReturn(true);var j=padJob(1);var p=printer();service.attachRenderedContent(j,p.id,"Original");service.markPadDirectQueued(j,p);String text=j.rendered_text_snapshot,bytes=j.escposPayloadBase64;
  assertThrows(RuntimeException.class,()->service.attachRenderedContent(j,p.id,"Changed"));assertThrows(RuntimeException.class,()->service.markPadDirectQueued(j,p));assertEquals(text,j.rendered_text_snapshot);assertEquals(bytes,j.escposPayloadBase64);
 }
 @Test void missingOrMockReceiptIntentCannotGeneratePhysicalBytesEvenBeforeAnyArtifactExists(){
  when(printingPolicy.padDirectActive(1L)).thenReturn(true);
  for(String captured:List.of("",",\"execution_mode\":\"MOCK\"")){
   var j=job(1);j.payload_snapshot="{\"receipt_context\":{\"version\":\"ALLO_RECEIPT_V1\""+captured+"}}";
   assertThrows(RuntimeException.class,()->service.attachRenderedContent(j,5L,"Historical pending"));
   assertThrows(RuntimeException.class,()->service.markPadDirectQueued(j,printer()));assertNull(j.escposPayloadBase64);
  }
 }
 PrintJob templateJob(long id,String capturedMode){var j=job(id);j.module_code="TEMPLATE_KITCHEN";j.payload_snapshot=capturedMode==null?"{\"group_code\":\"A\"}":"{\"group_code\":\"A\",\"execution_mode\":\""+capturedMode+"\"}";return j;}
 @Test void legacyTemplateOriginalCannotEnterPadThroughManualCopy(){
  var source=templateJob(1,null);source.executionMode="MOCK";source.status="PRINTED";source.rendered_text_snapshot="Frozen old template";
  var copy=templateJob(2,null);copy.reprintSourceJobId=source.id;copy.rendered_text_snapshot=source.rendered_text_snapshot;
  when(printingPolicy.padDirectActive(1L)).thenReturn(true);
  assertThrows(RuntimeException.class,()->service.attachRenderedContent(copy,5L,source.rendered_text_snapshot));
  assertThrows(RuntimeException.class,()->service.markPadDirectQueued(copy,printer()));
  assertEquals("PRINTED",source.status);assertEquals("MOCK",source.executionMode);assertNull(source.escposPayloadBase64);assertNull(copy.escposPayloadBase64);
 }
 @Test void explicitTemplatePadIntentGeneratesArtifactAndOriginalCopyKeepsExactBytes(){
  when(printingPolicy.padDirectActive(1L)).thenReturn(true);var source=templateJob(1,"PAD_DIRECT");var p=printer();
  service.attachRenderedContent(source,p.id,"Frozen new template\n");service.markPadDirectQueued(source,p);source.status="PRINTED";
  assertEquals(ReceiptArtifact.PAD_TRANSPORT,ReceiptArtifact.validate(source).path("transport").asText());
  var copy=templateJob(2,"PAD_DIRECT");copy.payload_snapshot=source.payload_snapshot;copy.reprintSourceJobId=source.id;
  service.attachRenderedContent(copy,p.id,source.rendered_text_snapshot);service.markPadDirectQueued(copy,p);
  assertEquals(source.escposPayloadBase64,copy.escposPayloadBase64);assertEquals(source.payload_snapshot,copy.payload_snapshot);assertEquals("PRINTED",source.status);
 }
 @Test void legacyTemplateCanStillBeCopiedInMockWithoutInventingPhysicalProvenance(){
  var source=templateJob(1,null);source.status="PRINTED";source.executionMode="MOCK";source.rendered_text_snapshot="Old template";
  var copy=templateJob(2,null);copy.reprintSourceJobId=source.id;
  service.attachRenderedContent(copy,5L,source.rendered_text_snapshot);
  assertEquals(source.payload_snapshot,copy.payload_snapshot);assertNull(copy.escposPayloadBase64);assertFalse(ReceiptArtifact.present(copy));
 }
 @Test void alloReceiptWithoutAnyContextCannotEnterPhysicalQueueThroughLegacyAttachment(){
  when(printingPolicy.allowsPadDirect(1L)).thenReturn(true);var j=job(1);j.payload_snapshot="{}";
  service.attachRenderedContent(j,5L,"Legacy text");assertThrows(RuntimeException.class,()->service.markPadDirectQueued(j,printer()));
  assertEquals("PENDING",j.status);assertNull(j.escposPayloadBase64);
 }
}
