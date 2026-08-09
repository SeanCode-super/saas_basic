<script setup lang="ts">
defineProps<{
  eyebrow: string;
  title: string;
  description: string;
  summary: Array<{
    label: string;
    value: string | number;
    note?: string;
  }>;
}>();
</script>

<template>
  <section class="module-workbench">
    <header class="module-workbench__hero">
      <div class="module-workbench__hero-copy">
        <span class="module-workbench__eyebrow">{{ eyebrow }}</span>
        <span class="module-workbench__stage">MODULE COMMAND</span>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
      <div class="module-workbench__hero-side">
        <div class="module-workbench__actions">
          <slot name="actions" />
        </div>
        <slot name="spotlight" />
      </div>
    </header>

    <div class="module-workbench__summary-band">
      <div class="module-workbench__summary-copy">
        <span>RUNTIME FABRIC</span>
        <strong>模块运行态</strong>
        <p>先看当前治理信号和底座规模，再进入具体策略与台账。</p>
      </div>
      <div class="module-workbench__summary">
      <article v-for="item in summary" :key="item.label" class="module-workbench__summary-item">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <p v-if="item.note">{{ item.note }}</p>
      </article>
      </div>
    </div>

    <div class="module-workbench__shell">
      <aside class="module-workbench__rail">
        <slot name="rail" />
      </aside>
      <div class="module-workbench__content">
        <slot />
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.module-workbench,
.module-workbench__summary,
.module-workbench__shell,
.module-workbench__summary-band {
  display: grid;
  gap: 16px;
}

.module-workbench__hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 26px;
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-lg);
  background:
    radial-gradient(circle at right top, rgb(30 94 255 / 0.12), transparent 28%),
    radial-gradient(circle at left bottom, rgb(199 134 47 / 0.1), transparent 20%),
    linear-gradient(180deg, rgb(255 255 255 / 0.98), rgb(245 249 255 / 0.94));
  box-shadow: var(--sb-shadow-md);
  position: relative;
  overflow: hidden;

  &::after {
    content: "";
    position: absolute;
    inset: 0;
    background-image:
      linear-gradient(90deg, transparent 0, transparent calc(100% - 1px), rgb(15 27 45 / 0.025) calc(100% - 1px)),
      linear-gradient(180deg, transparent 0, transparent calc(100% - 1px), rgb(15 27 45 / 0.025) calc(100% - 1px));
    background-size: 128px 128px;
    mask-image: linear-gradient(135deg, rgb(0 0 0 / 0.16), transparent 74%);
    pointer-events: none;
  }
}

.module-workbench__hero-copy {
  display: grid;
  gap: 9px;
  min-width: 0;

  h1 {
    margin: 0;
    font-size: 32px;
    line-height: 1.08;
    letter-spacing: -0.04em;
  }

  p {
    margin: 0;
    color: var(--sb-text-secondary);
    line-height: 1.7;
    max-width: 760px;
  }
}

.module-workbench__hero-side {
  display: grid;
  gap: 14px;
  justify-items: end;
}

.module-workbench__spotlight,
.module-workbench__spotlight-grid {
  display: grid;
  gap: 10px;
}

.module-workbench__spotlight {
  width: min(380px, 100%);
  padding: 16px;
  border: 1px solid rgb(30 94 255 / 0.12);
  border-radius: var(--sb-radius-md);
  background: rgb(255 255 255 / 0.66);
  backdrop-filter: blur(10px);

  strong {
    display: block;
    font-size: 14px;
  }

  p {
    margin: 0;
    color: var(--sb-text-secondary);
    font-size: 12px;
    line-height: 1.6;
  }
}

.module-workbench__spotlight-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.module-workbench__spotlight-item {
  padding: 12px;
  border-radius: 12px;
  background: rgb(248 250 255 / 0.92);
  border: 1px solid rgb(30 94 255 / 0.08);

  span,
  strong {
    display: block;
  }

  span {
    color: var(--sb-text-tertiary);
    font-size: 11px;
  }

  strong {
    margin-top: 6px;
    font-size: 16px;
    line-height: 1.2;
  }
}

.module-workbench__eyebrow {
  display: inline-flex;
  color: var(--sb-primary-strong);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.module-workbench__stage {
  display: inline-flex;
  width: fit-content;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgb(15 27 45 / 0.06);
  color: var(--sb-text-secondary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.module-workbench__actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.module-workbench__summary-band {
  grid-template-columns: 240px minmax(0, 1fr);
  align-items: stretch;
}

.module-workbench__summary-copy {
  display: grid;
  align-content: start;
  gap: 6px;
  padding: 18px 20px;
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  background: linear-gradient(180deg, rgb(248 250 255 / 0.96), rgb(241 247 255 / 0.92));

  span {
    color: var(--sb-primary-strong);
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.16em;
  }

  strong {
    font-size: 16px;
  }

  p {
    margin: 0;
    color: var(--sb-text-secondary);
    line-height: 1.65;
    font-size: 13px;
  }
}

.module-workbench__summary {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.module-workbench__summary-item {
  display: grid;
  gap: 6px;
  padding: 18px 18px 16px;
  border: 1px solid rgb(30 94 255 / 0.08);
  border-radius: var(--sb-radius-md);
  background:
    linear-gradient(180deg, rgb(255 255 255 / 0.98), rgb(246 250 255 / 0.92)),
    white;
  backdrop-filter: blur(10px);
  box-shadow: var(--sb-shadow-sm);
  position: relative;

  &::before {
    content: "";
    position: absolute;
    inset: 0 auto 0 0;
    width: 3px;
    background: linear-gradient(180deg, var(--sb-primary-color), transparent 72%);
    opacity: 0.75;
  }

  span,
  p {
    display: block;
    margin: 0;
    color: var(--sb-text-tertiary);
  }

  strong {
    display: block;
    margin-top: 2px;
    font-size: 26px;
    line-height: 1.05;
    color: var(--sb-text-primary);
  }

  p {
    font-size: 12px;
    line-height: 1.5;
  }
}

.module-workbench__shell {
  grid-template-columns: 300px 1fr;
  align-items: start;
}

.module-workbench__rail,
.module-workbench__content {
  display: grid;
  gap: 18px;
}

.module-workbench__rail {
  position: sticky;
  top: 18px;
}

@media (max-width: 1280px) {
  .module-workbench__summary-band {
    grid-template-columns: 1fr;
  }

  .module-workbench__summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .module-workbench__shell {
    grid-template-columns: 1fr;
  }

  .module-workbench__rail {
    position: static;
  }
}

@media (max-width: 860px) {
  .module-workbench__hero {
    flex-direction: column;
    padding: 20px;
  }

  .module-workbench__hero-side {
    justify-items: start;
    width: 100%;
  }

  .module-workbench__spotlight,
  .module-workbench__spotlight-grid {
    width: 100%;
  }

  .module-workbench__actions {
    justify-content: flex-start;
  }

  .module-workbench__summary {
    grid-template-columns: 1fr;
  }
}
</style>
