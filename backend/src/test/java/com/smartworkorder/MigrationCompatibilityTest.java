package com.smartworkorder;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MigrationCompatibilityTest {
    @Test void upgradingV2KeepsExistingDescriptionsAndAllowsManualNarrativesToBeAbsent() throws Exception {
        String url="jdbc:h2:mem:migration_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("2").load().migrate();
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            statement.executeUpdate("INSERT INTO app_users VALUES (1,'legacy','原用户','test-hash','USER',CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO tickets (number,owner_id,request_key,original_description,summary,category,priority,"
                    +"tags_json,analysis_source,status,created_at,updated_at) VALUES ('LEGACY',1,'old-key','  保留原文  ',"
                    +"'原摘要','设备故障','一般','[]','MANUAL','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        }
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            try(var result=statement.executeQuery("SELECT original_description,impact_scope FROM tickets WHERE number='LEGACY'")) {
                assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("  保留原文  ");
                assertThat(result.getString(2)).isNull();
            }
            statement.executeUpdate("INSERT INTO tickets (number,owner_id,request_key,summary,category,priority,"
                    +"tags_json,analysis_source,status,created_at,updated_at) VALUES ('MANUAL',1,'new-key',"
                    +"'手动摘要','设备故障','一般','[]','MANUAL','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            try(var result=statement.executeQuery("SELECT original_description FROM tickets WHERE number='MANUAL'")) {
                assertThat(result.next()).isTrue();assertThat(result.getString(1)).isNull();
            }
        }
    }
}
