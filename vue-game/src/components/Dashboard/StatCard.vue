<script setup>
import { Icon } from '@iconify/vue'

const props = defineProps({
    icon: {
        type: String,
        default: 'mdi:gamepad-variant'
    },
    value: {
        type: [String, Number],
        required: true
    },
    label: {
        type: String,
        required: true
    },
    color: {
        type: String,
        default: '#667eea'
    },
    extra: {
        type: String,
        default: ''
    },
    type: {
        type: String,
        default: 'normal' // normal | total | time | games
    }
})
</script>

<template>
    <div class="stat-card" :class="type" :style="{ '--stat-color': color }">
        <div v-if="icon.startsWith('mdi')" class="stat-icon-wrapper">
            <Icon :icon="icon" class="stat-icon" />
        </div>
        <div v-else class="stat-icon-wrapper emoji">
            <span class="stat-icon">{{ icon }}</span>
        </div>

        <div class="stat-content">
            <div class="stat-value">{{ value }}</div>
            <div class="stat-label">{{ label }}</div>
        </div>

        <div v-if="extra" class="stat-extra">{{ extra }}</div>
    </div>
</template>

<style scoped>
.stat-card {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 20px;
    background: white;
    border-radius: 12px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    transition: all 0.3s ease;
    position: relative;
    overflow: hidden;
    min-width: 220px;
    flex-shrink: 0;
}

.stat-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 4px;
    height: 100%;
    background: linear-gradient(to bottom, var(--stat-color, #667eea), transparent);
}

.stat-card:hover {
    transform: translateY(-4px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.stat-icon-wrapper {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    flex-shrink: 0;
}

.stat-icon-wrapper.emoji {
    font-size: 36px;
}

.stat-icon {
    font-size: 36px;
    color: var(--stat-color, #667eea);
}

.stat-content {
    flex: 1;
}

.stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #1f2937;
    line-height: 1;
    margin-bottom: 4px;
}

.stat-label {
    font-size: 12px;
    color: #6b7280;
}

.stat-extra {
    font-size: 12px;
    color: #10b981;
    font-weight: 600;
    position: absolute;
    bottom: 8px;
    right: 12px;
}

/* 不同类型的颜色变体 */
.stat-card.total {
    --stat-color: #667eea;
}

.stat-card.time {
    --stat-color: #f59e0b;
}

.stat-card.games {
    --stat-color: #10b981;
}
</style>
