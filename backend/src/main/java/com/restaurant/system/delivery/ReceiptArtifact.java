package com.restaurant.system.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.routing.TemplateRoutingService;
import com.restaurant.system.printing.transport.EscPosFontSizeMode;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/** Frozen receipt provenance. MOCK bytes can never be promoted to a physical artifact. */
public final class ReceiptArtifact {
    public static final String MOCK_TRANSPORT = "MOCK_ONLY_NOT_PHYSICAL_PROOF";
    public static final String PAD_TRANSPORT = "PAD_DIRECT_ESCPOS_V1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private ReceiptArtifact() {}

    public static boolean isTemplateJob(PrintJob job) {
        return job != null && TemplateRoutingService.EVENT.equals(job.module_code);
    }

    /** Server-created, order-free Print Center samples; never grants intent to historical jobs. */
    public static String withDiagnosticIntent(String snapshot, String mode) {
        try {
            ObjectNode payload = (ObjectNode) MAPPER.readTree(snapshot);
            ObjectNode intent = payload.putObject("diagnostic_intent");
            intent.put("version", "ALLO_PRINT_TEST_V1");
            intent.put("execution_mode", mode);
            return MAPPER.writeValueAsString(payload);
        } catch (Exception invalid) { throw new BusinessException("RECEIPT_DIAGNOSTIC_INTENT_INVALID"); }
    }

    public static boolean isDiagnosticJob(PrintJob job) {
        if (job == null || job.payload_snapshot == null) return false;
        JsonNode payload;
        try { payload = MAPPER.readTree(job.payload_snapshot); }
        catch (Exception legacy) { return false; }
        if (payload == null || !payload.has("diagnostic_intent")) return false;
        try {
            boolean printerTest = "ADMIN_TEST_PRINT".equals(payload.path("source").asText())
                && "TEST_PRINT".equals(job.receipt_type);
            boolean moduleTest = "ADMIN_MODULE_TEST_PRINT".equals(payload.path("source").asText())
                && (job.module_code + "_TEST").equals(job.receipt_type);
            if ((!printerTest && !moduleTest) || job.order_id != null || job.order_update_batch_id != null
                    || !payload.path("order_id").isNull()
                    || job.store_id == null || payload.path("store_id").asLong(-1) != job.store_id
                    || !java.util.Objects.equals(job.module_code, payload.path("module_code").asText())
                    || !"ALLO_PRINT_TEST_V1".equals(payload.path("diagnostic_intent").path("version").asText())) {
                throw new IllegalArgumentException();
            }
            return true;
        } catch (Exception invalid) { throw new BusinessException("RECEIPT_DIAGNOSTIC_INTENT_INVALID"); }
    }

    public static boolean protects(PrintJob job) {
        return isTemplateJob(job) || ReceiptContextService.hasContext(job) || isDiagnosticJob(job);
    }

    public static void requirePadIntent(PrintJob job) {
        try {
            JsonNode payload = MAPPER.readTree(job.payload_snapshot);
            JsonNode intent = isTemplateJob(job) ? payload
                : payload.path(isDiagnosticJob(job) ? "diagnostic_intent" : "receipt_context");
            if (!"PAD_DIRECT".equals(intent.path("execution_mode").asText())) {
                throw new IllegalArgumentException();
            }
        } catch (Exception invalid) { throw new BusinessException("RECEIPT_PAD_INTENT_REQUIRED"); }
    }

    public static boolean present(PrintJob job) {
        try { return MAPPER.readTree(job.payload_snapshot).has("receipt_artifact"); }
        catch (Exception invalid) { throw new BusinessException("RECEIPT_ARTIFACT_INVALID"); }
    }

    public static Charset physicalCharset(PrinterConfig printer) {
        String encoding = printer == null ? null : printer.text_encoding;
        try { return Charset.forName(encoding == null || encoding.isBlank() ? "GBK" : encoding); }
        catch (Exception invalid) { throw new BusinessException("RECEIPT_ENCODING_UNSUPPORTED"); }
    }

    public static void requireEncodable(String text, Charset charset) {
        try {
            charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
        } catch (Exception invalid) { throw new BusinessException("RECEIPT_ENCODING_UNSUPPORTED"); }
    }

    public static void freeze(PrintJob job, String transport, PrinterConfig printer, String fontSize) {
        try {
            ObjectNode payload = (ObjectNode) MAPPER.readTree(job.payload_snapshot);
            ObjectNode artifact = payload.putObject("receipt_artifact");
            artifact.put("encoding", physicalCharset(printer).name());
            artifact.put("paper_width_mm", paperWidth(printer));
            artifact.put("font", EscPosFontSizeMode.fromConfig(fontSize).code);
            if (printer != null && printer.escpos_code_page != null) artifact.put("escpos_code_page", printer.escpos_code_page & 0xFF);
            artifact.put("transport", transport);
            artifact.put("text_sha256", hash(job.rendered_text_snapshot.getBytes(StandardCharsets.UTF_8)));
            artifact.put("bytes_sha256", hash(Base64.getDecoder().decode(job.escposPayloadBase64)));
            job.payload_snapshot = MAPPER.writeValueAsString(payload);
        } catch (Exception invalid) { throw new BusinessException("RECEIPT_ARTIFACT_INVALID"); }
    }

    public static JsonNode validate(PrintJob job) {
        try {
            JsonNode artifact = MAPPER.readTree(job.payload_snapshot).path("receipt_artifact");
            String transport = artifact.path("transport").asText();
            if (!MOCK_TRANSPORT.equals(transport) && !PAD_TRANSPORT.equals(transport)
                    || !hash(job.rendered_text_snapshot.getBytes(StandardCharsets.UTF_8)).equals(artifact.path("text_sha256").asText())
                    || !hash(Base64.getDecoder().decode(job.escposPayloadBase64)).equals(artifact.path("bytes_sha256").asText())
                    || artifact.path("paper_width_mm").asInt() <= 0) throw new IllegalArgumentException();
            Charset.forName(artifact.path("encoding").asText());
            if (MOCK_TRANSPORT.equals(transport) && (!"UTF-8".equals(artifact.path("encoding").asText())
                    || artifact.path("paper_width_mm").asInt() != 80
                    || !EscPosFontSizeMode.DEFAULT_CODE.equals(artifact.path("font").asText()))) throw new IllegalArgumentException();
            return artifact;
        } catch (Exception invalid) { throw new BusinessException("RECEIPT_ORIGINAL_ARTIFACT_INVALID"); }
    }

    public static void requirePadArtifact(PrintJob job) {
        if (!PAD_TRANSPORT.equals(validate(job).path("transport").asText())) {
            throw new BusinessException("RECEIPT_MOCK_ARTIFACT_NOT_PHYSICAL");
        }
        requirePadIntent(job);
    }

    public static void requirePadSourceJob(PrintJob job) {
        requirePadArtifact(job);
        if (!"PAD_DIRECT".equals(job.executionMode)) throw new BusinessException("RECEIPT_PAD_SOURCE_REQUIRED");
    }

    public static void requireCompatiblePadArtifact(PrintJob job, PrinterConfig printer, String fontSize) {
        requirePadArtifact(job);
        JsonNode artifact = validate(job);
        Integer page = printer == null || printer.escpos_code_page == null ? null : printer.escpos_code_page & 0xFF;
        if (!physicalCharset(printer).name().equals(artifact.path("encoding").asText())
                || artifact.path("paper_width_mm").asInt() != paperWidth(printer)
                || !EscPosFontSizeMode.fromConfig(fontSize).code.equals(artifact.path("font").asText())
                || (page == null ? artifact.has("escpos_code_page") : !artifact.has("escpos_code_page")
                    || artifact.path("escpos_code_page").asInt() != page)) {
            throw new BusinessException("RECEIPT_ORIGINAL_PRINTER_INCOMPATIBLE");
        }
    }

    private static int paperWidth(PrinterConfig printer) {
        return printer == null || printer.paper_width_mm == null ? 80 : printer.paper_width_mm;
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
