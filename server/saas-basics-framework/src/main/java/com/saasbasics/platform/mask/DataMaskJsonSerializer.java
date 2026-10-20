package com.saasbasics.platform.mask;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.mask.DataMaskType;
import com.saasbasics.platform.common.mask.DataMaskUtils;

import java.io.IOException;
import java.util.Objects;

/**
 * Jackson 数据脱敏序列化器。
 */
public class DataMaskJsonSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private final DataMaskType type;
    private final int prefixKeep;
    private final int suffixKeep;
    private final char maskChar;
    private final String unmaskPermission;

    public DataMaskJsonSerializer() {
        this(DataMaskType.CUSTOM, 1, 1, '*', "data:unmask");
    }

    public DataMaskJsonSerializer(DataMaskType type, int prefixKeep, int suffixKeep, char maskChar, String unmaskPermission) {
        this.type = type;
        this.prefixKeep = prefixKeep;
        this.suffixKeep = suffixKeep;
        this.maskChar = maskChar;
        this.unmaskPermission = unmaskPermission;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        // 判断当前登录主体是否有豁免查看明文权限
        if (canViewUnmasked()) {
            gen.writeString(value);
            return;
        }

        String maskedValue = DataMaskUtils.mask(value, type, prefixKeep, suffixKeep, maskChar);
        gen.writeString(maskedValue);
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property != null) {
            if (Objects.equals(property.getType().getRawClass(), String.class)) {
                DataMask annotation = property.getAnnotation(DataMask.class);
                if (annotation == null) {
                    annotation = property.getContextAnnotation(DataMask.class);
                }
                if (annotation != null) {
                    return new DataMaskJsonSerializer(
                            annotation.type(),
                            annotation.prefixKeep(),
                            annotation.suffixKeep(),
                            annotation.maskChar(),
                            annotation.unmaskPermission()
                    );
                }
            }
            return prov.findValueSerializer(property.getType(), property);
        }
        return prov.findNullValueSerializer(null);
    }

    private boolean canViewUnmasked() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            return false;
        }
        if (principal.isSuperAdmin() || "PLATFORM".equalsIgnoreCase(principal.userType())) {
            return true;
        }
        if (unmaskPermission == null || unmaskPermission.isBlank()) {
            return false;
        }
        return principal.permissions() != null && principal.permissions().contains(unmaskPermission);
    }
}
