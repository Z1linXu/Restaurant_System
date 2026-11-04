package com.restaurant.system.integration.ubereats.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.integration.ubereats.dto.UberFinancialSnapshot;
import com.restaurant.system.integration.ubereats.repository.UberFinancialCorrectionRepository;
import java.sql.*;
import java.util.*;

/**
 * SSH-only maintenance main, not a Spring application/endpoint: no scheduler, importer or printer is started.
 * Scope is deliberately the one Owner-authorized case. Preview is read-only; apply/revoke touch only the journal.
 */
public final class UberFinancialCorrectionCli {
    public static final String CASE = "UBER_ANALYTICS_20261103_ST_DENIS";
    public static final String RULE = "MERCHANT_POST_PROMO_STRICT_V2";
    public static final Set<String> TARGETS = Set.of("U-6632c973","U-7009e85a","U-ec16337a","U-e081b098",
            "U-ca3f5b0c","U-d40b43e1","U-85566866","U-284238ab","U-5b5e15b8","U-31281cd2",
            "U-5a10537c","U-a9c8dfef","U-6f6dfe76","U-ae3be476");
    private static final ObjectMapper JSON = new ObjectMapper();
    public record Preview(String auditId, String originalState, String originalReason, Long candidateMinor,
            String currency, String validation, boolean allowed, String originalHash, String sourceHash,
            String candidateHash, String changedFields) {}
    private record Target(long id, String original, String source, UberFinancialSnapshot candidate, Preview preview) {}
    public record Result(String mode, String caseId, String previewSha256, List<Preview> orders, int inserted) {}

    public static void main(String[] args) {
        try {
            Map<String,String> options = new HashMap<>();
            for (int i=0; i<args.length; i+=2) {
                if (i+1>=args.length || !Set.of("--mode","--preview-sha").contains(args[i]))
                    throw new IllegalArgumentException("INVALID_ARGUMENTS");
                options.put(args[i],args[i+1]);
            }
            String host=System.getenv().getOrDefault("DB_HOST","127.0.0.1");
            String port=System.getenv().getOrDefault("DB_PORT","5432");
            String name=System.getenv().getOrDefault("DB_NAME","restaurant_pos");
            String url=System.getProperty("pending.jdbc.url","jdbc:postgresql://"+host+":"+port+"/"+name);
            String user=System.getProperty("pending.db.user",System.getenv().getOrDefault("DB_USER","restaurant_pos"));
            String password=System.getenv().getOrDefault("DB_PASSWORD","");
            try (Connection connection=DriverManager.getConnection(url,user,password)) {
                System.out.println(JSON.writeValueAsString(execute(connection,options.getOrDefault("--mode","preview"),options.get("--preview-sha"))));
            }
        } catch (Exception error) {
            // Never dump SQL, connection properties, snapshots, tokens or personal data.
            System.err.println("FINANCIAL_CORRECTION_FAILED:"+error.getClass().getSimpleName());
            System.exit(2);
        }
    }

    public static Result execute(Connection c, String mode, String expectedHash) throws Exception {
        if (!Set.of("preview","apply","revoke").contains(mode)) throw new IllegalArgumentException("INVALID_MODE");
        boolean preview=mode.equals("preview");
        c.setAutoCommit(false); c.setReadOnly(preview);
        try {
            try (Statement limits=c.createStatement()) {
                limits.execute("SET LOCAL statement_timeout = '3s'");
                limits.execute("SET LOCAL lock_timeout = '1s'");
            }
            if (mode.equals("revoke")) {
                Result result=revoke(c,expectedHash); c.commit(); return result;
            }
            List<Target> targets=readTargets(c,!preview);
            List<Preview> views=targets.stream().map(Target::preview).toList();
            String hash=UberFinancialCorrectionRepository.sha256(JSON.writeValueAsString(views));
            if (!preview && !hash.equals(expectedHash)) throw new IllegalStateException("PREVIEW_CHANGED");
            int inserted=0;
            if (!preview) for (Target t:targets) {
                if (!t.preview.allowed()) continue;
                String applyKey=CASE+":"+t.id+":"+t.preview.originalHash();
                if (mode.equals("apply")) {
                    try (PreparedStatement p=c.prepareStatement("""
                        INSERT INTO uber_eats_financial_corrections(operation_key,environment,store_id,uber_eats_order_id,
                        action,snapshot_version,original_snapshot_sha256,source_charges_sha256,source_kind,batch_preview_sha256,reason,
                        rule_version,snapshot_json) VALUES (?,'production',1,?,'APPLY',2,?,?,'RETAINED_CHARGES',?,
                        'OWNER_APPROVED_PROMO_RECONCILIATION',?,?) ON CONFLICT(operation_key) DO NOTHING
                        """)) {
                        p.setString(1,applyKey);p.setLong(2,t.id);p.setString(3,t.preview.originalHash());
                        p.setString(4,t.preview.sourceHash());p.setString(5,hash);p.setString(6,RULE);p.setString(7,JSON.writeValueAsString(t.candidate));
                        inserted+=p.executeUpdate();
                    }
                }
            }
            if (preview) c.rollback(); else c.commit();
            return new Result(mode,CASE,hash,views,inserted);
        } catch (Exception e) { c.rollback();throw e; }
        finally { c.setReadOnly(false);c.setAutoCommit(true); }
    }

    /** Compensation uses the committed batch, not current business state or later source charges. */
    private static Result revoke(Connection c, String hash) throws Exception {
        if (hash == null || !hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("INVALID_BATCH_HASH");
        int inserted=0;
        List<Preview> views=new ArrayList<>();
        try (PreparedStatement read=c.prepareStatement("""
                SELECT id,operation_key,uber_eats_order_id,original_snapshot_sha256,source_charges_sha256,snapshot_json
                FROM uber_eats_financial_corrections WHERE environment='production' AND store_id=1 AND action='APPLY'
                AND rule_version=? AND batch_preview_sha256=? AND left(operation_key,?)=? ORDER BY id FOR UPDATE
                """)) {
            read.setString(1,RULE);read.setString(2,hash);read.setInt(3,CASE.length()+1);read.setString(4,CASE+":");
            read.setQueryTimeout(3);
            try(ResultSet r=read.executeQuery()) {
                while(r.next()) {
                    String audit="U-"+md5("analytics-20261103:"+r.getLong(3)).substring(0,8);
                    if(!TARGETS.contains(audit))throw new IllegalStateException("REVOCATION_SCOPE_MISMATCH");
                    try(PreparedStatement p=c.prepareStatement("""
                        INSERT INTO uber_eats_financial_corrections(operation_key,environment,store_id,uber_eats_order_id,
                        action,snapshot_version,original_snapshot_sha256,source_charges_sha256,source_kind,batch_preview_sha256,
                        reason,rule_version,snapshot_json,reverses_id)
                        VALUES(?,'production',1,?,'REVOKE',2,?,?,'RETAINED_CHARGES',?,'OWNER_APPROVED_COMPENSATION',?,NULL,?)
                        ON CONFLICT(operation_key) DO NOTHING
                        """)) {
                        p.setString(1,"revoke:"+r.getString(2));p.setLong(2,r.getLong(3));p.setString(3,r.getString(4).trim());
                        p.setString(4,r.getString(5).trim());p.setString(5,hash);p.setString(6,RULE);p.setLong(7,r.getLong(1));
                        inserted+=p.executeUpdate();
                    }
                    var applied=JSON.readValue(r.getString(6),UberFinancialSnapshot.class);
                    views.add(new Preview(audit,"CORRECTION_APPLIED",null,applied.revenue_minor(),applied.currency(),
                            "COMPENSATED",true,r.getString(4).trim(),r.getString(5).trim(),
                            UberFinancialCorrectionRepository.sha256(r.getString(6)),"APPEND REVOKE journal event only"));
                }
            }
        }
        if(views.isEmpty())throw new IllegalStateException("BATCH_NOT_FOUND");
        return new Result("revoke",CASE,hash,views,inserted);
    }

    private static List<Target> readTargets(Connection c, boolean lock) throws Exception {
        List<Target> result=new ArrayList<>();
        try (PreparedStatement p=c.prepareStatement("""
                SELECT id,uber_order_id,uber_store_id,item_financial_snapshot_json,raw_financial_snapshot_json,
                status,cancelled,edit_required,scheduled,accepted_observed_at,accepted_at,local_order_id
                FROM uber_eats_orders WHERE environment='production' AND store_id=1
                AND placed_at>=TIMESTAMP '2026-11-03 05:00:00' AND placed_at<TIMESTAMP '2026-11-04 05:00:00'
                AND ('U-' || substr(md5('analytics-20261103:' || id::text),1,8)) IN (
                """ + String.join(",",Collections.nCopies(TARGETS.size(),"?")) + ") ORDER BY id" + (lock?" FOR UPDATE":""))) {
            int parameter=1;
            for (String auditId:TARGETS) p.setString(parameter++,auditId);
            p.setQueryTimeout(3);
            try (ResultSet r=p.executeQuery()) {
                while(r.next()) {
                    long id=r.getLong("id");
                    String auditId="U-"+md5("analytics-20261103:"+id).substring(0,8);
                    if (!TARGETS.contains(auditId)) continue;
                    String original=r.getString("item_financial_snapshot_json"),source=r.getString("raw_financial_snapshot_json");
                    UberFinancialSnapshot old=JSON.readValue(original,UberFinancialSnapshot.class);
                    if (!Objects.equals(old.order_id(),r.getString("uber_order_id"))
                            || !Objects.equals(old.store_id(),r.getString("uber_store_id")))
                        throw new IllegalStateException("IDENTITY_CHANGED");
                    if (r.getBoolean("cancelled") || r.getBoolean("edit_required") || r.getBoolean("scheduled")
                            || Set.of("DENIED","CANCELLED","CANCELLED_AFTER_RELEASE").contains(r.getString("status"))
                            || r.getObject("accepted_observed_at")==null && r.getObject("accepted_at")==null && r.getObject("local_order_id")==null)
                        throw new IllegalStateException("BUSINESS_STATE_CHANGED");
                    var candidate=UberFinancialSnapshot.reconcileRetained(old,JSON.readTree(source));
                    boolean allowed="CAD".equals(candidate.currency()) && candidate.blocked_reason()==null && candidate.revenue_minor()!=null;
                    Preview view=new Preview(auditId,"PENDING",old.blocked_reason(),
                            JSON.readTree(source).path("total_promo_applied").path("amount").isIntegralNumber()
                                    ? JSON.readTree(source).path("total_promo_applied").path("amount").longValue():null,
                            candidate.currency(),allowed?"PASS":candidate.blocked_reason(),allowed,
                            UberFinancialCorrectionRepository.sha256(original),UberFinancialCorrectionRepository.sha256(source),
                            UberFinancialCorrectionRepository.sha256(JSON.writeValueAsString(candidate)),
                            allowed?"INSERT financial correction journal only; original snapshot/order/print/outbox unchanged":"NONE");
                    result.add(new Target(id,original,source,candidate,view));
                }
            }
        }
        if (result.size()!=14 || !result.stream().map(t->t.preview.auditId()).collect(java.util.stream.Collectors.toSet()).equals(TARGETS))
            throw new IllegalStateException("TARGET_SET_CHANGED");
        return result;
    }

    private static String md5(String value) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
