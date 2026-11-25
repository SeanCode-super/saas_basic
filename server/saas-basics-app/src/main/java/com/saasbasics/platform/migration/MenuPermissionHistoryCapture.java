package com.saasbasics.platform.migration;

import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/** Preserve the rows V83 will remove, including earlier revocations. */
@Component
public final class MenuPermissionHistoryCapture extends BaseCallback {
    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE && context.getMigrationInfo() != null
                && "V83__platform_qa_regression_fixes.sql".equals(context.getMigrationInfo().getScript());
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return false;
    }

    @Override
    public void handle(Event event, Context context) {
        ScriptUtils.executeSqlScript(context.getConnection(),
                new ClassPathResource("db/repair/capture_v83_menu_permissions.sql"));
    }
}
