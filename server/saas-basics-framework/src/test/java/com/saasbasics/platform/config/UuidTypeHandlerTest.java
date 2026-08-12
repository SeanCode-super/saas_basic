package com.saasbasics.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.saasbasics.platform.mybatis.UuidTypeHandler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.Test;

class UuidTypeHandlerTest {

    @Test
    void registersUuidHandlerForCharacterColumnsAndImplicitResultMappings() {
        MybatisConfiguration configuration = new MybatisConfiguration();

        new MybatisPlusConfig().uuidTypeHandlerCustomizer().customize(configuration);

        TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
        assertThat(registry.getTypeHandler(UUID.class, JdbcType.CHAR)).isInstanceOf(UuidTypeHandler.class);
        assertThat(registry.getTypeHandler(UUID.class, JdbcType.VARCHAR)).isInstanceOf(UuidTypeHandler.class);
        assertThat(registry.getTypeHandler(UUID.class)).isInstanceOf(UuidTypeHandler.class);
    }

    @Test
    void writesAndReadsCanonicalUuidText() throws SQLException {
        UUID publicId = UUID.fromString("0198936d-34af-7a32-a6ea-bb58bdcdf005");
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("publicId")).thenReturn(publicId.toString());
        TypeHandler<UUID> handler = new UuidTypeHandler();

        handler.setParameter(statement, 1, publicId, JdbcType.CHAR);
        UUID mapped = handler.getResult(resultSet, "publicId");

        verify(statement).setString(1, publicId.toString());
        assertThat(mapped).isEqualTo(publicId);
    }

    @Test
    void rejectsMalformedDatabaseValues() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(1)).thenReturn("not-a-uuid");
        UuidTypeHandler handler = new UuidTypeHandler();

        assertThatThrownBy(() -> handler.getResult(resultSet, 1))
                .isInstanceOf(ResultMapException.class)
                .hasCauseInstanceOf(SQLException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The database value is not a valid UUID");
    }

    @Test
    void rejectsNonCanonicalUuidTextAcceptedByTheJdkParser() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(1)).thenReturn("1-1-1-1-1");
        UuidTypeHandler handler = new UuidTypeHandler();

        assertThatThrownBy(() -> handler.getResult(resultSet, 1))
                .isInstanceOf(ResultMapException.class)
                .hasCauseInstanceOf(SQLException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsCharacterColumnIntoUuidRecordConstructor() throws SQLException {
        UUID publicId = UUID.fromString("0198936d-34af-7a32-a6ea-bb58bdcdf005");
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:uuid_type_handler;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS uuid_projection");
            statement.execute("CREATE TABLE uuid_projection (public_id CHAR(36) NOT NULL)");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO uuid_projection (public_id) VALUES (?)")) {
                insert.setString(1, publicId.toString());
                insert.executeUpdate();
            }
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        new MybatisPlusConfig().uuidTypeHandlerCustomizer().customize(configuration);
        configuration.addMapper(UuidProjectionMapper.class);
        SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(configuration);

        try (SqlSession session = sessionFactory.openSession()) {
            assertThat(session.getMapper(UuidProjectionMapper.class).select())
                    .isEqualTo(new UuidProjection(publicId));
        }
    }

    interface UuidProjectionMapper {

        @Select("SELECT public_id AS publicId FROM uuid_projection")
        UuidProjection select();
    }

    record UuidProjection(UUID publicId) {
    }
}
