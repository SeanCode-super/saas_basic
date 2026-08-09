module.exports = {
  extends: ["stylelint-config-standard-scss", "stylelint-config-recommended-vue/scss"],
  ignoreFiles: ["dist/**", "node_modules/**"],
  rules: {
    "selector-class-pattern": null
  }
};
