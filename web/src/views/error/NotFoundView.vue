<template>
  <div class="not-found">
    <h1>404</h1>
    <p>你访问的工作台页面不存在。</p>
    <router-link to="/dashboard">返回平台总览</router-link>
  </div>
</template>

<style scoped lang="scss">
.not-found {
  min-height: 100vh;
  display: grid;
  place-items: center;
  text-align: center;

  h1 {
    margin: 0;
    font-size: 72px;
  }

  p {
    color: var(--sb-text-secondary);
  }
}
</style>
