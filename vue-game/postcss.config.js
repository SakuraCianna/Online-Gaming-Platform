export default {
    plugins: {
        autoprefixer: {
            overrideBrowserslist: [
                'Safari >= 9',
                'iOS >= 9',
                'Chrome >= 60',
                'Firefox >= 60',
                'Edge >= 79',
                '> 1%',
                'not dead'
            ],
            grid: 'autoplace'
        },
        'postcss-px-to-viewport-8-plugin': {
            viewportWidth: 375, // 设计稿宽度（iPhone 标准）
            viewportHeight: 667,
            unitPrecision: 5,
            viewportUnit: 'vw',
            selectorBlackList: ['.ignore-', '.pc-'], // 忽略转换的类名
            minPixelValue: 1,
            mediaQuery: false,
            exclude: [/node_modules/], // 排除第三方库
            include: [/\/src\/components\//] // 对所有组件生效
        }
    }
}