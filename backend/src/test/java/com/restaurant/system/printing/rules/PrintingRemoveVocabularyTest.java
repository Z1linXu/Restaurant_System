package com.restaurant.system.printing.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PrintingRemoveVocabularyTest {
    @Test
    void projectsOnlyStoreSharedSemanticsAndNewMastersAppearWithoutRuleRevision() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:remove_vocab_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("create table store_removes(id bigint,store_id bigint,code varchar,name_zh varchar,sort_order integer)");
        jdbc.update("insert into store_removes values (1,1,'remove_beef','走牛',0),(2,1,'beef','牛肉',1),(3,1,'meat','走肉',2),(4,2,'cilantro','走香',0)");
        var vocabulary = new PrintingRemoveVocabulary(jdbc);
        var content = PrintingRemoveAliasesTest.content("[[\"remove_beef\",\"牛自定\"],[\"carrot_slice\",\"片自定\"]]");
        var rows = vocabulary.entries(1L, content);
        assertThat(rows).extracting(PrintingRemoveVocabulary.Entry::code).containsExactly("beef", "meat");
        assertThat(rows.get(0).default_print_text()).isEqualTo("走牛");
        assertThat(rows.get(0).alias_codes()).contains("beef", "remove_beef");
        assertThat(vocabulary.entries(2L, content)).extracting(PrintingRemoveVocabulary.Entry::code).containsExactly("cilantro");
        jdbc.update("insert into store_removes values (5,1,'tofu','不要豆腐',3)");
        assertThat(vocabulary.entries(1L, content)).extracting(PrintingRemoveVocabulary.Entry::code).containsExactly("beef", "meat", "tofu");
        assertThat(vocabulary.entries(1L, content).get(2).default_print_text()).isEqualTo("不要豆腐");
    }
}
