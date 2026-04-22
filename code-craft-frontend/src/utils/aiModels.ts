export const DEFAULT_AI_MODEL = 'qwen3.6-plus'

export const AI_MODEL_OPTIONS = [
  { label: 'Qwen3.6 Plus', value: 'qwen3.6-plus' },
  { label: 'Qwen3.6 Plus 2026-04-02', value: 'qwen3.6-plus-2026-04-02' },
  { label: 'Qwen3.6 Max Preview', value: 'qwen3.6-max-preview' },
  { label: 'Qwen3.6 Flash', value: 'qwen3.6-flash' },
  { label: 'Qwen3.6 35B A3B', value: 'qwen3.6-35b-a3b' },
  { label: 'Qwen3.5 Plus 2026-02-15', value: 'qwen3.5-plus-2026-02-15' },
  { label: 'Kimi K2.6', value: 'kimi-k2.6' },
  { label: 'Kimi K2.5', value: 'kimi-k2.5' },
  { label: 'MiniMax M2.1', value: 'MiniMax-M2.1' },
] as const

export const formatAiModel = (modelKey?: string) => {
  return AI_MODEL_OPTIONS.find((item) => item.value === modelKey)?.label || modelKey || DEFAULT_AI_MODEL
}
