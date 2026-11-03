module.exports = {
  extends: ["stylelint-config-standard-scss", "stylelint-config-recommended-vue/scss"],
  ignoreFiles: ["dist/**", "node_modules/**"],
  rules: {
    "alpha-value-notation": "number",
    "media-feature-range-notation": "prefix",
    "no-descending-specificity": null,
    "selector-class-pattern": null,
    "declaration-block-single-line-max-declarations": null,
    "color-function-notation": null,
    "property-no-vendor-prefix": null,
    "color-hex-length": null,
    "shorthand-property-no-redundant-values": null,
    "rule-empty-line-before": null,
    "custom-property-empty-line-before": null,
    "declaration-empty-line-before": null,
    "keyframes-name-pattern": null,
    "declaration-block-no-redundant-longhand-properties": null,
    "no-duplicate-selectors": null
  }
};
