import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import os from 'node:os'

function getRealLocalIPv4() {
  const interfaces = os.networkInterfaces()
  const candidates = []

  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        candidates.push(iface.address)
      }
    }
  }

  const lanIp = candidates.find(ip =>
    ip.startsWith('192.168.') ||
    ip.startsWith('10.') ||
    ip.startsWith('172.')
  );
  if (lanIp) {
    return lanIp;
  }

  if (candidates.length > 0) {
    return candidates[0];
  }

  return 'localhost'
}

function printRealNetworkAddress() {
  return {
    name: 'print-real-network-address',
    configureServer(server) {
      server.httpServer?.once('listening', () => {
        const ipv4 = getRealLocalIPv4()
        const port = server.config.server.port || 5173

        console.log('\n')
        console.log('\x1b[36m%s\x1b[0m', '═══════════════════════════════════════')
        console.log('\x1b[32m%s\x1b[0m', '🌐 局域网访问地址:')
        console.log('\x1b[33m%s\x1b[0m', `   http://${ipv4}:${port}`)
        console.log('\x1b[36m%s\x1b[0m', '═══════════════════════════════════════')
        console.log('\n')
      })
    }
  }
}

export default defineConfig({
  define: {
    global: 'window',
  },
  plugins: [
    vue(),
    printRealNetworkAddress()
  ],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        changeOrigin: true,
        ws: true,
      }
    }
  }
})
