/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** "demo" = BFF simulado no navegador (GitHub Pages); vazio = BFF real na mesma origem. */
  readonly VITE_MODO?: 'demo' | 'bff'
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
