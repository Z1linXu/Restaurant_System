package com.restaurant.system.owner.provisioning.part2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.PrintingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class Part2PlanNormalizer {

    private static final String DEFAULT_MODE = PrintingMode.DISABLED;
    private static final String DEFAULT_WORKER_STATUS = "HEALTHY";

    private final ObjectMapper objectMapper;

    public Part2PlanNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Part2ProvisioningPlan normalize(StorePart2ProvisioningRequest request) {
        if (request == null) {
            throw invalid("PART2_REQUEST_REQUIRED");
        }

        List<Part2ProvisioningPlan.StationSpec> stations = new ArrayList<>();
        for (StorePart2ProvisioningRequest.StationSpec station : safe(request.stations)) {
            String code = normalizeCode(station.code, "STATION_CODE_REQUIRED");
            stations.add(new Part2ProvisioningPlan.StationSpec(
                code,
                text(station.name, code),
                textOrNull(station.name_zh),
                textOrNull(station.name_en),
                text(station.station_type, "KITCHEN").toUpperCase(Locale.ROOT),
                station.sort_order == null ? 0 : station.sort_order,
                station.is_active == null || station.is_active
            ));
        }

        List<Part2ProvisioningPlan.TableSpec> tables = new ArrayList<>();
        for (StorePart2ProvisioningRequest.TableSpec table : safe(request.tables)) {
            String code = normalizeCode(table.table_code, "TABLE_CODE_REQUIRED");
            tables.add(new Part2ProvisioningPlan.TableSpec(
                code,
                text(table.table_name, code),
                textOrNull(table.area_name),
                textOrNull(table.table_config),
                table.capacity == null ? 2 : Math.max(1, table.capacity),
                table.supports_split == null || table.supports_split,
                table.sort_order == null ? 0 : table.sort_order,
                table.is_active == null || table.is_active
            ));
        }

        List<Part2ProvisioningPlan.StaffSpec> staff = new ArrayList<>();
        for (StorePart2ProvisioningRequest.StaffSpec person : safe(request.staff)) {
            String role = text(person.role_code, "STAFF_ROLE_REQUIRED").toUpperCase(Locale.ROOT);
            if (!List.of("MANAGER", "FRONTDESK").contains(role)) {
                throw invalid("PART2_STAFF_ROLE_UNSUPPORTED");
            }
            String login = textOrNull(person.login_identifier);
            String password = textOrNull(person.temporary_password);
            if (password != null && password.length() < 8) {
                throw invalid("PART2_STAFF_PASSWORD_TOO_SHORT");
            }
            staff.add(new Part2ProvisioningPlan.StaffSpec(role, login, textOrNull(person.full_name), password));
        }

        List<Part2ProvisioningPlan.PrinterRoleSpec> printerRoles = new ArrayList<>();
        for (StorePart2ProvisioningRequest.PrinterRoleSpec role : safe(request.printer_roles)) {
            String roleCode = normalizeCode(role.role_code, "PRINTER_ROLE_CODE_REQUIRED");
            String moduleCode = text(role.module_code, "PRINTER_MODULE_CODE_REQUIRED").toUpperCase(Locale.ROOT);
            if (!PrintModuleCode.ALL.contains(moduleCode)) {
                throw invalid("PART2_PRINTER_MODULE_UNSUPPORTED");
            }
            String mode = PrintingMode.normalizeOrDefault(role.mode, DEFAULT_MODE);
            if (!PrintingMode.DISABLED.equals(mode) && !PrintingMode.MOCK.equals(mode)) {
                throw invalid("PART2_PRINTER_MODE_NOT_ALLOWED");
            }
            printerRoles.add(new Part2ProvisioningPlan.PrinterRoleSpec(
                roleCode,
                moduleCode,
                text(role.display_name, roleCode),
                mode,
                role.enabled != null && role.enabled,
                role.required == null || role.required
            ));
        }

        List<Part2ProvisioningPlan.DeviceSpec> devices = new ArrayList<>();
        for (StorePart2ProvisioningRequest.DeviceSpec device : safe(request.devices)) {
            devices.add(new Part2ProvisioningPlan.DeviceSpec(
                text(device.device_name, "Synthetic Pad"),
                text(device.device_type, "ANDROID_PAD").toUpperCase(Locale.ROOT),
                textOrNull(device.app_version),
                textOrNull(device.platform),
                device.trusted_build == null || device.trusted_build,
                text(device.worker_status, DEFAULT_WORKER_STATUS).toUpperCase(Locale.ROOT)
            ));
        }

        stations.sort(Comparator.comparing(Part2ProvisioningPlan.StationSpec::code));
        tables.sort(Comparator.comparing(Part2ProvisioningPlan.TableSpec::code));
        staff.sort(Comparator.comparing(Part2ProvisioningPlan.StaffSpec::roleCode)
            .thenComparing(value -> value.loginIdentifier() == null ? "" : value.loginIdentifier()));
        printerRoles.sort(Comparator.comparing(Part2ProvisioningPlan.PrinterRoleSpec::roleCode));
        devices.sort(Comparator.comparing(Part2ProvisioningPlan.DeviceSpec::deviceName));

        String sanitizedJson = sanitizedJson(stations, tables, staff, printerRoles, devices);
        return new Part2ProvisioningPlan(
            stations,
            tables,
            staff,
            printerRoles,
            devices,
            sanitizedJson,
            StoreProfileCanonicalJson.sha256Canonical(sanitizedJson)
        );
    }

    private String sanitizedJson(
        List<Part2ProvisioningPlan.StationSpec> stations,
        List<Part2ProvisioningPlan.TableSpec> tables,
        List<Part2ProvisioningPlan.StaffSpec> staff,
        List<Part2ProvisioningPlan.PrinterRoleSpec> printerRoles,
        List<Part2ProvisioningPlan.DeviceSpec> devices
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode stationArray = root.putArray("stations");
        stations.forEach(station -> stationArray.addObject()
            .put("code", station.code())
            .put("name", station.name())
            .put("station_type", station.stationType())
            .put("sort_order", station.sortOrder())
            .put("is_active", station.active()));
        ArrayNode tableArray = root.putArray("tables");
        tables.forEach(table -> tableArray.addObject()
            .put("table_code", table.code())
            .put("table_name", table.name())
            .put("area_name", table.areaName() == null ? "" : table.areaName())
            .put("table_config", table.tableConfig() == null ? "" : table.tableConfig())
            .put("capacity", table.capacity())
            .put("supports_split", table.supportsSplit())
            .put("sort_order", table.sortOrder())
            .put("is_active", table.active()));
        ArrayNode staffArray = root.putArray("staff");
        staff.forEach(person -> staffArray.addObject()
            .put("role_code", person.roleCode())
            .put("login_identifier", person.loginIdentifier() == null ? "AUTO_GENERATED" : person.loginIdentifier())
            .put("full_name", person.fullName() == null ? "" : person.fullName())
            .put("credential_policy", "BCrypt_RUNTIME_ONLY"));
        ArrayNode printerArray = root.putArray("printer_roles");
        printerRoles.forEach(role -> printerArray.addObject()
            .put("role_code", role.roleCode())
            .put("module_code", role.moduleCode())
            .put("display_name", role.displayName())
            .put("mode", role.mode())
            .put("enabled", role.enabled())
            .put("required", role.required())
            .put("physical_binding_status", "UNBOUND"));
        ArrayNode deviceArray = root.putArray("devices");
        devices.forEach(device -> deviceArray.addObject()
            .put("device_name", device.deviceName())
            .put("device_type", device.deviceType())
            .put("app_version", device.appVersion() == null ? "" : device.appVersion())
            .put("platform", device.platform() == null ? "" : device.platform())
            .put("trusted_build", device.trustedBuild())
            .put("worker_status", device.workerStatus())
            .put("credential_policy", "DEVICE_TOKEN_HASH_RUNTIME_ONLY"));
        return objectMapper.valueToTree(root).toString();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String normalizeCode(String value, String errorCode) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9_-]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^-|-$", "");
        if (normalized.isBlank() || !normalized.matches("[A-Z0-9][A-Z0-9_-]{0,63}")) {
            throw invalid(errorCode);
        }
        return normalized;
    }

    private String text(String value, String fallbackOrError) {
        String normalized = textOrNull(value);
        if (normalized != null) {
            return normalized;
        }
        if (fallbackOrError.endsWith("_REQUIRED")) {
            throw invalid(fallbackOrError);
        }
        return fallbackOrError;
    }

    private String textOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private BusinessException invalid(String code) {
        return new BusinessException(code);
    }
}
