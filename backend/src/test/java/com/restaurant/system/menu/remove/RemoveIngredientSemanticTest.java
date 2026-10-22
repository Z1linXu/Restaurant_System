package com.restaurant.system.menu.remove;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class RemoveIngredientSemanticTest {
    @Test void registryIdentityOnlyNormalizesOneVerifiedPrefixAndKeepsEighteenDistinctSemantics() {
        assertThat(RemoveSemantics.baselineDefaults()).hasSize(18).containsKeys("beef", "meat", "radish", "carrot", "noodle", "less_noodle");
        assertThat(RemoveSemantics.canonicalSemantic("remove_beef")).isEqualTo("beef").isNotEqualTo(RemoveSemantics.canonicalSemantic("remove_meat"));
        assertThat(RemoveSemantics.canonicalSemantic("remove_carrot_slice")).isEqualTo("carrot_slice");
        assertThat(RemoveSemantics.canonicalSemantic("remove_cucumber_shred")).isEqualTo("cucumber_shred");
        assertThat(RemoveSemantics.canonicalSemantic("remove_crushed_peanut")).isEqualTo("crushed_peanut");
        assertThat(RemoveSemantics.canonicalSemantic("remove_remove_beef")).isEqualTo("remove_beef");
        assertThat(RemoveSemantics.canonicalSemantic("remove_")).isNull();
        assertThat(RemoveSemantics.defaultPrintText("remove_cilantro")).isEqualTo("走香");
    }

    @Test void stableCodesPairExplicitAliasesAndNeverInferFromDisplayNamesOrExpandAllVegetables() {
        assertThat(RemoveSelectionValidator.ingredient("extra_meat")).isEqualTo(RemoveSelectionValidator.ingredient("remove_beef"));
        assertThat(RemoveSelectionValidator.ingredient("extra_noodle")).isEqualTo(RemoveSelectionValidator.ingredient("remove_noodle"));
        assertThat(RemoveSelectionValidator.ingredient("remove_crushed_peanut")).isEqualTo("peanut");
        assertThat(RemoveSelectionValidator.ingredient("carrot_slice")).isEqualTo("carrot");
        assertThat(RemoveSelectionValidator.ingredient("remove_all_vegetables")).isNull();
        assertThat(RemoveSelectionValidator.ingredient("remove_private_spice")).isEqualTo("private_spice");
    }
}
