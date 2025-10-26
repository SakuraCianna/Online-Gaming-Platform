import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'

dayjs.locale('zh-cn')

/**
 * 统一时间格式化工具
 */

// 格式化为标准日期时间
export function formatTime(time, format = 'YYYY-MM-DD HH:mm:ss') {
    if (!time) return '未知时间'
    return dayjs(time).format(format)
}

// 格式化为日期
export function formatDate(time) {
    if (!time) return '未知日期'
    return dayjs(time).format('YYYY-MM-DD')
}

// 格式化为相对时间
export function formatRelativeTime(time) {
    if (!time) return '未知'
    const now = dayjs()
    const target = dayjs(time)
    const diffInSeconds = now.diff(target, 'second')

    if (diffInSeconds < 60) return '刚刚'
    if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}分钟前`
    if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}小时前`
    if (diffInSeconds < 604800) return `${Math.floor(diffInSeconds / 86400)}天前`

    return formatDate(time)
}

// 格式化游戏时长（秒 -> 可读格式）
export function formatDuration(seconds) {
    if (!seconds || seconds < 0) return '0秒'

    const hours = Math.floor(seconds / 3600)
    const minutes = Math.floor((seconds % 3600) / 60)
    const secs = seconds % 60

    if (hours > 0) {
        return `${hours}小时${minutes}分${secs}秒`
    } else if (minutes > 0) {
        return `${minutes}分${secs}秒`
    } else {
        return `${secs}秒`
    }
}

// 导出dayjs实例，供其他地方使用
export { dayjs }

