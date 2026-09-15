ALTER TABLE public.menu_items
    ADD COLUMN default_combo_egg_component_code VARCHAR(120);

COMMENT ON COLUMN public.menu_items.default_combo_egg_component_code IS
    'Store-local COMBO_EGG component code; NULL inherits the Store group default.';
