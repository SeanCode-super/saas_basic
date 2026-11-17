package com.saasbasics.platform.security.vault;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas-basics.security.vault")
public class VaultCryptoProperties {

    public static final String DEV_DEFAULT_MASTER_KEY = "tianshu-saas-basics-local-vault-master-key-change-in-prod";

    private boolean enabled = true;
    private String activeKeyVersion = "v1";
    private String masterKey = DEV_DEFAULT_MASTER_KEY;
    private Map<String, String> previousKeys = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getActiveKeyVersion() {
        return activeKeyVersion;
    }

    public void setActiveKeyVersion(String activeKeyVersion) {
        this.activeKeyVersion = activeKeyVersion;
    }

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public Map<String, String> getPreviousKeys() {
        return previousKeys;
    }

    public void setPreviousKeys(Map<String, String> previousKeys) {
        this.previousKeys = previousKeys;
    }
}
