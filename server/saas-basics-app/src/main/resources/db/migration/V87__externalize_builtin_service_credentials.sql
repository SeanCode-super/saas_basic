-- The built-in Java services run with the server module as their working directory.
UPDATE sys_app_service
SET start_cmd = REGEXP_REPLACE(start_cmd, '-DDB_PASSWORD=[^[:space:]]+',
                               '-Dspring.config.additional-location=optional:file:../.env[.properties]')
WHERE service_code IN ('backend-main', 'workflow-app')
  AND driver_type = 'HOST_PROCESS'
  AND start_cmd LIKE '%-DDB_PASSWORD=%';
