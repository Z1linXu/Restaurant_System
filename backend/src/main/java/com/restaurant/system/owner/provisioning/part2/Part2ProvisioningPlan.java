package com.restaurant.system.owner.provisioning.part2;

import java.util.List;

public record Part2ProvisioningPlan(
    List<StationSpec> stations,
    List<TableSpec> tables,
    List<StaffSpec> staff,
    List<PrinterRoleSpec> printerRoles,
    List<DeviceSpec> devices,
    String sanitizedJson,
    String fingerprint
) {

    public Part2ProvisioningPlan {
        stations = stations == null ? List.of() : List.copyOf(stations);
        tables = tables == null ? List.of() : List.copyOf(tables);
        staff = staff == null ? List.of() : List.copyOf(staff);
        printerRoles = printerRoles == null ? List.of() : List.copyOf(printerRoles);
        devices = devices == null ? List.of() : List.copyOf(devices);
    }

    public record StationSpec(
        String code,
        String name,
        String nameZh,
        String nameEn,
        String stationType,
        Integer sortOrder,
        Boolean active
    ) {}

    public record TableSpec(
        String code,
        String name,
        String areaName,
        String tableConfig,
        Integer capacity,
        Boolean supportsSplit,
        Integer sortOrder,
        Boolean active
    ) {}

    public record StaffSpec(
        String roleCode,
        String loginIdentifier,
        String fullName,
        String rawPassword
    ) {}

    public record PrinterRoleSpec(
        String roleCode,
        String moduleCode,
        String displayName,
        String mode,
        Boolean enabled,
        Boolean required
    ) {}

    public record DeviceSpec(
        String deviceName,
        String deviceType,
        String appVersion,
        String platform,
        Boolean trustedBuild,
        String workerStatus
    ) {}
}
