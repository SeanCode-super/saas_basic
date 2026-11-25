package @PACKAGE@.@CODE@;

import @PACKAGE@.@CODE@.controller.@TYPE@Controller;
import @PACKAGE@.@CODE@.mapper.@TYPE@Mapper;
import @PACKAGE@.@CODE@.service.@TYPE@Service;
import com.saasbasics.platform.common.tenant.TenantApplication;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@AutoConfiguration
@Profile("db")
@Import({@TYPE@Controller.class, @TYPE@Service.class})
@MapperScan(basePackageClasses = @TYPE@Mapper.class,
        nameGenerator = org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator.class)
public class @TYPE@Application implements TenantApplication {
    public String code() { return "@CODE@"; }
    public String name() { return "@LABEL@"; }
    public String description() { return "@LABEL@"; }
    public String componentPath() { return "@/views/industry/IndustryApplication.vue"; }
    public List<WorkspaceField> workspaceFields(int revision) { return List.of(@FIELDS@); }
    public int latestRevision() { return 1; }

    public List<Endpoint> endpoints() {
        String path = "/api/apps/@CODE@";
        return List.of(new Endpoint("query", "查询", "GET", path + "/**"),
                new Endpoint("create", "新增", "POST", path),
                new Endpoint("update", "修改", "PUT", path + "/*"),
                new Endpoint("delete", "删除", "DELETE", path + "/*"));
    }

    public void migrate(long tenantId, int revision) {
        // Revision 1 needs no tenant-specific data backfill.
    }

    @Bean(name = "@CODE@SchemaMigration")
    @DependsOnDatabaseInitialization
    InitializingBean schemaMigration(DataSource dataSource) {
        return () -> Flyway.configure().dataSource(dataSource)
                .locations("classpath:extensions/@CODE@/db")
                .table("ext_@CODE@_history").baselineOnMigrate(true).baselineVersion("0")
                .load().migrate();
    }
}
