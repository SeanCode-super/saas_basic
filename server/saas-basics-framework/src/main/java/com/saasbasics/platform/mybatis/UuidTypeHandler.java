package com.saasbasics.platform.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(UUID.class)
@MappedJdbcTypes(value = {JdbcType.CHAR, JdbcType.VARCHAR}, includeNullJdbcType = true)
public final class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement statement,
                                    int parameterIndex,
                                    UUID parameter,
                                    JdbcType jdbcType) throws SQLException {
        statement.setString(parameterIndex, parameter.toString());
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public UUID getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private UUID parse(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("UUID text is not canonical");
            }
            return uuid;
        } catch (IllegalArgumentException exception) {
            throw new SQLException("The database value is not a valid UUID", exception);
        }
    }
}
