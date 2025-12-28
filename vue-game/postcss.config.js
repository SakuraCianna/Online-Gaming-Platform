export default {
  plugins: {
    autoprefixer: {
      overrideBrowserslist: [
        'Chrome >= 60',
        'Firefox >= 60',
        'Safari >= 12',
        'Edge >= 79',
        'iOS >= 12',
        '> 1%',
        'not dead'
      ],
      grid: 'autoplace'
    }
  }
}