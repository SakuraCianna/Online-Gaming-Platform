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
        }
    }
}