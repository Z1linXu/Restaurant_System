package com.restaurant.system.owner.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.impl.MenuRevisionServiceImpl;
import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.CategorySelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.CategorySourcePolicy;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemRole;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemSelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.SourcePolicy;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.StationSelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.StationSourcePolicy;
import com.restaurant.system.owner.menu.StoreMenuCloneCompositionContext;
import com.restaurant.system.owner.menu.StoreMenuCloneGraphComposer;
import com.restaurant.system.owner.menu.StoreMenuCloneProfileRegistry;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneRequestCoordinator;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneSuccessEvidence;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneTransactionCommand;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneTransactionResult;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = StoreMenuCloneTransactionServiceImplIntegrationTest.JpaSliceConfiguration.class)
@Import(MenuRevisionServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StoreMenuCloneTransactionServiceImplIntegrationTest {

    private static final long ORGANIZATION_ID = 81L;
    private static final String PROFILE_CODE = "SYNTHETIC_BASE_GRAPH_V1";

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
    }

    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuCategoryRepository categoryRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private MenuItemRepository itemRepository;
    @Autowired private MenuItemOptionRepository optionRepository;
    @Autowired private MenuRevisionService menuRevisionService;
    @Autowired private PlatformTransactionManager transactionManager;

    private final OwnerStoreMenuCloneRequestCoordinator coordinator = mock(
        OwnerStoreMenuCloneRequestCoordinator.class
    );

    @BeforeEach
    void cleanDatabase() {
        reset(coordinator);
        transaction().executeWithoutResult(status -> {
            optionRepository.deleteAll();
            itemRepository.deleteAll();
            categoryRepository.deleteAll();
            stationRepository.deleteAll();
            storeRepository.deleteAll();
        });
    }

    @Test
    void exactProfileCodeCreatesGenericGraphAndPreservesSource() {
        Fixture fixture = fixture();
        SyntheticProfile profile = defaultProfile(fixture.source().id);
        AtomicReference<StoreMenuCloneCompositionContext> contextRef = new AtomicReference<>();
        StoreMenuCloneGraphComposer composer = composer(
            "snapshot-capture",
            StoreMenuCloneGraphComposer.Phase.SOURCE_OPTIONS,
            10,
            PROFILE_CODE,
            context -> {
            contextRef.set(context);
            return 0;
        });
        SourceState sourceBefore = sourceState(fixture.source().id);

        assertCloneError(
            () -> execute(service(profile, List.of(composer)), command(fixture, PROFILE_CODE.toLowerCase())),
            "MENU_CLONE_REQUEST_INVALID"
        );
        assertCloneError(
            () -> execute(service(profile, List.of(composer)), command(fixture, " " + PROFILE_CODE)),
            "MENU_CLONE_REQUEST_INVALID"
        );

        OwnerStoreMenuCloneTransactionResult result = execute(
            service(profile, List.of(composer)),
            command(fixture, PROFILE_CODE)
        );

        assertThat(result.evidence().resultCode()).isEqualTo("MENU_CLONE_COMPLETED");
        assertThat(result.evidence().createdCategoryCount()).isEqualTo(2);
        assertThat(result.evidence().createdStationCount()).isEqualTo(2);
        assertThat(result.evidence().createdItemCount()).isEqualTo(4);
        assertThat(result.evidence().createdOptionCount()).isZero();
        assertThat(result.evidence().targetRevisionBefore()).isEqualTo(1L);
        assertThat(result.evidence().targetRevisionAfter()).isEqualTo(2L);
        assertThat(sourceState(fixture.source().id)).isEqualTo(sourceBefore);

        List<MenuCategory> targetCategories = categoryRepository.findAllByStoreIdOrderByIdAsc(fixture.target().id);
        assertThat(targetCategories).extracting(category -> category.code)
            .containsExactly("TARGET_MAIN", "TARGET_SIDE");
        assertThat(targetCategories).extracting(category -> category.name_en)
            .containsExactly("Target Main", "Target Side");
        assertThat(targetCategories).noneMatch(category -> category.id.equals(fixture.sourceCategory().id));

        List<Station> targetStations = stationRepository.findAllByStoreIdOrderByIdAsc(fixture.target().id);
        assertThat(targetStations).extracting(station -> station.code)
            .containsExactly("PRODUCTION", "SERVICE");
        assertThat(targetStations).extracting(station -> station.name)
            .containsExactly("Production", "Service Counter");
        assertThat(targetStations).noneMatch(station -> station.code.equals("BAR"));
        assertThat(targetStations).noneMatch(station -> station.id.equals(fixture.kitchenStation().id));

        List<MenuItem> targetItems = itemRepository.findAllByStoreIdOrderByIdAsc(fixture.target().id);
        assertThat(targetItems).extracting(item -> item.sku)
            .containsExactly("target_alpha", "target_beta", "target_soda", "target_created");
        MenuItem alpha = targetItems.get(0);
        assertThat(alpha.name_en).isEqualTo("Target Alpha");
        assertThat(alpha.base_price).isEqualByComparingTo("21.00");
        assertThat(alpha.item_type).isEqualTo("source_type");
        assertThat(alpha.cost_per_item).isEqualByComparingTo("3.50");
        assertThat(alpha.is_active).isTrue();
        assertThat(alpha.is_sold_out).isFalse();
        MenuItem fallback = targetItems.get(1);
        assertThat(fallback.item_type).isEqualTo("profile_item");
        assertThat(fallback.cost_per_item).isNull();
        MenuItem created = targetItems.get(3);
        assertThat(created.item_type).isEqualTo("drink");
        assertThat(created.cost_per_item).isNull();
        assertThat(targetItems).noneMatch(item -> Set.of(fixture.alpha().id, fixture.soda().id).contains(item.id));

        StoreMenuCloneCompositionContext context = contextRef.get();
        assertThat(context).isNotNull();
        assertThat(context.profile()).isSameAs(profile);
        assertThat(context.baseGraph().targetItemIdByTargetSku())
            .containsOnlyKeys("target_alpha", "target_beta", "target_soda", "target_created");
        assertThat(context.baseGraph().targetItemIdBySourceId())
            .containsKeys(fixture.alpha().id, fixture.soda().id)
            .doesNotContainKey(fixture.inactiveBeta().id);
        assertThat(context.baseGraph().sourceSnapshot().options())
            .extracting(option -> option.id())
            .containsExactly(fixture.childOption().id, fixture.inactiveOption().id, fixture.parentOption().id);
        assertThat(context.baseGraph().sourceSnapshot().options())
            .anySatisfy(option -> {
                assertThat(option.id()).isEqualTo(fixture.inactiveOption().id);
                assertThat(option.active()).isFalse();
            })
            .anySatisfy(option -> {
                assertThat(option.id()).isEqualTo(fixture.parentOption().id);
                assertThat(option.ownerMenuItemId()).isEqualTo(fixture.parentOwner().id);
                assertThat(option.ownerStoreId()).isEqualTo(fixture.source().id);
            });
        verify(coordinator, times(1)).complete(any(OwnerStoreMenuCloneSuccessEvidence.class));
    }

    @Test
    void dynamicStationFailsWhenSelectedSourceItemsResolveToDifferentStations() {
        Fixture fixture = fixture();
        Station alternate = station(fixture.source(), "SECONDARY_COUNTER", "Secondary", 3, true);
        MenuItem juice = item(
            fixture.source(),
            fixture.sourceCategory(),
            alternate,
            "source_juice",
            "drink",
            true,
            5
        );
        SyntheticProfile profile = defaultProfile(fixture.source().id).withAdditionalItem(new ItemSelection(
            "source_juice",
            SourcePolicy.REQUIRED_SOURCE_CODE,
            "target_juice",
            "TARGET_SIDE",
            "SERVICE",
            null,
            "目标果汁",
            "Target Juice",
            new BigDecimal("7.00"),
            true,
            false,
            3,
            Set.of(ItemRole.DRINK)
        ));

        assertThat(juice.station_id).isEqualTo(alternate.id);
        assertCloneError(
            () -> execute(service(profile, List.of(noopComposer())), command(fixture, PROFILE_CODE)),
            "SOURCE_DRINK_STATION_AMBIGUOUS"
        );
        assertTargetRolledBack(fixture);
    }

    @Test
    void unsafeOrNonEmptyTargetIsRejectedBeforeComposition() {
        Fixture activeTargetFixture = fixture();
        activeTargetFixture.target().status = "active";
        storeRepository.saveAndFlush(activeTargetFixture.target());
        StoreMenuCloneGraphComposer composer = mock(StoreMenuCloneGraphComposer.class);

        assertCloneError(
            () -> execute(
                service(defaultProfile(activeTargetFixture.source().id), List.of(composer)),
                command(activeTargetFixture, PROFILE_CODE)
            ),
            "TARGET_STORE_NOT_READY"
        );
        verify(composer, never()).compose(any());

        cleanDatabase();
        Fixture nonEmptyFixture = fixture();
        category(nonEmptyFixture.target(), "EXISTING", "Existing", 1, true);
        assertCloneError(
            () -> execute(
                service(defaultProfile(nonEmptyFixture.source().id), List.of(noopComposer())),
                command(nonEmptyFixture, PROFILE_CODE)
            ),
            "TARGET_MENU_NOT_EMPTY"
        );
        assertThat(categoryRepository.countAllByStoreId(nonEmptyFixture.target().id)).isOne();
    }

    @Test
    void zeroComposerCompletesBaseGraph() {
        Fixture fixture = fixture();

        OwnerStoreMenuCloneTransactionResult result = execute(
            service(defaultProfile(fixture.source().id), List.of()),
            command(fixture, PROFILE_CODE)
        );

        assertThat(result.evidence().createdOptionCount()).isZero();
        assertThat(itemRepository.countAllByStoreId(fixture.target().id)).isEqualTo(4L);
        assertThat(storeRepository.findMenuRevisionById(fixture.target().id)).isEqualTo(2L);
    }

    @Test
    void duplicateAndFailingComposerRollback() {

        cleanDatabase();
        Fixture duplicateFixture = fixture();
        assertCloneError(
            () -> execute(
                service(defaultProfile(duplicateFixture.source().id), List.of(noopComposer(), noopComposer())),
                command(duplicateFixture, PROFILE_CODE)
            ),
            "TARGET_MENU_VALIDATION_FAILED"
        );
        assertTargetRolledBack(duplicateFixture);

        cleanDatabase();
        Fixture failingFixture = fixture();
        StoreMenuCloneGraphComposer failing = composer(
            "failing-composer",
            StoreMenuCloneGraphComposer.Phase.SOURCE_OPTIONS,
            10,
            PROFILE_CODE,
            context -> {
            throw new IllegalStateException("synthetic composer failure");
        });
        assertThatThrownBy(() -> execute(
            service(defaultProfile(failingFixture.source().id), List.of(failing)),
            command(failingFixture, PROFILE_CODE)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("synthetic composer failure");
        assertTargetRolledBack(failingFixture);
        verify(coordinator, never()).complete(any());
    }

    @Test
    void composerOrderAmbiguityRejectsBeforeAnyComposerRuns() {
        Fixture fixture = fixture();
        List<String> calls = new ArrayList<>();
        StoreMenuCloneGraphComposer first = composer(
            "first",
            StoreMenuCloneGraphComposer.Phase.SOURCE_OPTIONS,
            10,
            PROFILE_CODE,
            context -> {
                calls.add("first");
                return 0;
            }
        );
        StoreMenuCloneGraphComposer second = composer(
            "second",
            StoreMenuCloneGraphComposer.Phase.SOURCE_OPTIONS,
            10,
            PROFILE_CODE,
            context -> {
                calls.add("second");
                return 0;
            }
        );

        assertCloneError(
            () -> execute(
                service(defaultProfile(fixture.source().id), List.of(first, second)),
                command(fixture, PROFILE_CODE)
            ),
            "TARGET_MENU_VALIDATION_FAILED"
        );
        assertThat(calls).isEmpty();
        assertTargetRolledBack(fixture);
    }

    @Test
    void sourceRevisionDriftDetectedAfterCompositionRollsBackAllChanges() {
        Fixture fixture = fixture();
        StoreMenuCloneGraphComposer drifting = composer(
            "revision-drift",
            StoreMenuCloneGraphComposer.Phase.SOURCE_OPTIONS,
            10,
            PROFILE_CODE,
            context -> {
            assertThat(storeRepository.incrementMenuRevision(context.sourceStoreId())).isOne();
            return 0;
        });

        assertCloneError(
            () -> execute(
                service(defaultProfile(fixture.source().id), List.of(drifting)),
                command(fixture, PROFILE_CODE)
            ),
            "SOURCE_MENU_CHANGED"
        );
        assertTargetRolledBack(fixture);
        assertThat(storeRepository.findMenuRevisionById(fixture.source().id)).isEqualTo(1L);
        verify(coordinator, never()).complete(any());
    }

    @Test
    void completionOccursAfterSingleCompositionAndSingleRevisionIncrement() {
        Fixture fixture = fixture();
        List<String> events = new ArrayList<>();
        StoreMenuCloneGraphComposer sourceOptions = composer(
            "source-options",
            StoreMenuCloneGraphComposer.Phase.SOURCE_OPTIONS,
            20,
            PROFILE_CODE,
            context -> {
            events.add("source-options");
            assertThat(categoryRepository.countAllByStoreId(context.targetStoreId())).isEqualTo(2L);
            assertThat(stationRepository.countAllByStoreId(context.targetStoreId())).isEqualTo(2L);
            assertThat(itemRepository.countAllByStoreId(context.targetStoreId())).isEqualTo(4L);
            assertThat(storeRepository.findMenuRevisionById(context.targetStoreId())).isEqualTo(1L);
            return 0;
        });
        StoreMenuCloneGraphComposer profileOverrides = composer(
            "profile-overrides",
            StoreMenuCloneGraphComposer.Phase.PROFILE_OVERRIDES,
            5,
            PROFILE_CODE,
            context -> {
                events.add("profile-overrides");
                return 0;
            }
        );
        doAnswer(invocation -> {
            events.add("complete");
            OwnerStoreMenuCloneSuccessEvidence evidence = invocation.getArgument(0);
            assertThat(evidence.targetRevisionAfter()).isEqualTo(2L);
            assertThat(storeRepository.findMenuRevisionById(fixture.target().id)).isEqualTo(2L);
            return null;
        }).when(coordinator).complete(any());

        execute(
            service(defaultProfile(fixture.source().id), List.of(profileOverrides, sourceOptions)),
            command(fixture, PROFILE_CODE)
        );

        assertThat(events).containsExactly("source-options", "profile-overrides", "complete");
        verify(coordinator, times(1)).complete(any());
        assertThat(storeRepository.findMenuRevisionById(fixture.target().id)).isEqualTo(2L);
    }

    @Test
    void crossStoreParentOwnershipFailsClosedAndRollsBack() {
        Fixture fixture = fixture();
        Store otherStore = store("OTHER", "active", false, "DISABLED");
        MenuCategory otherCategory = category(otherStore, "OTHER", "Other", 1, true);
        Station otherStation = station(otherStore, "OTHER", "Other", 1, true);
        MenuItem otherItem = item(otherStore, otherCategory, otherStation, "other_item", "menu_item", true, 1);
        MenuItemOption crossStoreParent = option(otherItem, "CROSS_PARENT", null, false, 1);
        fixture.childOption().parent_option_id = crossStoreParent.id;
        optionRepository.saveAndFlush(fixture.childOption());

        assertCloneError(
            () -> execute(
                service(defaultProfile(fixture.source().id), List.of(noopComposer())),
                command(fixture, PROFILE_CODE)
            ),
            "SOURCE_OPTION_AMBIGUOUS"
        );
        assertTargetRolledBack(fixture);
    }

    @Test
    void targetOptionParentMustExistOnTheSameTargetItem() {
        Fixture fixture = fixture();
        StoreMenuCloneGraphComposer invalidParent = composer(
            "invalid-parent",
            StoreMenuCloneGraphComposer.Phase.SOURCE_OPTIONS,
            10,
            PROFILE_CODE,
            context -> {
                MenuItem alpha = itemRepository.findById(
                    context.baseGraph().targetItemIdByTargetSku().get("target_alpha")
                ).orElseThrow();
                MenuItem soda = itemRepository.findById(
                    context.baseGraph().targetItemIdByTargetSku().get("target_soda")
                ).orElseThrow();
                MenuItemOption parent = option(alpha, "TARGET_PARENT", null, true, 1);
                option(soda, "TARGET_CHILD", parent.id, true, 2);
                return 2;
            }
        );

        assertCloneError(
            () -> execute(
                service(defaultProfile(fixture.source().id), List.of(invalidParent)),
                command(fixture, PROFILE_CODE)
            ),
            "TARGET_MENU_VALIDATION_FAILED"
        );
        assertTargetRolledBack(fixture);
        assertThat(optionRepository.findAllByMenuItemIdsOrdered(List.of(fixture.alpha().id)))
            .extracting(option -> option.option_code)
            .containsExactly("CHILD", "INACTIVE");
    }

    @Test
    void optionCountAndTargetStoreOwnershipAreValidated() {
        Fixture validFixture = fixture();
        StoreMenuCloneGraphComposer validOptions = composer(
            "valid-options",
            StoreMenuCloneGraphComposer.Phase.SOURCE_OPTIONS,
            10,
            PROFILE_CODE,
            context -> {
                MenuItem alpha = itemRepository.findById(
                    context.baseGraph().targetItemIdByTargetSku().get("target_alpha")
                ).orElseThrow();
                MenuItemOption parent = option(alpha, "TARGET_PARENT", null, true, 1);
                option(alpha, "TARGET_CHILD", parent.id, true, 2);
                return 2;
            }
        );

        OwnerStoreMenuCloneTransactionResult result = execute(
            service(defaultProfile(validFixture.source().id), List.of(validOptions)),
            command(validFixture, PROFILE_CODE)
        );
        assertThat(result.evidence().createdOptionCount()).isEqualTo(2);

        cleanDatabase();
        Fixture wrongStoreFixture = fixture();
        StoreMenuCloneGraphComposer wrongStore = composer(
            "wrong-store",
            StoreMenuCloneGraphComposer.Phase.SOURCE_OPTIONS,
            10,
            PROFILE_CODE,
            context -> {
                option(wrongStoreFixture.alpha(), "SOURCE_SIDE_EFFECT", null, true, 4);
                return 1;
            }
        );
        assertCloneError(
            () -> execute(
                service(defaultProfile(wrongStoreFixture.source().id), List.of(wrongStore)),
                command(wrongStoreFixture, PROFILE_CODE)
            ),
            "TARGET_MENU_VALIDATION_FAILED"
        );
        assertTargetRolledBack(wrongStoreFixture);
        assertThat(optionRepository.findAllByMenuItemIdsOrdered(List.of(wrongStoreFixture.alpha().id)))
            .extracting(option -> option.option_code)
            .doesNotContain("SOURCE_SIDE_EFFECT");
    }

    private StoreMenuCloneTransactionServiceImpl service(
        StoreMenuCloneBaseGraphProfile profile,
        List<StoreMenuCloneGraphComposer> composers
    ) {
        return new StoreMenuCloneTransactionServiceImpl(
            storeRepository,
            categoryRepository,
            stationRepository,
            itemRepository,
            optionRepository,
            menuRevisionService,
            coordinator,
            new StoreMenuCloneProfileRegistry(List.of(profile)),
            composers
        );
    }

    private OwnerStoreMenuCloneTransactionResult execute(
        StoreMenuCloneTransactionServiceImpl service,
        OwnerStoreMenuCloneTransactionCommand command
    ) {
        return transaction().execute(status -> service.execute(command));
    }

    private OwnerStoreMenuCloneTransactionCommand command(Fixture fixture, String profileCode) {
        return new OwnerStoreMenuCloneTransactionCommand(
            901L,
            ORGANIZATION_ID,
            fixture.source().id,
            fixture.target().id,
            profileCode,
            77L
        );
    }

    private void assertCloneError(Runnable action, String expectedCode) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(
                OwnerStoreMenuCloneException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedCode)
            );
    }

    private void assertTargetRolledBack(Fixture fixture) {
        assertThat(categoryRepository.countAllByStoreId(fixture.target().id)).isZero();
        assertThat(stationRepository.countAllByStoreId(fixture.target().id)).isZero();
        assertThat(itemRepository.countAllByStoreId(fixture.target().id)).isZero();
        assertThat(storeRepository.findMenuRevisionById(fixture.target().id)).isEqualTo(1L);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private StoreMenuCloneGraphComposer noopComposer() {
        return composer(
            "noop-composer",
            StoreMenuCloneGraphComposer.Phase.SOURCE_OPTIONS,
            10,
            PROFILE_CODE,
            context -> 0
        );
    }

    private StoreMenuCloneGraphComposer composer(
        String identity,
        StoreMenuCloneGraphComposer.Phase phase,
        int order,
        String profileCode,
        ComposerAction action
    ) {
        return new StoreMenuCloneGraphComposer() {
            @Override
            public String identity() {
                return identity;
            }

            @Override
            public Phase phase() {
                return phase;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public boolean supports(String candidate) {
                return profileCode.equals(candidate);
            }

            @Override
            public int compose(StoreMenuCloneCompositionContext context) {
                return action.compose(context);
            }
        };
    }

    private SyntheticProfile defaultProfile(Long sourceStoreId) {
        return new SyntheticProfile(sourceStoreId, List.of(
            itemSelection(
                "source_alpha",
                SourcePolicy.REQUIRED_SOURCE_CODE,
                "target_alpha",
                "TARGET_MAIN",
                "PRODUCTION",
                null,
                "Target Alpha",
                "21.00",
                1,
                ItemRole.NOODLE
            ),
            itemSelection(
                "source_beta",
                SourcePolicy.CLONE_IF_ACTIVE_OR_CREATE,
                "target_beta",
                "TARGET_MAIN",
                "PRODUCTION",
                "profile_item",
                "Target Beta",
                "22.00",
                2,
                ItemRole.NOODLE
            ),
            itemSelection(
                "source_soda",
                SourcePolicy.REQUIRED_SOURCE_CODE,
                "target_soda",
                "TARGET_SIDE",
                "SERVICE",
                null,
                "Target Soda",
                "4.00",
                1,
                ItemRole.DRINK
            ),
            itemSelection(
                null,
                SourcePolicy.CREATE_ONLY,
                "target_created",
                "TARGET_SIDE",
                "SERVICE",
                "drink",
                "Target Created",
                "6.00",
                2,
                ItemRole.DRINK
            )
        ));
    }

    private ItemSelection itemSelection(
        String sourceSku,
        SourcePolicy sourcePolicy,
        String targetSku,
        String targetCategoryCode,
        String targetStationCode,
        String profileCreatedItemType,
        String targetName,
        String price,
        int sortOrder,
        ItemRole role
    ) {
        return new ItemSelection(
            sourceSku,
            sourcePolicy,
            targetSku,
            targetCategoryCode,
            targetStationCode,
            profileCreatedItemType,
            "ZH " + targetName,
            targetName,
            new BigDecimal(price),
            true,
            false,
            sortOrder,
            Set.of(role)
        );
    }

    private Fixture fixture() {
        Store source = store("SOURCE", "active", false, "DISABLED");
        Store target = store("TARGET", "inactive", false, "DISABLED");
        MenuCategory sourceCategory = category(source, "SOURCE_MAIN", "Source Main", 9, true);
        Station kitchen = station(source, "SOURCE_KITCHEN", "Source Kitchen", 7, true);
        Station drink = station(source, "MOBILE_COUNTER_42", "Mobile Counter", 8, true);
        MenuItem alpha = item(source, sourceCategory, kitchen, "source_alpha", "source_type", true, 11);
        alpha.name_en = "Old Alpha";
        alpha.base_price = new BigDecimal("9.00");
        alpha.cost_per_item = new BigDecimal("3.50");
        alpha.is_sold_out = true;
        alpha = itemRepository.saveAndFlush(alpha);
        MenuItem inactiveBeta = item(source, sourceCategory, kitchen, "source_beta", "old_beta", false, 12);
        MenuItem soda = item(source, sourceCategory, drink, "source_soda", "drink", true, 13);
        MenuItem parentOwner = item(source, sourceCategory, kitchen, "parent_owner", "menu_item", true, 14);
        MenuItemOption parent = option(parentOwner, "PARENT", null, false, 1);
        MenuItemOption child = option(alpha, "CHILD", parent.id, true, 2);
        MenuItemOption inactive = option(alpha, "INACTIVE", null, false, 3);
        return new Fixture(
            source,
            target,
            sourceCategory,
            kitchen,
            drink,
            alpha,
            inactiveBeta,
            soda,
            parentOwner,
            parent,
            child,
            inactive
        );
    }

    private Store store(String code, String status, boolean printingEnabled, String printingMode) {
        LocalDateTime now = LocalDateTime.now();
        Store store = new Store();
        store.organization_id = ORGANIZATION_ID;
        store.code = code;
        store.name = code;
        store.status = status;
        store.printing_enabled = printingEnabled;
        store.printing_mode = printingMode;
        store.menu_revision = 1L;
        store.menu_updated_at = now;
        store.created_at = now;
        store.updated_at = now;
        return storeRepository.saveAndFlush(store);
    }

    private MenuCategory category(Store store, String code, String name, int sortOrder, boolean active) {
        MenuCategory category = new MenuCategory();
        category.store_id = store.id;
        category.code = code;
        category.name_zh = name;
        category.name_en = name;
        category.sort_order = sortOrder;
        category.is_active = active;
        category.created_at = LocalDateTime.now();
        category.updated_at = category.created_at;
        return categoryRepository.saveAndFlush(category);
    }

    private Station station(Store store, String code, String name, int sortOrder, boolean active) {
        Station station = new Station();
        station.store_id = store.id;
        station.code = code;
        station.name = name;
        station.sort_order = sortOrder;
        station.is_active = active;
        station.created_at = LocalDateTime.now();
        station.updated_at = station.created_at;
        return stationRepository.saveAndFlush(station);
    }

    private MenuItem item(
        Store store,
        MenuCategory category,
        Station station,
        String sku,
        String itemType,
        boolean active,
        int sortOrder
    ) {
        MenuItem item = new MenuItem();
        item.store_id = store.id;
        item.category_id = category.id;
        item.station_id = station.id;
        item.sku = sku;
        item.name_zh = sku;
        item.name_en = sku;
        item.item_type = itemType;
        item.base_price = new BigDecimal("10.00");
        item.cost_per_item = new BigDecimal("1.00");
        item.is_active = active;
        item.is_sold_out = false;
        item.sort_order = sortOrder;
        item.created_at = LocalDateTime.now();
        item.updated_at = item.created_at;
        return itemRepository.saveAndFlush(item);
    }

    private MenuItemOption option(
        MenuItem owner,
        String code,
        Long parentId,
        boolean active,
        int sortOrder
    ) {
        MenuItemOption option = new MenuItemOption();
        option.menu_item_id = owner.id;
        option.option_type = "ADD_ON";
        option.option_code = code;
        option.option_group = "ADD_ON";
        option.parent_option_id = parentId;
        option.sort_order = sortOrder;
        option.name_zh = code;
        option.name_en = code;
        option.price_delta = BigDecimal.ONE;
        option.is_active = active;
        option.created_at = LocalDateTime.now();
        option.updated_at = option.created_at;
        return optionRepository.saveAndFlush(option);
    }

    private SourceState sourceState(Long sourceStoreId) {
        Store store = storeRepository.findById(sourceStoreId).orElseThrow();
        return new SourceState(
            store.menu_revision,
            store.menu_updated_at,
            categoryRepository.findAllByStoreIdOrderByIdAsc(sourceStoreId).stream()
                .map(category -> row(
                    category.id,
                    category.store_id,
                    category.code,
                    category.name_zh,
                    category.name_en,
                    category.sort_order,
                    category.is_active,
                    category.created_at,
                    category.updated_at
                ))
                .toList(),
            stationRepository.findAllByStoreIdOrderByIdAsc(sourceStoreId).stream()
                .map(station -> row(
                    station.id,
                    station.store_id,
                    station.code,
                    station.name,
                    station.sort_order,
                    station.is_active,
                    station.created_at,
                    station.updated_at
                ))
                .toList(),
            itemRepository.findAllByStoreIdOrderByIdAsc(sourceStoreId).stream()
                .map(item -> row(
                    item.id,
                    item.store_id,
                    item.category_id,
                    item.station_id,
                    item.sku,
                    item.name_zh,
                    item.name_en,
                    item.item_type,
                    item.base_price,
                    item.cost_per_item,
                    item.is_active,
                    item.is_sold_out,
                    item.sort_order,
                    item.created_at,
                    item.updated_at
                ))
                .toList(),
            optionRepository.findAllByMenuItemIdsOrdered(
                itemRepository.findAllByStoreIdOrderByIdAsc(sourceStoreId).stream().map(item -> item.id).toList()
            ).stream()
                .map(option -> row(
                    option.id,
                    option.menu_item_id,
                    option.option_type,
                    option.option_code,
                    option.option_group,
                    option.parent_option_id,
                    option.sort_order,
                    option.name_zh,
                    option.name_en,
                    option.price_delta,
                    option.is_active,
                    option.created_at,
                    option.updated_at
                ))
                .toList()
        );
    }

    private String row(Object... values) {
        return java.util.Arrays.asList(values).toString();
    }

    private record Fixture(
        Store source,
        Store target,
        MenuCategory sourceCategory,
        Station kitchenStation,
        Station drinkStation,
        MenuItem alpha,
        MenuItem inactiveBeta,
        MenuItem soda,
        MenuItem parentOwner,
        MenuItemOption parentOption,
        MenuItemOption childOption,
        MenuItemOption inactiveOption
    ) {
    }

    private record SourceState(
        Long revision,
        LocalDateTime updatedAt,
        List<String> categories,
        List<String> stations,
        List<String> items,
        List<String> options
    ) {
    }

    private record SyntheticProfile(Long sourceStoreId, List<ItemSelection> items)
        implements StoreMenuCloneBaseGraphProfile {

        @Override
        public String profileCode() {
            return PROFILE_CODE;
        }

        @Override
        public Long sourceStoreId() {
            return sourceStoreId;
        }

        @Override
        public String profileFingerprint() {
            return "SYNTHETIC_CONTRACT_V1";
        }

        @Override
        public List<CategorySelection> categories() {
            return List.of(
                new CategorySelection(
                    CategorySourcePolicy.REQUIRED_SOURCE_CODE,
                    "SOURCE_MAIN",
                    "TARGET_MAIN",
                    "目标主类",
                    "Target Main",
                    true,
                    1
                ),
                new CategorySelection(
                    CategorySourcePolicy.CREATE_ONLY,
                    null,
                    "TARGET_SIDE",
                    "目标小菜",
                    "Target Side",
                    true,
                    2
                )
            );
        }

        @Override
        public List<StationSelection> stations() {
            return List.of(
                new StationSelection(
                    StationSourcePolicy.REQUIRED_SOURCE_CODE,
                    "SOURCE_KITCHEN",
                    "PRODUCTION",
                    "Production",
                    true,
                    1
                ),
                new StationSelection(
                    StationSourcePolicy.UNIQUE_ACTIVE_STATION_FROM_SELECTED_ITEMS,
                    null,
                    "SERVICE",
                    "Service Counter",
                    true,
                    2
                )
            );
        }

        SyntheticProfile withAdditionalItem(ItemSelection item) {
            List<ItemSelection> updated = new ArrayList<>(items);
            updated.add(item);
            return new SyntheticProfile(sourceStoreId, List.copyOf(updated));
        }
    }

    @FunctionalInterface
    private interface ComposerAction {
        int compose(StoreMenuCloneCompositionContext context);
    }
}
