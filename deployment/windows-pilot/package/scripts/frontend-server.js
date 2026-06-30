const http = require('http')
const net = require('net')
const fs = require('fs')
const path = require('path')

const rootDir = path.resolve(__dirname, '..')
const distDir = path.join(rootDir, 'frontend', 'dist')
const frontendPort = Number(process.env.FRONTEND_PORT || 5173)
const backendHost = process.env.BACKEND_HOST || '127.0.0.1'
const backendPort = Number(process.env.BACKEND_PORT || 8080)

const mimeTypes = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
}

function send(res, statusCode, body, headers = {}) {
  res.writeHead(statusCode, headers)
  res.end(body)
}

function proxyHttp(req, res) {
  const options = {
    hostname: backendHost,
    port: backendPort,
    path: req.url,
    method: req.method,
    headers: {
      ...req.headers,
      host: `${backendHost}:${backendPort}`,
    },
  }

  const proxyReq = http.request(options, (proxyRes) => {
    res.writeHead(proxyRes.statusCode || 502, proxyRes.headers)
    proxyRes.pipe(res)
  })

  proxyReq.on('error', (error) => {
    send(res, 502, `Backend proxy failed: ${error.message}`, { 'Content-Type': 'text/plain; charset=utf-8' })
  })

  req.pipe(proxyReq)
}

function serveStatic(req, res) {
  const urlPath = decodeURIComponent((req.url || '/').split('?')[0])
  const safePath = path.normalize(urlPath).replace(/^(\.\.[/\\])+/, '')
  let filePath = path.join(distDir, safePath)

  if (!filePath.startsWith(distDir)) {
    send(res, 403, 'Forbidden', { 'Content-Type': 'text/plain; charset=utf-8' })
    return
  }

  if (fs.existsSync(filePath) && fs.statSync(filePath).isDirectory()) {
    filePath = path.join(filePath, 'index.html')
  }

  if (!fs.existsSync(filePath)) {
    filePath = path.join(distDir, 'index.html')
  }

  fs.readFile(filePath, (error, data) => {
    if (error) {
      send(res, 500, `Static file failed: ${error.message}`, { 'Content-Type': 'text/plain; charset=utf-8' })
      return
    }
    const ext = path.extname(filePath).toLowerCase()
    send(res, 200, data, {
      'Content-Type': mimeTypes[ext] || 'application/octet-stream',
      'Cache-Control': ext === '.html' ? 'no-cache' : 'public, max-age=31536000, immutable',
    })
  })
}

const server = http.createServer((req, res) => {
  if ((req.url || '').startsWith('/api') || (req.url || '').startsWith('/ws')) {
    proxyHttp(req, res)
    return
  }
  serveStatic(req, res)
})

server.on('upgrade', (req, socket, head) => {
  if (!(req.url || '').startsWith('/ws')) {
    socket.destroy()
    return
  }

  const upstream = net.connect(backendPort, backendHost, () => {
    upstream.write(`${req.method} ${req.url} HTTP/${req.httpVersion}\r\n`)
    Object.entries(req.headers).forEach(([key, value]) => {
      upstream.write(`${key}: ${value}\r\n`)
    })
    upstream.write(`host: ${backendHost}:${backendPort}\r\n`)
    upstream.write('\r\n')
    if (head?.length) {
      upstream.write(head)
    }
    socket.pipe(upstream).pipe(socket)
  })

  upstream.on('error', () => socket.destroy())
})

server.listen(frontendPort, '0.0.0.0', () => {
  console.log(`Restaurant POS frontend serving ${distDir}`)
  console.log(`Frontend: http://0.0.0.0:${frontendPort}`)
  console.log(`Proxy: /api and /ws -> http://${backendHost}:${backendPort}`)
})
