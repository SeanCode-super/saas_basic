module.exports = {
  extends: ["stylelint-config-standard-scss", "stylelint-config-recommended-vue/scss"],
  ignoreFiles: ["dist/**", "node_modules/**"],
  rules: {
    "alpha-value-notation": "number",
    "media-feature-range-notation": "prefix",
    "no-descending-specificity": null,
    "selector-class-pattern": null
  }
};
