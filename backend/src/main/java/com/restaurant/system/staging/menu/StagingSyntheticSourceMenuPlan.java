package com.restaurant.system.staging.menu;

import java.util.List;
import java.util.Objects;

/** Deterministic dry-run plan. It deliberately contains no runtime IDs or private data. */
public record StagingSyntheticSourceMenuPlan(
    String manifestCode,
    String manifestVersion,
    String fingerprint,
    int categoryCount,
    int stationCount,
    int itemCount,
    int optionCount,
    List<CategoryPlan> categories
) {

    public StagingSyntheticSourceMenuPlan {
        categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
    }

    public record CategoryPlan(
        StagingSyntheticSourceMenuManifest.Category category,
        List<StationPlan> stations
    ) {

        public CategoryPlan {
            stations = List.copyOf(Objects.requireNonNull(stations, "stations"));
        }
    }

    public record StationPlan(
        StagingSyntheticSourceMenuManifest.Station station,
        List<ItemPlan> items
    ) {

        public StationPlan {
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    public record ItemPlan(
        StagingSyntheticSourceMenuManifest.Item item,
        List<OptionPlan> options
    ) {

        public ItemPlan {
            options = List.copyOf(Objects.requireNonNull(options, "options"));
        }
    }

    public record OptionPlan(
        StagingSyntheticSourceMenuManifest.Option option,
        List<OptionPlan> children
    ) {

        public OptionPlan {
            children = List.copyOf(Objects.requireNonNull(children, "children"));
        }
    }
}
