import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  // Standalone: a imagem final leva so o servidor e o que ele usa, sem o
  // node_modules inteiro do build.
  output: 'standalone',
  // As fotos de produto sao URLs externas cadastradas pela loja; o otimizador do
  // Next exigiria lista de dominios permitidos e barraria foto nova sem deploy.
  images: { unoptimized: true },
}

export default nextConfig
