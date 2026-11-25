import org.flowable.engine.ProcessEngineConfiguration;

public final class FlowableSchemaCheck {
    public static void main(String[] args) {
        if (args.length != 2 || !args[0].matches("init|verify") || !args[1].matches("[0-9]{1,5}")) {
            throw new IllegalArgumentException("Expected init|verify and the private MySQL port");
        }
        String catalog = args[0].equals("init") ? "recovered" : "exportcheck";
        var config = ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration();
        config.setJdbcDriver("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://127.0.0.1:" + args[1] + "/" + catalog
                + "?useSSL=false&allowPublicKeyRetrieval=true&nullDatabaseMeansCurrent=true");
        config.setDatabaseCatalog(catalog);
        config.setJdbcUsername("root");
        config.setJdbcPassword(System.getenv("FLOWABLE_REHEARSAL_PASSWORD"));
        config.setDatabaseSchemaUpdate(args[0].equals("init") ? "true" : "false");
        config.setAsyncExecutorActivate(false);
        var engine = config.buildProcessEngine();
        try {
            var repository = engine.getRepositoryService();
            var definitions = repository.createProcessDefinitionQuery().list();
            for (var definition : definitions) {
                if (repository.getBpmnModel(definition.getId()).getProcessById(definition.getKey()) == null) {
                    throw new IllegalStateException("Missing BPMN model for " + definition.getId());
                }
            }
            long activeTasks = engine.getTaskService().createTaskQuery().count();
            long running = engine.getRuntimeService().createProcessInstanceQuery().count();
            long historical = engine.getHistoryService().createHistoricProcessInstanceQuery().count();
            System.out.printf("FLOWABLE_COUNTS %d %d %d %d%n", definitions.size(), activeTasks, running, historical);
        } finally {
            engine.close();
        }
    }
}
