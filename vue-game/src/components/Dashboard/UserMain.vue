<template>
  <div class="user-main">
    <div class="content-wrapper">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

onMounted(() => {
  
})
</script>

<style scoped>
.user-main {
  width: 100%;
  height: 100%;
  background: #ffffff;
  border-radius: 15px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.content-wrapper {
  width: 100%;
  height: 100%;
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.content-wrapper > * {
  width: 100%;
  height: 100%;
  flex: 1;
}

@media (max-width: 768px) {
  .user-main {
    border-radius: 10px;
    margin: 0;
    box-shadow: none;
  }
}

@media (max-width: 480px) {
  .user-main {
    border-radius: 0;
  }
}
</style>