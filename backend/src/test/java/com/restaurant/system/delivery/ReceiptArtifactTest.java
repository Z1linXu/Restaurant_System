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
 PrintJobServiceImpl service=new PrintJobServiceImpl(jobs,mock(PrintJobAttemptRepository.class),mock(PrinterConfigRepository.class));
 PrintJob job(long id){var j=new PrintJob();j.id=id;j.store_id=1L;j.module_code="GRAB";j.status="PENDING";j.payload_snapshot="{\"receipt_context\":{\"version\":\"ALLO_RECEIPT_V1\"}}";when(jobs.findById(id)).thenReturn(Optional.of(j));when(jobs.save(any())).thenAnswer(i->i.getArgument(0));return j;}
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
}
