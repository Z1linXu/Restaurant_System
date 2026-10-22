package com.restaurant.system.menu.remove;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class RemoveIngredientSemanticTest {
    @Test void stableCodesPairExplicitAliasesAndNeverInferFromDisplayNamesOrExpandAllVegetables() {
        assertThat(RemoveSelectionValidator.ingredient("extra_meat")).isEqualTo(RemoveSelectionValidator.ingredient("remove_beef"));
        assertThat(RemoveSelectionValidator.ingredient("extra_noodle")).isEqualTo(RemoveSelectionValidator.ingredient("remove_noodle"));
        assertThat(RemoveSelectionValidator.ingredient("remove_crushed_peanut")).isEqualTo("peanut");
        assertThat(RemoveSelectionValidator.ingredient("carrot_slice")).isEqualTo("carrot");
        assertThat(RemoveSelectionValidator.ingredient("remove_all_vegetables")).isNull();
        assertThat(RemoveSelectionValidator.ingredient("remove_private_spice")).isEqualTo("private_spice");
    }
}
