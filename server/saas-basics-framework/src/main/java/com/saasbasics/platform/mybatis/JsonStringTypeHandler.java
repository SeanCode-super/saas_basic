package com.saasbasics.platform.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * 针对 MySQL JSON 列的字符串 TypeHandler。
 * 核心机制：当传入 null、空字符串 "" 或空白字符串时，自动转为 SQL NULL 设入 PreparedStatement，
 * 彻底杜绝 MySQL 报 "Data truncation: Invalid JSON text: The document is empty." 错误。
 */
@MappedTypes(String.class)
@MappedJdbcTypes(value = {JdbcType.VARCHAR, JdbcType.OTHER}, includeNullJdbcType = true)
public class JsonStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement statement,
                                    int parameterIndex,
                                    String parameter,
                                    JdbcType jdbcType) throws SQLException {
        if (parameter == null || parameter.trim().isEmpty()) {
            statement.setNull(parameterIndex, Types.VARCHAR);
        } else {
            statement.setString(parameterIndex, parameter.trim());
        }
    }

    @Override
    public String getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return statement.getString(columnIndex);
    }
}
