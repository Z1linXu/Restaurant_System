# Uber Root Mapping — Staging Store 1 / 2026-10-02

Read-only DB observation: 2026-10-03T03:07:26.336264+00:00. Menu root inventory: approved final mapping table; candidates below are that existing review, not new name matching. 41 roots; 5 MAP, 36 UNMAPPED; 226 persisted context rules unchanged.

`9c8260ac-717a-4d20-80b5-aeef17613186` remains UNMAPPED: `beef_chow_mein` is only an unapproved candidate. No mapping writes in this repair. New accepted Mirror orders use frozen raw GRAB fallback and `UNMAPPED_ROUTE_REVIEW`; HOT routing is not guessed.

## CURRENTLY_MAPPED_ROOTS

| Uber Name | Uber Stable ID | Current State | Local Target / Candidate | Persisted | Fallback Behavior |
| --- | --- | --- | --- | --- | --- |
| Beef Lanzhou Noodles Special Combo | `eca7fbf3-a666-454b-b36e-7d380e80b49d` | MAP | traditional_beef_noodle + implicit combo | YES | Local semantics; unknown modifiers retained raw |
| Cucumber Mix With Home Made Spicy Sauce | `0cbf47bd-5d4c-456a-900e-7da7f6f8f54b` | MAP | cucumber_salad | YES | Local semantics; unknown modifiers retained raw |
| Edamame With Preserved Vegetable | `2155b250-58f2-49d2-a971-d149d8a61386` | MAP | edamame | YES | Local semantics; unknown modifiers retained raw |
| Sweet & Sour Mini Fries | `540c3a0b-3830-4bc6-b706-14638c7e6352` | MAP | shredded_potato | YES | Local semantics; unknown modifiers retained raw |
| Traditional Lanzhou Hand-pull Beef Noodle | `b940caa7-6e37-4b3b-b964-3e10151e7903` | MAP | traditional_beef_noodle | YES | Local semantics; unknown modifiers retained raw |

## CURRENTLY_UNMAPPED_ROOTS

| Uber Name | Uber Stable ID | Current State | Local Target / Candidate | Persisted | Fallback Behavior |
| --- | --- | --- | --- | --- | --- |
| Braised Beef Tendon in Brown Sauce with Noodles | `Braised_Beef_Tendon_` | UNMAPPED | Candidate only: braised_beef_tendon_noodle | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Chicken Chow Mein | `d2a77967-74f3-4dfb-9e8e-a236369bb4f6` | UNMAPPED | Candidate only: chicken_chow_mein | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Chicken Chow Mein Combo | `c169f422-a9cc-4758-82a9-0f60eef89b68` | UNMAPPED | Candidate only: chicken_chow_mein | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Chinese Herbal Tea | `745a90de-3e4b-444e-8de4-fa4cefc63eb0` | UNMAPPED | Candidate only: chinese_herbal_tea | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Coke | `4c7162f6-1a21-4545-8eb5-6c49b01af0fd` | UNMAPPED | Candidate only: coke | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Crispy Tempura Shrimp (4pcs) | `Crispy_Tempura_Shrim` | UNMAPPED | Candidate only: tempura_shrimp | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Dandan Noodle Combo (With Peanuts) | `87bbecca-85fa-4681-a3dd-06c8bd97d70a` | UNMAPPED | Candidate only: dan_dan_noodle | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Dandan Noodles (With Peanuts) | `2be2f7be-aa39-4742-bf91-edc9759f19c0` | UNMAPPED | Candidate only: dan_dan_noodle | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Diet Coke | `8c3c9093-c5fb-4d6d-ac0c-140451e9f29a` | UNMAPPED | Candidate only: diet_coke | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Fried Chinese Steamed buns (3pcs) | `Fried_Chinese_Steame` | UNMAPPED | Candidate only: fried_steamed_buns | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Fried Wontons (6 pcs) | `Fried_Wontons_(6_pcs` | UNMAPPED | Candidate only: fried_wontons | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Ginger Ale | `cbcd1dcd-87bb-4bd3-aa95-147817e134c8` | UNMAPPED | Candidate only: canada_dry | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Green Grape Soju | `Green_Grape_Soju` | UNMAPPED | Candidate only: NONE | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Hakutsuru Junmai Ginjo Sake | `Hakutsuru_Junmai_Gin` | UNMAPPED | Candidate only: NONE | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Homemade Lanzhou Beef (With Peanuts) | `71020dd7-1dbc-4711-9852-dcd1191d5eaa` | UNMAPPED | Candidate only: braised_beef_shank_salad | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Ice Tea | `7aad8f91-c26d-4c1d-8609-341f83396317` | UNMAPPED | Candidate only: ice_tea | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Lanzhou Beef Chow Mein (Beef) | `9c8260ac-717a-4d20-80b5-aeef17613186` | UNMAPPED | Candidate only: beef_chow_mein | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Lanzhou Beef Chow Mein Combo | `b3f90251-5066-42c4-bb4c-9a1581a38936` | UNMAPPED | Candidate only: beef_chow_mein | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Lychee Soju | `Good_Day_Lychee_Soju` | UNMAPPED | Candidate only: NONE | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Melon Soju | `Melon_Soju` | UNMAPPED | Candidate only: NONE | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Noodle with Vegetables | `3b8b2764-d492-47e0-9977-107ed4e67d1f` | UNMAPPED | Candidate only: vegetable_noodle | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Noodles with Vegetables Combo | `e72231fb-9029-4539-91d6-b6efbeeb3767` | UNMAPPED | Candidate only: vegetable_noodle | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Original Soju | `Original_Soju` | UNMAPPED | Candidate only: soju | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Peach Soju | `Peach_Soju` | UNMAPPED | Candidate only: NONE | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Rouleaux de printemps frits (3 pcs) | `Rouleaux_de_printemp` | UNMAPPED | Candidate only: fried_spring_rolls | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Sapporo Beer | `Sapporo_Beer` | UNMAPPED | Candidate only: sapporo | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Sayuri Nigori Sake | `Nigori_Sake` | UNMAPPED | Candidate only: NONE | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Sprite | `79f85c57-ddb3-4f3f-87c6-9a07ad4f6af2` | UNMAPPED | Candidate only: NONE | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Stir-fried Tomato Beef Noodle (Beef) | `b11777c1-9d50-44fe-8397-040a5056152b` | UNMAPPED | Candidate only: tomato_chow_mein | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Stir-fried Tomato Beef Noodle Combo | `e45ddd3e-dbf5-449d-8ab6-badbccd4897b` | UNMAPPED | Candidate only: tomato_chow_mein | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Tea Corned Egg | `e8e7be55-3139-4949-8a7e-69a1d52161c5` | UNMAPPED | Candidate only: tea_egg | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Tsingtao Beer | `Tsingtao_Beer` | UNMAPPED | Candidate only: tsingtao_beer | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Vegetable Chow Mein | `842d5987-6945-42ef-a1d9-ed15cad18a0b` | UNMAPPED | Candidate only: vegetable_chow_mein | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Vegetable Chow Mein Combo | `78b60d9c-29fc-4064-9b10-10dd11427df5` | UNMAPPED | Candidate only: vegetable_chow_mein | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Zha Jiang Noodle | `41989752-4e09-4fc5-8d22-96e9427173eb` | UNMAPPED | Candidate only: zha_jiang_noodle | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
| Zhajiang Noodles Combo | `e8188b13-132d-48af-a981-a3dd1476ee01` | UNMAPPED | Candidate only: zha_jiang_noodle | NO | RAW_UBER_FALLBACK → GRAB; HOT=NO; UNMAPPED_ROUTE_REVIEW |
