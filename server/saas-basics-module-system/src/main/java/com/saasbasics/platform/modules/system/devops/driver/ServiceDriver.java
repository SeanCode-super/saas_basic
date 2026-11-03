package com.saasbasics.platform.modules.system.devops.driver;

import com.saasbasics.platform.modules.system.devops.entity.AppServiceEntity;
import java.util.List;

public interface ServiceDriver {

    String getDriverType();

    boolean isAvailable();

    ServiceRuntimeInfo inspect(AppServiceEntity service);

    int start(AppServiceEntity service) throws Exception;

    void stop(AppServiceEntity service, int timeoutSeconds) throws Exception;

    void restart(AppServiceEntity service, int timeoutSeconds) throws Exception;

    void kill(AppServiceEntity service) throws Exception;

    List<String> tailLogs(AppServiceEntity service, int maxLines);
}
