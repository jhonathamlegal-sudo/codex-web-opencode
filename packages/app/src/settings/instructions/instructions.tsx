import { Button } from "@opencode/ui/button"
import { createEffect, createMemo, createSignal, For, Show, type Component } from "solid-js"
import { useLanguage } from "@/runtime/i18n/language"
import { useServerSDK } from "@/runtime/server/client"
import { useModels } from "@/providers/models/models"

type Scope = "global" | "model"
type HistoryEntry = { id: string; createdAt: string; content: string }

const modelKey = (providerID: string, modelID: string) => `${providerID}:${modelID}`

export const SettingsInstructions: Component = () => {
  const language = useLanguage()
  const sdk = useServerSDK()
  const models = useModels()
  const [scope, setScope] = createSignal<Scope>("global")
  const [selectedModel, setSelectedModel] = createSignal("")
  const [content, setContent] = createSignal("")
  const [path, setPath] = createSignal("")
  const [history, setHistory] = createSignal<HistoryEntry[]>([])
  const [busy, setBusy] = createSignal(false)
  const [message, setMessage] = createSignal("")
  const [error, setError] = createSignal("")

  const options = createMemo(() =>
    models
      .list()
      .map((item) => ({
        key: modelKey(item.provider.id, item.id),
        providerID: item.provider.id,
        modelID: item.id,
        label: `${item.provider.name} / ${item.name}`,
      }))
      .sort((a, b) => a.label.localeCompare(b.label)),
  )

  const current = createMemo(() => options().find((item) => item.key === selectedModel()))

  const load = async () => {
    const model = current()
    if (scope() === "model" && !model) return

    setBusy(true)
    setMessage("")
    setError("")
    try {
      const document =
        scope() === "global"
          ? await sdk.api.instructions.global.get()
          : await sdk.api.instructions.model.get({ providerID: model!.providerID, modelID: model!.modelID })
      setContent(document.data.content)
      setPath(document.data.path)
      const entries = await sdk.api.instructions.history({
        scope: scope(),
        ...(model ? { providerID: model.providerID, modelID: model.modelID } : {}),
      })
      setHistory(entries.data)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy(false)
    }
  }

  createEffect(() => {
    if (!selectedModel() && options()[0]) setSelectedModel(options()[0].key)
  })

  createEffect(() => {
    scope()
    selectedModel()
    void load()
  })

  const save = async () => {
    const model = current()
    if (scope() === "model" && !model) return
    setBusy(true)
    setMessage("")
    setError("")
    try {
      const document =
        scope() === "global"
          ? await sdk.api.instructions.global.save({ content: content() })
          : await sdk.api.instructions.model.save({
              providerID: model!.providerID,
              modelID: model!.modelID,
              content: content(),
            })
      setPath(document.data.path)
      setMessage(language.t("settings.instructions.saved"))
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy(false)
    }
  }

  const validatePersistence = async () => {
    const model = current()
    if (scope() === "model" && !model) return
    setBusy(true)
    setMessage("")
    setError("")
    try {
      const expected = content()
      const saved =
        scope() === "global"
          ? await sdk.api.instructions.global.save({ content: expected })
          : await sdk.api.instructions.model.save({
              providerID: model!.providerID,
              modelID: model!.modelID,
              content: expected,
            })
      const reloaded =
        scope() === "global"
          ? await sdk.api.instructions.global.get()
          : await sdk.api.instructions.model.get({ providerID: model!.providerID, modelID: model!.modelID })
      if (!reloaded.data.exists || reloaded.data.content !== expected) {
        throw new Error(language.t("settings.instructions.validationFailed"))
      }
      setContent(reloaded.data.content)
      setPath(reloaded.data.path)
      setMessage(language.t("settings.instructions.validationPassed"))
      await load()
      void saved
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy(false)
    }
  }

  const restore = async (entry: HistoryEntry) => {
    const model = current()
    if (scope() === "model" && !model) return
    setBusy(true)
    setMessage("")
    setError("")
    try {
      const response = await sdk.api.instructions.restore({
        scope: scope(),
        ...(model ? { providerID: model.providerID, modelID: model.modelID } : {}),
        historyID: entry.id,
      })
      if (response.data) {
        setContent(response.data.content)
        setPath(response.data.path)
        setMessage(language.t("settings.instructions.restored"))
      }
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div class="settings-tab-body">
      <div class="settings-tab-header settings-tab-header--stacked">
        <div class="settings-tab-header-row">
          <div class="flex flex-col gap-1">
            <h2 class="settings-tab-title">{language.t("settings.instructions.title")}</h2>
            <span class="text-11-regular text-v2-text-text-muted">
              {language.t("settings.instructions.description")}
            </span>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <Button variant="ghost-muted" size="small" disabled={busy()} onClick={() => void validatePersistence()}>
              {language.t("settings.instructions.validate")}
            </Button>
            <Button variant="primary" size="small" disabled={busy()} onClick={() => void save()}>
              {busy() ? language.t("common.loading") : language.t("settings.instructions.save")}
            </Button>
          </div>
        </div>
      </div>

      <div class="flex flex-col gap-4 px-4 pb-8">
        <div class="flex flex-wrap gap-3">
          <label class="flex min-w-48 flex-1 flex-col gap-1 text-12-medium">
            {language.t("settings.instructions.scope")}
            <select
              class="settings-instructions-select"
              value={scope()}
              onChange={(event) => setScope(event.currentTarget.value as Scope)}
            >
              <option value="global">{language.t("settings.instructions.global")}</option>
              <option value="model">{language.t("settings.instructions.model")}</option>
            </select>
          </label>
          <Show when={scope() === "model"}>
            <label class="flex min-w-64 flex-[2] flex-col gap-1 text-12-medium">
              {language.t("settings.instructions.selectModel")}
              <select
                class="settings-instructions-select"
                value={selectedModel()}
                onChange={(event) => setSelectedModel(event.currentTarget.value)}
              >
                <For each={options()}>{(item) => <option value={item.key}>{item.label}</option>}</For>
              </select>
            </label>
          </Show>
        </div>

        <Show when={path()}>
          <div class="text-11-regular text-v2-text-text-muted break-all">{path()}</div>
        </Show>
        <Show when={error()}>
          <div class="text-12-medium text-red-400">{error()}</div>
        </Show>
        <Show when={message()}>
          <div class="text-12-medium text-green-400">{message()}</div>
        </Show>
        <textarea
          class="min-h-[22rem] w-full resize-y rounded-lg border border-v2-border-base bg-v2-bg-base p-3 font-mono text-12-regular leading-relaxed outline-none focus:border-v2-border-focus"
          value={content()}
          disabled={busy()}
          onInput={(event) => setContent(event.currentTarget.value)}
          spellcheck={false}
        />

        <Show when={history().length > 0}>
          <section class="flex flex-col gap-2">
            <h3 class="text-13-medium">{language.t("settings.instructions.history")}</h3>
            <For each={history()}>
              {(entry) => (
                <div class="flex items-center justify-between gap-3 rounded-md border border-v2-border-base px-3 py-2">
                  <div class="min-w-0">
                    <div class="text-12-medium truncate">{new Date(entry.createdAt).toLocaleString()}</div>
                    <div class="text-11-regular text-v2-text-text-muted truncate">{entry.id}</div>
                  </div>
                  <Button variant="ghost-muted" size="small" disabled={busy()} onClick={() => void restore(entry)}>
                    {language.t("settings.instructions.restore")}
                  </Button>
                </div>
              )}
            </For>
          </section>
        </Show>
      </div>
    </div>
  )
}
